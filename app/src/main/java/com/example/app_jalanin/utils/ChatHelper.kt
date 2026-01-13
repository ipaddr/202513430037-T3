package com.example.app_jalanin.utils

import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.ChatChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper untuk membuat dan mendapatkan chat channel
 * All chats must be tied to an order/rental
 */
object ChatHelper {
    /**
     * Get or create DM channel between two users for a specific rental/order
     * Each order gets its own chat session
     */
    suspend fun getOrCreateDMChannel(
        database: AppDatabase,
        user1: String,
        user2: String,
        rentalId: String,
        orderStatus: String = "ACTIVE"
    ): ChatChannel {
        return withContext(Dispatchers.IO) {
            // Channel ID includes rentalId to ensure unique chat per order
            val channelId = "CHAT_DM_${rentalId}"
            
            // Try to get existing channel for this rental
            val existing = database.chatChannelDao().getDMChannelByRentalAndUsers(rentalId, user1, user2)
            if (existing != null) {
                // Update order status if it changed
                if (existing.orderStatus != orderStatus) {
                    val updated = existing.copy(orderStatus = orderStatus)
                    database.chatChannelDao().updateChannel(updated)
                    return@withContext updated
                }
                return@withContext existing
            }
            
            // Sort emails for consistent participant order
            val sortedEmails = listOf(user1, user2).sorted()
            
            // Create new channel tied to rental
            val newChannel = ChatChannel(
                id = channelId,
                channelType = "DM",
                participant1 = sortedEmails[0],
                participant2 = sortedEmails[1],
                participant3 = null,
                rentalId = rentalId,
                orderStatus = orderStatus
            )
            database.chatChannelDao().insertChannel(newChannel)
            return@withContext newChannel
        }
    }
    
    /**
     * Get or create group chat channel for rental (Owner, Driver, Passenger)
     */
    suspend fun getOrCreateGroupChannel(
        database: AppDatabase,
        ownerEmail: String,
        driverEmail: String,
        passengerEmail: String,
        rentalId: String,
        orderStatus: String = "ACTIVE"
    ): ChatChannel {
        return withContext(Dispatchers.IO) {
            val channelId = "CHAT_GROUP_${rentalId}"
            
            // Try to get existing channel
            val existing = database.chatChannelDao().getGroupChannelByRental(rentalId)
            if (existing != null) {
                // Update order status if it changed
                if (existing.orderStatus != orderStatus) {
                    val updated = existing.copy(orderStatus = orderStatus)
                    database.chatChannelDao().updateChannel(updated)
                    return@withContext updated
                }
                return@withContext existing
            }
            
            // Create new group channel
            val newChannel = ChatChannel(
                id = channelId,
                channelType = "GROUP",
                participant1 = ownerEmail,
                participant2 = driverEmail,
                participant3 = passengerEmail,
                rentalId = rentalId,
                orderStatus = orderStatus
            )
            database.chatChannelDao().insertChannel(newChannel)
            return@withContext newChannel
        }
    }
    
    /**
     * Generate channel ID for DM (includes rentalId)
     */
    fun generateDMChannelId(rentalId: String): String {
        return "CHAT_DM_${rentalId}"
    }
    
    /**
     * Generate channel ID for group chat
     */
    fun generateGroupChannelId(rentalId: String): String {
        return "CHAT_GROUP_${rentalId}"
    }
    
    /**
     * Update order status for a channel (when order completes or cancels)
     */
    suspend fun updateChannelOrderStatus(
        database: AppDatabase,
        rentalId: String,
        orderStatus: String
    ) {
        withContext(Dispatchers.IO) {
            database.chatChannelDao().updateOrderStatus(rentalId, orderStatus)
        }
    }
}
