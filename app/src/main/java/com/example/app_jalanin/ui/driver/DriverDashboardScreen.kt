package com.example.app_jalanin.ui.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.local.entity.DriverRequest
import com.example.app_jalanin.utils.ChatHelper
import com.example.app_jalanin.utils.UsernameResolver
import com.example.app_jalanin.utils.UsernameMigrationHelper
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun DriverDashboardScreen(
    username: String? = null,
    role: String? = null,
    onLogout: () -> Unit = {},
    onRequestsClick: () -> Unit = {},
    onRequestSelected: (DriverRequest) -> Unit = {},
    onChatClick: (String) -> Unit = {}, // channelId
    onOrderHistoryClick: () -> Unit = {},
    onPaymentHistoryClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            DriverBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
        ) {
            when (selectedTab) {
                0 -> DriverHomeContent(
                    driverEmail = username ?: "",
                    username = username ?: "Driver",
                    onRequestsClick = onRequestsClick,
                    onRequestSelected = { request ->
                        // Navigate to detail will be handled by MainActivity
                        onRequestsClick()
                    },
                    onChatClick = onChatClick,
                    onLogout = onLogout,
                    onOrderHistoryClick = onOrderHistoryClick,
                    onPaymentHistoryClick = onPaymentHistoryClick
                )
                1 -> DriverOrderHistoryTabContent(
                    driverEmail = username ?: ""
                )
                2 -> DriverChatContent(
                    driverEmail = username ?: "",
                    onChatClick = onChatClick
                )
                3 -> DriverAccountTabContent(
                    username = username ?: "Driver",
                    role = role ?: "",
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun DriverHomeContent(
    driverEmail: String, 
    username: String,
    onRequestsClick: () -> Unit = {},
    onRequestSelected: (DriverRequest) -> Unit = {},
    onChatClick: (String) -> Unit = {}, // channelId
    onLogout: () -> Unit = {},
    onOrderHistoryClick: () -> Unit = {},
    onPaymentHistoryClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    
    // Load initial online status and SIM from database
    var isOnline by remember { mutableStateOf(false) }
    var driverSimCertifications by remember { mutableStateOf<String?>(null) }
    var showSimInputDialog by remember { mutableStateOf(false) }
    
    // Load pending requests count
    val pendingRequestsFlow = remember(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            database.driverRequestDao().getPendingRequestsByDriver(driverEmail)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<DriverRequest>())
        }
    }
    val pendingRequestsState = pendingRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingCount = pendingRequestsState.value.size
    
    // Show notification dialog for new requests
    var showNotificationDialog by remember { mutableStateOf(false) }
    var newRequestNotification by remember { mutableStateOf<DriverRequest?>(null) }
    var previousPendingCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(pendingCount) {
        if (pendingCount > previousPendingCount && pendingRequestsState.value.isNotEmpty()) {
            // New request detected
            val newestRequest = pendingRequestsState.value.firstOrNull()
            if (newestRequest != null && newestRequest.status == "PENDING") {
                newRequestNotification = newestRequest
                showNotificationDialog = true
            }
        }
        previousPendingCount = pendingCount
    }
    
    LaunchedEffect(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            scope.launch {
                try {
                    // Load driver profile from driver_profiles table
                    var profile = database.driverProfileDao().getByEmail(driverEmail)
                    
                    // If profile doesn't exist, create one
                    if (profile == null) {
                        profile = com.example.app_jalanin.data.local.entity.DriverProfile(
                            driverEmail = driverEmail,
                            simCertifications = null,
                            isOnline = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            synced = false
                        )
                        val profileId = database.driverProfileDao().insert(profile)
                        profile = profile.copy(id = profileId)
                        android.util.Log.d("DriverDashboard", "✅ Created new driver profile for: $driverEmail")
                    }
                    
                    isOnline = profile.isOnline
                    driverSimCertifications = profile.simCertifications
                } catch (e: Exception) {
                    android.util.Log.e("DriverDashboard", "Error loading driver profile: ${e.message}", e)
                }
            }
        }
    }
    
    // Update SIM certifications
    fun updateSimCertifications(simTypes: List<com.example.app_jalanin.data.model.SimType>) {
        if (driverEmail.isEmpty()) return
        scope.launch {
            try {
                val simString = simTypes.joinToString(",") { it.name }
                val now = System.currentTimeMillis()
                
                // Get or create driver profile
                var profile = database.driverProfileDao().getByEmail(driverEmail)
                if (profile == null) {
                    profile = com.example.app_jalanin.data.local.entity.DriverProfile(
                        driverEmail = driverEmail,
                        simCertifications = simString,
                        isOnline = false,
                        createdAt = now,
                        updatedAt = now,
                        synced = false
                    )
                    val profileId = database.driverProfileDao().insert(profile)
                    profile = profile.copy(id = profileId)
                } else {
                    // Update existing profile
                    profile = profile.copy(
                        simCertifications = simString,
                        updatedAt = now,
                        synced = false
                    )
                    database.driverProfileDao().update(profile)
                }
                
                driverSimCertifications = simString
                android.util.Log.d("DriverDashboard", "✅ Updated SIM certifications: $simString")
                
                // Sync to Firestore
                try {
                    com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.syncSingleProfile(
                        context,
                        profile.id
                    )
                    android.util.Log.d("DriverDashboard", "✅ SIM certifications synced to Firestore")
                } catch (e: Exception) {
                    android.util.Log.e("DriverDashboard", "❌ Error syncing SIM to Firestore: ${e.message}", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverDashboard", "❌ Error updating SIM: ${e.message}", e)
            }
        }
    }
    
    // Save online status to database when changed
    fun updateOnlineStatus(newStatus: Boolean) {
        if (driverEmail.isEmpty()) return
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                
                // Get or create driver profile
                var profile = database.driverProfileDao().getByEmail(driverEmail)
                if (profile == null) {
                    profile = com.example.app_jalanin.data.local.entity.DriverProfile(
                        driverEmail = driverEmail,
                        simCertifications = null,
                        isOnline = newStatus,
                        createdAt = now,
                        updatedAt = now,
                        synced = false
                    )
                    val profileId = database.driverProfileDao().insert(profile)
                    profile = profile.copy(id = profileId)
                } else {
                    // Update existing profile
                    profile = profile.copy(
                        isOnline = newStatus,
                        updatedAt = now,
                        synced = false
                    )
                    database.driverProfileDao().update(profile)
                }
                
                isOnline = newStatus
                android.util.Log.d("DriverDashboard", "✅ Updated online status: $newStatus")
                
                // Sync to Firestore
                try {
                    com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.syncSingleProfile(
                        context,
                        profile.id
                    )
                    android.util.Log.d("DriverDashboard", "✅ Online status synced to Firestore")
                } catch (e: Exception) {
                    android.util.Log.e("DriverDashboard", "❌ Error syncing online status to Firestore: ${e.message}", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverDashboard", "❌ Error updating online status: ${e.message}", e)
            }
        }
    }

    // Resolve username dynamically
    var displayUsername by remember { mutableStateOf(username) }
    
    LaunchedEffect(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    // Ensure username exists
                    com.example.app_jalanin.utils.UsernameMigrationHelper.ensureUsername(context, driverEmail)
                    
                    val dbUser = database.userDao().getUserByEmail(driverEmail)
                    displayUsername = dbUser?.username ?: driverEmail.substringBefore("@")
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Header with Driver Profile
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF06A870)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = displayUsername,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF06A870).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Driver",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF06A870),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ✅ Balance Card
            if (driverEmail.isNotEmpty()) {
                val balanceRepository = remember { com.example.app_jalanin.data.local.BalanceRepository(context) }
                
                // ✅ CRITICAL FIX: Download balance from Firestore (READ-ONLY, no recalculation)
                // Firestore balance is the SINGLE SOURCE OF TRUTH
                // DO NOT recalculate balance from transaction history
                LaunchedEffect(driverEmail) {
                    scope.launch {
                        try {
                            // Initialize balance if not exists (only creates if missing, never resets)
                            balanceRepository.initializeBalance(driverEmail)
                            
                            // Download balance from Firestore (READ-ONLY operation)
                            // This is the ONLY balance update during login/dashboard open
                            com.example.app_jalanin.data.remote.FirestoreBalanceSyncManager.downloadUserBalance(
                                context,
                                driverEmail
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("DriverDashboard", "Error downloading balance: ${e.message}", e)
                        }
                    }
                }
                
                com.example.app_jalanin.ui.common.BalanceCard(
                    userEmail = driverEmail,
                    balanceRepository = balanceRepository,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Status Driver Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Status Driver",
                        color = Color(0xFF424242),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OFFLINE",
                            color = Color(0xFF9E9E9E),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Toggle Switch
                        Switch(
                            checked = isOnline,
                            onCheckedChange = { newStatus -> updateOnlineStatus(newStatus) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF22C55E),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF9E9E9E)
                            )
                        )

                        Text(
                            text = "ONLINE",
                            color = Color(0xFF06A870),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Summary Cards Row
            var todayIncome by remember { mutableStateOf(0) }
            var todayCompletedOrders by remember { mutableStateOf(0) }
            
            // Calculate today's income and completed orders
            LaunchedEffect(driverEmail) {
                if (driverEmail.isNotEmpty()) {
                    scope.launch {
                        try {
                            // Sync from Firestore first
                            withContext(Dispatchers.IO) {
                                try {
                                    // ✅ FIX: Download driver payments (where driverEmail matches and driverIncome > 0)
                                    com.example.app_jalanin.data.remote.FirestorePaymentSyncManager.downloadDriverPayments(context, driverEmail)
                                    // Also download user payments (for passenger payments to driver)
                                    com.example.app_jalanin.data.remote.FirestorePaymentSyncManager.downloadUserPayments(context, driverEmail)
                                    // Download driver orders (DriverRental)
                                    com.example.app_jalanin.data.remote.FirestoreDriverRentalSyncManager.downloadDriverRentals(context, driverEmail)
                                    // Download vehicle rentals where driver is assigned (as deliveryDriverId or travelDriverId)
                                    com.example.app_jalanin.data.remote.FirestoreRentalSyncManager.downloadRentalsByDriver(context, driverEmail)
                                } catch (e: Exception) {
                                    android.util.Log.e("DriverDashboard", "Error syncing from Firestore: ${e.message}", e)
                                }
                                
                                // Get today's start and end timestamps
                                val calendar = java.util.Calendar.getInstance()
                                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                calendar.set(java.util.Calendar.MINUTE, 0)
                                calendar.set(java.util.Calendar.SECOND, 0)
                                calendar.set(java.util.Calendar.MILLISECOND, 0)
                                val todayStart = calendar.timeInMillis
                                
                                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                                val todayEnd = calendar.timeInMillis
                                
                                // Calculate today's income from PaymentHistory (driverIncome only, exclude vehicle rental cost)
                                val allPayments = database.paymentHistoryDao().getAllPayments()
                                val todayPayments = allPayments.filter { payment ->
                                    payment.driverEmail == driverEmail &&
                                    payment.driverIncome > 0 && // Only driver income, exclude owner income
                                    payment.createdAt >= todayStart &&
                                    payment.createdAt < todayEnd &&
                                    payment.status == "COMPLETED"
                                }
                                todayIncome = todayPayments.sumOf { it.driverIncome }
                                android.util.Log.d("DriverDashboard", "💰 Today's income: $todayIncome from ${todayPayments.size} payments")
                                
                                // Count today's completed orders (only COMPLETED status, not CONFIRMED)
                                val driverRentalsFlow = database.driverRentalDao().getRentalsByDriver(driverEmail)
                                val vehicleRentalsList = database.rentalDao().getRentalsByDriver(driverEmail)
                                
                                val driverRentals = driverRentalsFlow.first()
                                val vehicleRentals = vehicleRentalsList
                                
                                val todayDriverRentals = driverRentals.filter { rental ->
                                    rental.status == "COMPLETED" &&
                                    rental.updatedAt >= todayStart &&
                                    rental.updatedAt < todayEnd
                                }
                                
                                val todayVehicleRentals = vehicleRentals.filter { rental ->
                                    rental.status == "COMPLETED" &&
                                    rental.updatedAt >= todayStart &&
                                    rental.updatedAt < todayEnd
                                }
                                
                                todayCompletedOrders = todayDriverRentals.size + todayVehicleRentals.size
                                android.util.Log.d("DriverDashboard", "✅ Today's completed orders: $todayCompletedOrders")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DriverDashboard", "Error calculating today's stats: ${e.message}", e)
                        }
                    }
                }
            }
            
            // ✅ Income Summary Card (Total + Today)
            DriverIncomeSummaryCard(
                driverEmail = driverEmail,
                todayIncome = todayIncome
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    title = "Pendapatan Hari Ini",
                    value = if (todayIncome > 0) {
                        val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                        formatter.format(todayIncome)
                    } else {
                        "Rp 0"
                    },
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Order Selesai",
                    value = "$todayCompletedOrders trip",
                    modifier = Modifier.weight(1f)
                )
            }

            // SIM Info Card
            if (driverSimCertifications.isNullOrBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    border = BorderStroke(1.dp, Color(0xFFFFC107))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFF856404)
                            )
                    Text(
                                text = "SIM Belum Diinput",
                                color = Color(0xFF856404),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                        }
                        Text(
                            text = "Anda perlu menginput SIM untuk dapat menerima order. Klik tombol di bawah untuk input SIM.",
                            color = Color(0xFF856404),
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { showSimInputDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF856404)
                            )
                        ) {
                            Icon(Icons.Default.DriveEta, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Input SIM")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                        ) {
                            Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                        Column {
                            Text(
                                text = "SIM Anda",
                                color = Color(0xFF757575),
                                fontSize = 12.sp
                                )
                                Text(
                                text = driverSimCertifications?.replace("SIM_", "SIM ") ?: "-",
                                    color = Color(0xFF424242),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                                )
                            }
                        TextButton(onClick = { showSimInputDialog = true }) {
                            Text("Edit")
                        }
                    }
                }
            }

            // Pending Requests Card
            if (driverEmail.isNotEmpty() && !driverSimCertifications.isNullOrBlank()) {
                Card(
                                modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRequestsClick),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pendingCount > 0) Color(0xFFFFF3E0) else Color.White
                    ),
                    border = BorderStroke(
                        1.dp, 
                        if (pendingCount > 0) Color(0xFFFF9800) else Color(0xFFE0E0E0)
                    )
                ) {
                            Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (pendingCount > 0) Color(0xFFFF9800) else Color(0xFF9E9E9E),
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                            Text(
                                    text = "Request Driver",
                                color = Color(0xFF424242),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                                Text(
                                    text = if (pendingCount > 0) {
                                        "$pendingCount request menunggu konfirmasi"
                                    } else {
                                        "Tidak ada request pending"
                                    },
                                    color = if (pendingCount > 0) Color(0xFFFF9800) else Color(0xFF757575),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (pendingCount > 0) {
                            Badge(
                                containerColor = Color(0xFFFF9800)
                    ) {
                        Text(
                                    text = pendingCount.toString(),
                            color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                        )
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E)
                            )
                        }
                    }
                }
                
                // ✅ Pending Requests Summary (Ringkas)
                if (pendingCount > 0 && pendingRequestsState.value.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Request Pending",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                )
                                TextButton(onClick = onRequestsClick) {
                                    Text("Lihat Semua", fontSize = 12.sp)
                                }
                            }
                            
                            // Show first 3 requests only (summary)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(pendingRequestsState.value.take(3)) { request ->
                                    DriverRequestSummaryCard(
                                        request = request,
                                        onClick = { onRequestSelected(request) }
                                    )
                                }
                            }
                            
                            if (pendingCount > 3) {
                                Text(
                                    text = "+${pendingCount - 3} request lainnya",
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Order Aktif Card
            DriverActiveOrdersCard(
                driverEmail = driverEmail,
                database = database,
                onChatClick = onChatClick,
                onRequestSelected = onRequestSelected
            )

            // SIM Input Dialog
            if (showSimInputDialog) {
                DriverSimInputDialog(
                    currentSimCertifications = driverSimCertifications,
                    onDismiss = { showSimInputDialog = false },
                    onConfirm = { simTypes ->
                        updateSimCertifications(simTypes)
                        showSimInputDialog = false
                    }
                )
            }
            
            // Notification Dialog for new request
            if (showNotificationDialog && newRequestNotification != null) {
                AlertDialog(
                    onDismissRequest = { showNotificationDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFFF9800)
                        )
                    },
                    title = {
                        Text("Request Baru!")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                                text = "Anda mendapat request dari:",
                                fontSize = 14.sp
                            )
                            Text(
                                text = newRequestNotification!!.passengerName,
                    fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kendaraan: ${newRequestNotification!!.vehicleBrand} ${newRequestNotification!!.vehicleModel}",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                            Text(
                                text = "Lokasi: ${newRequestNotification!!.pickupAddress}",
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showNotificationDialog = false
                                onRequestsClick()
                            }
                        ) {
                            Text("Lihat Detail")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNotificationDialog = false }) {
                            Text("Nanti")
            }
        }
                )
    }
}
    }
}

