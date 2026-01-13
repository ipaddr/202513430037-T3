package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.local.entity.DriverRequest
import com.example.app_jalanin.auth.AuthStateManager
import com.example.app_jalanin.utils.DurationUtils
import com.example.app_jalanin.utils.ChatHelper
import com.example.app_jalanin.utils.UsernameResolver
import com.example.app_jalanin.utils.RoleResolver
import com.example.app_jalanin.utils.UsernameMigrationHelper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerDashboardScreen(
    onServiceClick: (String) -> Unit = {},
    onEmergencyClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onVehiclesClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onChatClick: (String) -> Unit = {}, // channelId
    onMessageHistoryClick: () -> Unit = {}, // ✅ NEW: for message history
    onTripHistoryClick: () -> Unit = {}, // ✅ NEW: for trip history
    onAccountClick: () -> Unit = {}, // ✅ NEW: for account page navigation
    username: String? = null,
    role: String? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // Load active rental for countdown
    var userEmail by remember { mutableStateOf<String?>(null) }
    
    // Load user email in coroutine
    LaunchedEffect(Unit) {
        val user = AuthStateManager.getCurrentUser(context)
        userEmail = user?.email ?: AuthStateManager.getCurrentUserEmail(context)
    }
    
    val activeRentalsFlow = remember(userEmail) {
        if (userEmail != null) {
            database.rentalDao().getActiveRentalsByEmailFlow(userEmail!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<Rental>())
        }
    }
    val activeRentalsState = activeRentalsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeRental = activeRentalsState.value.firstOrNull { it.status == "ACTIVE" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onAccountClick) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Account",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> {
                    // Resolve username dynamically
                    var displayUsername by remember { mutableStateOf<String?>(null) }
                    
                    LaunchedEffect(userEmail) {
                        if (userEmail != null) {
                            withContext(Dispatchers.IO) {
                                // Ensure username exists
                                com.example.app_jalanin.utils.UsernameMigrationHelper.ensureUsername(context, userEmail!!)
                                
                                val dbUser = database.userDao().getUserByEmail(userEmail!!)
                                displayUsername = dbUser?.username ?: userEmail!!.substringBefore("@")
                            }
                        }
                    }
                    
                    HomeContent(
                        username = displayUsername ?: username ?: "User",
                        role = role ?: "",
                        onServiceClick = onServiceClick,
                        onEmergencyClick = onEmergencyClick,
                        activeRental = activeRental,
                        onHistoryClick = onHistoryClick
                    )
                }
                1 -> HistoryContent(
                    onHistoryClick = onHistoryClick,
                    onTripHistoryClick = onTripHistoryClick
                )
                2 -> PaymentContent()
                3 -> PassengerChatContent(
                    userEmail = userEmail ?: "",
                    onChatClick = onChatClick
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    username: String,
    role: String,
    onServiceClick: (String) -> Unit,
    onEmergencyClick: () -> Unit,
    activeRental: Rental? = null,
    onHistoryClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // Get user email for chat
    var userEmail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val user = AuthStateManager.getCurrentUser(context)
        userEmail = user?.email ?: AuthStateManager.getCurrentUserEmail(context)
    }
    
    // ...existing code...
    val activeDriverRequestsFlow = remember(userEmail) {
        if (userEmail != null) {
            database.driverRequestDao().getActiveRequestsByPassenger(userEmail!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<DriverRequest>())
        }
    }
    val activeDriverRequestsState = activeDriverRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeDriverRequest = activeDriverRequestsState.value.firstOrNull()
    
    // Get driver email from rental OR active driver request
    // Priority: 1. Active DriverRequest (Sewa Driver), 2. Rental travelDriverId, 3. Rental deliveryDriverId, 4. Rental driverId
    val driverEmail = activeDriverRequest?.driverEmail
        ?: activeRental?.travelDriverId 
        ?: activeRental?.deliveryDriverId 
        ?: activeRental?.driverId
    
    // Countdown timer for active rental
    var remainingTime by remember { mutableStateOf(0L) }
    var isOvertime by remember { mutableStateOf(false) }

    LaunchedEffect(activeRental) {
        while (activeRental != null && activeRental.status == "ACTIVE") {
            val now = System.currentTimeMillis()
            val diff = activeRental.endDate - now

            if (diff <= 0) {
                remainingTime = Math.abs(diff)
                isOvertime = true
            } else {
                remainingTime = diff
                isOvertime = false
            }

            delay(1000) // Update every second
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Header(username = username, role = role)
        
        // ✅ Balance Card
        if (userEmail != null) {
            val balanceRepository = remember { com.example.app_jalanin.data.local.BalanceRepository(context) }
            
            // Initialize and sync balance on dashboard open
            LaunchedEffect(userEmail) {
                scope.launch {
                    try {
                        // Initialize balance if not exists
                        balanceRepository.initializeBalance(userEmail!!)
                        
                        // Download balance from Firestore
                        com.example.app_jalanin.data.remote.FirestoreBalanceSyncManager.downloadUserBalance(
                            context,
                            userEmail!!
                        )
                        
                        // Sync unsynced balance changes
                        balanceRepository.syncToFirestore()
                    } catch (e: Exception) {
                        android.util.Log.e("PassengerDashboard", "Error initializing/syncing balance: ${e.message}", e)
                    }
                }
            }
            
            com.example.app_jalanin.ui.common.BalanceCard(
                userEmail = userEmail!!,
                balanceRepository = balanceRepository,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        // ✅ Active Rental Countdown Card (or Active Driver Request)
        if ((activeRental != null && activeRental.status == "ACTIVE") || activeDriverRequest != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOvertime) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            isOvertime -> "⚠️ PERINGATAN KETERLAMBATAN"
                            activeDriverRequest != null -> "🚕 Driver Aktif"
                            else -> "🚗 Sewa Aktif"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isOvertime -> Color(0xFFD32F2F)
                            activeDriverRequest != null -> Color(0xFF1976D2)
                            else -> Color(0xFF2E7D32)
                        }
                    )

                    // Display vehicle info if it's a rental, or driver request info
                    if (activeRental != null && activeRental.status == "ACTIVE") {
                        Text(
                            text = activeRental.vehicleName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )

                        Text(
                            text = DurationUtils.formatTime(remainingTime),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOvertime) Color(0xFFEF5350) else Color(0xFF4CAF50)
                        )
                        
                        LinearProgressIndicator(
                            progress = {
                                val totalDuration = activeRental.endDate - activeRental.startDate
                                val elapsed = System.currentTimeMillis() - activeRental.startDate
                                (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (isOvertime) Color(0xFFEF5350) else Color(0xFF4CAF50),
                            trackColor = Color(0xFFC8E6C9)
                        )

                        if (isOvertime) {
                            Text(
                                text = "⚠️ Keterlambatan dikenakan Rp 50.000/jam",
                                fontSize = 12.sp,
                                color = Color(0xFFD32F2F),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = "⚠️ Keterlambatan dikenakan Rp 50.000/jam",
                                fontSize = 11.sp,
                                color = Color(0xFF666666),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else if (activeDriverRequest != null) {
                        // Display driver request info
                        Text(
                            text = "Driver: ${activeDriverRequest.driverName ?: "Driver"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = "${activeDriverRequest.vehicleBrand} ${activeDriverRequest.vehicleModel}",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                        val statusText = when (activeDriverRequest.status) {
                            "ACCEPTED" -> "Driver Diterima"
                            "DRIVER_ARRIVING" -> "Driver Menuju Lokasi"
                            "DRIVER_ARRIVED" -> "Driver Tiba di Lokasi"
                            "IN_PROGRESS" -> "Sedang Berjalan"
                            else -> activeDriverRequest.status
                        }
                        Text(
                            text = "Status: $statusText",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    // ✅ Action Buttons Row
                    if (driverEmail != null && userEmail != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Chat dengan Driver Button
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            if (activeDriverRequest != null) {
                                                // Use DriverRequest id as rentalId and status as orderStatus
                                                val channel = ChatHelper.getOrCreateDMChannel(
                                                    database,
                                                    userEmail!!,
                                                    driverEmail,
                                                    activeDriverRequest.id, // Use request id as rentalId
                                                    activeDriverRequest.status // Use request status as orderStatus
                                                )
                                                onChatClick(channel.id)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("PassengerDashboard", "Error creating chat channel: ${e.message}", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = "Chat",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Chat Driver",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // Lihat Detail Button
                            OutlinedButton(
                                onClick = onHistoryClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text(
                                    text = "Detail",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Jika tidak ada driver, hanya tampilkan tombol detail
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onHistoryClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = "Lihat Detail",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        
        MainContent(onServiceClick)
        EmergencySection(onEmergencyClick)
    }
}

@Composable
private fun HistoryContent(
    onHistoryClick: () -> Unit,
    onTripHistoryClick: () -> Unit = {} // ✅ NEW: Trip History
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Riwayat",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp)
        )

        // ✅ Riwayat Sewa Driver Button (Trip History - Summary)
        Button(
            onClick = onTripHistoryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800)
            )
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Riwayat Sewa Driver",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Ringkasan sewa driver Anda",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // Riwayat Sewa Kendaraan Button
        Button(
            onClick = onHistoryClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3)
            )
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Riwayat Sewa Kendaraan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Lihat status penyewaan kendaraan",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

    }
}

@Composable
private fun PaymentContent() {
    val context = LocalContext.current
    var userEmail by remember { mutableStateOf<String?>(null) }
    
    // Load user email
    LaunchedEffect(Unit) {
        val user = AuthStateManager.getCurrentUser(context)
        userEmail = user?.email ?: AuthStateManager.getCurrentUserEmail(context)
    }
    
    if (userEmail != null) {
        PaymentHistoryScreen(
            userEmail = userEmail!!,
            onBackClick = { /* No back action needed in tab */ }
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassengerChatContent(
    userEmail: String,
    onChatClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var chatHistory by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.ChatChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load active rental and driver request to determine if chat is available
    val activeRentalsFlow = remember(userEmail) {
        if (userEmail.isNotEmpty()) {
            database.rentalDao().getActiveRentalsByEmailFlow(userEmail)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<Rental>())
        }
    }
    val activeRentalsState = activeRentalsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeRental = activeRentalsState.value.firstOrNull { it.status == "ACTIVE" }
    
    val activeDriverRequestsFlow = remember(userEmail) {
        if (userEmail.isNotEmpty()) {
            database.driverRequestDao().getActiveRequestsByPassenger(userEmail)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<DriverRequest>())
        }
    }
    val activeDriverRequestsState = activeDriverRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeDriverRequest = activeDriverRequestsState.value.firstOrNull()
    
    // Load completed chat channels (chat history only)
    LaunchedEffect(userEmail) {
        if (userEmail.isNotEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    // Load only completed channels for history
                    chatHistory = database.chatChannelDao().getCompletedChannelsByUser(userEmail).first()
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerChatContent", "Error loading channels: ${e.message}", e)
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Chat") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (chatHistory.isEmpty() && activeRental == null && activeDriverRequest == null) {
            // No active chat sessions
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Belum ada chat aktif",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Chat akan tersedia saat Anda menyewa kendaraan atau memesan driver",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active chat sessions
                if (activeRental != null || activeDriverRequest != null) {
                    item {
                        Text(
                            text = "Chat Aktif",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // Active rental chat - can be with Owner or Driver
                    if (activeRental != null) {
                        // Chat with Owner (if vehicle rental without driver or owner needs to be contacted)
                        if (activeRental.ownerEmail != null) {
                            item {
                                var channelId by remember { mutableStateOf<String?>(null) }
                                var ownerUsername by remember { mutableStateOf<String?>(null) }
                                var ownerRole by remember { mutableStateOf<String?>(null) }
                                
                                LaunchedEffect(activeRental.id, activeRental.ownerEmail, activeRental.status) {
                                    scope.launch {
                                        try {
                                            val channel = ChatHelper.getOrCreateDMChannel(
                                                database,
                                                userEmail,
                                                activeRental.ownerEmail!!,
                                                activeRental.id,
                                                activeRental.status
                                            )
                                            channelId = channel.id
                                            
                                            // Resolve owner username and role
                                            ownerUsername = UsernameResolver.resolveUsernameFromEmail(context, activeRental.ownerEmail!!)
                                            ownerRole = RoleResolver.resolveRoleFromEmail(context, activeRental.ownerEmail!!)
                                        } catch (e: Exception) {
                                            android.util.Log.e("PassengerChatContent", "Error creating channel: ${e.message}", e)
                                        }
                                    }
                                }
                                
                                if (channelId != null && ownerUsername != null) {
                                    ActiveChatCard(
                                        title = ownerUsername ?: "Pemilik Kendaraan",
                                        subtitle = activeRental.vehicleName,
                                        role = ownerRole,
                                        channelId = channelId!!,
                                        onChatClick = onChatClick
                                    )
                                }
                            }
                        }
                        
                        // Chat with Driver (if driver is assigned)
                        val driverEmail = activeRental.travelDriverId 
                            ?: activeRental.deliveryDriverId 
                            ?: activeRental.driverId
                        
                        if (driverEmail != null) {
                            item {
                                var channelId by remember { mutableStateOf<String?>(null) }
                                var driverUsername by remember { mutableStateOf<String?>(null) }
                                var driverRole by remember { mutableStateOf<String?>(null) }
                                
                                LaunchedEffect(activeRental.id, driverEmail, activeRental.status) {
                                    scope.launch {
                                        try {
                                            val channel = ChatHelper.getOrCreateDMChannel(
                                                database,
                                                userEmail,
                                                driverEmail,
                                                activeRental.id,
                                                activeRental.status
                                            )
                                            channelId = channel.id
                                            
                                            // Resolve driver username and role
                                            driverUsername = UsernameResolver.resolveUsernameFromEmail(context, driverEmail)
                                            driverRole = RoleResolver.resolveRoleFromEmail(context, driverEmail)
                                        } catch (e: Exception) {
                                            android.util.Log.e("PassengerChatContent", "Error creating channel: ${e.message}", e)
                                        }
                                    }
                                }
                                
                                if (channelId != null && driverUsername != null) {
                                    ActiveChatCard(
                                        title = driverUsername ?: "Driver",
                                        subtitle = activeRental.vehicleName,
                                        role = driverRole,
                                        channelId = channelId!!,
                                        onChatClick = onChatClick
                                    )
                                }
                            }
                        }
                    }
                    
                    // Active driver request chat
                    if (activeDriverRequest != null && activeDriverRequest.driverEmail != null) {
                        item {
                            var channelId by remember { mutableStateOf<String?>(null) }
                            
                            LaunchedEffect(activeDriverRequest.id, activeDriverRequest.driverEmail, activeDriverRequest.status) {
                                scope.launch {
                                    try {
                                        // Use DriverRequest id as rentalId and status as orderStatus
                                        val channel = ChatHelper.getOrCreateDMChannel(
                                            database,
                                            userEmail,
                                            activeDriverRequest.driverEmail!!,
                                            activeDriverRequest.id, // Use request id as rentalId
                                            activeDriverRequest.status // Use request status as orderStatus
                                        )
                                        channelId = channel.id
                                    } catch (e: Exception) {
                                        android.util.Log.e("PassengerChatContent", "Error creating channel: ${e.message}", e)
                                    }
                                }
                            }
                            
                            if (channelId != null) {
                                var driverUsername by remember { mutableStateOf<String?>(null) }
                                var driverRole by remember { mutableStateOf<String?>(null) }
                                
                                LaunchedEffect(activeDriverRequest.driverEmail) {
                                    scope.launch {
                                        try {
                                            driverUsername = UsernameResolver.resolveUsernameFromEmail(context, activeDriverRequest.driverEmail!!)
                                            driverRole = RoleResolver.resolveRoleFromEmail(context, activeDriverRequest.driverEmail!!)
                                        } catch (e: Exception) {
                                            android.util.Log.e("PassengerChatContent", "Error resolving driver info: ${e.message}", e)
                                        }
                                    }
                                }
                                
                                if (driverUsername != null) {
                                    ActiveChatCard(
                                        title = driverUsername ?: "Driver",
                                        subtitle = "Sewa Driver",
                                        role = driverRole,
                                        channelId = channelId!!,
                                        onChatClick = onChatClick
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Chat history (completed orders only)
                if (chatHistory.isNotEmpty()) {
                    item {
                        Text(
                            text = "Riwayat Chat",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(chatHistory) { channel ->
                        ChatHistoryCard(
                            channel = channel,
                            userEmail = userEmail,
                            onChatClick = onChatClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveChatCard(
    title: String,
    subtitle: String,
    role: String? = null,
    channelId: String,
    onChatClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick(channelId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Role badge
                    if (role != null && role != "Unknown") {
                        val roleDisplayName = com.example.app_jalanin.utils.RoleResolver.getRoleDisplayName(role)
                        val badgeColor = when {
                            com.example.app_jalanin.utils.RoleResolver.isDriver(role) -> MaterialTheme.colorScheme.tertiary
                            com.example.app_jalanin.utils.RoleResolver.isOwner(role) -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, badgeColor)
                        ) {
                            Text(
                                text = roleDisplayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ChatHistoryCard(
    channel: com.example.app_jalanin.data.local.entity.ChatChannel,
    userEmail: String,
    onChatClick: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    var otherParticipantName by remember { mutableStateOf<String?>(null) }
    var otherParticipantRole by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    
    // Get other participant email
    val otherParticipantEmail = when {
        channel.participant1 == userEmail -> channel.participant2
        channel.participant2 == userEmail -> channel.participant1
        else -> channel.participant3 ?: channel.participant2
    }
    
    LaunchedEffect(otherParticipantEmail) {
        if (otherParticipantEmail != null) {
            try {
                otherParticipantName = withContext(Dispatchers.IO) {
                    com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                        context,
                        otherParticipantEmail
                    )
                }
                otherParticipantRole = withContext(Dispatchers.IO) {
                    com.example.app_jalanin.utils.RoleResolver.resolveRoleFromEmail(
                        context,
                        otherParticipantEmail
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatHistoryCard", "Error loading username: ${e.message}", e)
                otherParticipantName = otherParticipantEmail.split("@").firstOrNull()
            }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick(channel.id) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = otherParticipantName ?: otherParticipantEmail ?: "Unknown",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Role badge
                    if (otherParticipantRole != null && otherParticipantRole != "Unknown") {
                        val roleDisplayName = com.example.app_jalanin.utils.RoleResolver.getRoleDisplayName(otherParticipantRole!!)
                        val badgeColor = when {
                            com.example.app_jalanin.utils.RoleResolver.isDriver(otherParticipantRole!!) -> MaterialTheme.colorScheme.tertiary
                            com.example.app_jalanin.utils.RoleResolver.isOwner(otherParticipantRole!!) -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, badgeColor)
                        ) {
                            Text(
                                text = roleDisplayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (channel.lastMessage != null) {
                    Text(
                        text = channel.lastMessage ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = dateFormat.format(Date(channel.lastMessageAt)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerAccountScreen(
    username: String,
    role: String,
    onBackClick: () -> Unit = {},
    onVehiclesClick: () -> Unit = {},
    onMessageHistoryClick: () -> Unit = {},
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Akun") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        AccountContent(
            username = username,
            role = role,
            onVehiclesClick = onVehiclesClick,
            onMessageHistoryClick = onMessageHistoryClick,
            onLogout = onLogout,
            onDeleteAccount = onDeleteAccount,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun AccountContent(
    username: String,
    role: String,
    onVehiclesClick: () -> Unit = {},
    onMessageHistoryClick: () -> Unit = {},
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // Load current user data
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    var currentUser by remember { mutableStateOf<com.example.app_jalanin.data.local.entity.User?>(null) }
    var displayUsername by remember { mutableStateOf(username) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val user = com.example.app_jalanin.auth.AuthStateManager.getCurrentUser(context)
            currentUserEmail = user?.email ?: com.example.app_jalanin.auth.AuthStateManager.getCurrentUserEmail(context)
            
            if (currentUserEmail != null) {
                // Ensure username exists
                com.example.app_jalanin.utils.UsernameMigrationHelper.ensureUsername(context, currentUserEmail!!)
                
                val dbUser = database.userDao().getUserByEmail(currentUserEmail!!)
                currentUser = dbUser
                displayUsername = dbUser?.username ?: currentUserEmail!!.substringBefore("@")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()) // ✅ ADDED SCROLL!
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Akun",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column {
                    Text(
                        text = displayUsername,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = role.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Menu Items
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AccountMenuItem(
                    icon = Icons.Filled.Person,
                    title = "Edit Profil",
                    onClick = { showEditProfileDialog = true }
                )
                HorizontalDivider()
                AccountMenuItem(
                    icon = Icons.Filled.DirectionsCar,
                    title = "Kendaraan Saya",
                    onClick = onVehiclesClick
                )
                HorizontalDivider()
                AccountMenuItem(
                    icon = Icons.Filled.Message,
                    title = "Riwayat Pesan",
                    onClick = onMessageHistoryClick
                )
                HorizontalDivider()
                AccountMenuItem(
                    icon = Icons.Filled.Settings,
                    title = "Pengaturan",
                    onClick = { /* TODO */ }
                )
                HorizontalDivider()
                AccountMenuItem(
                    icon = Icons.Filled.Info,
                    title = "Tentang Aplikasi",
                    onClick = { /* TODO */ }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Delete Account Button
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hapus Akun",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Logout",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Delete Account Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Hapus Akun Permanen",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("⚠️ PERINGATAN: Tindakan ini TIDAK BISA dibatalkan!")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Akun Anda akan dihapus PERMANEN dari:")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Database Lokal")
                    Text("• Cloud Database (Firestore)")
                    Text("• Firebase Authentication")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Anda harus login dengan akun ini terlebih dahulu untuk menghapusnya secara lengkap.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Ya, Hapus Akun")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Edit Profile Dialog (Username Edit)
    if (showEditProfileDialog) {
        var newUsername by remember { mutableStateOf(displayUsername) }
        var isUpdating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                showEditProfileDialog = false
                errorMessage = null
            },
            title = { Text("Edit Username") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { 
                            newUsername = it
                            errorMessage = null
                        },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = errorMessage != null
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsername.isBlank()) {
                            errorMessage = "Username tidak boleh kosong"
                            return@Button
                        }
                        
                        if (newUsername == displayUsername) {
                            showEditProfileDialog = false
                            return@Button
                        }
                        
                        isUpdating = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    if (currentUserEmail != null && currentUser != null) {
                                        // Check if username is already taken
                                        val existingUser = database.userDao().getUserByUsername(newUsername)
                                        if (existingUser != null && existingUser.email != currentUserEmail) {
                                            withContext(Dispatchers.Main) {
                                                errorMessage = "Username sudah digunakan"
                                                isUpdating = false
                                            }
                                            return@withContext
                                        }
                                        
                                        // Update user
                                        val updatedUser = currentUser!!.copy(
                                            username = newUsername,
                                            synced = false
                                        )
                                        database.userDao().update(updatedUser)
                                        
                                        // Sync to Firestore
                                        try {
                                            com.example.app_jalanin.data.remote.FirestoreUserService.upsertUser(updatedUser)
                                        } catch (e: Exception) {
                                            android.util.Log.e("AccountContent", "Error syncing username: ${e.message}", e)
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            displayUsername = newUsername
                                            currentUser = updatedUser
                                            showEditProfileDialog = false
                                            isUpdating = false
                                            android.widget.Toast.makeText(
                                                context,
                                                "Username berhasil diperbarui",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Error: ${e.message}"
                                    isUpdating = false
                                }
                            }
                        }
                    },
                    enabled = !isUpdating && newUsername.isNotBlank()
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Simpan")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showEditProfileDialog = false
                    errorMessage = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Konfirmasi Logout") },
            text = { Text("Apakah Anda yakin ingin keluar dari akun?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun AccountMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}


// Header
@Composable
private fun Header(username: String, role: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFEAEAEA)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Halo, $username!", fontWeight = FontWeight.SemiBold)
                    if (role.isNotBlank()) Text("Role: ${role.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Icon(Icons.Filled.Notifications, contentDescription = null)
        }
    }
}

// Main Content
@Composable
private fun MainContent(onServiceClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SearchSection()
        ServicesSection(onServiceClick)
    }
}

@Composable
private fun SearchSection() {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text("Cari lokasi atau alamat…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun ServiceCard(title: String, subtitle: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.width(150.dp).height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEAEAEA)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun ServicesSection(onServiceClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pilih Layanan", fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ServiceCard("🚗 Sewa Kendaraan", "Harian/mingguan", icon = { Text("🚗", fontSize = 24.sp) }) { onServiceClick("sewa_kendaraan") }
            ServiceCard("🚕 Sewa Driver", "Per jam/hari/minggu", icon = { Text("🚕", fontSize = 24.sp) }) { onServiceClick("rent_driver") }
        }
    }
}

// Emergency Section
@Composable
private fun EmergencySection(onEmergencyClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFECECEC)).clickable { onEmergencyClick() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Phone, contentDescription = null, tint = Color(0xFFEC1C24)) }
    }
}

// Bottom Navigation
@Composable
private fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
            label = { Text("Riwayat") }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
            label = { Text("Pembayaran") }
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
            label = { Text("Chat") }
        )
    }
}

