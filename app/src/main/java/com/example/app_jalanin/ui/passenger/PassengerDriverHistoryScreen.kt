package com.example.app_jalanin.ui.passenger

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.auth.AuthStateManager
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRequest
import com.example.app_jalanin.utils.ChatHelper
import com.example.app_jalanin.utils.UsernameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Screen untuk menampilkan riwayat order driver (DriverRequest) dari penumpang
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerDriverHistoryScreen(
    onBackClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {} // channelId
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var userEmail by remember { mutableStateOf<String?>(null) }
    
    // Load user email
    LaunchedEffect(Unit) {
        val user = AuthStateManager.getCurrentUser(context)
        userEmail = user?.email ?: AuthStateManager.getCurrentUserEmail(context)
    }
    
    // Load all driver requests for passenger
    val driverRequestsFlow = remember(userEmail) {
        if (userEmail != null) {
            database.driverRequestDao().getRequestsByPassenger(userEmail!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList<DriverRequest>())
        }
    }
    val driverRequestsState = driverRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Order Driver") },
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
            if (driverRequestsState.value.isEmpty()) {
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
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Belum Ada Order Driver",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Anda belum pernah memesan driver",
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
                    items(driverRequestsState.value) { request ->
                        PassengerDriverHistoryCard(
                            request = request,
                            passengerEmail = userEmail ?: "",
                            database = database,
                            onChatClick = onChatClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassengerDriverHistoryCard(
    request: DriverRequest,
    passengerEmail: String,
    database: AppDatabase,
    onChatClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    
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
    
    // Get driver username
    var driverUsername by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(request.driverEmail) {
        scope.launch {
            driverUsername = withContext(Dispatchers.IO) {
                UsernameResolver.resolveUsernameFromEmail(
                    context,
                    request.driverEmail
                )
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Driver name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driverUsername ?: "Unknown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = dateFormat.format(request.createdAt),
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 2.dp)
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
            
            // Vehicle info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (request.vehicleType == "MOBIL") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF2196F3)
                )
                Column {
                    Text(
                        text = "${request.vehicleBrand} ${request.vehicleModel}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = request.vehicleLicensePlate,
                        fontSize = 12.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
            
            // Location info
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF4CAF50)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Penjemputan",
                        fontSize = 11.sp,
                        color = Color(0xFF757575)
                    )
                    Text(
                        text = request.pickupAddress,
                        fontSize = 14.sp
                    )
                }
            }
            
            if (request.destinationAddress != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF2196F3)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tujuan",
                            fontSize = 11.sp,
                            color = Color(0xFF757575)
                        )
                        Text(
                            text = request.destinationAddress ?: "",
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Action buttons (Chat if driver accepted or in progress)
            if (request.status in listOf("ACCEPTED", "DRIVER_ARRIVING", "DRIVER_ARRIVED", "IN_PROGRESS", "COMPLETED")) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val channel = ChatHelper.getOrCreateDMChannel(
                                    database,
                                    passengerEmail,
                                    request.driverEmail,
                                    request.id, // Use request ID as rentalId
                                    "COMPLETED" // Default status for completed requests
                                )
                                onChatClick(channel.id)
                            } catch (e: Exception) {
                                android.util.Log.e("PassengerDriverHistory", "Error creating chat channel: ${e.message}", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = "Chat",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chat dengan Driver",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            // Timestamp info
            if (request.acceptedAt != null) {
                Text(
                    text = "Diterima: ${dateFormat.format(request.acceptedAt)}",
                    fontSize = 11.sp,
                    color = Color(0xFF9E9E9E)
                )
            }
            if (request.completedAt != null) {
                Text(
                    text = "Selesai: ${dateFormat.format(request.completedAt)}",
                    fontSize = 11.sp,
                    color = Color(0xFF9E9E9E)
                )
            }
        }
    }
}
