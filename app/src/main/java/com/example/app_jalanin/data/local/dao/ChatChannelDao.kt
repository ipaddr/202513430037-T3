package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.ChatChannel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatChannelDao {
    
    /**
     * Get active chat channels for user (order status NOT completed/cancelled)
     */
    @Query("""
        SELECT * FROM chat_channels 
        WHERE (participant1 = :userEmail OR participant2 = :userEmail OR participant3 = :userEmail)
        AND orderStatus NOT IN ('COMPLETED', 'CANCELLED')
        ORDER BY lastMessageAt DESC
    """)
    fun getActiveChannelsByUser(userEmail: String): Flow<List<ChatChannel>>
    
    /**
     * Get completed chat channels for user (chat history)
     */
    @Query("""
        SELECT * FROM chat_channels 
        WHERE (participant1 = :userEmail OR participant2 = :userEmail OR participant3 = :userEmail)
        AND orderStatus IN ('COMPLETED', 'CANCELLED')
        ORDER BY lastMessageAt DESC
    """)
    fun getCompletedChannelsByUser(userEmail: String): Flow<List<ChatChannel>>
    
    /**
     * Get all channels for user (for backward compatibility)
     */
    @Query("SELECT * FROM chat_channels WHERE participant1 = :userEmail OR participant2 = :userEmail OR participant3 = :userEmail ORDER BY lastMessageAt DESC")
    fun getChannelsByUser(userEmail: String): Flow<List<ChatChannel>>
    
    @Query("SELECT * FROM chat_channels ORDER BY lastMessageAt DESC")
    fun getAllChannelsFlow(): Flow<List<ChatChannel>>
    
    @Query("SELECT * FROM chat_channels WHERE id = :channelId")
    suspend fun getChannelById(channelId: String): ChatChannel?
    
    @Query("SELECT * FROM chat_channels WHERE id = :channelId")
    fun getChannelByIdFlow(channelId: String): Flow<ChatChannel?>
    
    /**
     * Get DM channel by rentalId (each order has its own chat)
     */
    @Query("""
        SELECT * FROM chat_channels 
        WHERE channelType = 'DM' 
        AND rentalId = :rentalId
        LIMIT 1
    """)
    suspend fun getDMChannelByRental(rentalId: String): ChatChannel?
    
    /**
     * Get DM channel between two users for a specific rental
     */
    @Query("""
        SELECT * FROM chat_channels 
        WHERE channelType = 'DM' 
        AND rentalId = :rentalId
        AND ((participant1 = :user1 AND participant2 = :user2) OR (participant1 = :user2 AND participant2 = :user1))
        LIMIT 1
    """)
    suspend fun getDMChannelByRentalAndUsers(rentalId: String, user1: String, user2: String): ChatChannel?
    
    /**
     * Get group channel by rentalId
     */
    @Query("""
        SELECT * FROM chat_channels 
        WHERE channelType = 'GROUP' 
        AND rentalId = :rentalId
        LIMIT 1
    """)
    suspend fun getGroupChannelByRental(rentalId: String): ChatChannel?
    
    /**
     * Update order status for a channel (when order status changes)
     */
    @Query("UPDATE chat_channels SET orderStatus = :orderStatus, updatedAt = :updatedAt WHERE rentalId = :rentalId")
    suspend fun updateOrderStatus(rentalId: String, orderStatus: String, updatedAt: Long = System.currentTimeMillis())
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChatChannel)
    
    @Update
    suspend fun updateChannel(channel: ChatChannel)
    
    @Query("UPDATE chat_channels SET lastMessage = :message, lastMessageAt = :timestamp, updatedAt = :timestamp WHERE id = :channelId")
    suspend fun updateLastMessage(channelId: String, message: String, timestamp: Long)
    
    @Delete
    suspend fun deleteChannel(channel: ChatChannel)
}
