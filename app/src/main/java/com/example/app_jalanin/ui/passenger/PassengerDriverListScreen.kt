package com.example.app_jalanin.ui.passenger

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.auth.UserRole
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.model.DriverRoleHelper
import com.example.app_jalanin.data.model.PassengerVehicle
import com.example.app_jalanin.data.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Halaman list driver untuk penumpang
 * Penumpang harus punya kendaraan pribadi aktif sebelum bisa melihat list driver
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerDriverListScreen(
    passengerEmail: String,
    onBackClick: () -> Unit = {},
    onDriverSelected: (User, PassengerVehicle) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    
    var passengerVehicles by remember { mutableStateOf<List<PassengerVehicle>>(emptyList()) }
    var selectedVehicle by remember { mutableStateOf<PassengerVehicle?>(null) }
    var availableDrivers by remember { mutableStateOf<List<User>>(emptyList()) }
    var driverProfilesMap by remember { mutableStateOf<Map<String, com.example.app_jalanin.data.local.entity.DriverProfile>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showVehicleSelectionDialog by remember { mutableStateOf(false) }
    
    // ✅ FIX: Download passenger vehicles and drivers from Firestore when screen loads
    LaunchedEffect(Unit) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // Download passenger vehicles
                if (passengerEmail.isNotEmpty()) {
                    android.util.Log.d("PassengerDriverList", "📥 Downloading passenger vehicles from Firestore...")
                    com.example.app_jalanin.data.remote.FirestorePassengerVehicleSyncManager.downloadPassengerVehicles(
                        context,
                        passengerEmail
                    )
                    android.util.Log.d("PassengerDriverList", "✅ Passenger vehicles download completed")
                }
                
                // Download all driver profiles
                android.util.Log.d("PassengerDriverList", "📥 Downloading all driver profiles from Firestore...")
                com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.downloadAllDriverProfiles(context)
                android.util.Log.d("PassengerDriverList", "✅ Driver profiles download completed")
            } catch (e: Exception) {
                android.util.Log.e("PassengerDriverList", "❌ Error downloading data: ${e.message}", e)
            }
        }
    }
    
    // Load passenger vehicles
    val vehiclesFlow = remember(passengerEmail) {
        if (passengerEmail.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList<PassengerVehicle>())
        } else {
            database.passengerVehicleDao().getActiveVehiclesByPassenger(passengerEmail)
        }
    }
    val vehiclesState = vehiclesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    LaunchedEffect(vehiclesState.value) {
        passengerVehicles = vehiclesState.value
        // Auto-select first vehicle if only one
        if (vehiclesState.value.size == 1 && selectedVehicle == null) {
            selectedVehicle = vehiclesState.value[0]
        }
        isLoading = false
    }
    
    // Load drivers when vehicle is selected
    LaunchedEffect(selectedVehicle?.id) {
        val vehicle = selectedVehicle
        if (vehicle == null) {
            availableDrivers = emptyList()
            return@LaunchedEffect
        }
        
        try {
            val allDrivers = withContext(Dispatchers.IO) {
                database.userDao().getUsersByRole(UserRole.DRIVER.name)
            }
            
            // Load driver profiles
            val profiles = withContext(Dispatchers.IO) {
                database.driverProfileDao().getAll()
            }
            val profilesMap = profiles.associateBy { it.driverEmail }
            driverProfilesMap = profilesMap
            
            // Filter: Only online drivers with matching SIM
            availableDrivers = DriverRoleHelper.filterAvailableDrivers(
                allDrivers, 
                vehicle.type,
                profilesMap
            )
            
            android.util.Log.d("PassengerDriverList", "✅ Loaded ${availableDrivers.size} drivers for ${vehicle.type.name}")
        } catch (e: Exception) {
            android.util.Log.e("PassengerDriverList", "Error loading drivers: ${e.message}", e)
            errorMessage = "Error loading drivers: ${e.message}"
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilih Driver") },
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
            // Vehicle Selection Section
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kendaraan Pribadi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedVehicle != null) {
                                    "${selectedVehicle!!.brand} ${selectedVehicle!!.model} (${selectedVehicle!!.licensePlate})"
                                } else {
                                    "Pilih kendaraan untuk melihat driver"
                                },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        OutlinedButton(
                            onClick = { showVehicleSelectionDialog = true }
                        ) {
                            Text(
                                text = if (selectedVehicle != null) "Ganti" else "Pilih",
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    // Info about SIM requirement
                    if (selectedVehicle != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Memerlukan ${selectedVehicle!!.getRequiredSimType().name.replace("SIM_", "SIM ")}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            
            // Validation: Must have vehicle
            if (passengerVehicles.isEmpty()) {
                // Empty state - no vehicles
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
                            text = "Belum Ada Kendaraan Pribadi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Anda harus memiliki kendaraan pribadi aktif untuk bisa menggunakan fitur sewa driver",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Silakan tambahkan kendaraan di menu 'Kendaraan Saya'",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (selectedVehicle == null) {
                // No vehicle selected
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
                            text = "Pilih Kendaraan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Pilih kendaraan pribadi Anda untuk melihat driver yang tersedia",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { showVehicleSelectionDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pilih Kendaraan")
                        }
                    }
                }
            } else {
                // Driver List
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage ?: "Error",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }
                } else if (availableDrivers.isEmpty()) {
                    // No drivers available
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
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tidak ada driver online dengan SIM yang sesuai untuk ${selectedVehicle!!.type.name}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Show driver list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Driver Tersedia (${availableDrivers.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(availableDrivers) { driver ->
                            val driverProfile = driverProfilesMap[driver.email]
                            DriverListItem(
                                driver = driver,
                                vehicleType = selectedVehicle?.type ?: VehicleType.MOTOR,
                                driverProfile = driverProfile,
                                onClick = {
                                    onDriverSelected(driver, selectedVehicle!!)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Vehicle Selection Dialog
    if (showVehicleSelectionDialog) {
        SelectPassengerVehicleDialog(
            passengerEmail = passengerEmail,
            vehicleType = null, // Show all types
            onDismiss = { showVehicleSelectionDialog = false },
            onVehicleSelected = { vehicle ->
                selectedVehicle = vehicle
                showVehicleSelectionDialog = false
            },
            onAddVehicle = {
                showVehicleSelectionDialog = false
                android.widget.Toast.makeText(
                    context,
                    "Silakan tambahkan kendaraan di menu 'Kendaraan Saya'",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

@Composable
private fun DriverListItem(
    driver: User,
    vehicleType: VehicleType,
    driverProfile: com.example.app_jalanin.data.local.entity.DriverProfile?,
    onClick: () -> Unit
) {
    val simTypes = driverProfile?.simCertifications?.split(",")?.mapNotNull { 
        try { com.example.app_jalanin.data.model.SimType.valueOf(it.trim()) } 
        catch (e: Exception) { null }
    } ?: emptyList()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Online status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driver.fullName ?: driver.email,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = driver.email,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    simTypes.forEach { simType ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = simType.name.replace("SIM_", "SIM "),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
