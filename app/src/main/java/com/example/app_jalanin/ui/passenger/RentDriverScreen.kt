package com.example.app_jalanin.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.auth.UserRole
import com.example.app_jalanin.data.local.entity.DriverProfile
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.model.DriverRoleHelper
import com.example.app_jalanin.data.model.DriverRentalPricing
import com.example.app_jalanin.data.model.VehicleType
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.app_jalanin.utils.getRouteInfo
import java.text.NumberFormat
import java.util.Locale

/**
 * Screen for passenger to rent a driver independently
 * ✅ NEW FEATURE: Independent driver rental (not tied to vehicle rental)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentDriverScreen(
    passengerEmail: String,
    onBackClick: () -> Unit = {},
    onDriverSelected: (
        driverEmail: String,
        driverName: String?,
        vehicleType: String,
        durationType: String,
        durationCount: Int,
        price: Long,
        pickupAddress: String,
        pickupLat: Double,
        pickupLon: Double,
        destinationAddress: String?,
        destinationLat: Double?,
        destinationLon: Double?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // List kendaraan pribadi penumpang
    var personalVehicles by remember { mutableStateOf<List<com.example.app_jalanin.data.model.PassengerVehicle>>(emptyList()) }
    var selectedVehicle by remember { mutableStateOf<com.example.app_jalanin.data.model.PassengerVehicle?>(null) }
    var selectedVehicleTypeFilter by remember { mutableStateOf<VehicleType?>(null) } // Filter: MOBIL or MOTOR
    
    // Filter vehicles based on selected type
    val filteredVehicles = remember(personalVehicles, selectedVehicleTypeFilter) {
        if (selectedVehicleTypeFilter == null) {
            emptyList()
        } else {
            personalVehicles.filter { it.type == selectedVehicleTypeFilter }
        }
    }
    
    // Set selectedVehicleType from selectedVehicle (for backward compatibility with existing logic)
    val selectedVehicleType = remember(selectedVehicle) {
        selectedVehicle?.type?.name
    }

    // Info: Sewa Driver hanya untuk kendaraan pribadi penumpang
    val infoText = "Sewa Driver hanya berlaku untuk kendaraan pribadi Anda. Pilih kendaraan Anda terlebih dahulu."

    // Load kendaraan pribadi penumpang
    LaunchedEffect(passengerEmail) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.passengerVehicleDao()
        val vehicles = withContext(Dispatchers.IO) {
            dao.getAllVehiclesByPassengerSync(passengerEmail)
        }
        personalVehicles = vehicles.filter { it.isActive }
    }
    
    // Driver selection
    var selectedDriver by remember { mutableStateOf<User?>(null) }
    var availableDrivers by remember { mutableStateOf<List<User>>(emptyList()) }
    var driverProfilesMap by remember { mutableStateOf<Map<String, com.example.app_jalanin.data.local.entity.DriverProfile>>(emptyMap()) }
    var isLoadingDrivers by remember { mutableStateOf(false) }
    
    // Duration selection
    var durationType by remember { mutableStateOf<String?>(null) } // "PER_HOUR", "PER_DAY", "PER_WEEK"
    var durationCount by remember { mutableStateOf("1") }
    var durationCountInt by remember { mutableStateOf(1) }
    
    // Location
    var pickupAddress by remember { mutableStateOf("") }
    var destinationAddress by remember { mutableStateOf("") }
    var pickupLat by remember { mutableStateOf(0.0) }
    var pickupLon by remember { mutableStateOf(0.0) }
    var destinationLat by remember { mutableStateOf<Double?>(null) }
    var destinationLon by remember { mutableStateOf<Double?>(null) }
    
    // Active location editing mode: "pickup" or "destination"
    var activeLocationMode by remember { mutableStateOf("pickup") }
    
    // Calculate price
    val calculatedPrice = remember(selectedVehicleType, durationType, durationCountInt) {
        if (selectedVehicleType != null && durationType != null && durationCountInt > 0) {
            DriverRentalPricing.calculatePrice(
                selectedVehicleType!!,
                durationType!!,
                durationCountInt
            )
        } else {
            0L
        }

    }
    
    // Format price
    val formattedPrice = remember(calculatedPrice) {
        if (calculatedPrice > 0) {
            NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(calculatedPrice)
        } else {
            "Rp 0"
        }

    }
    
    // Load drivers when vehicle is selected
    LaunchedEffect(selectedVehicle) {
        val vehicle = selectedVehicle
        if (vehicle == null) {
            availableDrivers = emptyList()
            selectedDriver = null
            return@LaunchedEffect
        }
        scope.launch {
            try {
                isLoadingDrivers = true
                val allDrivers = withContext(Dispatchers.IO) {
                    database.userDao().getUsersByRole(UserRole.DRIVER.name)
                }
                val profiles = withContext(Dispatchers.IO) {
                    database.driverProfileDao().getAll()
                }
                val profilesMap = profiles.associateBy { it.driverEmail }
                driverProfilesMap = profilesMap
                // Filter: Only online drivers with matching SIM for this vehicle
                availableDrivers = allDrivers.filter { driver ->
                    val profile = profilesMap[driver.email]
                    profile?.isOnline == true && vehicle.canBeDrivenBy(profile.simCertifications?.split(",")?.mapNotNull {
                        try { com.example.app_jalanin.data.model.SimType.valueOf(it.trim()) } catch (_: Exception) { null }
                    } ?: emptyList())
                }
                android.util.Log.d("RentDriverScreen", "✅ Loaded ${availableDrivers.size} available drivers for ${vehicle.type}")
            } catch (e: Exception) {
                android.util.Log.e("RentDriverScreen", "Error loading drivers: ${e.message}", e)
                Toast.makeText(context, "Error loading drivers: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingDrivers = false
            }

        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚕 Sewa Driver (Kendaraan Pribadi)") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = infoText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            // Step 1: Pilih Tipe Kendaraan (Filter)
            item {
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
                            text = "Pilih Tipe Kendaraan",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // MOBIL option
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { 
                                        selectedVehicleTypeFilter = VehicleType.MOBIL
                                        selectedVehicle = null // Reset selection when filter changes
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedVehicleTypeFilter == VehicleType.MOBIL)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    if (selectedVehicleTypeFilter == VehicleType.MOBIL)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🚗", fontSize = 32.sp)
                                    Text(
                                        text = "Mobil",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            
                            // MOTOR option
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { 
                                        selectedVehicleTypeFilter = VehicleType.MOTOR
                                        selectedVehicle = null // Reset selection when filter changes
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedVehicleTypeFilter == VehicleType.MOTOR)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    if (selectedVehicleTypeFilter == VehicleType.MOTOR)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🏍️", fontSize = 32.sp)
                                    Text(
                                        text = "Motor",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Step 2: Pilih Kendaraan Pribadi (Filtered by type)
            if (selectedVehicleTypeFilter != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pilih Kendaraan Pribadi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (personalVehicles.isEmpty()) {
                            Text("Anda belum memiliki kendaraan pribadi aktif. Tambahkan kendaraan di menu kendaraan.", color = MaterialTheme.colorScheme.error)
                        } else if (filteredVehicles.isEmpty()) {
                            Text("Tidak ada kendaraan ${if (selectedVehicleTypeFilter == VehicleType.MOBIL) "Mobil" else "Motor"} yang tersedia.", color = MaterialTheme.colorScheme.error)
                        } else {
                            filteredVehicles.forEach { vehicle ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedVehicle = vehicle },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedVehicle?.id == vehicle.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        if (selectedVehicle?.id == vehicle.id) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (vehicle.type == VehicleType.MOBIL) Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("${vehicle.brand} ${vehicle.model} (${vehicle.licensePlate})", fontWeight = FontWeight.SemiBold)
                                            Text(vehicle.type.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Driver Selection (only if vehicle type selected)
            if (selectedVehicleType != null) {
                item {
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
                                text = "Pilih Driver",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (isLoadingDrivers) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (availableDrivers.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PersonOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Tidak Ada Driver Tersedia",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "Tidak ada driver online dengan SIM yang sesuai untuk $selectedVehicleType",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    availableDrivers.forEach { driver ->
                                        val driverProfile = driverProfilesMap[driver.email]
                                        DriverSelectionCard(
                                            driver = driver,
                                            driverProfile = driverProfile,
                                            isSelected = driver.email == selectedDriver?.email,
                                            onClick = { selectedDriver = driver }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Duration Selection (only if driver selected)
            if (selectedDriver != null) {
                item {
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
                                text = "Durasi Sewa",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Duration type selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // PER_HOUR
                                FilterChip(
                                    selected = durationType == "PER_HOUR",
                                    onClick = { durationType = "PER_HOUR" },
                                    label = { Text("Per Jam") },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // PER_DAY
                                FilterChip(
                                    selected = durationType == "PER_DAY",
                                    onClick = { durationType = "PER_DAY" },
                                    label = { Text("Per Hari") },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // PER_WEEK
                                FilterChip(
                                    selected = durationType == "PER_WEEK",
                                    onClick = { durationType = "PER_WEEK" },
                                    label = { Text("Per Minggu") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            // Duration count input
                            if (durationType != null) {
                                OutlinedTextField(
                                    value = durationCount,
                                    onValueChange = { newValue ->
                                        if (newValue.all { it.isDigit() }) {
                                            durationCount = newValue
                                            durationCountInt = newValue.toIntOrNull() ?: 1
                                        }
                                    },
                                    label = { 
                                        Text(
                                            when (durationType) {
                                                "PER_HOUR" -> "Jumlah Jam"
                                                "PER_DAY" -> "Jumlah Hari"
                                                "PER_WEEK" -> "Jumlah Minggu"
                                                else -> "Jumlah"
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    )
                                )
                                
                                // Price preview
                                if (calculatedPrice > 0) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Total Harga",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = formattedPrice,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Lokasi Input dengan Map View (hanya untuk kendaraan pribadi penumpang)
            if (durationType != null && durationCountInt > 0) {
                item {
                    LocationInputWithMapCard(
                        pickupAddress = pickupAddress,
                        pickupLat = pickupLat,
                        pickupLon = pickupLon,
                        onPickupChange = { lat, lon, address ->
                            pickupLat = lat
                            pickupLon = lon
                            pickupAddress = address
                        },
                        destinationAddress = destinationAddress,
                        destinationLat = destinationLat,
                        destinationLon = destinationLon,
                        onDestinationChange = { lat, lon, address ->
                            destinationLat = lat
                            destinationLon = lon
                            destinationAddress = address
                        },
                        activeLocationMode = activeLocationMode,
                        onActiveLocationModeChange = { activeLocationMode = it },
                        selectedVehicleType = selectedVehicleTypeFilter,
                        context = context,
                        scope = scope
                    )
                }
            }
            
            // Tombol Lanjut ke Konfirmasi
            item {
                Button(
                    onClick = {
                        // Validation khusus sewa driver kendaraan pribadi
                        if (selectedVehicleType == null) {
                            Toast.makeText(context, "Pilih tipe kendaraan pribadi Anda terlebih dahulu", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedDriver == null) {
                            Toast.makeText(context, "Pilih driver terlebih dahulu", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (durationType == null) {
                            Toast.makeText(context, "Pilih durasi sewa terlebih dahulu", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (durationCountInt <= 0) {
                            Toast.makeText(context, "Masukkan jumlah durasi yang valid", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pickupAddress.isBlank() || pickupLat == 0.0 || pickupLon == 0.0) {
                            Toast.makeText(context, "Masukkan alamat penjemputan", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Destination is optional, but if provided, must have coordinates
                        if (destinationAddress.isNotBlank() && (destinationLat == null || destinationLon == null)) {
                            Toast.makeText(context, "Lokasi tujuan tidak valid", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (calculatedPrice <= 0) {
                            Toast.makeText(context, "Harga tidak valid", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Validasi driver online
                        val driverProfile = driverProfilesMap[selectedDriver!!.email]
                        if (driverProfile == null || !driverProfile.isOnline) {
                            Toast.makeText(context, "Driver yang dipilih sedang offline. Pilih driver lain.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        // Callback hanya untuk driver-only rental (bukan rental kendaraan)
                        onDriverSelected(
                            selectedDriver!!.email,
                            selectedDriver!!.fullName,
                            selectedVehicleType!!,
                            durationType!!,
                            durationCountInt,
                            calculatedPrice,
                            pickupAddress,
                            pickupLat,
                            pickupLon,
                            if (destinationAddress.isNotBlank()) destinationAddress else null,
                            destinationLat,
                            destinationLon
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedVehicleType != null &&
                             selectedDriver != null &&
                             durationType != null &&
                             durationCountInt > 0 &&
                             pickupAddress.isNotBlank() &&
                             calculatedPrice > 0
                ) {
                    Text("Lanjut ke Konfirmasi")
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Location Input Card with Interactive Map, Toggle, and Input Fields
 */
