package com.prismml.grove.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String?,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
)

@Entity(tableName = "models")
data class ModelRecordEntity(
    @PrimaryKey val id: String,
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
    val state: String,
    val errorMessage: String?,
    val isPreset: Boolean,
    val workId: String?,
)

data class ConversationListRow(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val modelId: String?,
    val preview: String?,
)
