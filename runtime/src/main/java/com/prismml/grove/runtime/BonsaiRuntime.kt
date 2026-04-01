package com.prismml.grove.runtime

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class RuntimeConfig(
    val contextLength: Int = 8192,
    val temperature: Float = 0.5f,
    val topK: Int = 20,
    val topP: Float = 0.9f,
    val threadCount: Int = 0,
)

data class ModelMetadata(
    val architecture: String?,
    val contextLength: Int?,
    val chatTemplate: String?,
    val name: String?,
)

sealed class RuntimeState {
    data object Uninitialized : RuntimeState()
    data object Initializing : RuntimeState()
    data object Ready : RuntimeState()
    data object LoadingModel : RuntimeState()
    data object ModelReady : RuntimeState()
    data object Generating : RuntimeState()
    data class Error(val message: String) : RuntimeState()
}

class UnsupportedArchitectureException : Exception("Unsupported model architecture or ABI")

class BonsaiRuntime private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val bridge = NativeBridge()
    private val inspector = GgufInspector()
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Uninitialized)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    @Volatile
    private var cancelled = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    init {
        runBlocking(dispatcher) {
            _state.value = RuntimeState.Initializing
            System.loadLibrary("prism_grove_runtime")
            bridge.init(appContext.applicationInfo.nativeLibraryDir)
            Log.i("BonsaiRuntime", bridge.systemInfo())
            _state.value = RuntimeState.Ready
        }
    }

    suspend fun inspect(file: File): ModelMetadata = withContext(dispatcher) {
        file.inputStream().buffered().use(inspector::readMetadata)
    }

    suspend fun inspect(context: Context, uri: Uri): ModelMetadata = withContext(dispatcher) {
        context.contentResolver.openInputStream(uri)?.buffered()?.use(inspector::readMetadata)
            ?: error("Unable to open $uri")
    }

    suspend fun importModel(uri: Uri, targetDir: File, contentResolver: android.content.ContentResolver): File =
        withContext(dispatcher) {
            val document = DocumentFile.fromSingleUri(appContext, uri)
                ?: error("Unable to resolve imported file")
            val target = File(targetDir, document.name ?: "imported.gguf")
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read imported file")
            target
        }

    suspend fun loadModel(modelPath: String, config: RuntimeConfig) = withContext(dispatcher) {
        unloadInternal()
        _state.value = RuntimeState.LoadingModel
        val loadResult = bridge.load(modelPath)
        if (loadResult != 0) {
            _state.value = RuntimeState.Error("Native model load failed")
            throw UnsupportedArchitectureException()
        }
        val prepareResult = bridge.prepare(
            contextLength = config.contextLength,
            threadCount = config.threadCount,
            temperature = config.temperature,
            topK = config.topK,
            topP = config.topP,
        )
        if (prepareResult != 0) {
            _state.value = RuntimeState.Error("Native runtime preparation failed")
            throw IllegalStateException("Native runtime preparation failed: $prepareResult")
        }
        _state.value = RuntimeState.ModelReady
    }

    suspend fun resetSession(systemPrompt: String) = withContext(dispatcher) {
        val result = bridge.reset(systemPrompt)
        if (result != 0) {
            _state.value = RuntimeState.Error("Failed to reset runtime session")
            throw IllegalStateException("Runtime reset failed: $result")
        }
        _state.value = RuntimeState.ModelReady
    }

    suspend fun appendMessage(role: String, content: String) = withContext(dispatcher) {
        if (content.isBlank()) return@withContext
        val result = bridge.append(role, content)
        if (result != 0) {
            _state.value = RuntimeState.Error("Failed to append history message")
            throw IllegalStateException("Runtime append failed: $result")
        }
        _state.value = RuntimeState.ModelReady
    }

    fun generateReply(prompt: String, predictLength: Int): Flow<String> = flow {
        cancelled = false
        _state.value = RuntimeState.Generating
        val result = bridge.beginCompletion(prompt, predictLength)
        if (result != 0) {
            _state.value = RuntimeState.Error("Failed to begin completion")
            throw IllegalStateException("Runtime completion start failed: $result")
        }
        while (!cancelled) {
            val token = bridge.generateNextToken() ?: break
            if (token.isNotEmpty()) {
                emit(token)
            }
        }
        _state.value = RuntimeState.ModelReady
    }.flowOn(dispatcher)

    fun cancelGeneration() {
        cancelled = true
    }

    suspend fun unload() = withContext(dispatcher) {
        unloadInternal()
        _state.value = RuntimeState.Ready
    }

    private fun unloadInternal() {
        try {
            bridge.unload()
        } catch (_: Throwable) {
        }
    }

    fun destroy() {
        cancelled = true
        runBlocking(dispatcher) {
            unloadInternal()
            bridge.shutdown()
            _state.value = RuntimeState.Uninitialized
        }
        scope.cancel()
    }

    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        @Volatile
        private var instance: BonsaiRuntime? = null

        fun getInstance(context: Context): BonsaiRuntime =
            instance ?: synchronized(this) {
                instance ?: BonsaiRuntime(context).also { instance = it }
            }
    }
}
