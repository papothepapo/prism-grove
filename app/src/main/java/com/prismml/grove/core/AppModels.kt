package com.prismml.grove.core

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

enum class DownloadState {
    REMOTE,
    QUEUED,
    DOWNLOADING,
    READY,
    ERROR,
    IMPORTED,
    PAUSED,
}

data class AppSettings(
    val systemPrompt: String = "You are a helpful assistant.",
    val temperature: Float = 0.5f,
    val topK: Int = 20,
    val topP: Float = 0.9f,
    val contextLength: Int = 8192,
    val threadCount: Int = 0,
    val defaultModelId: String? = null,
    val keepScreenOnDuringGeneration: Boolean = true,
    val huggingFaceToken: String = "",
)

data class ConversationSummary(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val modelId: String?,
    val preview: String?,
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val status: String,
)

data class ModelItem(
    val id: String,
    val displayName: String,
    val description: String,
    val huggingFaceRepo: String?,
    val downloadUrl: String?,
    val fileName: String,
    val sizeBytes: Long,
    val downloadedBytes: Long,
    val architecture: String?,
    val contextLength: Int?,
    val chatTemplate: String?,
    val localPath: String?,
    val state: DownloadState,
    val errorMessage: String?,
    val isPreset: Boolean,
)
