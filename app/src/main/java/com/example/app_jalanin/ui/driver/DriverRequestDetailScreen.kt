package com.example.app_jalanin.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.app_jalanin.utils.ChatHelper

/**
 * Screen detail request untuk driver dengan tombol accept, chat, dll
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRequestDetailScreen(
    requestId: String,
    driverEmail: String,
    onBackClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {}, // channelId
    onRequestAccepted: () -> Unit = {},
    onRequestRejected: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    val requestFlow = remember(requestId) {
        database.driverRequestDao().getRequestByIdFlow(requestId)
    }
    val requestState = requestFlow.collectAsStateWithLifecycle(initialValue = null)
    val request = requestState.value
    
    var isAccepting by remember { mutableStateOf(false) }
    var isRejecting by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    if (request == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Request") },
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
        },
        floatingActionButton = {
            if (request.status == "PENDING") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chat Button
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                                        database,
                                        driverEmail,
                                        request.passengerEmail,
                                        request.id, // Use request id as rentalId
                                        request.status // Use request status as orderStatus
                                    )
                                    onChatClick(channel.id)
                                } catch (e: Exception) {
                                    android.util.Log.e("DriverRequestDetail", "Error creating chat channel: ${e.message}", e)
                                    errorMessage = "Error creating chat: ${e.message}"
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "Chat")
                    }
                    // Reject Button
                    FloatingActionButton(
                        onClick = { showRejectDialog = true },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Tolak", tint = MaterialTheme.colorScheme.error)
                    }
                    // Accept Button
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                try {
                                    isAccepting = true
                                    errorMessage = null
                                    
                                    val now = System.currentTimeMillis()
                                    withContext(Dispatchers.IO) {
                                        database.driverRequestDao().acceptRequest(
                                            requestId = request.id,
                                            status = "ACCEPTED",
                                            acceptedAt = now,
                                            updatedAt = now
                                        )
                                    }
                                    
                                    android.util.Log.d("DriverRequestDetail", "✅ Request accepted: ${request.id}")
                                    onRequestAccepted()
                                } catch (e: Exception) {
                                    android.util.Log.e("DriverRequestDetail", "Error accepting request: ${e.message}", e)
                                    errorMessage = "Error: ${e.message}"
                                } finally {
                                    isAccepting = false
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (isAccepting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Terima")
                        }
                    }
                }
            } else {
                // Chat Button for non-pending requests
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            try {
                                val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                                    database,
                                    driverEmail,
                                    request.passengerEmail,
                                    request.id, // Use request id as rentalId
                                    request.status // Use request status as orderStatus
                                )
                                onChatClick(channel.id)
                            } catch (e: Exception) {
                                android.util.Log.e("DriverRequestDetail", "Error creating chat channel: ${e.message}", e)
                                errorMessage = "Error creating chat: ${e.message}"
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
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
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = statusColor.copy(alpha = 0.1f)
                )
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
                            text = "Status",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = statusText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Icon(
                        when (request.status) {
                            "PENDING" -> Icons.Default.Schedule
                            "ACCEPTED" -> Icons.Default.CheckCircle
                            "DRIVER_ARRIVING" -> Icons.Default.DirectionsRun
                            "DRIVER_ARRIVED" -> Icons.Default.LocationOn
                            "IN_PROGRESS" -> Icons.Default.DirectionsCar
                            "COMPLETED" -> Icons.Default.CheckCircle
                            "REJECTED" -> Icons.Default.Close
                            "CANCELLED" -> Icons.Default.Cancel
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Passenger Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Informasi Penumpang",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = request.passengerName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = request.passengerEmail,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Vehicle Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Kendaraan",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (request.vehicleType == "MOBIL") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "${request.vehicleBrand} ${request.vehicleModel}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = request.vehicleLicensePlate,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Location Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Lokasi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Penjemputan",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = request.pickupAddress,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    if (request.destinationAddress != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color(0xFF2196F3)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tujuan",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = request.destinationAddress ?: "",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Timestamp Info
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Waktu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dibuat: ${dateFormat.format(request.createdAt)}",
                        fontSize = 12.sp
                    )
                    if (request.acceptedAt != null) {
                        Text(
                            text = "Diterima: ${dateFormat.format(request.acceptedAt)}",
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // Error Message
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage ?: "",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
    
    // Reject Confirmation Dialog
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Tolak Request?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Apakah Anda yakin ingin menolak request dari ${request.passengerName}?",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Tindakan ini tidak dapat dibatalkan.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                isRejecting = true
                                errorMessage = null
                                
                                val now = System.currentTimeMillis()
                                withContext(Dispatchers.IO) {
                                    database.driverRequestDao().rejectRequest(
                                        requestId = request.id,
                                        updatedAt = now
                                    )
                                }
                                
                                android.util.Log.d("DriverRequestDetail", "✅ Request rejected: ${request.id}")
                                showRejectDialog = false
                                onRequestRejected()
                            } catch (e: Exception) {
                                android.util.Log.e("DriverRequestDetail", "Error rejecting request: ${e.message}", e)
                                errorMessage = "Error: ${e.message}"
                                isRejecting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = !isRejecting
                ) {
                    if (isRejecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Tolak")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRejectDialog = false },
                    enabled = !isRejecting
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
