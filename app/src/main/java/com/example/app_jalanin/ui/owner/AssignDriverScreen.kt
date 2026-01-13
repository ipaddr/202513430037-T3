package com.example.app_jalanin.ui.owner

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.auth.UserRole
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.model.*
import com.example.app_jalanin.data.remote.FirestoreVehicleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated screen for assigning/managing driver for a vehicle
 * ✅ STRICT FILTERING: Only shows ONLINE drivers with matching SIM
 * ✅ ASSIGNMENT MODE: Owner must choose DELIVERY_ONLY or DELIVERY_AND_RENTAL
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignDriverScreen(
    vehicle: Vehicle,
    ownerEmail: String,
    onBackClick: () -> Unit,
    onAssignmentSaved: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // State
    var selectedDriverId by remember { mutableStateOf<String?>(vehicle.driverId) }
    var selectedAssignmentMode by remember { mutableStateOf<DriverAssignmentMode?>(
        vehicle.driverAssignmentMode?.let {
            try { DriverAssignmentMode.valueOf(it) } catch (e: Exception) { null }
        }
    ) }
    var availableDrivers by remember { mutableStateOf<List<User>>(emptyList()) }
    var driverProfilesMap by remember { mutableStateOf<Map<String, com.example.app_jalanin.data.local.entity.DriverProfile>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load current assigned driver info
    var currentDriver by remember { mutableStateOf<User?>(null) }
    var currentDriverProfile by remember { mutableStateOf<com.example.app_jalanin.data.local.entity.DriverProfile?>(null) }
    
    // Load eligible drivers (ONLINE + matching SIM)
    LaunchedEffect(vehicle.type) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                
                // Load all drivers
                val allDrivers = withContext(Dispatchers.IO) {
                    database.userDao().getUsersByRole(UserRole.DRIVER.name)
                }
                
                // Load driver profiles
                val profiles = withContext(Dispatchers.IO) {
                    database.driverProfileDao().getAll()
                }
                val profilesMap = profiles.associateBy { it.driverEmail }
                driverProfilesMap = profilesMap
                
                // ✅ STRICT FILTERING: Only online drivers with matching SIM
                availableDrivers = DriverRoleHelper.filterAvailableDrivers(
                    allDrivers,
                    vehicle.type,
                    profilesMap
                )
                
                android.util.Log.d("AssignDriverScreen", "✅ Loaded ${availableDrivers.size} eligible drivers for ${vehicle.type.name}")
                
                // Load current assigned driver info
                if (vehicle.driverId != null) {
                    currentDriver = allDrivers.find { it.email == vehicle.driverId }
                    currentDriverProfile = profilesMap[vehicle.driverId]
                }
                
                isLoading = false
            } catch (e: Exception) {
                android.util.Log.e("AssignDriverScreen", "❌ Error loading drivers: ${e.message}", e)
                errorMessage = "Error loading drivers: ${e.message}"
                isLoading = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign / Manage Driver") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Info Card
            item {
                VehicleInfoCard(vehicle = vehicle)
            }
            
            // Current Assignment Card (if driver is assigned)
            if (currentDriver != null && vehicle.driverId != null) {
                item {
                    CurrentAssignmentCard(
                        driver = currentDriver!!,
                        driverProfile = currentDriverProfile,
                        assignmentMode = selectedAssignmentMode,
                        vehicleType = vehicle.type
                    )
                }
            }
            
            // Assignment Mode Selector (only if driver is selected)
            if (selectedDriverId != null) {
                item {
                    AssignmentModeSelector(
                        selectedMode = selectedAssignmentMode,
                        onModeSelected = { selectedAssignmentMode = it }
                    )
                }
            }
            
            // Error Message
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage ?: "Error",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // Driver List Section
            item {
                Text(
                    text = "Pilih Driver",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (availableDrivers.isEmpty()) {
                item {
                    EmptyDriversCard(vehicleType = vehicle.type)
                }
            } else {
                items(availableDrivers) { driver ->
                    val driverProfile = driverProfilesMap[driver.email]
                    DriverSelectionCard(
                        driver = driver,
                        driverProfile = driverProfile,
                        vehicleType = vehicle.type,
                        isSelected = selectedDriverId == driver.email,
                        onClick = {
                            selectedDriverId = if (selectedDriverId == driver.email) null else driver.email
                            // Reset assignment mode when driver changes
                            if (selectedDriverId != driver.email) {
                                selectedAssignmentMode = null
                            }
                        }
                    )
                }
            }
            
            // Remove Driver Option
            if (vehicle.driverId != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            selectedDriverId = null
                            selectedAssignmentMode = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Driver yang Ditugaskan")
                    }
                }
            }
            
            // Save Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // ✅ VALIDATION: If driver is selected, assignment mode must be selected
                        if (selectedDriverId != null && selectedAssignmentMode == null) {
                            Toast.makeText(
                                context,
                                "⚠️ Pilih mode penugasan driver terlebih dahulu",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        
                        // ✅ VALIDATION: Check if selected driver is still online
                        if (selectedDriverId != null) {
                            val profile = driverProfilesMap[selectedDriverId]
                            if (profile?.isOnline != true) {
                                Toast.makeText(
                                    context,
                                    "❌ Driver yang dipilih sedang offline. Pilih driver lain atau coba lagi nanti.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                        }
                        
                        scope.launch {
                            try {
                                isSaving = true
                                
                                // Update vehicle in local database
                                val updatedVehicle = vehicle.copy(
                                    driverId = selectedDriverId,
                                    driverAssignmentMode = selectedAssignmentMode?.name,
                                    // ✅ Map assignment mode to driverAvailability for backward compatibility
                                    driverAvailability = if (selectedDriverId != null && selectedAssignmentMode != null) {
                                        when (selectedAssignmentMode) {
                                            DriverAssignmentMode.DELIVERY_ONLY -> DriverAvailability.AVAILABLE_DELIVERY_ONLY.name
                                            DriverAssignmentMode.DELIVERY_AND_RENTAL -> DriverAvailability.AVAILABLE_FULL_RENT.name
                                            null -> null
                                        }
                                    } else {
                                        null
                                    },
                                    updatedAt = System.currentTimeMillis()
                                )
                                
                                withContext(Dispatchers.IO) {
                                    database.vehicleDao().updateVehicle(updatedVehicle)
                                }
                                
                                android.util.Log.d("AssignDriverScreen", "✅ Vehicle updated in local database")
                                
                                // Sync to Firestore
                                val syncSuccess = withContext(Dispatchers.IO) {
                                    FirestoreVehicleService.syncVehicle(updatedVehicle)
                                }
                                
                                if (syncSuccess) {
                                    android.util.Log.d("AssignDriverScreen", "✅ Vehicle synced to Firestore")
                                    Toast.makeText(
                                        context,
                                        "✅ Driver assignment berhasil disimpan",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onAssignmentSaved()
                                } else {
                                    android.util.Log.w("AssignDriverScreen", "⚠️ Firestore sync failed, but local update succeeded")
                                    Toast.makeText(
                                        context,
                                        "⚠️ Disimpan lokal, akan disinkronkan nanti",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onAssignmentSaved()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AssignDriverScreen", "❌ Error saving assignment: ${e.message}", e)
                                Toast.makeText(
                                    context,
                                    "❌ Gagal menyimpan: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && (selectedDriverId != vehicle.driverId || selectedAssignmentMode?.name != vehicle.driverAssignmentMode)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menyimpan...")
                    } else {
                        Text("Simpan Assignment")
                    }
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VehicleInfoCard(vehicle: Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (vehicle.type == VehicleType.MOBIL) "🚗" else "🏍️",
                    fontSize = 32.sp
                )
                Column {
                    Text(
                        text = vehicle.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${vehicle.brand} ${vehicle.model} ${vehicle.year}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = vehicle.licensePlate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentAssignmentCard(
    driver: User,
    driverProfile: com.example.app_jalanin.data.local.entity.DriverProfile?,
    assignmentMode: DriverAssignmentMode?,
    vehicleType: VehicleType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Driver Saat Ini",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Online status indicator
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = if (driverProfile?.isOnline == true) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                ) {}
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver.fullName ?: driver.email,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = driver.email,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    
                    // SIM badges
                    driverProfile?.simCertifications?.let { simStr ->
                        val simTypes = simStr.split(",").mapNotNull {
                            try { SimType.valueOf(it.trim()) } catch (e: Exception) { null }
                        }
                        if (simTypes.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    }
                }
            }
            
            if (assignmentMode != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = when (assignmentMode) {
                            DriverAssignmentMode.DELIVERY_ONLY -> "Mode: Antar Saja"
                            DriverAssignmentMode.DELIVERY_AND_RENTAL -> "Mode: Antar + Mengemudi"
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentModeSelector(
    selectedMode: DriverAssignmentMode?,
    onModeSelected: (DriverAssignmentMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mode Penugasan Driver",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Pilih bagaimana driver akan digunakan:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            // DELIVERY_ONLY option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(DriverAssignmentMode.DELIVERY_ONLY) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedMode == DriverAssignmentMode.DELIVERY_ONLY)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (selectedMode == DriverAssignmentMode.DELIVERY_ONLY)
                        MaterialTheme.colorScheme.primary
                    else
                        Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedMode == DriverAssignmentMode.DELIVERY_ONLY,
                        onClick = { onModeSelected(DriverAssignmentMode.DELIVERY_ONLY) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🚚 Antar Saja",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Driver hanya mengantarkan kendaraan ke lokasi penumpang. Tidak mengemudi selama sewa.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // DELIVERY_AND_RENTAL option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(DriverAssignmentMode.DELIVERY_AND_RENTAL) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedMode == DriverAssignmentMode.DELIVERY_AND_RENTAL)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (selectedMode == DriverAssignmentMode.DELIVERY_AND_RENTAL)
                        MaterialTheme.colorScheme.primary
                    else
                        Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedMode == DriverAssignmentMode.DELIVERY_AND_RENTAL,
                        onClick = { onModeSelected(DriverAssignmentMode.DELIVERY_AND_RENTAL) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "✅ Antar + Mengemudi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Driver mengantarkan kendaraan dan menjadi driver aktif selama seluruh durasi sewa.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            if (selectedMode == null) {
                Text(
                    text = "⚠️ Pilih mode penugasan driver",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DriverSelectionCard(
    driver: User,
    driverProfile: com.example.app_jalanin.data.local.entity.DriverProfile?,
    vehicleType: VehicleType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val simTypes = driverProfile?.simCertifications?.split(",")?.mapNotNull {
        try { SimType.valueOf(it.trim()) } catch (e: Exception) { null }
    } ?: emptyList()
    
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
            2.dp,
            if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selection indicator
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            
            // Online status indicator
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = Color(0xFF4CAF50)
            ) {}
            
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
                
                // SIM badges
                if (simTypes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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

@Composable
private fun EmptyDriversCard(vehicleType: VehicleType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.PersonOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "Tidak Ada Driver Tersedia",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Tidak ada driver online dengan SIM yang sesuai untuk ${vehicleType.name}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

