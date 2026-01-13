package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk Chat Channel
 * Support untuk Direct Message (2 participants) dan Group Chat (3+ participants)
 */
@Entity(
    tableName = "chat_channels",
    indices = [
        Index(value = ["participant1"]),
        Index(value = ["participant2"]),
        Index(value = ["participant3"]),
        Index(value = ["rentalId"]),
        Index(value = ["channelType"]),
        Index(value = ["orderStatus"]),
        Index(value = ["lastMessageAt"])
    ]
)
data class ChatChannel(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Format: "CHAT_<type>_<rentalId>"
    
    @ColumnInfo(name = "channelType")
    val channelType: String, // "DM" (Direct Message) or "GROUP"
    
    @ColumnInfo(name = "participant1")
    val participant1: String, // Email of first participant
    
    @ColumnInfo(name = "participant2")
    val participant2: String, // Email of second participant
    
    @ColumnInfo(name = "participant3")
    val participant3: String? = null, // Email of third participant (for group chat)
    
    @ColumnInfo(name = "rentalId")
    val rentalId: String, // Associated rental/order ID (REQUIRED - each chat tied to an order)
    
    @ColumnInfo(name = "orderStatus")
    val orderStatus: String, // Order status: "ACTIVE", "COMPLETED", "CANCELLED", etc.
    
    @ColumnInfo(name = "lastMessageAt")
    val lastMessageAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "lastMessage")
    val lastMessage: String? = null,
    
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Get all participants as list
     */
    fun getParticipants(): List<String> {
        return listOfNotNull(participant1, participant2, participant3)
    }
    
    /**
     * Check if user is participant
     */
    fun isParticipant(userEmail: String): Boolean {
        return participant1 == userEmail || participant2 == userEmail || participant3 == userEmail
    }
    
    /**
     * Get other participant(s) for DM
     */
    fun getOtherParticipant(userEmail: String): String? {
        return when {
            participant1 == userEmail -> participant2
            participant2 == userEmail -> participant1
            else -> null
        }
    }
}
