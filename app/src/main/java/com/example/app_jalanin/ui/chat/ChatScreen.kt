package com.example.app_jalanin.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.ChatChannel
import com.example.app_jalanin.data.local.entity.ChatMessage
import com.example.app_jalanin.utils.UsernameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen untuk chat (DM dan Group Chat)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelId: String,
    currentUserEmail: String,
    onBackClick: () -> Unit = {},
    onSetReturnLocationClick: ((com.example.app_jalanin.data.local.entity.Rental) -> Unit)? = null // Callback to navigate to set return location
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var messageText by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf<ChatChannel?>(null) }
    
    // Load channel
    val channelFlow = remember(channelId) {
        database.chatChannelDao().getChannelByIdFlow(channelId)
    }
    val channelState = channelFlow.collectAsStateWithLifecycle(initialValue = null)
    
    // Load messages
    val messagesFlow = remember(channelId) {
        database.chatMessageDao().getMessagesByChannel(channelId)
    }
    val messagesState = messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    LaunchedEffect(channelState.value) {
        channel = channelState.value
    }
    
    // Auto scroll to bottom
    val listState = rememberLazyListState()
    LaunchedEffect(messagesState.value.size) {
        if (messagesState.value.isNotEmpty()) {
            listState.animateScrollToItem(messagesState.value.size - 1)
        }
    }
    
    // Mark as read
    LaunchedEffect(channelId, currentUserEmail) {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.chatMessageDao().markAsRead(channelId, currentUserEmail)
            }
        }
    }
    
    if (channel == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    val otherParticipants = channel!!.getParticipants().filter { it != currentUserEmail }
    
    // ✅ Get username (fullName) for other participants
    var otherParticipantName by remember { mutableStateOf<String?>(null) }
    var groupParticipantNames by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // ✅ Check if current user is owner and if there's a pending early return request
    var pendingEarlyReturnRental by remember { mutableStateOf<com.example.app_jalanin.data.local.entity.Rental?>(null) }
    var currentUserRole by remember { mutableStateOf<String?>(null) }
    var showSetReturnLocationDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(channelId, currentUserEmail) {
        scope.launch {
            // Get current user role
            val user = withContext(Dispatchers.IO) {
                database.userDao().getUserByEmail(currentUserEmail)
            }
            currentUserRole = user?.role
            
            // If user is owner, check for pending early return requests
            if (user?.role?.uppercase() == "PEMILIK_KENDARAAN" || user?.role?.uppercase() == "PEMILIK KENDARAAN") {
                // Get rentals by owner
                val ownerRentals = withContext(Dispatchers.IO) {
                    database.rentalDao().getEarlyReturnRequestsByOwner(currentUserEmail)
                }
                
                // Find rental where the other participant is the renter
                val otherEmail = otherParticipants.firstOrNull()
                if (otherEmail != null) {
                    pendingEarlyReturnRental = ownerRentals.firstOrNull { rental ->
                        rental.userEmail == otherEmail && 
                        rental.earlyReturnStatus == "REQUESTED" &&
                        (rental.returnLocationLat == null || rental.returnLocationLon == null || rental.returnAddress == null)
                    }
                }
            }
            
            if (channel!!.channelType == "GROUP") {
                // For group chat, get all participant names
                val names = withContext(Dispatchers.IO) {
                    channel!!.getParticipants().mapNotNull { email ->
                        com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                            context,
                            email
                        )
                    }
                }
                groupParticipantNames = names
            } else {
                // For DM, get the other participant's name
                val otherEmail = otherParticipants.firstOrNull()
                if (otherEmail != null) {
                    otherParticipantName = withContext(Dispatchers.IO) {
                        com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                            context,
                            otherEmail
                        )
                    }
                }
            }
        }
    }
    
    val channelTitle = if (channel!!.channelType == "GROUP") {
        "Group Chat"
    } else {
        otherParticipantName ?: otherParticipants.firstOrNull() ?: "Chat"
    }
    
    // Check if order is completed (chat is read-only)
    val isOrderCompleted = channel!!.orderStatus == "COMPLETED" || channel!!.orderStatus == "CANCELLED"
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(channelTitle)
                        if (channel!!.channelType == "GROUP") {
                            Text(
                                text = "${channel!!.getParticipants().size} participants",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ✅ Early Return Request Banner (for owner)
            if (pendingEarlyReturnRental != null && currentUserRole?.uppercase()?.contains("PEMILIK") == true) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permintaan Pengembalian Lebih Awal",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                Text(
                                    text = "Penumpang ingin mengembalikan ${pendingEarlyReturnRental!!.vehicleName} lebih awal. Tentukan lokasi pengembalian.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                pendingEarlyReturnRental?.let { rental ->
                                    onSetReturnLocationClick?.invoke(rental)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tentukan Lokasi Pengembalian", fontSize = 14.sp)
                        }
                    }
                }
            }
            
            // Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messagesState.value) { message ->
                    MessageBubble(
                        message = message,
                        isOwnMessage = message.senderEmail == currentUserEmail,
                        database = database
                    )
                }
            }
            
            // Order Status Indicator (if completed)
            if (isOrderCompleted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE0E0E0).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF757575),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Order Selesai - Chat hanya untuk melihat riwayat",
                            fontSize = 12.sp,
                            color = Color(0xFF757575),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
            
            // ✅ Quick Chat Buttons (for passengers only, only if order is active)
            var currentUserRole by remember { mutableStateOf<String?>(null) }
            
            LaunchedEffect(currentUserEmail) {
                withContext(Dispatchers.IO) {
                    val user = database.userDao().getUserByEmail(currentUserEmail)
                    currentUserRole = user?.role
                }
            }
            
            val isPassenger = currentUserRole == "passenger" || currentUserRole == null
            
            if (isPassenger && !isOrderCompleted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChatButton(
                        text = "Saya sudah di lokasi",
                        onClick = {
                            scope.launch {
                                sendQuickMessage(
                                    channelId = channelId,
                                    currentUserEmail = currentUserEmail,
                                    message = "Saya sudah di lokasi",
                                    database = database
                                )
                            }
                        }
                    )
                    QuickChatButton(
                        text = "Mohon segera datang",
                        onClick = {
                            scope.launch {
                                sendQuickMessage(
                                    channelId = channelId,
                                    currentUserEmail = currentUserEmail,
                                    message = "Mohon segera datang",
                                    database = database
                                )
                            }
                        }
                    )
                    QuickChatButton(
                        text = "Terima kasih",
                        onClick = {
                            scope.launch {
                                sendQuickMessage(
                                    channelId = channelId,
                                    currentUserEmail = currentUserEmail,
                                    message = "Terima kasih",
                                    database = database
                                )
                            }
                        }
                    )
                }
            }
            
            // Input Field (disabled if order is completed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { if (!isOrderCompleted) messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(if (isOrderCompleted) "Chat sudah ditutup" else "Tulis pesan...") 
                    },
                    enabled = !isOrderCompleted,
                    trailingIcon = {
                        if (messageText.isNotBlank() && !isOrderCompleted) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        sendMessage(
                                            channelId = channelId,
                                            currentUserEmail = currentUserEmail,
                                            messageText = messageText,
                                            database = database
                                        )
                                        messageText = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickChatButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp
        )
    }
}

