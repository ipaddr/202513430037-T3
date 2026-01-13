package com.example.app_jalanin.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRequest
import com.example.app_jalanin.utils.UsernameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Screen untuk driver melihat pending requests dengan notifikasi pop-up
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRequestsScreen(
    driverEmail: String,
    onBackClick: () -> Unit = {},
    onRequestSelected: (DriverRequest) -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var pendingRequests by remember { mutableStateOf<List<DriverRequest>>(emptyList()) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var newRequestNotification by remember { mutableStateOf<DriverRequest?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Pending, 1: All
    
    // Load pending requests
    val pendingRequestsFlow = remember(driverEmail) {
        database.driverRequestDao().getPendingRequestsByDriver(driverEmail)
    }
    val pendingRequestsState = pendingRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Load all requests
    val allRequestsFlow = remember(driverEmail) {
        database.driverRequestDao().getRequestsByDriver(driverEmail)
    }
    val allRequestsState = allRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Check for new pending requests and show notification
    LaunchedEffect(pendingRequestsState.value) {
        val currentPending = pendingRequestsState.value
        if (currentPending.isNotEmpty() && currentPending != pendingRequests) {
            // New request detected
            val newestRequest = currentPending.firstOrNull()
            if (newestRequest != null && newestRequest.status == "PENDING") {
                newRequestNotification = newestRequest
                showNotificationDialog = true
            }
        }
        pendingRequests = currentPending
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Driver") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pending")
                            if (pendingRequestsState.value.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge {
                                    Text(pendingRequestsState.value.size.toString())
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Semua") }
                )
            }
            
            // Content
            when (selectedTab) {
                0 -> {
                    if (pendingRequestsState.value.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "Tidak Ada Request Pending",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tidak ada request dari penumpang saat ini",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pendingRequestsState.value) { request ->
                                DriverRequestCard(
                                    request = request,
                                    onClick = { onRequestSelected(request) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Load all requests and confirmed orders (DriverRental and Rental)
                    var allOrdersList by remember { mutableStateOf<List<Any>>(emptyList()) }
                    
                    LaunchedEffect(driverEmail) {
                        if (driverEmail.isNotEmpty()) {
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val orders = mutableListOf<Any>()
                                        
                                        // Add all pending requests
                                        val pendingRequests = database.driverRequestDao().getPendingRequestsByDriver(driverEmail).first()
                                        orders.addAll(pendingRequests)
                                        
                                        // Add confirmed DriverRentals (CONFIRMED or COMPLETED)
                                        val driverRentals = database.driverRentalDao().getRentalsByDriver(driverEmail).first()
                                        val confirmedDriverRentals = driverRentals.filter { rental ->
                                            rental.status == "CONFIRMED" || rental.status == "COMPLETED" 
                                        }
                                        orders.addAll(confirmedDriverRentals)
                                        
                                        // Add confirmed Rentals (vehicle rentals with driver) - CONFIRMED, ACTIVE, or COMPLETED
                                        val vehicleRentals = database.rentalDao().getRentalsByDriver(driverEmail)
                                        val confirmedVehicleRentals = vehicleRentals.filter { rental ->
                                            rental.status == "COMPLETED" || rental.status == "ACTIVE" || rental.status == "CONFIRMED"
                                        }
                                        orders.addAll(confirmedVehicleRentals)
                                        
                                        // Sort by date (newest first)
                                        allOrdersList = orders.sortedByDescending { order ->
                                            when (order) {
                                                is DriverRequest -> order.createdAt
                                                is com.example.app_jalanin.data.local.entity.DriverRental -> order.createdAt
                                                is com.example.app_jalanin.data.local.entity.Rental -> order.createdAt
                                                else -> 0L
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DriverRequests", "Error loading all orders: ${e.message}", e)
                                }
                            }
                        }
                    }
                    
                    if (allOrdersList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "Tidak Ada Request",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(allOrdersList) { order ->
                                when (order) {
                                    is DriverRequest -> {
                                        DriverRequestCard(
                                            request = order,
                                            onClick = { onRequestSelected(order) }
                                        )
                                    }
                                    is com.example.app_jalanin.data.local.entity.DriverRental -> {
                                        DriverRentalOrderCard(
                                            rental = order,
                                            onClick = { /* Navigate to order detail if needed */ }
                                        )
                                    }
                                    is com.example.app_jalanin.data.local.entity.Rental -> {
                                        VehicleRentalOrderCard(
                                            rental = order,
                                            onClick = { /* Navigate to order detail if needed */ }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Notification Dialog for new request
    if (showNotificationDialog && newRequestNotification != null) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            icon = {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Lokasi: ${newRequestNotification!!.pickupAddress}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationDialog = false
                        onRequestSelected(newRequestNotification!!)
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

/**
 * Card for displaying DriverRental order in "Semua" tab
 */
@Composable
private fun DriverRentalOrderCard(
    rental: com.example.app_jalanin.data.local.entity.DriverRental,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passengerUsername by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(rental.passengerEmail) {
        scope.launch {
            passengerUsername = withContext(Dispatchers.IO) {
                UsernameResolver.resolveUsernameFromEmail(
                    context,
                    rental.passengerEmail
                )
            }
        }
    }
    
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val statusColor = when (rental.status) {
        "PENDING" -> Color(0xFFFF9800)
        "CONFIRMED" -> Color(0xFF2196F3)
        "COMPLETED" -> Color(0xFF4CAF50)
        "CANCELLED" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    
    val statusText = when (rental.status) {
        "PENDING" -> "Pending"
        "CONFIRMED" -> "Confirmed"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        else -> rental.status
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = passengerUsername ?: "Unknown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(rental.createdAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
            
            HorizontalDivider()
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Service Type: Driver only",
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Card for displaying Vehicle Rental order in "Semua" tab
 */
@Composable
private fun VehicleRentalOrderCard(
    rental: com.example.app_jalanin.data.local.entity.Rental,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    
    var passengerName by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(rental.userEmail) {
        try {
            val user = withContext(Dispatchers.IO) {
                database.userDao().getUserByEmail(rental.userEmail)
            }
            passengerName = user?.fullName ?: rental.userEmail.split("@").firstOrNull()
        } catch (e: Exception) {
            passengerName = rental.userEmail.split("@").firstOrNull()
        }
    }
    
    val statusColor = when (rental.status) {
        "PENDING" -> Color(0xFFFF9800)
        "CONFIRMED" -> Color(0xFF2196F3)
        "ACTIVE" -> Color(0xFF4CAF50)
        "COMPLETED" -> Color(0xFF4CAF50)
        "CANCELLED" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    
    val statusText = when (rental.status) {
        "PENDING" -> "Pending"
        "CONFIRMED" -> "Confirmed"
        "ACTIVE" -> "Active"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        else -> rental.status
    }
    
    val serviceType = if (rental.isWithDriver) "Vehicle + Driver" else "Vehicle Only"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = passengerName ?: rental.userEmail.split("@").firstOrNull() ?: "Unknown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(rental.createdAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
            
            HorizontalDivider()
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Service Type: $serviceType",
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun DriverRequestCard(
    request: DriverRequest,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val statusColor = when (request.status) {
        "PENDING" -> Color(0xFFFF9800)
        "ACCEPTED" -> Color(0xFF2196F3)
        "DRIVER_ARRIVING" -> Color(0xFF9C27B0)
        "DRIVER_ARRIVED" -> Color(0xFF4CAF50)
        "IN_PROGRESS" -> Color(0xFF00BCD4)
        "COMPLETED" -> Color(0xFF4CAF50)
        "REJECTED" -> Color(0xFFF44336)
        "CANCELLED" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    
    val statusText = when (request.status) {
        "PENDING" -> "Menunggu Konfirmasi"
        "ACCEPTED" -> "Diterima"
        "DRIVER_ARRIVING" -> "Driver Menuju Lokasi"
        "DRIVER_ARRIVED" -> "Driver Tiba"
        "IN_PROGRESS" -> "Sedang Berjalan"
        "COMPLETED" -> "Selesai"
        "REJECTED" -> "Ditolak"
        "CANCELLED" -> "Dibatalkan"
        else -> request.status
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.passengerName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(request.createdAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
            
            HorizontalDivider()
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${request.vehicleBrand} ${request.vehicleModel} (${request.vehicleLicensePlate})",
                    fontSize = 14.sp
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF4CAF50)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.pickupAddress,
                        fontSize = 14.sp
                    )
                    if (request.destinationAddress != null) {
                        Text(
                            text = "→ ${request.destinationAddress}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
