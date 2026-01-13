package com.example.app_jalanin.ui.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen untuk tracking kedatangan driver ke lokasi penumpang
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverArrivalTrackingScreen(
    request: DriverRequest,
    isDriver: Boolean,
    onBackClick: () -> Unit = {},
    onArrivalMethodSelected: (String, Int) -> Unit = { _, _ -> },
    onStartJourney: () -> Unit = {},
    onMarkArrived: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var selectedArrivalMethod by remember { mutableStateOf<String?>(request.driverArrivalMethod) }
    var estimatedMinutes by remember { mutableStateOf<Int?>(request.estimatedArrivalMinutes) }
    var showArrivalMethodDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isDriver) "Menuju Lokasi Penumpang" else "Driver Menuju Anda") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Pickup Location Card
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
                        text = "Lokasi Penjemputan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            text = request.pickupAddress,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Driver Actions (if driver)
            if (isDriver) {
                if (request.status == "ACCEPTED" && request.startedAt == null) {
                    // Select arrival method
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Pilih Cara Tiba",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            ArrivalMethodOption(
                                method = "WALKING",
                                label = "Jalan Kaki",
                                estimatedMinutes = 15,
                                icon = Icons.Default.DirectionsWalk,
                                isSelected = selectedArrivalMethod == "WALKING",
                                onClick = {
                                    selectedArrivalMethod = "WALKING"
                                    estimatedMinutes = 15
                                }
                            )
                            
                            ArrivalMethodOption(
                                method = "VEHICLE",
                                label = "Kendaraan Pribadi",
                                estimatedMinutes = 10,
                                icon = Icons.Default.DirectionsCar,
                                isSelected = selectedArrivalMethod == "VEHICLE",
                                onClick = {
                                    selectedArrivalMethod = "VEHICLE"
                                    estimatedMinutes = 10
                                }
                            )
                            
                            ArrivalMethodOption(
                                method = "PUBLIC_TRANSPORT",
                                label = "Transportasi Umum",
                                estimatedMinutes = 20,
                                icon = Icons.Default.DirectionsBus,
                                isSelected = selectedArrivalMethod == "PUBLIC_TRANSPORT",
                                onClick = {
                                    selectedArrivalMethod = "PUBLIC_TRANSPORT"
                                    estimatedMinutes = 20
                                }
                            )
                            
                            if (selectedArrivalMethod != null && estimatedMinutes != null) {
                                Button(
                                    onClick = {
                                        onArrivalMethodSelected(selectedArrivalMethod!!, estimatedMinutes!!)
                                        onStartJourney()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Mulai Perjalanan")
                                }
                            }
                        }
                    }
                } else if (request.status == "IN_PROGRESS" && request.arrivedAt == null) {
                    // Show tracking info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Status Perjalanan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    when (request.driverArrivalMethod) {
                                        "WALKING" -> Icons.Default.DirectionsWalk
                                        "VEHICLE" -> Icons.Default.DirectionsCar
                                        "PUBLIC_TRANSPORT" -> Icons.Default.DirectionsBus
                                        else -> Icons.Default.DirectionsCar
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = when (request.driverArrivalMethod) {
                                        "WALKING" -> "Jalan Kaki"
                                        "VEHICLE" -> "Kendaraan Pribadi"
                                        "PUBLIC_TRANSPORT" -> "Transportasi Umum"
                                        else -> "Menuju Lokasi"
                                    },
                                    fontSize = 14.sp
                                )
                            }
                            
                            if (request.estimatedArrivalMinutes != null) {
                                Text(
                                    text = "Estimasi: ${request.estimatedArrivalMinutes} menit",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Button(
                                onClick = onMarkArrived,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Saya Sudah Tiba")
                            }
                        }
                    }
                }
            } else {
                // Passenger view - show driver status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Driver Menuju Anda",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (request.driverArrivalMethod != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    when (request.driverArrivalMethod) {
                                        "WALKING" -> Icons.Default.DirectionsWalk
                                        "VEHICLE" -> Icons.Default.DirectionsCar
                                        "PUBLIC_TRANSPORT" -> Icons.Default.DirectionsBus
                                        else -> Icons.Default.DirectionsCar
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = when (request.driverArrivalMethod) {
                                        "WALKING" -> "Driver datang dengan jalan kaki"
                                        "VEHICLE" -> "Driver datang dengan kendaraan"
                                        "PUBLIC_TRANSPORT" -> "Driver datang dengan transportasi umum"
                                        else -> "Driver menuju lokasi"
                                    },
                                    fontSize = 14.sp
                                )
                            }
                        }
                        
                        if (request.estimatedArrivalMinutes != null) {
                            Text(
                                text = "Estimasi kedatangan: ${request.estimatedArrivalMinutes} menit",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalMethodOption(
    method: String,
    label: String,
    estimatedMinutes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else 
            null
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
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Column {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "Estimasi: $estimatedMinutes menit",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
