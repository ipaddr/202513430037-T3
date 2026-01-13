package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app_jalanin.auth.AuthStateManager
import com.example.app_jalanin.data.local.entity.Rental
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen for renter to submit early return request
 * Shows waiting state until owner confirms and sets return location
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarlyReturnRequestScreen(
    rental: Rental,
    onBackClick: () -> Unit,
    onChatClick: (String) -> Unit = {},
    onOwnerConfirmed: () -> Unit = {} // Called when owner confirms and sets return location
) {
    val context = LocalContext.current
    val viewModel: EarlyReturnRequestViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val scope = rememberCoroutineScope()
    
    // Load rental
    LaunchedEffect(rental.id) {
        viewModel.loadRental(rental.id)
    }
    
    // Observe states
    val rentalState by viewModel.rental.collectAsState()
    val requestStatus by viewModel.requestStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    // Get current user
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val user = withContext(Dispatchers.IO) {
            AuthStateManager.getCurrentUser(context)
        }
        currentUserEmail = user?.email
    }
    
    // ✅ Auto-refresh to check for owner confirmation (polling every 3 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000) // Check every 3 seconds
            val currentStatus = viewModel.requestStatus.value
            if (currentStatus == EarlyReturnRequestStatus.WAITING_FOR_OWNER) {
                viewModel.refreshRentalStatus()
            } else {
                break // Stop polling if status changed
            }
        }
    }
    
    // ✅ Check if owner has confirmed (return location is set) - Auto-navigate to map
    // Only navigate if request was previously submitted (REQUESTED) and now owner confirmed
    LaunchedEffect(rentalState, requestStatus) {
        rentalState?.let { updatedRental ->
            // ✅ Only navigate if:
            // 1. Status is OWNER_CONFIRMED (meaning request was submitted and owner confirmed)
            // 2. Return location is set
            // 3. earlyReturnStatus is "CONFIRMED" or "REQUESTED" (to ensure request was actually submitted)
            if (requestStatus == EarlyReturnRequestStatus.OWNER_CONFIRMED &&
                updatedRental.returnLocationLat != null && 
                updatedRental.returnLocationLon != null && 
                updatedRental.returnAddress != null &&
                (updatedRental.earlyReturnStatus == "CONFIRMED" || updatedRental.earlyReturnStatus == "REQUESTED")) {
                // ✅ Owner has confirmed and set return location - trigger notification to passenger
                // Navigate to map view automatically
                android.util.Log.d("EarlyReturnRequestScreen", "✅ Owner confirmed! Navigating to map...")
                onOwnerConfirmed()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajukan Pengembalian Lebih Awal") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
            
            // Vehicle info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Kendaraan",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = rental.vehicleName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rental.vehicleType,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Status card based on request status
            when (requestStatus) {
                EarlyReturnRequestStatus.NOT_REQUESTED -> {
                    // Request form
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Ajukan Pengembalian Lebih Awal",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Anda akan mengajukan pengembalian kendaraan sebelum jadwal yang ditentukan. Owner akan menerima notifikasi dan menentukan lokasi pengembalian.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            
                            Button(
                                onClick = {
                                    currentUserEmail?.let { email ->
                                        // ✅ Submit request - this will set earlyReturnStatus to "REQUESTED"
                                        // Chat channel creation is handled in ViewModel.submitRequest
                                        viewModel.submitRequest(rental.id, email, rental.ownerEmail)
                                        
                                        // After submission, status will change to WAITING_FOR_OWNER
                                        // and the UI will automatically update to show waiting screen
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading && currentUserEmail != null && requestStatus == EarlyReturnRequestStatus.NOT_REQUESTED,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Ajukan Pengembalian", fontSize = 16.sp)
                            }
                        }
                    }
                }
                
                EarlyReturnRequestStatus.WAITING_FOR_OWNER -> {
                    // ✅ Waiting state - "Tunggu Owner Konfirmasi Pengembalian Anda"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = Color(0xFFFF9800)
                            )
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFFFF9800)
                            )
                            Text(
                                text = "Tunggu Owner Konfirmasi Pengembalian Anda",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF9800),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Permintaan pengembalian lebih awal telah dikirim ke owner. Owner sedang memproses permintaan Anda dan akan menentukan lokasi pengembalian.",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            // Chat button
                            if (rental.ownerEmail != null && currentUserEmail != null) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val database = com.example.app_jalanin.data.AppDatabase.getDatabase(context)
                                                val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                                                    database,
                                                    currentUserEmail!!,
                                                    rental.ownerEmail!!,
                                                    rental.id, // rentalId
                                                    rental.status // orderStatus
                                                )
                                                onChatClick(channel.id)
                                            } catch (e: Exception) {
                                                android.util.Log.e("EarlyReturnRequestScreen", "Error opening chat: ${e.message}", e)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Chat dengan Owner", fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
                
                EarlyReturnRequestStatus.OWNER_CONFIRMED -> {
                    // Owner confirmed - should navigate to map (handled by LaunchedEffect)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Text(
                                text = "Owner Telah Mengonfirmasi",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = "Lokasi pengembalian telah ditentukan. Memuat peta...",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                
                else -> {
                    // Other states
                    Text(
                        text = "Status: $requestStatus",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