@Composable
private fun LocationInputWithMapCard(
    pickupAddress: String,
    pickupLat: Double,
    pickupLon: Double,
    onPickupChange: (Double, Double, String) -> Unit,
    destinationAddress: String,
    destinationLat: Double?,
    destinationLon: Double?,
    onDestinationChange: (Double, Double, String) -> Unit,
    activeLocationMode: String,
    onActiveLocationModeChange: (String) -> Unit,
    selectedVehicleType: VehicleType?,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // Search state
    var pickupSearchQuery by remember { mutableStateOf(pickupAddress) }
    var destinationSearchQuery by remember { mutableStateOf(destinationAddress) }
    var searchSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    // GPS tracking state (only for pickup)
    var isGpsTracking by remember { mutableStateOf(false) }
    var isLoadingGps by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Route calculation state
    var routeGeometry by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var routeDistance by remember { mutableStateOf<Double?>(null) }
    var routeDuration by remember { mutableStateOf<Double?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    
    // Update search queries when addresses change externally
    LaunchedEffect(pickupAddress) {
        if (pickupSearchQuery != pickupAddress) {
            pickupSearchQuery = pickupAddress
        }
    }
    LaunchedEffect(destinationAddress) {
        if (destinationSearchQuery != destinationAddress) {
            destinationSearchQuery = destinationAddress
        }
    }
    
    // Get current active search query
    val activeSearchQuery = remember(activeLocationMode, pickupSearchQuery, destinationSearchQuery) {
        if (activeLocationMode == "pickup") pickupSearchQuery else destinationSearchQuery
    }
    
    // Search location suggestions with debouncing
    LaunchedEffect(activeSearchQuery) {
        if (activeSearchQuery.length >= 3) {
            delay(500) // Debounce
            if (activeSearchQuery.length >= 3) {
                isSearching = true
                scope.launch {
                    try {
                        val results = searchLocation(geocoder, activeSearchQuery)
                        searchSuggestions = results
                    } catch (e: Exception) {
                        android.util.Log.e("LocationInputWithMapCard", "Search error: ${e.message}")
                        searchSuggestions = emptyList()
                    } finally {
                        isSearching = false
                    }
                }
            }
        } else {
            searchSuggestions = emptyList()
        }
    }
    
    // GPS tracking callback (only for pickup)
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (activeLocationMode == "pickup" && isGpsTracking) {
                    result.lastLocation?.let { location ->
                        scope.launch {
                            try {
                                val geoPoint = GeoPoint(location.latitude, location.longitude)
                                val address = getAddressFromGeoPoint(geocoder, geoPoint)
                                onPickupChange(
                                    location.latitude,
                                    location.longitude,
                                    address ?: "Lokasi GPS saat ini"
                                )
                                pickupSearchQuery = address ?: "Lokasi GPS saat ini"
                                
                                // Animate camera to current location
                                mapViewRef?.post {
                                    mapViewRef?.controller?.animateTo(geoPoint)
                                    mapViewRef?.controller?.setZoom(15.0)
                                    mapViewRef?.invalidate()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LocationInputWithMapCard", "GPS error: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Start GPS tracking function (defined before launcher to avoid reference issues)
    fun startGpsTracking() {
        if (activeLocationMode != "pickup") return
        
        isLoadingGps = true
        @Suppress("MissingPermission")
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let { loc ->
                scope.launch {
                    try {
                        val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                        val address = getAddressFromGeoPoint(geocoder, geoPoint)
                        onPickupChange(
                            loc.latitude,
                            loc.longitude,
                            address ?: "Lokasi GPS saat ini"
                        )
                        pickupSearchQuery = address ?: "Lokasi GPS saat ini"
                        
                        // Animate camera to current location
                        mapViewRef?.post {
                            mapViewRef?.controller?.animateTo(geoPoint)
                            mapViewRef?.controller?.setZoom(15.0)
                            mapViewRef?.invalidate()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LocationInputWithMapCard", "GPS error: ${e.message}")
                    } finally {
                        isLoadingGps = false
                    }
                }
            }
            
            // Request continuous location updates
            @Suppress("MissingPermission")
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()
            
            @Suppress("MissingPermission")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isGpsTracking = true
            isLoadingGps = false
        }.addOnFailureListener {
            isLoadingGps = false
            Toast.makeText(context, "Failed to get GPS location", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Location permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (hasFineLocation || hasCoarseLocation) {
            // Permission granted, start GPS tracking
            startGpsTracking()
        } else {
            // Permission denied
            Toast.makeText(context, "Location permission is required for GPS tracking", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Toggle GPS tracking
    fun toggleGpsTracking() {
        if (activeLocationMode != "pickup") return
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            // Request permission
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        
        if (isGpsTracking) {
            // Stop GPS tracking
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isGpsTracking = false
        } else {
            // Start GPS tracking
            startGpsTracking()
        }
    }
    
    // Cleanup GPS tracking
    DisposableEffect(Unit) {
        onDispose {
            if (isGpsTracking) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }
    
    // Initialize osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
    }
    
    // Calculate route when both pickup and destination are set
    LaunchedEffect(pickupLat, pickupLon, destinationLat, destinationLon) {
        if (pickupLat != 0.0 && pickupLon != 0.0 && destinationLat != null && destinationLon != null) {
            isCalculatingRoute = true
            scope.launch {
                try {
                    val pickupPoint = GeoPoint(pickupLat, pickupLon)
                    val destinationPoint = GeoPoint(destinationLat, destinationLon)
                    val routeInfo = getRouteInfo(pickupPoint, destinationPoint)
                    
                    routeDistance = routeInfo.distance
                    routeDuration = routeInfo.duration
                    routeGeometry = routeInfo.geometry
                    
                    android.util.Log.d("LocationInputWithMapCard", "✅ Route calculated: ${routeInfo.distance} km, ${routeInfo.duration} s")
                } catch (e: Exception) {
                    android.util.Log.e("LocationInputWithMapCard", "❌ Route calculation error: ${e.message}")
                    routeDistance = null
                    routeDuration = null
                    routeGeometry = null
                } finally {
                    isCalculatingRoute = false
                }
            }
        } else {
            // Clear route if either location is missing
            routeDistance = null
            routeDuration = null
            routeGeometry = null
        }
    }
    
    // Calculate estimated travel time based on vehicle type
    val estimatedTravelTime = remember(routeDistance, selectedVehicleType) {
        if (routeDistance != null && selectedVehicleType != null) {
            // Average speeds (km/h) for urban driving
            val averageSpeed = when (selectedVehicleType) {
                VehicleType.MOBIL -> 45.0 // Car: 45 km/h average
                VehicleType.MOTOR -> 35.0 // Motorcycle: 35 km/h average
            }
            // Time in minutes
            (routeDistance!! / averageSpeed) * 60.0
        } else {
            null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Lokasi",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Toggle between Pickup and Destination
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeLocationMode == "pickup",
                    onClick = { 
                        onActiveLocationModeChange("pickup")
                        // Force map update to show correct icons
                        mapViewRef?.invalidate()
                    },
                    label = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Penjemputan")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                
                FilterChip(
                    selected = activeLocationMode == "destination",
                    onClick = { 
                        // Stop GPS tracking when switching to destination
                        if (isGpsTracking) {
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                            isGpsTracking = false
                        }
                        onActiveLocationModeChange("destination") 
                    },
                    label = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Tujuan")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Interactive Map View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F0F0)
                )
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(13.0)
                            
                            // Set initial center
                            val initialLocation = if (pickupLat != 0.0 && pickupLon != 0.0) {
                                GeoPoint(pickupLat, pickupLon)
                            } else if (destinationLat != null && destinationLon != null) {
                                GeoPoint(destinationLat, destinationLon)
                            } else {
                                GeoPoint(-6.2088, 106.8456) // Jakarta default
                            }
                            controller.setCenter(initialLocation)
                            
                            mapViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        mapView.overlays.clear()
                        
                        // Add pickup marker (if set) - Blue/Person icon
                        if (pickupLat != 0.0 && pickupLon != 0.0) {
                            val pickupMarker = Marker(mapView).apply {
                                position = GeoPoint(pickupLat, pickupLon)
                                title = "Lokasi Penjemputan"
                                snippet = pickupAddress.ifEmpty { "Ketuk peta untuk memilih lokasi" }
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                // Person icon with blue color
                                icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                                    setTint(android.graphics.Color.parseColor("#2196F3")) // Blue
                                }
                            }
                            mapView.overlays.add(pickupMarker)
                        }
                        
                        // Add destination marker (if set) - Red/Flag icon
                        destinationLat?.let { lat ->
                            destinationLon?.let { lon ->
                                val destMarker = Marker(mapView).apply {
                                    position = GeoPoint(lat, lon)
                                    title = "Lokasi Tujuan"
                                    snippet = destinationAddress.ifEmpty { "Ketuk peta untuk memilih lokasi" }
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    // Flag/Finish icon with red color
                                    icon = context.getDrawable(android.R.drawable.ic_dialog_map)?.apply {
                                        setTint(android.graphics.Color.parseColor("#F44336")) // Red
                                    }
                                }
                                mapView.overlays.add(destMarker)
                            }
                        }
                        
                        // Add route polyline if both locations are set
                        routeGeometry?.let { geometry ->
                            if (geometry.isNotEmpty()) {
                                val routeLine = Polyline().apply {
                                    setPoints(geometry)
                                    outlinePaint.color = android.graphics.Color.parseColor("#2196F3") // Blue color
                                    outlinePaint.strokeWidth = 12f
                                }
                                mapView.overlays.add(routeLine)
                            }
                        }
                        
                        // Add tap-to-place overlay (only affects active location)
                        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                scope.launch {
                                    try {
                                        val address = getAddressFromGeoPoint(geocoder, p)
                                        val addressText = address ?: "Lokasi: ${p.latitude}, ${p.longitude}"
                                        
                                        if (activeLocationMode == "pickup") {
                                            onPickupChange(p.latitude, p.longitude, addressText)
                                            pickupSearchQuery = addressText
                                        } else {
                                            onDestinationChange(p.latitude, p.longitude, addressText)
                                            destinationSearchQuery = addressText
                                        }
                                        
                                        // Animate camera to selected location
                                        mapView.post {
                                            mapView.controller.animateTo(p)
                                            mapView.controller.setZoom(15.0)
                                            mapView.invalidate()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("LocationInputWithMapCard", "Tap error: ${e.message}")
                                    }
                                }
                                return true
                            }
                            
                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        })
                        mapView.overlays.add(mapEventsOverlay)
                        
                        // Update map center to show both pins and route if both are set
                        val pointsToShow = mutableListOf<GeoPoint>()
                        if (pickupLat != 0.0 && pickupLon != 0.0) {
                            pointsToShow.add(GeoPoint(pickupLat, pickupLon))
                        }
                        destinationLat?.let { lat ->
                            destinationLon?.let { lon ->
                                pointsToShow.add(GeoPoint(lat, lon))
                            }
                        }
                        
                        // Include route geometry points for better bounding box
                        routeGeometry?.let { geometry ->
                            pointsToShow.addAll(geometry)
                        }
                        
                        if (pointsToShow.size > 1) {
                            val boundingBox = BoundingBox.fromGeoPoints(pointsToShow)
                            mapView.post {
                                mapView.zoomToBoundingBox(boundingBox, true, 100)
                            }
                        } else if (pointsToShow.isNotEmpty()) {
                            mapView.post {
                                mapView.controller.setCenter(pointsToShow.first())
                                mapView.controller.setZoom(15.0)
                            }
                        }
                        
                        mapView.invalidate()
                    }
                )
            }
            
            // Route Distance & Time Estimation
            if (pickupLat != 0.0 && pickupLon != 0.0 && destinationLat != null && destinationLon != null) {
                if (isCalculatingRoute) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = "Menghitung rute...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (routeDistance != null) {
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📊 Estimasi Perjalanan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Jarak",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "%.2f km".format(routeDistance),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                estimatedTravelTime?.let { time ->
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Waktu Tempuh",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                        Text(
                                            text = "%.0f menit".format(time),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                        Text(
                                            text = "(${if (selectedVehicleType == VehicleType.MOBIL) "Mobil" else "Motor"})",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                            if (routeGeometry != null && routeGeometry!!.isNotEmpty()) {
                                Text(
                                    text = "🔵 Rute biru ditampilkan di peta",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Location Input Field (for active location)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = activeSearchQuery,
                        onValueChange = { newValue ->
                            if (activeLocationMode == "pickup") {
                                pickupSearchQuery = newValue
                            } else {
                                destinationSearchQuery = newValue
                            }
                        },
                        label = { 
                            Text(
                                if (activeLocationMode == "pickup") "Alamat Penjemputan" else "Alamat Tujuan (Opsional)"
                            )
                        },
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(
                                if (activeLocationMode == "pickup") Icons.Default.Person else Icons.Default.Place,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (activeLocationMode == "pickup") {
                                IconButton(
                                    onClick = { toggleGpsTracking() },
                                    enabled = !isLoadingGps
                                ) {
                                    if (isLoadingGps) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(
                                            Icons.Default.MyLocation,
                                            contentDescription = if (isGpsTracking) "Stop GPS" else "Start GPS",
                                            tint = if (isGpsTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true
                    )
                }
                
                // Search suggestions dropdown
                if (searchSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        LazyColumn {
                            items(searchSuggestions) { address ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val lat = address.latitude
                                            val lon = address.longitude
                                            val addressText = address.getAddressLine(0) ?: address.featureName ?: "Lokasi terpilih"
                                            
                                            if (activeLocationMode == "pickup") {
                                                onPickupChange(lat, lon, addressText)
                                                pickupSearchQuery = addressText
                                            } else {
                                                onDestinationChange(lat, lon, addressText)
                                                destinationSearchQuery = addressText
                                            }
                                            
                                            searchSuggestions = emptyList()
                                            
                                            // Update map center
                                            mapViewRef?.post {
                                                mapViewRef?.controller?.setCenter(GeoPoint(lat, lon))
                                                mapViewRef?.controller?.setZoom(15.0)
                                                mapViewRef?.invalidate()
                                            }
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = address.getAddressLine(0) ?: address.featureName ?: "Lokasi",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        if (address.maxAddressLineIndex > 0) {
                                            Text(
                                                text = buildString {
                                                    for (i in 1..address.maxAddressLineIndex) {
                                                        if (i > 1) append(", ")
                                                        append(address.getAddressLine(i))
                                                    }
                                                },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Text(
                text = "💡 Ketuk peta untuk memilih lokasi • GPS hanya untuk penjemputan",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Location Input Field with GPS tracking, autocomplete, and pin placement
 */
@Composable
private fun LocationInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    latitude: Double,
    longitude: Double,
    onLocationChange: (Double, Double, String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    allowGpsTracking: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var searchQuery by remember { mutableStateOf(value) }
    var searchSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isGpsTracking by remember { mutableStateOf(false) }
    var isLoadingGps by remember { mutableStateOf(false) }
    
    // Update searchQuery when value changes externally
    LaunchedEffect(value) {
        if (searchQuery != value) {
            searchQuery = value
        }
    }
    
    // Search location suggestions with debouncing
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3 && searchQuery != value) {
            delay(500) // Debounce
            if (searchQuery.length >= 3) {
                isSearching = true
                scope.launch {
                    try {
                        val results = searchLocation(geocoder, searchQuery)
                        searchSuggestions = results
                    } catch (e: Exception) {
                        android.util.Log.e("LocationInputField", "Search error: ${e.message}")
                        searchSuggestions = emptyList()
                    } finally {
                        isSearching = false
                    }
                }
            }
        } else {
            searchSuggestions = emptyList()
        }
    }
    
    // GPS tracking callback
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    scope.launch {
                        try {
                            val geoPoint = GeoPoint(location.latitude, location.longitude)
                            val address = getAddressFromGeoPoint(geocoder, geoPoint)
                            onLocationChange(
                                location.latitude,
                                location.longitude,
                                address ?: "Lokasi GPS saat ini"
                            )
                            searchQuery = address ?: "Lokasi GPS saat ini"
                        } catch (e: Exception) {
                            android.util.Log.e("LocationInputField", "GPS error: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    // Start/stop GPS tracking
    fun toggleGpsTracking() {
        if (!allowGpsTracking) return
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            Toast.makeText(context, "Izin lokasi diperlukan untuk GPS tracking", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isGpsTracking) {
            // Stop GPS tracking
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isGpsTracking = false
        } else {
            // Start GPS tracking
            isLoadingGps = true
            @Suppress("MissingPermission")
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { loc ->
                    scope.launch {
                        try {
                            val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                            val address = getAddressFromGeoPoint(geocoder, geoPoint)
                            onLocationChange(
                                loc.latitude,
                                loc.longitude,
                                address ?: "Lokasi GPS saat ini"
                            )
                            searchQuery = address ?: "Lokasi GPS saat ini"
                        } catch (e: Exception) {
                            android.util.Log.e("LocationInputField", "GPS error: ${e.message}")
                        } finally {
                            isLoadingGps = false
                        }
                    }
                }
                
                // Request continuous location updates
                @Suppress("MissingPermission")
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                    .setMinUpdateIntervalMillis(3000L)
                    .build()
                
                @Suppress("MissingPermission")
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                isGpsTracking = true
                isLoadingGps = false
            }.addOnFailureListener {
                isLoadingGps = false
                Toast.makeText(context, "Gagal mendapatkan lokasi GPS", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Cleanup GPS tracking on dispose
    DisposableEffect(Unit) {
        onDispose {
            if (isGpsTracking) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Text input with GPS button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    onValueChange(it)
                },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    Icon(icon, contentDescription = null)
                },
                trailingIcon = {
                    if (allowGpsTracking) {
                        IconButton(
                            onClick = { toggleGpsTracking() },
                            enabled = !isLoadingGps
                        ) {
                            if (isLoadingGps) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = if (isGpsTracking) "Stop GPS" else "Start GPS",
                                    tint = if (isGpsTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                singleLine = true
            )
        }
        
        // Search suggestions dropdown
        if (searchSuggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                LazyColumn {
                    items(searchSuggestions) { address ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val lat = address.latitude
                                    val lon = address.longitude
                                    val addressText = address.getAddressLine(0) ?: address.featureName ?: "Lokasi terpilih"
                                    onLocationChange(lat, lon, addressText)
                                    searchQuery = addressText
                                    onValueChange(addressText)
                                    searchSuggestions = emptyList()
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = address.getAddressLine(0) ?: address.featureName ?: "Lokasi",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (address.maxAddressLineIndex > 0) {
                                    Text(
                                        text = buildString {
                                            for (i in 1..address.maxAddressLineIndex) {
                                                if (i > 1) append(", ")
                                                append(address.getAddressLine(i))
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * Helper function to search location by name
 */
private suspend fun searchLocation(geocoder: Geocoder, query: String): List<Address> {
    return withContext(Dispatchers.IO) {
        try {
            val searchQuery = if (!query.contains("Indonesia", ignoreCase = true)) {
                "$query, Indonesia"
            } else {
                query
            }
            val addresses = geocoder.getFromLocationName(searchQuery, 10)
            val results = addresses ?: emptyList()
            
            // If no results with "Indonesia", try without it
            if (results.isEmpty() && searchQuery != query) {
                geocoder.getFromLocationName(query, 10) ?: emptyList()
            } else {
                results
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationInputField", "Search failed: ${e.message}")
            emptyList()
        }
    }
}

/**
 * Helper function to get address from GeoPoint
 */
private suspend fun getAddressFromGeoPoint(geocoder: Geocoder, geoPoint: GeoPoint): String? {
    return withContext(Dispatchers.IO) {
        try {
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                buildString {
                    for (i in 0..address.maxAddressLineIndex) {
                        if (i > 0) append(", ")
                        append(address.getAddressLine(i))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationInputField", "Geocoding failed: ${e.message}")
            null
        }
    }
}

@Composable
private fun DriverSelectionCard(
    driver: User,
    driverProfile: DriverProfile?,
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = driver.fullName ?: driver.email,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    if (driverProfile != null) {
                        Text(
                            text = if (driverProfile.isOnline) "Online" else "Offline",
                            fontSize = 12.sp,
                            color = if (driverProfile.isOnline)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
