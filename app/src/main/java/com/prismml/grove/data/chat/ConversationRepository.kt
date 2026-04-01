package com.prismml.grove.data.chat

import com.prismml.grove.core.ChatMessage
import com.prismml.grove.core.ConversationSummary
import com.prismml.grove.core.MessageRole
import com.prismml.grove.data.db.ConversationDao
import com.prismml.grove.data.db.ConversationEntity
import com.prismml.grove.data.db.MessageDao
import com.prismml.grove.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    fun observeConversations(): Flow<List<ConversationSummary>> =
        conversationDao.observeConversations().map { rows ->
            rows.map {
                ConversationSummary(
                    id = it.id,
                    title = it.title,
                    updatedAt = it.updatedAt,
                    modelId = it.modelId,
                    preview = it.preview,
                )
            }
        }

    suspend fun createConversation(modelId: String?): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(
            ConversationEntity(
                title = "New Chat",
                createdAt = now,
                updatedAt = now,
                modelId = modelId,
            )
        )
    }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        messageDao.observeMessages(conversationId).map { rows ->
            rows.map {
                ChatMessage(
                    id = it.id,
                    conversationId = it.conversationId,
                    role = MessageRole.valueOf(it.role),
                    content = it.content,
                    timestamp = it.timestamp,
                    status = it.status,
                )
            }
        }

    suspend fun getMessages(conversationId: Long): List<ChatMessage> =
        messageDao.getMessages(conversationId).map {
            ChatMessage(it.id, it.conversationId, MessageRole.valueOf(it.role), it.content, it.timestamp, it.status)
        }

    suspend fun addMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        status: String = "complete",
    ): Long {
        val now = System.currentTimeMillis()
        val messageId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = role.name,
                content = content,
                timestamp = now,
                status = status,
            )
        )
        touchConversation(conversationId, content)
        return messageId
    }

    suspend fun updateMessage(id: Long, content: String, status: String = "complete") {
        messageDao.updateContent(id, content, status)
    }

    suspend fun renameConversation(conversationId: Long, title: String) {
        val current = conversationDao.getById(conversationId) ?: return
        conversationDao.update(current.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setConversationModel(conversationId: Long, modelId: String?) {
        val current = conversationDao.getById(conversationId) ?: return
        conversationDao.update(current.copy(modelId = modelId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMessage(conversationId: Long, messageId: Long) {
        messageDao.deleteById(messageId)
        touchConversation(conversationId, null)
    }

    suspend fun deleteMessagesAfter(conversationId: Long, messageId: Long) {
        messageDao.deleteAfter(conversationId, messageId)
        touchConversation(conversationId, null)
    }

    suspend fun clearConversation(conversationId: Long) {
        messageDao.clearConversation(conversationId)
        touchConversation(conversationId, null)
    }

    suspend fun deleteConversation(conversationId: Long) {
        messageDao.clearConversation(conversationId)
        conversationDao.deleteById(conversationId)
    }

    suspend fun clearAll() {
        conversationDao.clearAll()
    }

    private suspend fun touchConversation(conversationId: Long, previewContent: String?) {
        val current = conversationDao.getById(conversationId) ?: return
        val newTitle = if (current.title == "New Chat" && !previewContent.isNullOrBlank()) {
            previewContent.take(36)
        } else {
            current.title
        }
        conversationDao.update(current.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }
}
