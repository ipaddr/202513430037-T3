package com.example.app_jalanin.ui.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.ChatChannel
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.auth.UserRole
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Data class for Message History item
 */
data class MessageHistoryItemData(
    val channel: ChatChannel,
    val driverEmail: String,
    val driverName: String,
    val lastMessagePreview: String?,
    val timestamp: Long
)

class MessageHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    
    private val _messageHistory = MutableStateFlow<List<MessageHistoryItemData>>(emptyList())
    val messageHistory: StateFlow<List<MessageHistoryItemData>> = _messageHistory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun loadMessageHistory(passengerEmail: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d("MessageHistoryViewModel", "📥 Loading message history for: $passengerEmail")
                
                val historyItems = withContext(Dispatchers.IO) {
                    val items = mutableListOf<MessageHistoryItemData>()
                    
                    // Load all channels where passenger is a participant
                    val channels = database.chatChannelDao().getChannelsByUser(passengerEmail).first()
                    Log.d("MessageHistoryViewModel", "📦 Found ${channels.size} channels")
                    
                    // Filter channels to only show DM channels with drivers
                    val driverChannels = channels.filter { channel ->
                        channel.channelType == "DM" && (
                            (channel.participant1 == passengerEmail && channel.participant2 != passengerEmail) ||
                            (channel.participant2 == passengerEmail && channel.participant1 != passengerEmail)
                        )
                    }
                    
                    Log.d("MessageHistoryViewModel", "📦 Found ${driverChannels.size} DM channels")
                    
                    for (channel in driverChannels) {
                        // Get the other participant (driver)
                        val driverEmail = if (channel.participant1 == passengerEmail) {
                            channel.participant2
                        } else {
                            channel.participant1
                        }
                        
                        // Verify this is a driver
                        val driver = database.userDao().getUserByEmail(driverEmail)
                        if (driver != null && driver.role.uppercase() == "DRIVER") {
                            // Get driver name
                            val driverName = driver.fullName ?: driverEmail.split("@").firstOrNull() ?: driverEmail
                            
                            // Get last message preview
                            var lastMessagePreview = channel.lastMessage
                            if (lastMessagePreview == null) {
                                val recentMessages = database.chatMessageDao().getRecentMessages(channel.id, limit = 1)
                                lastMessagePreview = recentMessages.firstOrNull()?.message
                            }
                            
                            items.add(
                                MessageHistoryItemData(
                                    channel = channel,
                                    driverEmail = driverEmail,
                                    driverName = driverName,
                                    lastMessagePreview = lastMessagePreview,
                                    timestamp = channel.lastMessageAt
                                )
                            )
                        }
                    }
                    
                    // Sort by last message timestamp (newest first)
                    items.sortByDescending { it.timestamp }
                    items // Return sorted list
                }
                
                _messageHistory.value = historyItems
                Log.d("MessageHistoryViewModel", "✅ Loaded ${historyItems.size} message history items")
                
            } catch (e: Exception) {
                Log.e("MessageHistoryViewModel", "❌ Error loading message history: ${e.message}", e)
                _errorMessage.value = "Error loading message history: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

