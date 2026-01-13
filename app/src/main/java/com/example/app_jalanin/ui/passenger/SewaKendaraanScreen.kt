package com.example.app_jalanin.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.model.PassengerVehicle

// Data class untuk kendaraan rental
data class RentalVehicle(
    val id: String,
    val name: String,
    val type: String, // "Motor" atau "Mobil"
    val specs: String,
    val pricePerDay: Int,
    val pricePerWeek: Int,
    val pricePerHour: Int,
    val isAvailable: Boolean,
    val location: GeoPoint, // Lokasi kendaraan di Padang
    val locationName: String,
    val icon: String,
    val ownerName: String = "", // ✅ Nama owner kendaraan
    val ownerEmail: String = "", // ✅ Email owner kendaraan
    val driverId: String? = null, // ✅ Email driver yang ditugaskan
    val driverAvailability: String? = null, // ✅ DriverAvailability enum value
    val driverAssignmentMode: String? = null, // ✅ DriverAssignmentMode enum value (DELIVERY_ONLY, DELIVERY_AND_RENTAL)
    val driverPricePerHour: Int = 0, // Biaya driver per jam
    val driverPricePerDay: Int = 0, // Biaya driver per hari
    val driverPricePerWeek: Int = 0 // Biaya driver per minggu
)

