package com.prismml.grove.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.prismml.grove.data.db.AppDatabase
import com.prismml.grove.runtime.BonsaiRuntime
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val dao = AppDatabase.get(appContext).modelDao()
    private val runtime = BonsaiRuntime.getInstance(appContext)

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val targetDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val finalFile = File(targetDir, fileName)
        val partialFile = File(targetDir, "$fileName.part")
        val downloaded = if (partialFile.exists()) partialFile.length() else 0L

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (downloaded > 0L) {
            connection.setRequestProperty("Range", "bytes=$downloaded-")
        }
        connection.setRequestProperty("Accept-Encoding", "identity")
        setForeground(createForegroundInfo(fileName, downloaded, 0L))

        return try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                dao.finalizeDownload(modelId, "ERROR", downloaded, null, "HTTP $responseCode", null, null, null)
                return Result.failure()
            }
            val totalBytes = connection.getHeaderFieldLong("Content-Length", -1L).let {
                if (responseCode == HttpURLConnection.HTTP_PARTIAL) it + downloaded else it
            }
            if (totalBytes > 0 && applicationContext.filesDir.usableSpace < totalBytes) {
                dao.finalizeDownload(modelId, "ERROR", downloaded, null, "Insufficient storage", null, null, null)
                return Result.failure()
            }
            dao.updateProgress(modelId, "DOWNLOADING", downloaded, id.toString())

            RandomAccessFile(partialFile, "rw").use { output ->
                if (downloaded > 0) output.seek(downloaded)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = downloaded
                    while (true) {
                        if (isStopped) {
                            dao.updateProgress(modelId, "PAUSED", bytesCopied, null)
                            return Result.success()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        dao.updateProgress(modelId, "DOWNLOADING", bytesCopied, id.toString())
                        setProgress(androidx.work.workDataOf("downloaded" to bytesCopied, "total" to totalBytes))
                        setForeground(createForegroundInfo(fileName, bytesCopied, totalBytes))
                    }
                    if (finalFile.exists()) finalFile.delete()
                    partialFile.renameTo(finalFile)
                    val metadata = runtime.inspect(finalFile)
                    dao.finalizeDownload(
                        modelId,
                        "READY",
                        bytesCopied,
                        finalFile.absolutePath,
                        null,
                        metadata.architecture,
                        metadata.contextLength,
                        metadata.chatTemplate,
                    )
                    Result.success()
                }
            }
        } catch (t: Throwable) {
            val progress = if (partialFile.exists()) partialFile.length() else downloaded
            dao.finalizeDownload(modelId, "ERROR", progress, null, t.message ?: "Download failed", null, null, null)
            Result.retry()
        } finally {
            connection.disconnect()
        }
    }

    private fun createForegroundInfo(fileName: String, downloaded: Long, total: Long): ForegroundInfo {
        createNotificationChannel()
        val progress = if (total > 0L) ((downloaded * 100) / total).toInt() else 0
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $fileName")
            .setContentText(if (total > 0L) "$progress%" else "Preparing download")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, total <= 0L)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 2048
    }
}
