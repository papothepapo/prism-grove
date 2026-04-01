package com.prismml.grove.data.chat

import com.prismml.grove.core.AppSettings
import com.prismml.grove.core.ChatMessage
import com.prismml.grove.core.MessageRole
import com.prismml.grove.data.model.ModelRepository
import com.prismml.grove.data.settings.SettingsRepository
import com.prismml.grove.runtime.BonsaiRuntime
import com.prismml.grove.runtime.RuntimeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import java.io.File

class ChatRepository(
    private val conversationRepository: ConversationRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val runtime: BonsaiRuntime,
) {
    private var loadedModelPath: String? = null
    private var loadedConfig: RuntimeConfig? = null

    suspend fun sendMessage(conversationId: Long, prompt: String, replyToMessageId: Long? = null): Result<Long> = runCatching {
        val settings = settingsRepository.settings.first()
        val selectedModel = settings.defaultModelId?.let { modelRepository.getModel(it) }
            ?: error("No model selected")
        val modelPath = selectedModel.localPath ?: error("Selected model is not downloaded or imported")
        if (!File(modelPath).exists()) error("Selected model file is missing")

        if (replyToMessageId != null) {
            conversationRepository.deleteMessagesAfter(conversationId, replyToMessageId)
        }
        conversationRepository.setConversationModel(conversationId, selectedModel.id)

        ensureModelLoaded(modelPath, settings)
        runtime.resetSession(settings.systemPrompt)
        replayHistory(conversationId)

        val userMessageId = conversationRepository.addMessage(conversationId, MessageRole.USER, prompt)
        val assistantMessageId = conversationRepository.addMessage(conversationId, MessageRole.ASSISTANT, "", "streaming")
        var built = ""
        runtime.generateReply(prompt, predictLength = 512)
            .onCompletion {
                val status = if (built.isBlank()) "canceled" else "complete"
                conversationRepository.updateMessage(assistantMessageId, built, status)
            }
            .collect { token ->
                built += token
                conversationRepository.updateMessage(assistantMessageId, built, "streaming")
            }
        userMessageId
    }

    fun streamReply(conversationId: Long, prompt: String): Flow<Result<Long>> = flow {
        emit(sendMessage(conversationId, prompt))
    }

    suspend fun regenerateFrom(conversationId: Long, anchorMessageId: Long): Result<Unit> = runCatching {
        val messages = conversationRepository.getMessages(conversationId)
        val anchorIndex = messages.indexOfFirst { it.id == anchorMessageId }
        require(anchorIndex >= 0) { "Message not found" }
        val userMessage = messages.subList(0, anchorIndex + 1).lastOrNull { it.role == MessageRole.USER }
            ?: error("No user message available to resend")
        conversationRepository.deleteMessagesAfter(conversationId, userMessage.id)
        sendMessage(conversationId, userMessage.content, userMessage.id).getOrThrow()
        Unit
    }

    suspend fun editMessage(conversationId: Long, messageId: Long, newContent: String): Result<Unit> = runCatching {
        val messages = conversationRepository.getMessages(conversationId)
        val target = messages.firstOrNull { it.id == messageId } ?: error("Message not found")
        require(target.role == MessageRole.USER) { "Only user messages can be edited" }
        conversationRepository.updateMessage(messageId, newContent)
        conversationRepository.deleteMessagesAfter(conversationId, messageId)
        sendMessage(conversationId, newContent, messageId).getOrThrow()
        Unit
    }

    suspend fun deleteMessage(conversationId: Long, messageId: Long) {
        conversationRepository.deleteMessage(conversationId, messageId)
    }

    fun cancelGeneration() {
        runtime.cancelGeneration()
    }

    private suspend fun replayHistory(conversationId: Long) {
        conversationRepository.getMessages(conversationId).forEach { message ->
            when (message.role) {
                MessageRole.USER -> runtime.appendMessage(BonsaiRuntime.ROLE_USER, message.content)
                MessageRole.ASSISTANT -> runtime.appendMessage(BonsaiRuntime.ROLE_ASSISTANT, message.content)
                MessageRole.SYSTEM -> runtime.appendMessage(BonsaiRuntime.ROLE_SYSTEM, message.content)
            }
        }
    }

    private suspend fun ensureModelLoaded(modelPath: String, settings: AppSettings) {
        val config = RuntimeConfig(
            contextLength = settings.contextLength,
            temperature = settings.temperature,
            topK = settings.topK,
            topP = settings.topP,
            threadCount = settings.threadCount,
        )
        if (loadedModelPath == modelPath && loadedConfig == config) {
            return
        }
        runtime.loadModel(modelPath, config)
        loadedModelPath = modelPath
        loadedConfig = config
    }
}