// Dummy vehicles di sekitar Padang, Sumatera Barat
// Predefined car types
val dummyVehiclesPadang = listOf(
    // Mobil
    RentalVehicle(
        id = "car_001",
        name = "Toyota Avanza 2022",
        type = "Mobil",
        specs = "Manual • 7 Kursi • AC",
        pricePerDay = 300000,
        pricePerWeek = 1800000,
        pricePerHour = 50000,
        isAvailable = true,
        location = GeoPoint(-0.9471, 100.4172), // Padang Pusat
        locationName = "Jl. Pemuda, Padang Barat",
        icon = "🚗",
        ownerEmail = "owner@jalanin.com", // Default owner for dummy vehicles
        driverPricePerHour = 25000,  // Rp 25K per jam
        driverPricePerDay = 150000,   // Rp 150K per hari
        driverPricePerWeek = 900000   // Rp 900K per minggu
    ),
    RentalVehicle(
        id = "car_002",
        name = "Honda Brio 2023",
        type = "Mobil",
        specs = "Automatic • 5 Kursi • AC",
        pricePerDay = 250000,
        pricePerWeek = 1500000,
        pricePerHour = 40000,
        isAvailable = true,
        location = GeoPoint(-0.9553, 100.3621), // Air Tawar
        locationName = "Air Tawar, Padang Utara",
        icon = "🚗",
        driverPricePerHour = 20000,
        driverPricePerDay = 120000,
        driverPricePerWeek = 720000
    ),
    RentalVehicle(
        id = "car_003",
        name = "Toyota Innova Reborn 2021",
        type = "Mobil",
        specs = "Diesel • 8 Kursi • AC",
        pricePerDay = 450000,
        pricePerWeek = 2700000,
        pricePerHour = 75000,
        isAvailable = true,
        location = GeoPoint(-0.9292, 100.3525), // Lubuk Begalung
        locationName = "Lubuk Begalung",
        icon = "🚐",
        driverPricePerHour = 35000,
        driverPricePerDay = 200000,
        driverPricePerWeek = 1200000
    ),
    RentalVehicle(
        id = "car_004",
        name = "Daihatsu Xenia 2022",
        type = "Mobil",
        specs = "Manual • 7 Kursi • AC",
        pricePerDay = 280000,
        pricePerWeek = 1680000,
        pricePerHour = 45000,
        isAvailable = false,
        location = GeoPoint(-0.9146, 100.3631), // Kuranji
        locationName = "Kuranji, Padang",
        icon = "🚗",
        driverPricePerHour = 23000,
        driverPricePerDay = 140000,
        driverPricePerWeek = 840000
    ),

    // Motor
    RentalVehicle(
        id = "moto_001",
        name = "Yamaha NMAX 2023",
        type = "Motor",
        specs = "Automatic • 150cc",
        pricePerDay = 80000,
        pricePerWeek = 480000,
        pricePerHour = 15000,
        isAvailable = true,
        location = GeoPoint(-0.9403, 100.3523), // Andalas
        locationName = "Andalas, Padang",
        icon = "🏍️",
        driverPricePerHour = 0,
        driverPricePerDay = 0,
        driverPricePerWeek = 0
    ),
    RentalVehicle(
        id = "moto_002",
        name = "Honda PCX 2022",
        type = "Motor",
        specs = "Automatic • 160cc",
        pricePerDay = 90000,
        pricePerWeek = 540000,
        pricePerHour = 18000,
        isAvailable = true,
        location = GeoPoint(-0.9517, 100.4163), // Padang Timur
        locationName = "Padang Timur",
        icon = "🏍️",
        driverPricePerHour = 0,
        driverPricePerDay = 0,
        driverPricePerWeek = 0
    ),
    RentalVehicle(
        id = "moto_003",
        name = "Honda Vario 160 2023",
        type = "Motor",
        specs = "Automatic • 160cc",
        pricePerDay = 70000,
        pricePerWeek = 420000,
        pricePerHour = 12000,
        isAvailable = true,
        location = GeoPoint(-0.9264, 100.3893), // Pauh
        locationName = "Pauh, Padang",
        icon = "🏍️",
        driverPricePerHour = 0,
        driverPricePerDay = 0,
        driverPricePerWeek = 0
    ),
    RentalVehicle(
        id = "moto_004",
        name = "Yamaha Aerox 2022",
        type = "Motor",
        specs = "Automatic • 155cc",
        pricePerDay = 85000,
        pricePerWeek = 510000,
        pricePerHour = 16000,
        isAvailable = true,
        location = GeoPoint(-0.9673, 100.3785), // Bungus
        locationName = "Bungus, Padang Selatan",
        icon = "🏍️",
        driverPricePerHour = 0,
        driverPricePerDay = 0,
        driverPricePerWeek = 0
    ),
    RentalVehicle(
        id = "moto_005",
        name = "Suzuki Burgman Street 2023",
        type = "Motor",
        specs = "Automatic • 125cc",
        pricePerDay = 75000,
        pricePerWeek = 450000,
        pricePerHour = 14000,
        isAvailable = false,
        location = GeoPoint(-0.8984, 100.3523), // Nanggalo
        locationName = "Nanggalo, Padang",
        icon = "🏍️",
        driverPricePerHour = 0,
        driverPricePerDay = 0,
        driverPricePerWeek = 0
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SewaKendaraanScreen(
    passengerEmail: String = "",
    onBackClick: () -> Unit = {},
    onVehicleSelected: (RentalVehicle, String, Boolean, String?) -> Unit = { _, _, _, _ -> }
) {
    var selectedVehicleType by remember { mutableStateOf<String?>("Mobil") } // Default: Mobil
    var selectedDuration by remember { mutableStateOf<String?>("Jam") } // Default: Jam
    var withDriver by remember { mutableStateOf(false) } // Default: Tanpa driver
    var selectedVehicle by remember { mutableStateOf<RentalVehicle?>(null) }
    var showVehicleTypeDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showMapDialog by remember { mutableStateOf(false) }
    var estimatedDistance by remember { mutableStateOf<Double?>(null) }
    var estimatedTime by remember { mutableStateOf<Double?>(null) }
    var deliveryFee by remember { mutableStateOf<Int?>(null) }
    
    // ✅ NEW: Driver selection states
    var selectedPassengerVehicle by remember { mutableStateOf<com.example.app_jalanin.data.model.PassengerVehicle?>(null) }
    var selectedDriver by remember { mutableStateOf<com.example.app_jalanin.data.local.entity.User?>(null) }
    var showSelectPassengerVehicleDialog by remember { mutableStateOf(false) }
    var showSelectDriverDialog by remember { mutableStateOf(false) }
    var passengerVehicles by remember { mutableStateOf<List<com.example.app_jalanin.data.model.PassengerVehicle>>(emptyList()) }

    val context = LocalContext.current
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val scope = rememberCoroutineScope()

    // ✅ ViewModel untuk mengambil data dari database
    val viewModel: SewaKendaraanViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )

    // Collect vehicles from ViewModel
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    
    // ✅ FIX: Download drivers from Firestore when entering screen
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("SewaKendaraan", "📥 Downloading all driver profiles from Firestore...")
                com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.downloadAllDriverProfiles(context)
                android.util.Log.d("SewaKendaraan", "✅ Driver profiles download completed")
            } catch (e: Exception) {
                android.util.Log.e("SewaKendaraan", "❌ Error downloading driver profiles: ${e.message}", e)
            }
        }
    }
    
    // ✅ Load passenger vehicles when withDriver is enabled
    LaunchedEffect(withDriver, passengerEmail) {
        if (withDriver && passengerEmail.isNotEmpty()) {
            scope.launch {
                try {
                    // ✅ FIX: Download passenger vehicles from Firestore first
                    scope.launch(Dispatchers.IO) {
                        try {
                            android.util.Log.d("SewaKendaraan", "📥 Downloading passenger vehicles from Firestore...")
                            com.example.app_jalanin.data.remote.FirestorePassengerVehicleSyncManager.downloadPassengerVehicles(
                                context,
                                passengerEmail
                            )
                            android.util.Log.d("SewaKendaraan", "✅ Passenger vehicles download completed")
                        } catch (e: Exception) {
                            android.util.Log.e("SewaKendaraan", "❌ Error downloading passenger vehicles: ${e.message}", e)
                        }
                    }
                    
                    // Then load from local DB
                    database.passengerVehicleDao().getActiveVehiclesByPassenger(passengerEmail)
                        .collect { vehicleList ->
                            passengerVehicles = vehicleList
                            android.util.Log.d("SewaKendaraan", "✅ Loaded ${vehicleList.size} passenger vehicles")
                        }
                } catch (e: Exception) {
                    android.util.Log.e("SewaKendaraan", "Error loading passenger vehicles: ${e.message}", e)
                }
            }
        }
    }

    // Initialize osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocationSewa(fusedLocationClient) { location ->
                userLocation = location
            }
        }
    }

    // Check location permission
    LaunchedEffect(Unit) {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                getCurrentLocationSewa(fusedLocationClient) { location ->
                    userLocation = location
                }
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // ✅ Filter vehicles based on selected type from database
    val filteredVehicles: List<RentalVehicle> = remember(selectedVehicleType, vehicles) {
        if (selectedVehicleType == null) {
            vehicles
        } else {
            vehicles.filter { vehicle -> vehicle.type == selectedVehicleType }
        }
    }

    // Show error toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
            viewModel.clearErrorMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        HeaderSewa(onBackClick = onBackClick)

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Jenis Kendaraan
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Jenis Kendaraan",
                    color = Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.8.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .clickable { showVehicleTypeDialog = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedVehicleType ?: "Motor / Mobil",
                            color = Color(0xFF333333),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.8.sp
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Durasi Sewa
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Durasi Sewa",
                    color = Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.8.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .clickable { showDurationDialog = true }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDuration ?: "Harian / Mingguan / Jam",
                            color = Color(0xFF333333),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.8.sp
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ✅ NEW: Driver Option (for both Mobil and Motor, with SIM validation)
            // ✅ FIX: Only show driver option UI if selected vehicle doesn't have assigned driver
            val hasAssignedDriver = selectedVehicle?.driverId != null && selectedVehicle?.driverAssignmentMode == "DELIVERY_AND_RENTAL"
            
            if ((selectedVehicleType == "Mobil" || selectedVehicleType == "Motor") && !hasAssignedDriver) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "+Driver",
                                color = Color(0xFF333333),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.8.sp
                            )
                            Text(
                                text = if (selectedVehicleType == "Mobil") "Memerlukan SIM A" else "Memerlukan SIM C",
                                color = Color(0xFF666666),
                                fontSize = 11.sp
                            )
                            // Show selected passenger vehicle and driver
                            if (withDriver) {
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedPassengerVehicle != null) {
                                    Text(
                                        text = "Kendaraan: ${selectedPassengerVehicle!!.brand} ${selectedPassengerVehicle!!.model}",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "⚠️ Pilih kendaraan pribadi Anda",
                                        color = Color(0xFFFF9800),
                                        fontSize = 11.sp
                                    )
                                }
                                if (selectedDriver != null) {
                                    Text(
                                        text = "Driver: ${selectedDriver!!.fullName ?: selectedDriver!!.email}",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else if (selectedPassengerVehicle != null) {
                                    Text(
                                        text = "⚠️ Pilih driver",
                                        color = Color(0xFFFF9800),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = withDriver,
                            onCheckedChange = { 
                                withDriver = it
                                if (!it) {
                                    // Reset selections when turning off driver
                                    selectedPassengerVehicle = null
                                    selectedDriver = null
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF111827),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCCCCCC)
                            )
                        )
                    }
                    
                    // Buttons to select passenger vehicle and driver
                    if (withDriver) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSelectPassengerVehicleDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (selectedPassengerVehicle != null) "Ganti Kendaraan" else "Pilih Kendaraan",
                                    fontSize = 12.sp
                                )
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    if (selectedPassengerVehicle != null) {
                                        showSelectDriverDialog = true
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Pilih kendaraan pribadi Anda terlebih dahulu",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = selectedPassengerVehicle != null
                            ) {
                                Text(
                                    text = if (selectedDriver != null) "Ganti Driver" else "Pilih Driver",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // ✅ NEW: Show assigned driver info if vehicle has assigned driver
            if (hasAssignedDriver) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Kendaraan ini sudah termasuk Driver",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Driver akan mengantarkan kendaraan dan mengemudi selama sewa",
                            color = Color(0xFF666666),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Kendaraan Tersedia
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Kendaraan Tersedia",
                    color = Color(0xFF333333),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.2.sp
                )

                // Display vehicles
                filteredVehicles.forEachIndexed { index, vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        duration = selectedDuration,
                        withDriver = withDriver,
                        isSelected = selectedVehicle?.id == vehicle.id,
                        onClick = {
                            selectedVehicle = vehicle
                            
                            // ✅ FIX: Auto-enable withDriver if vehicle has assigned driver with DELIVERY_AND_RENTAL mode
                            if (vehicle.driverId != null && vehicle.driverAssignmentMode == "DELIVERY_AND_RENTAL") {
                                withDriver = true
                                // Clear personal vehicle/driver selection since we're using assigned driver
                                selectedPassengerVehicle = null
                                selectedDriver = null
                            }

                            // Calculate delivery info
                            if (userLocation != null) {
                                scope.launch {
                                    val distance = calculateDistance(userLocation!!, vehicle.location)
                                    estimatedDistance = distance
                                    estimatedTime = (distance / 40.0) * 60.0 // Assume 40 km/h, convert to minutes
                                    deliveryFee = (distance * 5000.0).toInt() // Rp 5000 per km
                                }
                            }
                        },
                        onShowMap = { showMapDialog = true }
                    )
                }

                if (filteredVehicles.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Text(
                            text = "Silakan pilih jenis kendaraan terlebih dahulu",
                            modifier = Modifier.padding(24.dp),
                            color = Color(0xFF666666),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Delivery Info (if vehicle selected)
            if (selectedVehicle != null && estimatedDistance != null) {
                DeliveryInfoCard(
                    distance = estimatedDistance!!,
                    estimatedTime = estimatedTime!!,
                    deliveryFee = deliveryFee!!,
                    vehicleLocation = selectedVehicle!!.locationName
                )
            }

            // Sewa Sekarang Button
            Button(
                onClick = {
                    val currentVehicle = selectedVehicle
                    val currentDuration = selectedDuration
                    if (currentVehicle != null && currentDuration != null) {
                        // ✅ FIX: Validate driver selection only if vehicle doesn't have assigned driver
                        val vehicleHasAssignedDriver = currentVehicle.driverId != null && currentVehicle.driverAssignmentMode == "DELIVERY_AND_RENTAL"
                        if (withDriver && !vehicleHasAssignedDriver) {
                            if (selectedPassengerVehicle == null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Pilih kendaraan pribadi Anda terlebih dahulu",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            if (selectedDriver == null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Pilih driver terlebih dahulu",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                        }
                        onVehicleSelected(
                            currentVehicle, 
                            currentDuration, 
                            withDriver,
                            selectedDriver?.email
                        )
                    }
                },
                enabled = selectedVehicle != null && 
                         selectedDuration != null && 
                         (selectedVehicle?.isAvailable == true) &&
                         (!withDriver || hasAssignedDriver || (selectedPassengerVehicle != null && selectedDriver != null)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF333333),
                    disabledContainerColor = Color(0xFFCCCCCC)
                )
            ) {
                Text(
                    text = "Sewa Sekarang",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.2.sp
                )
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF333333))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Informasi:",
                        color = Color(0xFF333333),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.4.sp
                    )
                    Text(
                        text = "Kendaraan akan diantar ke lokasi Anda. Pembayaran bisa DP atau penuh.",
                        color = Color(0xFF333333),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 13.2.sp
                    )
                }
            }
        }
    }

    // Vehicle Type Dialog
    if (showVehicleTypeDialog) {
        Dialog(onDismissRequest = { showVehicleTypeDialog = false }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pilih Jenis Kendaraan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    listOf("Motor", "Mobil").forEach { type ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedVehicleType = type
                                    selectedVehicle = null
                                    showVehicleTypeDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedVehicleType == type) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                            )
                        ) {
                            Text(
                                text = type,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Duration Dialog
    if (showDurationDialog) {
        Dialog(onDismissRequest = { showDurationDialog = false }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pilih Durasi Sewa",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    listOf("Jam", "Harian", "Mingguan").forEach { duration ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedDuration = duration
                                    showDurationDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedDuration == duration) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                            )
                        ) {
                            Text(
                                text = duration,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    // ✅ Dialog untuk pilih kendaraan pribadi penumpang
    if (showSelectPassengerVehicleDialog) {
        val vehicleTypeFilter = when (selectedVehicleType) {
                "Mobil" -> com.example.app_jalanin.data.model.VehicleType.MOBIL
                "Motor" -> com.example.app_jalanin.data.model.VehicleType.MOTOR
                else -> null
        }
        
        SelectPassengerVehicleDialog(
            passengerEmail = passengerEmail,
            vehicleType = vehicleTypeFilter,
            onDismiss = { showSelectPassengerVehicleDialog = false },
            onVehicleSelected = { vehicle ->
                selectedPassengerVehicle = vehicle
                selectedDriver = null // Reset driver selection when vehicle changes
                showSelectPassengerVehicleDialog = false
                android.widget.Toast.makeText(
                    context,
                    "Kendaraan dipilih: ${vehicle.brand} ${vehicle.model}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddVehicle = {
                // Navigate to passenger vehicles screen
                showSelectPassengerVehicleDialog = false
                android.widget.Toast.makeText(
                    context,
                    "Silakan tambahkan kendaraan di menu 'Kendaraan Saya'",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    
    // ✅ Dialog untuk pilih driver (harus pilih kendaraan dulu)
    selectedPassengerVehicle?.let { passengerVehicle ->
        if (showSelectDriverDialog) {
            // Use passenger vehicle type for driver filtering
            val vehicleType = passengerVehicle.type
        
        SelectDriverDialog(
            vehicleType = vehicleType,
            passengerEmail = passengerEmail,
            onDismiss = { showSelectDriverDialog = false },
            onDriverSelected = { driver ->
                selectedDriver = driver
                showSelectDriverDialog = false
                android.widget.Toast.makeText(
                    context,
                    "Driver dipilih: ${driver.fullName ?: driver.email}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
        }
    }

    // Map Dialog
    if (showMapDialog && selectedVehicle != null && userLocation != null) {
        Dialog(onDismissRequest = { showMapDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lokasi Kendaraan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showMapDialog = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(13.0)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mapView ->
                            mapView.overlays.clear()

                            // User location marker
                            val userMarker = Marker(mapView).apply {
                                position = userLocation
                                title = "Lokasi Anda"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(userMarker)

                            // Vehicle location marker
                            val vehicleMarker = Marker(mapView).apply {
                                position = selectedVehicle!!.location
                                title = selectedVehicle!!.name
                                snippet = selectedVehicle!!.locationName
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(vehicleMarker)

                            // Zoom to show both markers
                            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                                listOf(userLocation, selectedVehicle!!.location)
                            )
                            mapView.post {
                                mapView.zoomToBoundingBox(boundingBox, true, 100)
                            }

                            mapView.invalidate()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSewa(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFFF5F5F5))
            .border(0.dp, Color(0xFF333333), RoundedCornerShape(0.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF333333)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "Sewa Kendaraan",
            color = Color(0xFF333333),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VehicleCard(
    vehicle: RentalVehicle,
    duration: String?,
    withDriver: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onShowMap: () -> Unit
) {
    val price = when (duration) {
        "Harian" -> vehicle.pricePerDay
        "Mingguan" -> vehicle.pricePerWeek
        "Jam" -> vehicle.pricePerHour
        else -> vehicle.pricePerHour
    }

    val durationText = when (duration) {
        "Harian" -> "hari"
        "Mingguan" -> "minggu"
        "Jam" -> "jam"
        else -> "jam"
    }

    // ✅ UPDATED: Get driver price based on duration (for both Mobil and Motor)
    val driverPrice = if (withDriver && (vehicle.type == "Mobil" || vehicle.type == "Motor")) {
        when (duration) {
            "Harian" -> vehicle.driverPricePerDay
            "Mingguan" -> vehicle.driverPricePerWeek
            "Jam" -> vehicle.driverPricePerHour
            else -> vehicle.driverPricePerHour
        }
    } else {
        0
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) Color(0xFFE8F5E9) else Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isSelected) Color(0xFF4CAF50) else Color(0xFF333333)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
            // Vehicle Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFE5E5E5), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = vehicle.icon,
                    fontSize = 28.sp
                )
            }

            // Vehicle Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = vehicle.name,
                    color = Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.8.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Text(
                    text = vehicle.specs,
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 14.4.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                // Price section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", price).replace(',', '.')}/$durationText",
                        color = Color(0xFF333333),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 15.sp
                    )

                    if (withDriver && vehicle.type == "Mobil" && driverPrice > 0) {
                        Text(
                            text = "+Driver: Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", driverPrice).replace(',', '.')}/$durationText",
                            color = Color(0xFF2196F3),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (vehicle.isAvailable) Color(0xFF22C55E) else Color(0xFFEF4444)
                                )
                            ) {
                                Text(
                                    text = if (vehicle.isAvailable) "Tersedia" else "Disewa",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            
                            // ✅ NEW: Show "Vehicle + Driver" badge if vehicle has assigned driver
                            if (vehicle.driverId != null && vehicle.driverAssignmentMode == "DELIVERY_AND_RENTAL") {
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF2196F3)
                                    )
                                ) {
                                    Text(
                                        text = "🚗 +Driver",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            TextButton(
                                onClick = onShowMap,
                                modifier = Modifier.height(24.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "📍 Peta",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2196F3)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        
        // ✅ Owner name watermark (kecil di pinggir)
        if (vehicle.ownerName.isNotEmpty()) {
            Text(
                text = vehicle.ownerName,
                fontSize = 8.sp,
                color = Color(0xFF999999).copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp),
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
private fun DeliveryInfoCard(
    distance: Double,
    estimatedTime: Double,
    deliveryFee: Int,
    vehicleLocation: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFA726))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📍 Info Pengantaran",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lokasi Kendaraan",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = vehicleLocation,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFFFD180))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Jarak",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "%.1f km".format(distance),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Estimasi Waktu",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "${estimatedTime.roundToInt()} menit",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Biaya Antar",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", deliveryFee).replace(',', '.')}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00)
                    )
                }
            }

            Text(
                text = "💡 Kendaraan akan diantar ke lokasi Anda dalam waktu estimasi di atas",
                fontSize = 11.sp,
                color = Color(0xFF666666),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

// Helper functions
@Suppress("MissingPermission")
private fun getCurrentLocationSewa(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocationReceived: (GeoPoint) -> Unit
) {
    fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                onLocationReceived(geoPoint)
            } else {
                // Default ke Padang Pusat
                val defaultLocation = GeoPoint(-0.9471, 100.4172)
                onLocationReceived(defaultLocation)
            }
        }
        .addOnFailureListener {
            val defaultLocation = GeoPoint(-0.9471, 100.4172)
            onLocationReceived(defaultLocation)
        }
}

private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
    return point1.distanceToAsDouble(point2) / 1000.0 // Convert to km
}