/**
 * Card untuk menampilkan order aktif driver
 */
@Composable
private fun DriverActiveOrdersCard(
    driverEmail: String,
    database: com.example.app_jalanin.data.AppDatabase,
    onChatClick: (String) -> Unit = {},
    onRequestSelected: (DriverRequest) -> Unit = {}
) {
    val activeRequestsFlow = remember(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            database.driverRequestDao().getActiveRequestsByDriver(driverEmail)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<DriverRequest>())
        }
    }
    val activeRequests by activeRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Order Aktif",
                    color = Color(0xFF424242),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${activeRequests.size} order",
                    color = Color(0xFF757575),
                    fontSize = 12.sp
                )
            }
            
            if (activeRequests.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Tidak ada order aktif",
                        color = Color(0xFF757575),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Pastikan status Anda ONLINE untuk menerima order",
                        color = Color(0xFF9E9E9E),
                        fontSize = 12.sp
                    )
                }
            } else {
                activeRequests.forEach { request ->
                    DriverActiveOrderItem(
                        request = request,
                        driverEmail = driverEmail,
                        database = database,
                        onChatClick = onChatClick,
                        onRequestClick = { onRequestSelected(request) }
                    )
                    if (request != activeRequests.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Item untuk menampilkan detail order aktif dari DriverRequest
 */
@Composable
private fun DriverActiveOrderItem(
    request: DriverRequest,
    driverEmail: String,
    database: com.example.app_jalanin.data.AppDatabase,
    onChatClick: (String) -> Unit,
    onRequestClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    
    val statusColor = when (request.status) {
        "ACCEPTED" -> Color(0xFF2196F3)
        "DRIVER_ARRIVING" -> Color(0xFF9C27B0)
        "DRIVER_ARRIVED" -> Color(0xFF4CAF50)
        "IN_PROGRESS" -> Color(0xFF00BCD4)
        else -> Color(0xFF9E9E9E)
    }
    
    val statusText = when (request.status) {
        "ACCEPTED" -> "Diterima"
        "DRIVER_ARRIVING" -> "Menuju Lokasi"
        "DRIVER_ARRIVED" -> "Tiba di Lokasi"
        "IN_PROGRESS" -> "Sedang Berjalan"
        else -> request.status
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRequestClick),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.passengerName,
                    color = Color(0xFF424242),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        if (request.vehicleType == "MOBIL") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF757575)
                    )
                    Text(
                        text = "${request.vehicleBrand} ${request.vehicleModel}",
                        color = Color(0xFF757575),
                        fontSize = 12.sp
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF4CAF50)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.pickupAddress,
                    color = Color(0xFF424242),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (request.destinationAddress != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF2196F3)
                        )
                        Text(
                            text = request.destinationAddress ?: "",
                            color = Color(0xFF757575),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dibuat: ${dateFormat.format(request.createdAt)}",
                color = Color(0xFF9E9E9E),
                fontSize = 10.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Chat Button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                // Use DriverRequest id as rentalId and status as orderStatus
                                val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                                    database,
                                    driverEmail,
                                    request.passengerEmail,
                                    request.id, // Use request id as rentalId
                                    request.status // Use request status as orderStatus
                                )
                                onChatClick(channel.id)
                            } catch (e: Exception) {
                                android.util.Log.e("DriverActiveOrder", "Error creating chat: ${e.message}", e)
                            }
                        }
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = "Chat",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Chat",
                        fontSize = 11.sp
                    )
                }
                // View Detail Button
                OutlinedButton(
                    onClick = onRequestClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Detail",
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Item untuk menampilkan detail order (LEGACY - untuk Rental)
 */
@Composable
private fun DriverOrderItem(rental: Rental) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("id", "ID")) }
    
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = rental.vehicleName,
                color = Color(0xFF424242),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (rental.status) {
                    "DELIVERING" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                    "ACTIVE" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                }
            ) {
                Text(
                    text = when (rental.status) {
                        "DELIVERING" -> "Mengantar"
                        "ACTIVE" -> "Aktif"
                        else -> rental.status
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (rental.status) {
                        "DELIVERING" -> Color(0xFFE65100)
                        "ACTIVE" -> Color(0xFF2E7D32)
                        else -> Color(0xFF616161)
                    }
                )
            }
        }
        
        if (rental.deliveryAddress.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    Icons.Default.LocationOn,
                contentDescription = null,
                    tint = Color(0xFF06A870),
                    modifier = Modifier.size(16.dp)
            )
            Text(
                    text = rental.deliveryAddress,
                    color = Color(0xFF757575),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
            )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Total: ${currencyFormat.format(rental.totalPrice)}",
                color = Color(0xFF424242),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = rental.getShortDuration(),
                color = Color(0xFF757575),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DriverAccountContent(
    onRequestsClick: () -> Unit = {},
    username: String,
    role: String,
    onLogout: () -> Unit,
    onOrderHistoryClick: () -> Unit = {},
    onPaymentHistoryClick: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSimInputDialog by remember { mutableStateOf(false) }
    var showEditUsernameDialog by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    
    // Load current user data and resolve username dynamically
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
    
    var driverSimCertifications by remember { mutableStateOf<String?>(null) }
    var driverEmail by remember { mutableStateOf(currentUserEmail ?: username) }
    
    LaunchedEffect(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            scope.launch {
                try {
                    // Load driver profile from driver_profiles table
                    val profile = database.driverProfileDao().getByEmail(driverEmail)
                    driverSimCertifications = profile?.simCertifications
                } catch (e: Exception) {
                    android.util.Log.e("DriverAccount", "Error loading SIM: ${e.message}", e)
                }
            }
        }
    }
    
    fun updateSimCertifications(simTypes: List<com.example.app_jalanin.data.model.SimType>) {
        if (driverEmail.isEmpty()) return
        scope.launch {
            try {
                val simString = simTypes.joinToString(",") { it.name }
                val now = System.currentTimeMillis()
                
                // Get or create driver profile
                var profile = database.driverProfileDao().getByEmail(driverEmail)
                if (profile == null) {
                    profile = com.example.app_jalanin.data.local.entity.DriverProfile(
                        driverEmail = driverEmail,
                        simCertifications = simString,
                        isOnline = false,
                        createdAt = now,
                        updatedAt = now,
                        synced = false
                    )
                    val profileId = database.driverProfileDao().insert(profile)
                    profile = profile.copy(id = profileId)
                } else {
                    // Update existing profile
                    profile = profile.copy(
                        simCertifications = simString,
                        updatedAt = now,
                        synced = false
                    )
                    database.driverProfileDao().update(profile)
                }
                
                driverSimCertifications = simString
                android.util.Log.d("DriverAccount", "✅ Updated SIM certifications: $simString")
                
                // Sync to Firestore
                try {
                    com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.syncSingleProfile(
                        context,
                        profile.id
                    )
                } catch (e: Exception) {
                    android.util.Log.e("DriverAccount", "❌ Error syncing SIM to Firestore: ${e.message}", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverAccount", "Error updating SIM: ${e.message}", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFF8F9FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Akun",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF424242),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                        .background(Color(0xFF06A870)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column {
                    Text(
                        text = displayUsername,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )
                    Text(
                        text = "Driver",
                        fontSize = 14.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
        }

        // SIM Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SIM Certification",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF424242)
                    )
                    TextButton(onClick = { showSimInputDialog = true }) {
                        Text(if (driverSimCertifications.isNullOrBlank()) "Input" else "Edit")
                    }
                }
                
                if (driverSimCertifications.isNullOrBlank()) {
                    Text(
                        text = "Belum ada SIM diinput",
                        color = Color(0xFF757575),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Input SIM Anda untuk dapat menerima order",
                        color = Color(0xFF9E9E9E),
                        fontSize = 12.sp
                    )
                } else {
                    val simText = driverSimCertifications ?: ""
                    Text(
                        text = simText.replace("SIM_", "SIM "),
                        color = Color(0xFF424242),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Menu Items
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DriverAccountMenuItem(
                    icon = Icons.Filled.Notifications,
                    title = "Request Driver",
                    onClick = onRequestsClick
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                DriverAccountMenuItem(
                    icon = Icons.Filled.History,
                    title = "Riwayat Pesanan",
                    onClick = onOrderHistoryClick
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                DriverAccountMenuItem(
                    icon = Icons.Filled.Payment,
                    title = "Riwayat Pembayaran",
                    onClick = onPaymentHistoryClick
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                DriverAccountMenuItem(
                    icon = Icons.Filled.Person,
                    title = "Edit Profil",
                    onClick = { showEditUsernameDialog = true }
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                DriverAccountMenuItem(
                    icon = Icons.Filled.DirectionsCar,
                    title = "Info Kendaraan",
                    onClick = { /* TODO */ }
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                DriverAccountMenuItem(
                    icon = Icons.Filled.Settings,
                    title = "Pengaturan",
                    onClick = { /* TODO */ }
                )
                HorizontalDivider(color = Color(0xFFE0E0E0))
                DriverAccountMenuItem(
                    icon = Icons.Filled.Info,
                    title = "Bantuan",
                    onClick = { /* TODO */ }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53E3E)
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

    // Edit Username Dialog
    if (showEditUsernameDialog) {
        var newUsername by remember { mutableStateOf(displayUsername) }
        var isUpdating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                showEditUsernameDialog = false
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
                        
                        if (newUsername.length < 3) {
                            errorMessage = "Username minimal 3 karakter"
                            return@Button
                        }
                        
                        if (newUsername.contains(" ")) {
                            errorMessage = "Username tidak boleh mengandung spasi"
                            return@Button
                        }
                        
                        if (newUsername == displayUsername) {
                            showEditUsernameDialog = false
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
                                            android.util.Log.e("DriverAccount", "Error syncing username: ${e.message}", e)
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            displayUsername = newUsername
                                            currentUser = updatedUser
                                            showEditUsernameDialog = false
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
                    showEditUsernameDialog = false
                    errorMessage = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

    // SIM Input Dialog
    if (showSimInputDialog) {
        DriverSimInputDialog(
            currentSimCertifications = driverSimCertifications,
            onDismiss = { showSimInputDialog = false },
            onConfirm = { simTypes ->
                updateSimCertifications(simTypes)
                showSimInputDialog = false
            }
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Apakah Anda yakin ingin logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = Color(0xFFE53E3E))
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
private fun DriverAccountMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF424242)
        )
        Text(
            text = title,
            fontSize = 16.sp,
            color = Color(0xFF424242)
        )
    }
}

// Summary Card Component
@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color(0xFF757575),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
            color = Color(0xFF424242),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
private fun DriverBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.border(BorderStroke(1.dp, Color(0xFFE0E0E0)))
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = {
                Icon(
                    Icons.Filled.Dashboard,
                    contentDescription = null,
                    tint = if (selectedTab == 0) Color(0xFF424242) else Color(0xFF9E9E9E)
                )
            },
            label = {
                Text(
                    "Dashboard",
                    color = if (selectedTab == 0) Color(0xFF424242) else Color(0xFF9E9E9E),
                    fontSize = 10.sp,
                    fontWeight = if (selectedTab == 0) FontWeight.Medium else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = if (selectedTab == 1) Color(0xFF424242) else Color(0xFF9E9E9E)
                )
            },
            label = {
                Text(
                    "Riwayat Pesanan",
                    color = if (selectedTab == 1) Color(0xFF424242) else Color(0xFF9E9E9E),
                    fontSize = 10.sp,
                    fontWeight = if (selectedTab == 1) FontWeight.Medium else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = {
                Icon(
                    Icons.Filled.Chat,
                    contentDescription = null,
                    tint = if (selectedTab == 2) Color(0xFF424242) else Color(0xFF9E9E9E)
                )
            },
            label = {
                Text(
                    "Chat",
                    color = if (selectedTab == 2) Color(0xFF424242) else Color(0xFF9E9E9E),
                    fontSize = 10.sp,
                    fontWeight = if (selectedTab == 2) FontWeight.Medium else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (selectedTab == 3) Color(0xFF424242) else Color(0xFF9E9E9E)
                )
            },
            label = {
                Text(
                    "Akun",
                    color = if (selectedTab == 3) Color(0xFF424242) else Color(0xFF9E9E9E),
                    fontSize = 10.sp,
                    fontWeight = if (selectedTab == 3) FontWeight.Medium else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent
            )
        )
    }
}

/**
 * Driver Account Tab Content
 * Simplified version with only logout functionality
 */
@Composable
private fun DriverAccountTabContent(
    username: String,
    role: String,
    onLogout: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditUsernameDialog by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // Load current user data and resolve username dynamically
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFF8F9FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Akun",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF424242),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                        .background(Color(0xFF06A870)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column {
                    Text(
                        text = displayUsername,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )
                    Text(
                        text = "Driver",
                        fontSize = 14.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
        }
        
        // Edit Username Button
        Button(
            onClick = { showEditUsernameDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit Username",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53E3E)
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

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Apakah Anda yakin ingin logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = Color(0xFFE53E3E))
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

/**
 * Driver Order History Tab Content
 * Shows Ongoing and Completed orders
 */
@Composable
private fun DriverOrderHistoryTabContent(
    driverEmail: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    
    var driverRentals by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.DriverRental>>(emptyList()) }
    var vehicleRentals by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.Rental>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(driverEmail) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Sync from Firestore first
                try {
                    com.example.app_jalanin.data.remote.FirestoreDriverRentalSyncManager.downloadDriverRentals(context, driverEmail)
                    // Vehicle rentals are synced via downloadUserRentals for passengers, but we need rentals where driver is assigned
                    // These are already synced in MainActivity, so we just load from local DB
                } catch (e: Exception) {
                    android.util.Log.e("DriverOrderHistory", "Error syncing from Firestore: ${e.message}", e)
                }
                
                // Load driver rentals (independent driver rentals)
                val driverRentalsFlow = database.driverRentalDao()
                    .getRentalsByDriver(driverEmail)
                val driverRentalsList = driverRentalsFlow.first()
                
                // Load vehicle rentals where driver is assigned
                val vehicleRentalsList = database.rentalDao()
                    .getRentalsByDriver(driverEmail)
                
                driverRentals = driverRentalsList
                vehicleRentals = vehicleRentalsList
                isLoading = false
            }
        } catch (e: Exception) {
            android.util.Log.e("DriverOrderHistory", "Error loading orders: ${e.message}", e)
            isLoading = false
        }
    }
    
    // Load payments for orders
    var paymentsMap by remember { mutableStateOf<Map<String, com.example.app_jalanin.data.local.entity.PaymentHistory>>(emptyMap()) }
    
    LaunchedEffect(driverRentals, vehicleRentals) {
        if (driverRentals.isNotEmpty() || vehicleRentals.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val payments = mutableMapOf<String, com.example.app_jalanin.data.local.entity.PaymentHistory>()
                
                // Load payments for driver rentals
                driverRentals.forEach { rental ->
                    val paymentList = database.paymentHistoryDao().getPaymentHistoryByRental(rental.id)
                    val payment = paymentList.firstOrNull { 
                        it.driverEmail == driverEmail && it.driverIncome > 0 
                    }
                    if (payment != null) {
                        payments[rental.id] = payment
                    }
                }
                
                // Load payments for vehicle rentals
                vehicleRentals.forEach { rental ->
                    val paymentList = database.paymentHistoryDao().getPaymentHistoryByRental(rental.id)
                    val payment = paymentList.firstOrNull { 
                        it.driverEmail == driverEmail && it.driverIncome > 0 
                    }
                    if (payment != null) {
                        payments[rental.id] = payment
                    }
                }
                
                paymentsMap = payments
            }
        }
    }
    
    // Combine and categorize orders
    val allOrders = remember(driverRentals, vehicleRentals, paymentsMap) {
        val orders = mutableListOf<DriverOrderItem>()
        
        // Add driver rentals (private vehicle orders)
        driverRentals.forEach { rental ->
            val payment = paymentsMap[rental.id]
            val paymentAmount = payment?.driverIncome ?: rental.price.toInt()
            
            orders.add(
                DriverOrderItem(
                    id = rental.id,
                    orderType = "private_vehicle",
                    serviceVariant = "Driver only",
                    passengerName = "Unknown", // Will be resolved dynamically via username
                    passengerEmail = rental.passengerEmail,
                    date = rental.createdAt,
                    status = when (rental.status) {
                        "COMPLETED", "CONFIRMED" -> "completed"
                        else -> "ongoing"
                    },
                    vehicleInfo = "${rental.vehicleType} (Private Vehicle)",
                    vehicleName = "${rental.vehicleType} (Private Vehicle)",
                    vehicleType = rental.vehicleType,
                    vehicleOwnerName = null, // Private vehicle, no owner
                    paymentAmount = paymentAmount
                )
            )
        }
        
        // Add vehicle rentals
        vehicleRentals.forEach { rental ->
            val payment = paymentsMap[rental.id]
            val paymentAmount = payment?.driverIncome ?: 0
            
            // Store owner email for username resolution in UI
            val ownerEmail = rental.ownerEmail
            
            val serviceVariant = if (rental.isWithDriver) "+Driver" else "Non-driver"
            
            orders.add(
                DriverOrderItem(
                    id = rental.id,
                    orderType = "rental_vehicle",
                    serviceVariant = serviceVariant,
                    passengerName = "Unknown", // Will be resolved dynamically in UI
                    passengerEmail = rental.userEmail,
                    date = rental.startDate,
                    status = when (rental.status) {
                        "COMPLETED" -> "completed"
                        else -> "ongoing"
                    },
                    vehicleInfo = rental.vehicleName,
                    vehicleName = rental.vehicleName,
                    vehicleType = rental.vehicleType,
                    vehicleOwnerName = ownerEmail, // Store email for username resolution
                    paymentAmount = paymentAmount
                )
            )
        }
        
        orders.sortedByDescending { it.date }.toList()
    }
    
    val ongoingOrders = allOrders.filter { it.status == "ongoing" }
    val completedOrders = allOrders.filter { it.status == "completed" }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ongoing Orders Section
                if (ongoingOrders.isNotEmpty()) {
                    item {
                        Text(
                            text = "Ongoing Orders",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(ongoingOrders) { order ->
                        DriverOrderCard(
                            order = order,
                            dateFormat = dateFormat
                        )
                    }
                }
                
                // Completed Orders Section
                if (completedOrders.isNotEmpty()) {
                    item {
                        Text(
                            text = "Completed Orders",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(top = if (ongoingOrders.isNotEmpty()) 8.dp else 0.dp, bottom = 8.dp)
                        )
                    }
                    items(completedOrders) { order ->
                        DriverOrderCard(
                            order = order,
                            dateFormat = dateFormat
                        )
                    }
                }
                
                // Empty State
                if (allOrders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9E9E9E)
                                )
                                Text(
                                    text = "Tidak ada riwayat pesanan",
                                    fontSize = 16.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DriverOrderItem(
    val id: String,
    val orderType: String, // "private_vehicle" or "rental_vehicle"
    val serviceVariant: String, // "Driver only", "Non-driver", "+Driver"
    val passengerName: String,
    val passengerEmail: String,
    val date: Long,
    val status: String, // "ongoing" or "completed"
    val vehicleInfo: String,
    val vehicleName: String,
    val vehicleType: String,
    val vehicleOwnerName: String? = null,
    val paymentAmount: Int = 0 // Driver's received payment amount
)

@Composable
private fun DriverOrderCard(
    order: DriverOrderItem,
    dateFormat: SimpleDateFormat
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Resolve passenger username dynamically
    var passengerUsername by remember { mutableStateOf<String?>(null) }
    var ownerUsername by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(order.passengerEmail) {
        scope.launch {
            passengerUsername = kotlinx.coroutines.withContext(Dispatchers.IO) {
                com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                    context,
                    order.passengerEmail
                )
            }
        }
    }
    
    LaunchedEffect(order.vehicleOwnerName) {
        if (order.vehicleOwnerName != null) {
            // vehicleOwnerName stores owner email for username resolution
            scope.launch {
                ownerUsername = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                        context,
                        order.vehicleOwnerName!!
                    )
                }
            }
        }
    }
    
    val formatRupiah = remember {
        { amount: Int ->
            val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID"))
            formatter.format(amount)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Order Type Badges & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Order Type Badge (private vehicle or rental vehicle)
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { 
                            Text(
                                if (order.orderType == "private_vehicle") "private vehicle" else "Rental vehicle",
                                fontSize = 10.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = when (order.orderType) {
                                "private_vehicle" -> Color(0xFF4CAF50)
                                "rental_vehicle" -> Color(0xFF2196F3)
                                else -> Color(0xFF9E9E9E)
                            },
                            labelColor = Color.White
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                    
                    // Service Variant Badge (Driver only, Non-driver, +Driver)
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(order.serviceVariant, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = when (order.serviceVariant) {
                                "Driver only" -> Color(0xFF4CAF50)
                                "+Driver" -> Color(0xFF2196F3)
                                "Non-driver" -> Color(0xFF9E9E9E)
                                else -> Color(0xFF9E9E9E)
                            },
                            labelColor = Color.White
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (order.status) {
                        "ongoing" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        "completed" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = order.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (order.status) {
                            "ongoing" -> Color(0xFFFF9800)
                            "completed" -> Color(0xFF4CAF50)
                            else -> Color(0xFF9E9E9E)
                        }
                    )
                }
            }
            
            // Passenger Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = passengerUsername ?: "Unknown",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Vehicle Info
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF757575)
                    )
                    Column {
                        Text(
                            text = order.vehicleName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                        Text(
                            text = "Tipe: ${order.vehicleType}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Vehicle Owner (for rental vehicles)
                if (order.vehicleOwnerName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF757575)
                        )
                        Text(
                            text = "Owner: ${ownerUsername ?: "Unknown"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Payment Amount
            if (order.paymentAmount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Payment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "Payment:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = formatRupiah(order.paymentAmount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            
            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF757575)
                )
                Text(
                    text = dateFormat.format(Date(order.date)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Driver Income History Tab Content
 * Shows total income and list of payments
 */
@Composable
private fun DriverIncomeSummaryCard(
    driverEmail: String,
    todayIncome: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    var totalIncome by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(driverEmail) {
        try {
            withContext(Dispatchers.IO) {
                // Sync from Firestore first
                try {
                    com.example.app_jalanin.data.remote.FirestorePaymentSyncManager.downloadDriverPayments(context, driverEmail)
                    com.example.app_jalanin.data.remote.FirestorePaymentSyncManager.downloadUserPayments(context, driverEmail)
                } catch (e: Exception) {
                    android.util.Log.e("DriverIncomeSummary", "Error syncing payments: ${e.message}", e)
                }
                
                // Calculate total income from PaymentHistory (driverIncome only)
                val allPayments = database.paymentHistoryDao().getAllPaymentsFlow().first()
                val driverPayments = allPayments.filter { 
                    it.driverEmail == driverEmail && 
                    it.driverIncome > 0 && 
                    it.status == "COMPLETED"
                }
                
                totalIncome = driverPayments.sumOf { it.driverIncome }
                isLoading = false
            }
        } catch (e: Exception) {
            android.util.Log.e("DriverIncomeSummary", "Error calculating income: ${e.message}", e)
            isLoading = false
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "💰 Total Pendapatan",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D32)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF4CAF50)
                )
            } else {
                Text(
                    text = formatRupiah(totalIncome.toDouble()),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Hari Ini",
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32).copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatRupiah(todayIncome.toDouble()),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                }
                Text(
                    text = "Semua Waktu",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32).copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverChatContent(
    driverEmail: String,
    onChatClick: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.ChatChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load active requests/rentals to determine if chat is available
    val pendingRequestsFlow = remember(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            database.driverRequestDao().getPendingRequestsByDriver(driverEmail)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<DriverRequest>())
        }
    }
    val pendingRequestsState = pendingRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    val activeDriverRentalsFlow = remember(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            database.driverRentalDao().getRentalsByDriver(driverEmail)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<com.example.app_jalanin.data.local.entity.DriverRental>())
        }
    }
    val activeDriverRentalsState = activeDriverRentalsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeDriverRental = activeDriverRentalsState.value.firstOrNull { 
        it.status == "CONFIRMED" || it.status == "ACTIVE" 
    }
    
    var activeVehicleRentals by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.Rental>>(emptyList()) }
    
    var chatHistory by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.ChatChannel>>(emptyList()) }
    
    LaunchedEffect(driverEmail) {
        if (driverEmail.isNotEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    activeVehicleRentals = database.rentalDao().getRentalsByDriver(driverEmail)
                    // Load only active channels (ongoing orders)
                    channels = database.chatChannelDao().getActiveChannelsByUser(driverEmail).first()
                    // Load completed channels (chat history)
                    chatHistory = database.chatChannelDao().getCompletedChannelsByUser(driverEmail).first()
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverChatContent", "Error loading channels: ${e.message}", e)
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }
    
    val activeVehicleRental = activeVehicleRentals.firstOrNull { 
        it.status == "ACTIVE" || it.status == "CONFIRMED" 
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
        } else if (channels.isEmpty() && activeDriverRental == null && activeVehicleRental == null && pendingRequestsState.value.isEmpty()) {
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
                        text = "Chat akan tersedia saat Anda menerima order dari penumpang",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
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
                if (activeDriverRental != null || activeVehicleRental != null || pendingRequestsState.value.isNotEmpty()) {
                    item {
                        Text(
                            text = "Chat Aktif",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // Active driver rental chat
                    if (activeDriverRental != null && activeDriverRental.passengerEmail != null) {
                        item {
                            var channelId by remember { mutableStateOf<String?>(null) }
                            
                            LaunchedEffect(activeDriverRental.id, activeDriverRental.passengerEmail, activeDriverRental.status) {
                                scope.launch {
                                    try {
                                        val channel = ChatHelper.getOrCreateDMChannel(
                                            database,
                                            driverEmail,
                                            activeDriverRental.passengerEmail!!,
                                            activeDriverRental.id,
                                            activeDriverRental.status
                                        )
                                        channelId = channel.id
                                    } catch (e: Exception) {
                                        android.util.Log.e("DriverChatContent", "Error creating channel: ${e.message}", e)
                                    }
                                }
                            }
                            
                            var passengerUsername by remember { mutableStateOf<String?>(null) }
                            
                            LaunchedEffect(activeDriverRental.passengerEmail) {
                                scope.launch {
                                    passengerUsername = withContext(Dispatchers.IO) {
                                        com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                                            context,
                                            activeDriverRental.passengerEmail!!
                                        )
                                    }
                                }
                            }
                            
                            if (channelId != null) {
                                ActiveChatCard(
                                    title = "Chat dengan Penumpang",
                                    subtitle = passengerUsername ?: "Penumpang",
                                    channelId = channelId!!,
                                    onChatClick = onChatClick
                                )
                            }
                        }
                    }
                    
                    // Active vehicle rental chat
                    if (activeVehicleRental != null && activeVehicleRental.userEmail != null) {
                        item {
                            var channelId by remember { mutableStateOf<String?>(null) }
                            
                            LaunchedEffect(activeVehicleRental.id, activeVehicleRental.userEmail, activeVehicleRental.status) {
                                scope.launch {
                                    try {
                                        val channel = ChatHelper.getOrCreateDMChannel(
                                            database,
                                            driverEmail,
                                            activeVehicleRental.userEmail!!,
                                            activeVehicleRental.id,
                                            activeVehicleRental.status
                                        )
                                        channelId = channel.id
                                    } catch (e: Exception) {
                                        android.util.Log.e("DriverChatContent", "Error creating channel: ${e.message}", e)
                                    }
                                }
                            }
                            
                            if (channelId != null) {
                                ActiveChatCard(
                                    title = "Chat dengan Penumpang",
                                    subtitle = activeVehicleRental.vehicleName,
                                    channelId = channelId!!,
                                    onChatClick = onChatClick
                                )
                            }
                        }
                    }
                    
                    // Pending request chat
                    pendingRequestsState.value.forEach { request ->
                        if (request.passengerEmail != null) {
                            item {
                                var channelId by remember { mutableStateOf<String?>(null) }
                                
                                LaunchedEffect(request.id, request.passengerEmail, request.status) {
                                    scope.launch {
                                        try {
                                            // Use DriverRequest id as rentalId and status as orderStatus
                                            val channel = ChatHelper.getOrCreateDMChannel(
                                                database,
                                                driverEmail,
                                                request.passengerEmail!!,
                                                request.id, // Use request id as rentalId
                                                request.status // Use request status as orderStatus
                                            )
                                            channelId = channel.id
                                        } catch (e: Exception) {
                                            android.util.Log.e("DriverChatContent", "Error creating channel: ${e.message}", e)
                                        }
                                    }
                                }
                                
                                var passengerUsername by remember { mutableStateOf<String?>(null) }
                                
                                LaunchedEffect(request.passengerEmail) {
                                    scope.launch {
                                        passengerUsername = withContext(Dispatchers.IO) {
                                            com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                                                context,
                                                request.passengerEmail!!
                                            )
                                        }
                                    }
                                }
                                
                                if (channelId != null) {
                                    ActiveChatCard(
                                        title = "Chat dengan Penumpang",
                                        subtitle = passengerUsername ?: "Penumpang",
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
                            userEmail = driverEmail,
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
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    var otherParticipantName by remember { mutableStateOf<String?>(null) }
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
                Text(
                    text = otherParticipantName ?: otherParticipantEmail ?: "Unknown",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

private fun formatRupiah(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return formatter.format(amount).replace("Rp", "Rp ")
}

@Composable
private fun DriverIncomeHistoryTabContent(
    driverEmail: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    
    var payments by remember { mutableStateOf<List<com.example.app_jalanin.data.local.entity.PaymentHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(driverEmail) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Sync from Firestore first
                try {
                    com.example.app_jalanin.data.remote.FirestorePaymentSyncManager.downloadUserPayments(context, driverEmail)
                } catch (e: Exception) {
                    android.util.Log.e("DriverIncomeHistory", "Error syncing payments from Firestore: ${e.message}", e)
                }
                
                // Load payments where driver is the receiver
                val allPaymentsFlow = database.paymentHistoryDao()
                    .getAllPaymentsFlow()
                val allPayments = allPaymentsFlow.first()
                    .filter { 
                        it.driverEmail == driverEmail && it.driverIncome > 0
                    }
                
                payments = allPayments.sortedByDescending { it.createdAt }
                isLoading = false
            }
        } catch (e: Exception) {
            android.util.Log.e("DriverIncomeHistory", "Error loading payments: ${e.message}", e)
            isLoading = false
        }
    }
    
    val totalIncome = remember(payments) {
        payments.sumOf { it.driverIncome.toLong() }.toInt()
    }
    
    val formatRupiah = remember {
        { amount: Int ->
            val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id", "ID"))
            formatter.format(amount)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Income Summary
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Total Pendapatan",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = formatRupiah(totalIncome),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${payments.size} pembayaran",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                // Income Items
                if (payments.isNotEmpty()) {
                    item {
                        Text(
                            text = "Riwayat Pembayaran",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(payments) { payment ->
                        DriverIncomeCard(
                            payment = payment,
                            dateFormat = dateFormat,
                            formatRupiah = formatRupiah
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Payment,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9E9E9E)
                                )
                                Text(
                                    text = "Tidak ada riwayat pembayaran",
                                    fontSize = 16.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverIncomeCard(
    payment: com.example.app_jalanin.data.local.entity.PaymentHistory,
    dateFormat: SimpleDateFormat,
    formatRupiah: (Int) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Vehicle Name & Role Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = payment.vehicleName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text("driver", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF4CAF50),
                            labelColor = Color.White
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
            
            // Payment Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Amount:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = formatRupiah(payment.driverIncome),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            
            // Order Reference
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF757575)
                )
                Text(
                    text = "Order ID: ${payment.rentalId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF757575)
                )
                Text(
                    text = dateFormat.format(Date(payment.createdAt)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Compact card untuk menampilkan summary request di dashboard
 */
@Composable
private fun DriverRequestSummaryCard(
    request: DriverRequest,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.passengerName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "PENDING",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF9800)
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (request.vehicleType == "MOBIL") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF757575)
                )
                Text(
                    text = "${request.vehicleBrand} ${request.vehicleModel}",
                    fontSize = 11.sp,
                    color = Color(0xFF757575),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF4CAF50)
                )
                Text(
                    text = request.pickupAddress,
                    fontSize = 10.sp,
                    color = Color(0xFF999999),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

