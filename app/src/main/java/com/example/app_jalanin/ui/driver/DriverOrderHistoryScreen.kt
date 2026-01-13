package com.example.app_jalanin.ui.driver

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
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRental
import com.example.app_jalanin.data.local.entity.Rental
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.app_jalanin.utils.UsernameResolver
import java.text.SimpleDateFormat
import java.util.*

/**
 * Driver Order History Screen
 * Displays list of orders handled by the driver
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrderHistoryScreen(
    driverEmail: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }
    
    var driverRentals by remember { mutableStateOf<List<DriverRental>>(emptyList()) }
    var vehicleRentals by remember { mutableStateOf<List<Rental>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(driverEmail) {
        try {
            withContext(Dispatchers.IO) {
                // Load driver rentals (independent driver rentals)
                val driverRentalsFlow = database.driverRentalDao()
                    .getRentalsByDriver(driverEmail)
                val driverRentalsList = driverRentalsFlow.first()
                    .filter { it.status == "COMPLETED" || it.status == "CONFIRMED" }
                
                // Load vehicle rentals where driver is assigned
                val vehicleRentalsList = database.rentalDao()
                    .getRentalsByDriver(driverEmail)
                    .filter { it.status == "COMPLETED" }
                
                driverRentals = driverRentalsList
                vehicleRentals = vehicleRentalsList
                isLoading = false
            }
        } catch (e: Exception) {
            android.util.Log.e("DriverOrderHistory", "Error loading orders: ${e.message}", e)
            isLoading = false
        }
    }
    
    // Combine and sort by date
    val allOrders = remember(driverRentals, vehicleRentals) {
        val orders = mutableListOf<OrderHistoryItem>()
        
        // Add driver rentals
        driverRentals.forEach { rental ->
            orders.add(
                OrderHistoryItem(
                    id = rental.id,
                    type = "Driver Only",
                    passengerName = "Unknown", // Will be resolved dynamically via username
                    passengerEmail = rental.passengerEmail,
                    date = rental.createdAt,
                    status = rental.status,
                    vehicleInfo = "${rental.vehicleType} (Private Vehicle)"
                )
            )
        }
        
        // Add vehicle rentals
        vehicleRentals.forEach { rental ->
            val serviceType = if (rental.isWithDriver) "Vehicle + Driver" else "Vehicle Only"
            orders.add(
                OrderHistoryItem(
                    id = rental.id,
                    type = serviceType,
                    passengerName = rental.userEmail.split("@").firstOrNull() ?: "Unknown",
                    passengerEmail = rental.userEmail,
                    date = rental.startDate,
                    status = rental.status,
                    vehicleInfo = rental.vehicleName,
                    ownerEmail = rental.ownerEmail // Store owner email for username resolution
                )
            )
        }
        
        orders.sortedByDescending { it.date }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Pesanan") },
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (allOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allOrders) { order ->
                    DriverOrderHistoryCard(
                        order = order,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

data class OrderHistoryItem(
    val id: String,
    val type: String, // "Driver Only", "Vehicle + Driver", "Vehicle Only"
    val passengerName: String,
    val passengerEmail: String,
    val date: Long,
    val status: String,
    val vehicleInfo: String,
    val ownerEmail: String? = null // Owner email for vehicle rentals (for username resolution)
)

@Composable
private fun DriverOrderHistoryCard(
    order: OrderHistoryItem,
    dateFormat: SimpleDateFormat
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passengerUsername by remember { mutableStateOf<String?>(null) }
    var ownerUsername by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(order.passengerEmail) {
        scope.launch {
            passengerUsername = withContext(Dispatchers.IO) {
                com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                    context,
                    order.passengerEmail
                )
            }
        }
    }
    
    LaunchedEffect(order.ownerEmail) {
        if (order.ownerEmail != null) {
            scope.launch {
                ownerUsername = withContext(Dispatchers.IO) {
                    com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(
                        context,
                        order.ownerEmail!!
                    )
                }
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Service Type & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(order.type, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = when (order.type) {
                            "Driver Only" -> Color(0xFF4CAF50)
                            "Vehicle + Driver" -> Color(0xFF2196F3)
                            else -> Color(0xFF9E9E9E)
                        },
                        labelColor = Color.White
                    ),
                    modifier = Modifier.height(24.dp)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Completed",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50)
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
                    Text(
                        text = order.vehicleInfo,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                
                // Owner Info (for vehicle rentals)
                if (order.ownerEmail != null) {
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

