package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk Chat Message
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["senderEmail"]),
        Index(value = ["createdAt"])
    ]
)
data class ChatMessage(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Format: "MSG_<timestamp>_<random>"
    
    @ColumnInfo(name = "channelId")
    val channelId: String, // Foreign key ke chat_channels.id
    
    @ColumnInfo(name = "senderEmail")
    val senderEmail: String, // Email of message sender
    
    @ColumnInfo(name = "senderName")
    val senderName: String, // Name of message sender
    
    @ColumnInfo(name = "message")
    val message: String, // Message content
    
    @ColumnInfo(name = "messageType")
    val messageType: String = "TEXT", // "TEXT", "IMAGE", "SYSTEM"
    
    @ColumnInfo(name = "isRead")
    val isRead: Boolean = false,
    
    @ColumnInfo(name = "readAt")
    val readAt: Long? = null,
    
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "synced")
    val synced: Boolean = false // Whether synced to remote (if applicable)
)