private suspend fun sendQuickMessage(
    channelId: String,
    currentUserEmail: String,
    message: String,
    database: AppDatabase
) {
    withContext(Dispatchers.IO) {
        // Check if order is completed (prevent sending messages)
        val channel = database.chatChannelDao().getChannelById(channelId)
        if (channel?.orderStatus == "COMPLETED" || channel?.orderStatus == "CANCELLED") {
            android.util.Log.w("ChatScreen", "Cannot send message: Order is ${channel.orderStatus}")
            return@withContext
        }
        
        val now = System.currentTimeMillis()
        val messageId = "MSG_${now}_${UUID.randomUUID().toString().take(8)}"
        
        // Get current user name
        val currentUser = database.userDao().getUserByEmail(currentUserEmail)
        
        // Resolve username
        val senderUsername = currentUser?.username ?: currentUser?.fullName ?: currentUserEmail.substringBefore("@")
        
        val newMessage = ChatMessage(
            id = messageId,
            channelId = channelId,
            senderEmail = currentUserEmail,
            senderName = senderUsername,
            message = message,
            messageType = "TEXT",
            createdAt = now
        )
        
        database.chatMessageDao().insertMessage(newMessage)
        database.chatChannelDao().updateLastMessage(
            channelId = channelId,
            message = message,
            timestamp = now
        )
    }
}

private suspend fun sendMessage(
    channelId: String,
    currentUserEmail: String,
    messageText: String,
    database: AppDatabase
) {
    withContext(Dispatchers.IO) {
        // Check if order is completed (prevent sending messages)
        val channel = database.chatChannelDao().getChannelById(channelId)
        if (channel?.orderStatus == "COMPLETED" || channel?.orderStatus == "CANCELLED") {
            android.util.Log.w("ChatScreen", "Cannot send message: Order is ${channel.orderStatus}")
            return@withContext
        }
        
        val now = System.currentTimeMillis()
        val messageId = "MSG_${now}_${UUID.randomUUID().toString().take(8)}"
        
        // Get current user name
        val currentUser = database.userDao().getUserByEmail(currentUserEmail)
        
        val newMessage = ChatMessage(
            id = messageId,
            channelId = channelId,
            senderEmail = currentUserEmail,
            senderName = currentUser?.fullName ?: currentUserEmail,
            message = messageText,
            messageType = "TEXT",
            createdAt = now
        )
        
        database.chatMessageDao().insertMessage(newMessage)
        database.chatChannelDao().updateLastMessage(
            channelId = channelId,
            message = messageText,
            timestamp = now
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    database: AppDatabase
) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val scope = rememberCoroutineScope()
    
    // ✅ Get sender username (fullName) from database
    var senderUsername by remember { mutableStateOf<String>(message.senderName) }
    
    LaunchedEffect(message.senderEmail) {
        scope.launch {
            val user = kotlinx.coroutines.withContext(Dispatchers.IO) {
                database.userDao().getUserByEmail(message.senderEmail)
            }
            senderUsername = user?.fullName ?: message.senderName.split("@").firstOrNull() ?: message.senderName
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwnMessage) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = senderUsername.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
        ) {
            if (!isOwnMessage) {
                Text(
                    text = senderUsername,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isOwnMessage) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = message.message,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = if (isOwnMessage) 
                        Color.White 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = dateFormat.format(message.createdAt),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        
        if (isOwnMessage) {
            Spacer(modifier = Modifier.width(8.dp))
            // Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = senderUsername.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
