package com.prismml.grove.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query(
        """
        SELECT c.id, c.title, c.updatedAt, c.modelId,
               (SELECT m.content FROM messages m WHERE m.conversationId = c.id ORDER BY m.id DESC LIMIT 1) AS preview
        FROM conversations c
        ORDER BY c.updatedAt DESC
        """
    )
    fun observeConversations(): Flow<List<ConversationListRow>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getMessages(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :messageId")
    suspend fun deleteAfter(conversationId: Long, messageId: Long)

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, status: String)
}

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<ModelRecordEntity>)

    @Query("SELECT * FROM models ORDER BY isPreset DESC, displayName ASC")
    fun observeAll(): Flow<List<ModelRecordEntity>>

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ModelRecordEntity?

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE models SET state = :state, downloadedBytes = :downloadedBytes, workId = :workId, errorMessage = NULL WHERE id = :id")
    suspend fun updateProgress(id: String, state: String, downloadedBytes: Long, workId: String?)

    @Query("UPDATE models SET state = :state, downloadedBytes = :downloadedBytes, localPath = :localPath, errorMessage = :errorMessage, architecture = :architecture, contextLength = :contextLength, chatTemplate = :chatTemplate, workId = NULL WHERE id = :id")
    suspend fun finalizeDownload(
        id: String,
        state: String,
        downloadedBytes: Long,
        localPath: String?,
        errorMessage: String?,
        architecture: String?,
        contextLength: Int?,
        chatTemplate: String?,
    )
}
