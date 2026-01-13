package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY createdAt ASC")
    fun getMessagesByChannel(channelId: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(channelId: String, limit: Int = 50): List<ChatMessage>
    
    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessage?
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE channelId = :channelId AND isRead = 0 AND senderEmail != :userEmail")
    fun getUnreadCount(channelId: String, userEmail: String): Flow<Int>
    
    @Query("UPDATE chat_messages SET isRead = 1, readAt = :timestamp WHERE channelId = :channelId AND senderEmail != :userEmail AND isRead = 0")
    suspend fun markAsRead(channelId: String, userEmail: String, timestamp: Long = System.currentTimeMillis())
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)
    
    @Update
    suspend fun updateMessage(message: ChatMessage)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessage)
    
    @Query("DELETE FROM chat_messages WHERE channelId = :channelId")
    suspend fun deleteMessagesByChannel(channelId: String)
}
