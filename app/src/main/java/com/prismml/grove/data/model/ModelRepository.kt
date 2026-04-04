package com.prismml.grove.data.model

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prismml.grove.core.DownloadState
import com.prismml.grove.core.ModelItem
import com.prismml.grove.data.db.ModelDao
import com.prismml.grove.data.db.ModelRecordEntity
import com.prismml.grove.data.download.ModelDownloadWorker
import com.prismml.grove.data.settings.SettingsRepository
import com.prismml.grove.runtime.BonsaiRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class ModelRepository(
    private val context: Context,
    private val dao: ModelDao,
    private val runtime: BonsaiRuntime,
    private val settingsRepository: SettingsRepository,
) {
    private val modelDir = File(context.filesDir, "models").apply { mkdirs() }

    fun observeModels(): Flow<List<ModelItem>> = dao.observeAll().map { rows ->
        rows.map { row ->
            ModelItem(
                id = row.id,
                displayName = row.displayName,
                description = row.description,
                huggingFaceRepo = row.huggingFaceRepo,
                downloadUrl = row.downloadUrl,
                fileName = row.fileName,
                sizeBytes = row.sizeBytes,
                downloadedBytes = row.downloadedBytes,
                architecture = row.architecture,
                contextLength = row.contextLength,
                chatTemplate = row.chatTemplate,
                localPath = row.localPath,
                state = DownloadState.valueOf(row.state),
                errorMessage = row.errorMessage,
                isPreset = row.isPreset,
            )
        }
    }

    suspend fun syncPresets() {
        val existing = dao.observeAll().first().associateBy { it.id }
        dao.upsertAll(
            ModelCatalog.presets.map { preset ->
                existing[preset.id]?.copy(
                    displayName = preset.displayName,
                    description = preset.description,
                    huggingFaceRepo = preset.huggingFaceRepo,
                    downloadUrl = preset.downloadUrl,
                    fileName = preset.fileName,
                    sizeBytes = preset.sizeBytes,
                    architecture = preset.architecture,
                    contextLength = preset.contextLength,
                    chatTemplate = preset.chatTemplate,
                    isPreset = true,
                ) ?: preset
            }
        )
    }

    suspend fun getModel(id: String): ModelItem? = dao.getById(id)?.toItem()

    suspend fun selectDefaultModel(id: String?) {
        settingsRepository.setDefaultModel(id)
    }

    suspend fun enqueueDownload(modelId: String) {
        val model = dao.getById(modelId) ?: return
        val workId = UUID.randomUUID().toString()
        dao.updateProgress(modelId, DownloadState.QUEUED.name, model.downloadedBytes, workId)
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .addTag(modelId)
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to modelId,
                    ModelDownloadWorker.KEY_URL to model.downloadUrl,
                    ModelDownloadWorker.KEY_FILE_NAME to model.fileName,
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(modelId, ExistingWorkPolicy.REPLACE, request)
        dao.updateProgress(modelId, DownloadState.QUEUED.name, model.downloadedBytes, request.id.toString())
    }

    suspend fun cancelDownload(modelId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(modelId)
        val current = dao.getById(modelId) ?: return
        dao.updateProgress(modelId, DownloadState.PAUSED.name, current.downloadedBytes, null)
    }

    suspend fun deleteModel(modelId: String) {
        val model = dao.getById(modelId) ?: return
        model.localPath?.let { File(it).delete() }
        if (model.isPreset) {
            dao.upsert(model.copy(localPath = null, downloadedBytes = 0, state = DownloadState.REMOTE.name, errorMessage = null, workId = null))
        } else {
            dao.deleteById(modelId)
        }
    }

    suspend fun importModel(uri: Uri): Result<ModelItem> = runCatching {
        val imported = runtime.importModel(uri, modelDir, context.contentResolver)
        val metadata = runtime.inspect(imported)
        val id = "imported-${imported.nameWithoutExtension.lowercase().replace(' ', '-')}"
        val entity = ModelRecordEntity(
            id = id,
            displayName = metadata.name ?: imported.nameWithoutExtension,
            description = "Imported local GGUF model",
            huggingFaceRepo = null,
            downloadUrl = null,
            fileName = imported.name,
            sizeBytes = imported.length(),
            downloadedBytes = imported.length(),
            architecture = metadata.architecture,
            contextLength = metadata.contextLength,
            chatTemplate = metadata.chatTemplate,
            localPath = imported.absolutePath,
            state = DownloadState.IMPORTED.name,
            errorMessage = null,
            isPreset = false,
            workId = null,
        )
        dao.upsert(entity)
        entity.toItem()
    }

    fun modelFile(fileName: String): File = File(modelDir, fileName)

    private fun ModelRecordEntity.toItem() = ModelItem(
        id = id,
        displayName = displayName,
        description = description,
        huggingFaceRepo = huggingFaceRepo,
        downloadUrl = downloadUrl,
        fileName = fileName,
        sizeBytes = sizeBytes,
        downloadedBytes = downloadedBytes,
        architecture = architecture,
        contextLength = contextLength,
        chatTemplate = chatTemplate,
        localPath = localPath,
        state = DownloadState.valueOf(state),
        errorMessage = errorMessage,
        isPreset = isPreset,
    )
}
