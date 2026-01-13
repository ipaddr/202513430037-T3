package com.example.app_jalanin.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.model.SimType
import com.example.app_jalanin.data.model.VehicleType
import com.example.app_jalanin.data.model.SimCertificationHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Screen untuk owner memilih driver untuk pengantaran kendaraan
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDriverForDeliveryScreen(
    rental: Rental,
    ownerEmail: String,
    deliveryMode: String, // "DRIVER_DELIVERY_ONLY" or "DRIVER_DELIVERY_TRAVEL" or empty string
    onBackClick: () -> Unit = {},
    onDriverSelected: (User, String) -> Unit = { _, _ -> } // User and selected delivery mode
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // Load available drivers with their profiles
    var allDrivers by remember { mutableStateOf<List<User>>(emptyList()) }
    var driverProfilesMap by remember { mutableStateOf<Map<String, com.example.app_jalanin.data.local.entity.DriverProfile>>(emptyMap()) }
    
    // Determine vehicle type from rental
    val vehicleType = remember(rental.vehicleType) {
        when (rental.vehicleType.uppercase()) {
            "MOTOR" -> VehicleType.MOTOR
            "MOBIL" -> VehicleType.MOBIL
            else -> null
        }
    }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val users = withContext(Dispatchers.IO) {
                    database.userDao().getUsersByRole("DRIVER")
                }
                val profiles = withContext(Dispatchers.IO) {
                    database.driverProfileDao().getAll()
                }
                val profilesMap = profiles.associateBy { it.driverEmail }
                driverProfilesMap = profilesMap
                
                // Use DriverRoleHelper to filter drivers
                if (vehicleType != null) {
                    allDrivers = com.example.app_jalanin.data.model.DriverRoleHelper.filterAvailableDrivers(
                        users, 
                        vehicleType, 
                        profilesMap
                    )
                    android.util.Log.d("SelectDriverForDelivery", "✅ Loaded ${allDrivers.size} available drivers for ${vehicleType.name}")
                } else {
                    allDrivers = emptyList()
                    android.util.Log.w("SelectDriverForDelivery", "⚠️ Unknown vehicle type: ${rental.vehicleType}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SelectDriverForDelivery", "Error loading drivers: ${e.message}", e)
                allDrivers = emptyList()
            }
        }
    }
    
    // Available drivers (already filtered by DriverRoleHelper)
    val availableDrivers = allDrivers
    
    var selectedDriver by remember { mutableStateOf<User?>(null) }
    var showModeSelectionDialog by remember { 
        mutableStateOf(deliveryMode.isEmpty() || deliveryMode == "DRIVER_DELIVERY_ONLY")
    }
    var currentDeliveryMode by remember { 
        mutableStateOf<String?>(if (deliveryMode.isNotEmpty() && deliveryMode != "DRIVER_DELIVERY_ONLY") deliveryMode else null)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Pilih Driver")
                        Text(
                            text = if (deliveryMode == "DRIVER_DELIVERY_TRAVEL") {
                                "Pengantaran + Travel Driver"
                            } else {
                                "Pengantaran Saja"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
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
            // Info Card (only show if mode is selected)
            if (currentDeliveryMode != null || (deliveryMode.isNotEmpty() && deliveryMode != "DRIVER_DELIVERY_ONLY")) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                        val mode = currentDeliveryMode ?: deliveryMode
                        Text(
                            text = if (mode == "DRIVER_DELIVERY_TRAVEL") {
                                "Mode: Pengantaran + Travel Driver"
                            } else {
                                "Mode: Pengantaran Saja"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (mode == "DRIVER_DELIVERY_TRAVEL") {
                                "Driver akan mengantarkan kendaraan dan menjadi driver perjalanan penumpang"
                            } else {
                                "Driver hanya mengantarkan kendaraan ke lokasi penumpang"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Driver List (only show if mode is selected)
            if (currentDeliveryMode == null && deliveryMode.isEmpty()) {
                // Wait for mode selection - show nothing, dialog will handle it
                Box(modifier = Modifier.fillMaxSize())
            } else if (availableDrivers.isEmpty()) {
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
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Tidak Ada Driver Tersedia",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tidak ada driver online dengan SIM yang sesuai untuk kendaraan ini",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(availableDrivers) { driver ->
                        val driverProfile = driverProfilesMap[driver.email]
                        DriverCard(
                            driver = driver,
                            driverProfile = driverProfile,
                            isSelected = selectedDriver?.email == driver.email,
                            onClick = { selectedDriver = driver }
                        )
                    }
                }
            }
            
            // Confirm Button
            if (selectedDriver != null && (currentDeliveryMode != null || deliveryMode.isNotEmpty())) {
                Button(
                    onClick = { 
                        // Update delivery mode if needed
                        val finalMode = currentDeliveryMode ?: deliveryMode
                        onDriverSelected(selectedDriver!!, finalMode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = selectedDriver != null && (currentDeliveryMode != null || deliveryMode.isNotEmpty())
                ) {
                    Text("Pilih Driver")
                }
            }
        }
        
        // Mode Selection Dialog
        if (showModeSelectionDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showModeSelectionDialog = false
                    onBackClick()
                },
                title = { Text("Pilih Mode Pengantaran") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pilih mode pengantaran untuk driver:")
                        
                        // Option 1: Delivery Only
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentDeliveryMode = "DRIVER_DELIVERY_ONLY" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentDeliveryMode == "DRIVER_DELIVERY_ONLY") 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Pengantaran Saja",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Driver hanya mengantarkan kendaraan ke lokasi penumpang",
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        // Option 2: Delivery + Travel
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentDeliveryMode = "DRIVER_DELIVERY_TRAVEL" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentDeliveryMode == "DRIVER_DELIVERY_TRAVEL") 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Pengantaran + Travel Driver",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Driver mengantarkan kendaraan dan menjadi driver perjalanan penumpang",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (currentDeliveryMode != null) {
                                showModeSelectionDialog = false
                            }
                        },
                        enabled = currentDeliveryMode != null
                    ) {
                        Text("Lanjutkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showModeSelectionDialog = false
                        onBackClick()
                    }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
private fun DriverCard(
    driver: User,
    driverProfile: com.example.app_jalanin.data.local.entity.DriverProfile?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val simTypes = SimCertificationHelper.parseSimCertifications(driverProfile?.simCertifications ?: "")
    val simText = simTypes.joinToString(", ") { it.name.replace("SIM_", "SIM ") }
    
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
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                Color(0xFFE0E0E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = driver.fullName?.take(1)?.uppercase() ?: "D",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Driver Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = driver.fullName ?: driver.email,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = driver.email,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "SIM: $simText",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Selection Indicator
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
