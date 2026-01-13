package com.example.app_jalanin.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
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
import com.example.app_jalanin.utils.getRouteInfo
import com.example.app_jalanin.utils.calculateDistance
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Calendar
import java.util.Locale

data class RentalConfirmation(
    val vehicle: RentalVehicle,
    val duration: String,
    val withDriver: Boolean,
    val startDate: Calendar,
    val endDate: Calendar,
    val days: Int,
    val basePrice: Int,
    val distanceSurcharge: Int,
    val totalPrice: Int,
    val deliveryLocation: GeoPoint?,
    val deliveryAddress: String,
    // ✅ FIX: Add proper duration breakdown for accurate rental tracking
    val durationDays: Int,
    val durationHours: Int,
    val durationMinutes: Int,
    // ✅ NEW: Driver availability state
    val driverAvailability: String? = null, // DriverAvailability enum value
    val ownerContacted: Boolean = false, // For NOT_AVAILABLE state
    val ownerConfirmed: Boolean = false, // For NOT_AVAILABLE state
    // ✅ NEW: Payment information
    val paymentMethod: String = "M-Banking", // "M-Banking", "ATM", "Tunai"
    val paymentType: String = "FULL" // "DP" atau "FULL"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KonfirmasiSewaScreen(
    vehicle: RentalVehicle,
    duration: String,
    withDriver: Boolean,
    selectedDriverEmail: String? = null, // Driver yang dipilih penumpang
    onBackClick: () -> Unit = {},
    onConfirmPayment: (RentalConfirmation) -> Unit = {}
) {
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var deliveryLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var deliveryAddress by remember { mutableStateOf("") }
    var useCurrentLocation by remember { mutableStateOf(false) }
    var showMapDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf("DP") } // "DP" atau "FULL"
    var selectedPaymentType by remember { mutableStateOf("M-Banking") } // "M-Banking" atau "Tunai"
    
    // ✅ NEW: Driver availability states
    val driverAvailability = vehicle.driverAvailability
    val isDriverNotAvailable = driverAvailability == "NOT_AVAILABLE"
    var ownerContacted by remember { mutableStateOf(false) }
    var ownerConfirmed by remember { mutableStateOf(false) }
    var showChatDialog by remember { mutableStateOf(false) }

    // Duration count input (user inputs how many hours/days/weeks)
    var durationCount by remember { mutableStateOf("1") }
    val durationCountInt = durationCount.toIntOrNull() ?: 1

    // Route calculation states
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var routeDistance by remember { mutableStateOf<Double?>(null) }
    var routeGeometry by remember { mutableStateOf<List<GeoPoint>?>(null) }

    // Date states
    var startDate by remember { mutableStateOf(Calendar.getInstance()) }
    var endDate by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, if (duration == "Harian") 1 else 7) }) }

    // Calculate days
    val days = remember(startDate, endDate, duration) {
        when (duration) {
            "Jam" -> 1
            "Harian" -> {
                val diff = endDate.timeInMillis - startDate.timeInMillis
                (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            }
            "Mingguan" -> {
                val diff = endDate.timeInMillis - startDate.timeInMillis
                ((diff / (1000 * 60 * 60 * 24)) / 7).toInt().coerceAtLeast(1)
            }
            else -> 1
        }
    }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val scope = rememberCoroutineScope()
    val database = remember { com.example.app_jalanin.data.AppDatabase.getDatabase(context) }
    
    // ✅ NEW: Driver validation state
    var driverValidationError by remember { mutableStateOf<String?>(null) }
    var isDriverValid by remember { mutableStateOf(false) }
    var isCheckingDriver by remember { mutableStateOf(false) }
    
    // ✅ NEW: Validate driver SIM and online status when withDriver is true
    // ✅ FIX: Priority: assigned driver (vehicle.driverId with DELIVERY_AND_RENTAL) > selectedTravelDriverEmail > vehicle.driverId (personal)
    LaunchedEffect(withDriver, selectedDriverEmail, vehicle.driverId, vehicle.driverAssignmentMode, vehicle.type) {
        if (withDriver) {
            // Priority: assigned driver > travel driver
            val vehicleHasAssignedDriver = vehicle.driverId != null && vehicle.driverAssignmentMode == "DELIVERY_AND_RENTAL"
            val driverEmailToValidate = if (vehicleHasAssignedDriver) {
                vehicle.driverId // Use assigned driver from vehicle
            } else {
                selectedDriverEmail ?: vehicle.driverId // Travel driver
            }
            
            if (driverEmailToValidate != null) {
                isCheckingDriver = true
                driverValidationError = null
                isDriverValid = false
                
                scope.launch {
                    try {
                        // Load driver from database
                        val driver = database.userDao().getUserByEmail(driverEmailToValidate)
                        val driverProfile = driver?.let { database.driverProfileDao().getByEmail(it.email) }
                        
                        if (driver == null) {
                            driverValidationError = "Driver tidak ditemukan"
                            isDriverValid = false
                        } else if (driverProfile?.isOnline != true) {
                            driverValidationError = "Driver sedang offline. Driver harus online untuk menerima order."
                            isDriverValid = false
                        } else {
                            // Check SIM compatibility
                            val vehicleType = when (vehicle.type) {
                                "Mobil" -> com.example.app_jalanin.data.model.VehicleType.MOBIL
                                "Motor" -> com.example.app_jalanin.data.model.VehicleType.MOTOR
                                else -> null
                            }
                            
                            if (vehicleType != null) {
                                val driverSims = driverProfile.simCertifications?.split(",")?.mapNotNull { 
                                    try { com.example.app_jalanin.data.model.SimType.valueOf(it.trim()) } 
                                    catch (e: Exception) { null }
                                } ?: emptyList()
                                
                                val canDrive = com.example.app_jalanin.data.model.SimCertificationHelper.canDriveVehicle(driverSims, vehicleType)
                                
                                if (!canDrive) {
                                    val requiredSim = com.example.app_jalanin.data.model.SimCertificationHelper.getRequiredSimType(vehicleType)
                                    driverValidationError = "Driver tidak memiliki ${requiredSim.name.replace("SIM_", "SIM ")} yang diperlukan untuk mengendarai ${vehicle.type}"
                                    isDriverValid = false
                                } else {
                                    isDriverValid = true
                                    driverValidationError = null
                                    android.util.Log.d("KonfirmasiSewa", "✅ Driver validated: ${driver.email}, SIM: ${driverProfile.simCertifications}")
                                }
                            } else {
                                driverValidationError = "Jenis kendaraan tidak valid"
                                isDriverValid = false
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("KonfirmasiSewa", "❌ Error validating driver: ${e.message}", e)
                        driverValidationError = "Error memvalidasi driver: ${e.message}"
                        isDriverValid = false
                    } finally {
                        isCheckingDriver = false
                    }
                }
            } else {
                // Driver email not provided
                driverValidationError = "Driver belum dipilih"
                isDriverValid = false
                isCheckingDriver = false
            }
        } else {
            // No driver requested
            isDriverValid = true
            driverValidationError = null
            isCheckingDriver = false
        }
    }

    // Calculate route distance and geometry when delivery location changes
    // OPTIMIZED: Single API call with debounce and error handling
    LaunchedEffect(deliveryLocation) {
        if (deliveryLocation != null) {
            isCalculatingRoute = true

            try {
                // Add delay to prevent rapid fire API calls
                kotlinx.coroutines.delay(300)

                // ✅ FIX: Get route info with both distance and geometry
                val routeInfo = getRouteInfo(vehicle.location, deliveryLocation!!)
                routeDistance = routeInfo.distance
                routeGeometry = routeInfo.geometry
                
                android.util.Log.d("KonfirmasiSewa", "Route calculated: ${routeInfo.distance} km with ${routeInfo.geometry?.size ?: 0} points")
            } catch (e: Exception) {
                // Fallback to 0 if error occurs
                android.util.Log.e("KonfirmasiSewa", "Error calculating route: ${e.message}")
                routeDistance = 0.0
                routeGeometry = null
            } finally {
                isCalculatingRoute = false
            }
        } else {
            routeDistance = null
            routeGeometry = null
        }
    }

    // Use route distance for calculations, fallback to null if not available
    val distance = routeDistance ?: 0.0

    // Base price based on duration and count
    val basePrice = remember(durationCountInt, duration) {
        val pricePerUnit = when (duration) {
            "Jam" -> vehicle.pricePerHour
            "Harian" -> vehicle.pricePerDay
            "Mingguan" -> vehicle.pricePerWeek
            else -> vehicle.pricePerHour
        }
        pricePerUnit * durationCountInt
    }

    // ✅ NEW: SIM validation state
    var showSimValidationDialog by remember { mutableStateOf(false) }
    var simValidationError by remember { mutableStateOf<String?>(null) }
    
    // ✅ NEW: Check SIM compatibility and online status when withDriver is true
    val canRequestDriver = remember(withDriver, vehicle.type, vehicle.driverId, isDriverValid, driverValidationError) {
        if (!withDriver || vehicle.driverId == null) {
            true // No driver requested or no driver assigned
        } else {
            // Check driver validation (SIM and online status)
            if (!isDriverValid || driverValidationError != null) {
                false // Driver validation failed
            } else {
                // Driver is valid, check availability
            when (vehicle.driverAvailability) {
                "NOT_AVAILABLE" -> false // Must contact owner
                "AVAILABLE_DELIVERY_ONLY", "AVAILABLE_FULL_RENT" -> true
                else -> true // Default allow
                }
            }
        }
    }
    
    // ✅ UPDATED: Driver surcharge based on duration and count (for both Mobil and Motor)
    val driverSurcharge = if (withDriver && canRequestDriver && (vehicle.type == "Mobil" || vehicle.type == "Motor")) {
        val driverPricePerUnit = when (duration) {
            "Jam" -> vehicle.driverPricePerHour
            "Harian" -> vehicle.driverPricePerDay
            "Mingguan" -> vehicle.driverPricePerWeek
            else -> vehicle.driverPricePerHour
        }
        driverPricePerUnit * durationCountInt
    } else {
        0
    }

    // Delivery fee: Rp 600 per km ONLY for distance above 20 km
    // First 20 km is FREE, then Rp 600/km for excess
    val deliveryFee = remember(distance) {
        if (distance > 20.0) {
            val excessDistance = distance - 20.0
            (excessDistance * 600).toInt()
        } else {
            0
        }
    }

    // Total price
    val totalPrice = basePrice + driverSurcharge + deliveryFee

    // Initialize osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocationKonfirmasi(fusedLocationClient) { location ->
                userLocation = location
                if (useCurrentLocation) {
                    deliveryLocation = location
                    scope.launch {
                        val address = getAddressFromGeoPoint(geocoder, location)
                        deliveryAddress = address ?: "Lokasi saat ini"
                    }
                }
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
                getCurrentLocationKonfirmasi(fusedLocationClient) { location ->
                    userLocation = location
                }
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Use current location toggle
    LaunchedEffect(useCurrentLocation) {
        if (useCurrentLocation && userLocation != null) {
            deliveryLocation = userLocation
            val address = getAddressFromGeoPoint(geocoder, userLocation!!)
            deliveryAddress = address ?: "Lokasi saat ini"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        HeaderKonfirmasi(onBackClick = onBackClick)

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Detail Kendaraan
            VehicleDetailCard(vehicle = vehicle, duration = duration, withDriver = withDriver)

            // Durasi Sewa
            DurationSection(
                duration = duration,
                durationCount = durationCount,
                onDurationCountChange = { durationCount = it },
                totalPrice = totalPrice
            )

            // Breakdown Harga
            PriceBreakdownCard(
                basePrice = basePrice,
                driverSurcharge = driverSurcharge,
                deliveryFee = deliveryFee,
                distance = distance,
                totalPrice = totalPrice
            )

            // Metode Pembayaran
            PaymentMethodSection(
                selectedMethod = paymentMethod,
                totalPrice = totalPrice,
                onMethodSelected = { paymentMethod = it }
            )

            // Lokasi Pengantaran
            DeliveryLocationSection(
                deliveryAddress = deliveryAddress,
                useCurrentLocation = useCurrentLocation,
                isCalculatingRoute = isCalculatingRoute,
                routeDistance = routeDistance,
                onToggleCurrentLocation = {
                    useCurrentLocation = !useCurrentLocation
                    if (!useCurrentLocation) {
                        deliveryLocation = null
                        deliveryAddress = ""
                        routeDistance = null
                    }
                },
                onSelectLocation = { showMapDialog = true }
            )

            // ✅ NEW: Driver validation error banner
            if (withDriver && driverValidationError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "⚠️ Validasi Driver Gagal",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                        Text(
                            text = driverValidationError ?: "Driver tidak valid",
                            fontSize = 12.sp,
                            color = Color(0xFFC62828)
                        )
                        if (isCheckingDriver) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Memvalidasi driver...",
                                    fontSize = 12.sp,
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }

            // ✅ NEW: Driver availability info banner
            if (isDriverNotAvailable) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "⚠️ Driver Tidak Tersedia",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF856404)
                        )
                        Text(
                            text = "Anda harus menghubungi pemilik kendaraan terlebih dahulu untuk konfirmasi sewa.",
                            fontSize = 12.sp,
                            color = Color(0xFF856404)
                        )
                        if (!ownerContacted) {
                            Button(
                                onClick = { showChatDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF856404))
                            ) {
                                Text("💬 Hubungi Pemilik")
                            }
                        } else if (!ownerConfirmed) {
                            Text(
                                text = "⏳ Menunggu konfirmasi dari pemilik...",
                                fontSize = 12.sp,
                                color = Color(0xFF856404),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = "✅ Pemilik telah mengkonfirmasi sewa",
                                fontSize = 12.sp,
                                color = Color(0xFF155724),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else if (driverAvailability == "AVAILABLE_DELIVERY_ONLY") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1ECF1)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "ℹ️ Driver hanya mengantarkan kendaraan ke lokasi Anda",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Color(0xFF0C5460)
                    )
                }
            } else if (driverAvailability == "AVAILABLE_FULL_RENT") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD4EDDA)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "✅ Driver tersedia untuk mengantarkan dan mengemudi selama sewa",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Color(0xFF155724)
                    )
                }
            }

            // Konfirmasi & Bayar Button (conditional based on driver availability)
            Button(
                onClick = {
                    if (isDriverNotAvailable) {
                        if (!ownerContacted) {
                            showChatDialog = true
                        } else if (ownerConfirmed && deliveryLocation != null && deliveryAddress.isNotEmpty()) {
                            showPaymentDialog = true
                        }
                    } else if (deliveryLocation != null && deliveryAddress.isNotEmpty()) {
                        showPaymentDialog = true
                    }
                },
                enabled = when {
                    isDriverNotAvailable -> ownerConfirmed && deliveryLocation != null && deliveryAddress.isNotEmpty()
                    else -> deliveryLocation != null && deliveryAddress.isNotEmpty()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF333333),
                    disabledContainerColor = Color(0xFFCCCCCC)
                )
            ) {
                Text(
                    text = when {
                        withDriver && !isDriverValid -> "Driver Tidak Valid"
                        isDriverNotAvailable && !ownerContacted -> "Hubungi Pemilik Dulu"
                        isDriverNotAvailable && !ownerConfirmed -> "Menunggu Konfirmasi"
                        else -> "Konfirmasi & Bayar"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.6.sp
                )
            }

            Text(
                text = if (isDriverNotAvailable && !ownerConfirmed) {
                    "Harap hubungi pemilik kendaraan terlebih dahulu untuk konfirmasi sewa."
                } else {
                    "Kendaraan akan dikirim ke alamat Anda sesuai jadwal sewa."
                },
                color = Color(0xFF666666),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 14.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bottom Safe Area
        BottomSafeAreaKonfirmasi()
    }

    // Map Dialog for selecting delivery location
    if (showMapDialog) {
        MapSelectionDialog(
            currentLocation = userLocation ?: GeoPoint(-0.9471, 100.4172),
            vehicleLocation = vehicle.location,
            onLocationSelected = { location ->
                deliveryLocation = location
                scope.launch {
                    isCalculatingRoute = true
                    val address = getAddressFromGeoPoint(geocoder, location)
                    deliveryAddress = address ?: "Lokasi terpilih"

                    // OPTIMIZED: Single API call for route info
                    val routeInfo = getRouteInfo(vehicle.location, location)
                    routeDistance = routeInfo.distance

                    isCalculatingRoute = false
                }
                showMapDialog = false
            },
            onDismiss = { showMapDialog = false }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DateRangePickerDialog(
            startDate = startDate,
            endDate = endDate,
            onDateRangeSelected = { start, end ->
                startDate = start
                endDate = end
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // ✅ NEW: Chat Dialog for NOT_AVAILABLE state
    if (showChatDialog) {
        OwnerChatDialog(
            ownerName = vehicle.ownerName,
            ownerEmail = vehicle.id, // Using vehicle id as placeholder, should be owner email
            onContactOwner = {
                ownerContacted = true
                // In real implementation, this would open chat or send message
                // For now, simulate owner confirmation after 2 seconds
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    ownerConfirmed = true
                }
                showChatDialog = false
            },
            onDismiss = { showChatDialog = false }
        )
    }

    // Payment Dialog
    if (showPaymentDialog) {
        PaymentDialog(
            totalPrice = totalPrice,
            paymentMethod = paymentMethod,
            selectedPaymentType = selectedPaymentType,
            onPaymentTypeSelected = { selectedPaymentType = it },
            onConfirm = {
                // ✅ FIX: Calculate proper duration breakdown based on user input
                val calculatedDays: Int
                val calculatedHours: Int
                val calculatedMinutes: Int

                when (duration) {
                    "Jam" -> {
                        calculatedDays = 0
                        calculatedHours = durationCountInt
                        calculatedMinutes = 0
                    }
                    "Harian" -> {
                        calculatedDays = durationCountInt
                        calculatedHours = 0
                        calculatedMinutes = 0
                    }
                    "Mingguan" -> {
                        calculatedDays = durationCountInt * 7
                        calculatedHours = 0
                        calculatedMinutes = 0
                    }
                    else -> {
                        calculatedDays = 0
                        calculatedHours = 1
                        calculatedMinutes = 0
                    }
                }

                android.util.Log.d("KonfirmasiSewa", "📊 Duration breakdown: $calculatedDays days, $calculatedHours hours, $calculatedMinutes minutes")

                // Create confirmation and proceed
                val confirmation = RentalConfirmation(
                    vehicle = vehicle,
                    duration = duration,
                    withDriver = withDriver,
                    startDate = startDate,
                    endDate = endDate,
                    days = days,
                    basePrice = basePrice + driverSurcharge,
                    distanceSurcharge = deliveryFee,
                    totalPrice = totalPrice,
                    deliveryLocation = deliveryLocation,
                    deliveryAddress = deliveryAddress,
                    durationDays = calculatedDays,
                    durationHours = calculatedHours,
                    durationMinutes = calculatedMinutes,
                    driverAvailability = driverAvailability, // ✅ Pass driver availability
                    ownerContacted = ownerContacted, // ✅ Pass owner contact status
                    ownerConfirmed = ownerConfirmed, // ✅ Pass owner confirmation status
                    paymentMethod = selectedPaymentType, // ✅ Pass payment method
                    paymentType = paymentMethod // ✅ Pass payment type (DP or FULL)
                )
                showPaymentDialog = false
                onConfirmPayment(confirmation)
            },
            onDismiss = { showPaymentDialog = false }
        )
    }
}

@Composable
private fun HeaderKonfirmasi(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF333333),
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "Konfirmasi Sewa",
            color = Color(0xFF333333),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun VehicleDetailCard(
    vehicle: RentalVehicle,
    duration: String,
    withDriver: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCCCCCC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Detail Kendaraan",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.2.sp
            )

            // Vehicle Image Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = vehicle.icon,
                        fontSize = 40.sp
                    )
                    Text(
                        text = vehicle.name,
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.8.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = vehicle.specs + if (withDriver) " • +Driver" else "",
                    color = Color(0xFF666666),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.8.sp
                )

                val pricePerUnit = when (duration) {
                    "Jam" -> vehicle.pricePerHour
                    "Mingguan" -> vehicle.pricePerWeek
                    else -> vehicle.pricePerDay
                }

                val unitText = when (duration) {
                    "Jam" -> "jam"
                    "Mingguan" -> "minggu"
                    else -> "hari"
                }

                Text(
                    text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", pricePerUnit).replace(',', '.')} / $unitText",
                    color = Color(0xFF333333),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.2.sp
                )
            }
        }
    }
}

@Composable
private fun DurationSection(
    duration: String,
    durationCount: String,
    onDurationCountChange: (String) -> Unit,
    totalPrice: Int
) {
    val unitText = when (duration) {
        "Jam" -> "Jam"
        "Mingguan" -> "Minggu"
        else -> "Hari"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Durasi Sewa",
            color = Color(0xFF333333),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.2.sp
        )

        // Input untuk jumlah durasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Berapa $unitText?",
                color = Color(0xFF666666),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = durationCount,
                onValueChange = { newValue ->
                    // Only allow digits
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        onDurationCountChange(newValue)
                    }
                },
                modifier = Modifier.width(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF0F0F0),
                    unfocusedContainerColor = Color(0xFFF0F0F0),
                    focusedIndicatorColor = Color(0xFF4CAF50),
                    unfocusedIndicatorColor = Color(0xFFCCCCCC)
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                suffix = {
                    Text(
                        text = unitText,
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Total: Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalPrice).replace(',', '.')}",
                    color = Color(0xFF333333),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.2.sp
                )
            }
        }
    }
}

@Composable
private fun PriceBreakdownCard(
    basePrice: Int,
    driverSurcharge: Int,
    deliveryFee: Int,
    distance: Double,
    totalPrice: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Price Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFA726))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💰 Rincian Biaya",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                HorizontalDivider(color = Color(0xFFFFD180))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Harga Sewa" + if (driverSurcharge > 0) " + Driver" else "",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", basePrice + driverSurcharge).replace(',', '.')}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                }

                if (deliveryFee > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Biaya Pengantaran",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                            val excessDistance = distance - 20.0
                            Text(
                                text = "(${String.format(Locale.forLanguageTag("id-ID"), "%.2f", distance)} km - 20 km gratis = ${String.format(Locale.forLanguageTag("id-ID"), "%.2f", excessDistance)} km × Rp 600)",
                                fontSize = 10.sp,
                                color = Color(0xFF999999),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 12.sp
                            )
                        }
                        Text(
                            text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", deliveryFee).replace(',', '.')}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF57C00)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Biaya Pengantaran",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                            Text(
                                text = "(${String.format(Locale.forLanguageTag("id-ID"), "%.2f", distance)} km - GRATIS! 20 km pertama)",
                                fontSize = 10.sp,
                                color = Color(0xFF4CAF50),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Rp 0",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFFFD180))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TOTAL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalPrice).replace(',', '.')}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00)
                    )
                }
            }
        }

        // Encouragement & Etiquette Message
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💚 Himbauan",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                Text(
                    text = "• Kami sangat menghargai jika Anda berkenan mengisi bahan bakar kendaraan sebelum pengembalian (sukarela)",
                    fontSize = 12.sp,
                    color = Color(0xFF333333),
                    lineHeight = 16.sp
                )

                if (driverSurcharge > 0) {
                    Text(
                        text = "• Tips untuk driver sangat dihargai sebagai bentuk apresiasi atas pelayanan (sukarela) 🙏",
                        fontSize = 12.sp,
                        color = Color(0xFF333333),
                        lineHeight = 16.sp
                    )
                }

                HorizontalDivider(color = Color(0xFFA5D6A7), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "🌟 Selamat menikmati perjalanan Anda!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )

                Text(
                    text = "Mohon maaf atas segala ketidaknyamanan yang mungkin terjadi. Terima kasih telah mempercayai layanan kami! 🚗💨",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    lineHeight = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodSection(
    selectedMethod: String,
    totalPrice: Int,
    onMethodSelected: (String) -> Unit
) {
    val dpAmount = totalPrice / 2
    val remainingAmount = totalPrice - dpAmount

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Metode Pembayaran",
            color = Color(0xFF333333),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.2.sp
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // DP 50%
            PaymentMethodCard(
                text = "DP 50%",
                description = "Bayar: Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", dpAmount).replace(',', '.')}\nSisa: Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", remainingAmount).replace(',', '.')} (saat pengantaran)",
                isSelected = selectedMethod == "DP",
                onClick = { onMethodSelected("DP") }
            )

            // Bayar Penuh
            PaymentMethodCard(
                text = "Bayar Penuh (Cash)",
                description = "Bayar: Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalPrice).replace(',', '.')}\nLunas sekarang",
                isSelected = selectedMethod == "FULL",
                onClick = { onMethodSelected("FULL") }
            )
        }

        // Info box with highlight
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA726))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selectedMethod == "DP") {
                    Text(
                        text = "💡 Informasi DP:",
                        color = Color(0xFF333333),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.4.sp
                    )
                    Text(
                        text = "• Bayar DP 50% sekarang: Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", dpAmount).replace(',', '.')}\n" +
                                "• Sisa Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", remainingAmount).replace(',', '.')} dibayar saat kendaraan diantar\n" +
                                "• DP tidak dapat dikembalikan jika pembatalan dari pemesan",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 14.sp
                    )
                } else {
                    Text(
                        text = "💡 Informasi Bayar Penuh:",
                        color = Color(0xFF333333),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.4.sp
                    )
                    Text(
                        text = "• Bayar langsung Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalPrice).replace(',', '.')}\n" +
                                "• Lunas sekarang, tidak ada pembayaran saat pengantaran\n" +
                                "• Dapat bonus gratis 1 jam tambahan untuk sewa harian",
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    text: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE8F5E8) else Color(0xFFF8F8F8)
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFF4CAF50) else Color(0xFFCCCCCC)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        if (isSelected) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFFCCCCCC), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = text,
                    color = Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.8.sp
                )

                Text(
                    text = description,
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun DeliveryLocationSection(
    deliveryAddress: String,
    useCurrentLocation: Boolean,
    isCalculatingRoute: Boolean,
    routeDistance: Double?,
    onToggleCurrentLocation: () -> Unit,
    onSelectLocation: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Lokasi Pengantaran",
            color = Color(0xFF333333),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.2.sp
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectLocation() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCCCCCC))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = if (deliveryAddress.isEmpty()) "Masukkan alamat pengantaran kendaraan" else deliveryAddress,
                    color = if (deliveryAddress.isEmpty()) Color(0xFF999999) else Color(0xFF333333),
                    fontSize = 14.sp,
                    fontWeight = if (deliveryAddress.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                    lineHeight = 16.8.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Show route calculation status and distance
        if (isCalculatingRoute) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFFA726)
                    )
                    Text(
                        text = "Menghitung rute...",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        } else if (routeDistance != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (routeDistance > 20.0) Color(0xFFFFEBEE) else Color(0xFFE8F5E8)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (routeDistance > 20.0) Color(0xFFEF5350) else Color(0xFF4CAF50)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📏 Jarak Rute",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = "%.2f km".format(routeDistance),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (routeDistance > 20.0) Color(0xFFEF5350) else Color(0xFF4CAF50)
                        )
                    }

                    Text(
                        text = if (routeDistance > 20.0) "⚠️ > 20 km" else "✅ < 20 km",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (routeDistance > 20.0) Color(0xFFEF5350) else Color(0xFF4CAF50)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gunakan lokasi saat ini",
                color = Color(0xFF333333),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.8.sp,
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = useCurrentLocation,
                onCheckedChange = { onToggleCurrentLocation() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCCCCCC)
                )
            )
        }
    }
}

@Composable
private fun MapSelectionDialog(
    currentLocation: GeoPoint,
    vehicleLocation: GeoPoint,
    onLocationSelected: (GeoPoint) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLocation by remember { mutableStateOf(currentLocation) } // Start with current location
    var searchQuery by remember { mutableStateOf("") }
    var mapCenter by remember { mutableStateOf(currentLocation) }
    var isCalculatingDistance by remember { mutableStateOf(false) }
    var calculatedDistance by remember { mutableStateOf<Double?>(null) }
    var routeGeometry by remember { mutableStateOf<List<GeoPoint>?>(null) }

    val context = LocalContext.current
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    // Calculate route distance and geometry when location changes
    // OPTIMIZED: Single API call with debounce to prevent force close
    LaunchedEffect(selectedLocation) {
        isCalculatingDistance = true

        try {
            // Debounce: wait 500ms to prevent rapid fire API calls
            kotlinx.coroutines.delay(500)

            // Get both distance and geometry in ONE API call
            val routeInfo = getRouteInfo(vehicleLocation, selectedLocation)
            calculatedDistance = routeInfo.distance
            routeGeometry = routeInfo.geometry
        } catch (e: Exception) {
            android.util.Log.e("MapSelectionDialog", "Error getting route: ${e.message}")
            calculatedDistance = null
            routeGeometry = null
        } finally {
            isCalculatingDistance = false
        }

        android.util.Log.d("MapRoute", "Route calculated: ${calculatedDistance} km with ${routeGeometry?.size ?: 0} points")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih Lokasi Pengantaran",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                    }
                }

                // Search Input with GPS button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari lokasi di Padang...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Search",
                                tint = Color(0xFF666666)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    scope.launch {
                                        val addresses = withContext(Dispatchers.IO) {
                                            try {
                                                geocoder.getFromLocationName(searchQuery + ", Padang, Sumatera Barat", 1)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                        addresses?.firstOrNull()?.let { address ->
                                            selectedLocation = GeoPoint(address.latitude, address.longitude)
                                            mapCenter = selectedLocation
                                        }
                                    }
                                }) {
                                    Text("🔍", fontSize = 18.sp)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F0F0),
                            unfocusedContainerColor = Color(0xFFF0F0F0),
                            focusedIndicatorColor = Color(0xFF4CAF50),
                            unfocusedIndicatorColor = Color(0xFFCCCCCC)
                        ),
                        singleLine = true
                    )

                    // GPS Crosshair button to return to current location
                    IconButton(
                        onClick = {
                            selectedLocation = currentLocation
                            mapCenter = currentLocation
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                    ) {
                        Text("🎯", fontSize = 24.sp)
                    }
                }

                Text(
                    text = "🚗 Pin kendaraan • 👤 Pin lokasi pemesan • 🎯 GPS untuk lokasi terkini" +
                           if (routeGeometry != null && routeGeometry!!.isNotEmpty())
                               " • 🔵 Rute biru: ${routeGeometry!!.size} titik"
                           else "",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Map
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            // Initial zoom will be set by zoomToBoundingBox in update block
                            // Set a reasonable default center
                            controller.setZoom(13.0)
                            controller.setCenter(mapCenter)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    update = { mapView ->
                        mapView.overlays.clear()

                        // Draw route line if available (Blue line) - ALWAYS CHECK AND DRAW
                        routeGeometry?.let { geometry ->
                            if (geometry.isNotEmpty()) {
                                val routeLine = org.osmdroid.views.overlay.Polyline().apply {
                                    setPoints(geometry)
                                    outlinePaint.color = android.graphics.Color.parseColor("#2196F3") // Blue color
                                    outlinePaint.strokeWidth = 10f // Slightly thicker for visibility
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.alpha = 200 // Slightly transparent
                                }
                                mapView.overlays.add(routeLine)

                                // Log for debugging
                                android.util.Log.d("MapRoute", "Route line added with ${geometry.size} points")
                            }
                        }

                        // Vehicle location marker with vehicle icon
                        val vehicleMarker = Marker(mapView).apply {
                            position = vehicleLocation
                            title = "🚗 Lokasi Kendaraan"
                            snippet = "Kendaraan berada di sini"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            icon = context.getDrawable(android.R.drawable.ic_menu_directions)?.apply {
                                setTint(android.graphics.Color.parseColor("#FF6B35"))
                            }
                        }
                        mapView.overlays.add(vehicleMarker)

                        // User/Delivery location marker (SAME PIN for both)
                        val userDeliveryMarker = Marker(mapView).apply {
                            position = selectedLocation
                            title = "👤 Lokasi Pengantaran"
                            snippet = "Kendaraan akan diantar ke lokasi ini"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            icon = context.getDrawable(android.R.drawable.ic_menu_myplaces)?.apply {
                                setTint(android.graphics.Color.parseColor("#2196F3"))
                            }
                        }
                        mapView.overlays.add(userDeliveryMarker)

                        // Map events for selecting location
                        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                selectedLocation = p
                                mapCenter = p
                                mapView.overlays.clear()
                                mapView.overlays.add(vehicleMarker)

                                val newUserDeliveryMarker = Marker(mapView).apply {
                                    position = p
                                    title = "👤 Lokasi Pengantaran"
                                    snippet = "Kendaraan akan diantar ke lokasi ini"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                    icon = context.getDrawable(android.R.drawable.ic_menu_myplaces)?.apply {
                                        setTint(android.graphics.Color.parseColor("#2196F3"))
                                    }
                                }
                                mapView.overlays.add(newUserDeliveryMarker)

                                mapView.overlays.add(MapEventsOverlay(this))
                                
                                // ✅ FIX: Zoom to show both markers after selecting new location
                                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                                    listOf(vehicleLocation, p)
                                )
                                mapView.post {
                                    mapView.zoomToBoundingBox(boundingBox, true, 100)
                                }
                                
                                mapView.invalidate()
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                return false
                            }
                        })
                        mapView.overlays.add(mapEventsOverlay)

                        // ✅ FIX: Always zoom to show both markers (vehicle location and delivery location)
                        // Include route geometry points if available for better bounding box
                        val pointsForBoundingBox = mutableListOf(vehicleLocation, selectedLocation)
                        routeGeometry?.let { geometry ->
                            if (geometry.isNotEmpty()) {
                                pointsForBoundingBox.addAll(geometry)
                            }
                        }
                        
                        val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(pointsForBoundingBox)
                        mapView.post {
                            mapView.zoomToBoundingBox(boundingBox, true, 100)
                        }

                        mapView.invalidate()
                    }
                )

                // Info about distance
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCalculatingDistance) {
                            Color(0xFFFFF9E6)
                        } else if ((calculatedDistance ?: 0.0) > 20.0) {
                            Color(0xFFFFEBEE)
                        } else {
                            Color(0xFFE3F2FD)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCalculatingDistance) {
                            Color(0xFFFFA726)
                        } else if ((calculatedDistance ?: 0.0) > 20.0) {
                            Color(0xFFEF5350)
                        } else {
                            Color(0xFF1976D2)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCalculatingDistance) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFA726)
                            )
                            Column {
                                Text(
                                    text = "Menghitung rute...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Menggunakan OSRM routing API + Garis Biru",
                                    fontSize = 10.sp,
                                    color = Color(0xFF999999)
                                )
                            }
                        } else {
                            Text(
                                text = "📏",
                                fontSize = 18.sp
                            )
                            Column {
                                Text(
                                    text = "Jarak Rute: %.2f km".format(calculatedDistance ?: 0.0),
                                    fontSize = 12.sp,
                                    color = if ((calculatedDistance ?: 0.0) > 20.0) Color(0xFFEF5350) else Color(0xFF1976D2),
                                    fontWeight = FontWeight.Bold
                                )
                                if (routeGeometry != null && routeGeometry!!.isNotEmpty()) {
                                    Text(
                                        text = "🔵 Garis biru: ${routeGeometry!!.size} titik rute",
                                        fontSize = 10.sp,
                                        color = Color(0xFF2196F3),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = if ((calculatedDistance ?: 0.0) > 20.0) "⚠️ Biaya tambahan mungkin berlaku" else "✅ Tidak ada biaya tambahan jarak",
                                        fontSize = 10.sp,
                                        color = if ((calculatedDistance ?: 0.0) > 20.0) Color(0xFFF57C00) else Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }

                // Confirm Button
                Button(
                    onClick = {
                        onLocationSelected(selectedLocation)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFFCCCCCC)
                    )
                ) {
                    Text("📍 Konfirmasi Lokasi Pengantaran")
                }
            }
        }
    }
}

@Composable
private fun DateRangePickerDialog(
    startDate: Calendar,
    endDate: Calendar,
    onDateRangeSelected: (Calendar, Calendar) -> Unit,
    onDismiss: () -> Unit
) {
    // Simple date picker - in production, use DatePickerDialog or custom calendar
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Pilih Tanggal Sewa",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Fitur kalender akan ditambahkan di versi mendatang.",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Button(
                    onClick = {
                        onDateRangeSelected(startDate, endDate)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
private fun BottomSafeAreaKonfirmasi() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(134.dp)
                .height(5.dp)
                .background(Color(0xFFCCCCCC), RoundedCornerShape(3.dp))
        )
    }
}

// Helper functions
@Suppress("MissingPermission")
private fun getCurrentLocationKonfirmasi(
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

private suspend fun getAddressFromGeoPoint(geocoder: Geocoder, geoPoint: GeoPoint): String? {
    return withContext(Dispatchers.IO) {
        try {
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun PaymentDialog(
    totalPrice: Int,
    paymentMethod: String,
    selectedPaymentType: String,
    onPaymentTypeSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dpAmount = totalPrice / 2
    val fullAmount = totalPrice

    // Determine amount to pay based on payment method
    val amountToPay = if (paymentMethod == "DP") dpAmount else fullAmount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "💳 Pilih Metode Pembayaran",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                HorizontalDivider(color = Color(0xFFEEEEEE))

                // Payment info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (paymentMethod == "DP") "Pembayaran DP (50%)" else "Pembayaran Penuh",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF666666)
                            )
                            Text(
                                text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", amountToPay).replace(',', '.')}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                        }

                        if (paymentMethod == "DP") {
                            Text(
                                text = "Sisa Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", fullAmount - dpAmount).replace(',', '.')} dibayar saat pengantaran",
                                fontSize = 11.sp,
                                color = Color(0xFF999999),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }

                // Payment type selection
                Text(
                    text = "Pilih Cara Pembayaran:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )

                // M-Banking option (Support DP)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPaymentTypeSelected("M-Banking") },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedPaymentType == "M-Banking") Color(0xFFE3F2FD) else Color(0xFFFAFAFA)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (selectedPaymentType == "M-Banking") 2.dp else 1.dp,
                        color = if (selectedPaymentType == "M-Banking") Color(0xFF2196F3) else Color(0xFFDDDDDD)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = if (selectedPaymentType == "M-Banking") Color(0xFF2196F3) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (selectedPaymentType == "M-Banking") Color(0xFF2196F3) else Color(0xFFCCCCCC),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedPaymentType == "M-Banking") {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📱 M-Banking (Dummy)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = "BCA, Mandiri, BNI, BRI",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                            if (paymentMethod == "DP") {
                                Text(
                                    text = "✅ Mendukung DP",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Tunai option (No DP support)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (paymentMethod == "FULL") {
                                onPaymentTypeSelected("Tunai")
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            paymentMethod == "DP" -> Color(0xFFF5F5F5)
                            selectedPaymentType == "Tunai" -> Color(0xFFE8F5E9)
                            else -> Color(0xFFFAFAFA)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (selectedPaymentType == "Tunai" && paymentMethod == "FULL") 2.dp else 1.dp,
                        color = when {
                            paymentMethod == "DP" -> Color(0xFFEEEEEE)
                            selectedPaymentType == "Tunai" -> Color(0xFF4CAF50)
                            else -> Color(0xFFDDDDDD)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = if (selectedPaymentType == "Tunai" && paymentMethod == "FULL") Color(0xFF4CAF50) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        paymentMethod == "DP" -> Color(0xFFDDDDDD)
                                        selectedPaymentType == "Tunai" -> Color(0xFF4CAF50)
                                        else -> Color(0xFFCCCCCC)
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedPaymentType == "Tunai" && paymentMethod == "FULL") {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💵 Tunai / Cash",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (paymentMethod == "DP") Color(0xFF999999) else Color(0xFF333333)
                            )
                            Text(
                                text = "Bayar saat pengantaran",
                                fontSize = 12.sp,
                                color = if (paymentMethod == "DP") Color(0xFFCCCCCC) else Color(0xFF666666)
                            )
                            if (paymentMethod == "DP") {
                                Text(
                                    text = "❌ Tidak mendukung DP",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF5350),
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "✅ Hanya bayar penuh",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Info box
                if (paymentMethod == "DP" && selectedPaymentType == "Tunai") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(text = "⚠️", fontSize = 16.sp)
                            Text(
                                text = "Pembayaran tunai tidak mendukung DP. Silakan pilih M-Banking atau ubah ke Bayar Penuh.",
                                fontSize = 11.sp,
                                color = Color(0xFFD32F2F),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFEEEEEE))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5F5F5)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Batal",
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            android.util.Log.d("PaymentDialog", "🔘 Bayar button clicked!")
                            android.util.Log.d("PaymentDialog", "Payment method: $paymentMethod, Type: $selectedPaymentType")
                            onConfirm()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !(paymentMethod == "DP" && selectedPaymentType == "Tunai"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFFCCCCCC)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (selectedPaymentType == "M-Banking") "Bayar Sekarang" else "Konfirmasi",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * ✅ NEW: Chat Dialog for contacting owner when driver is NOT_AVAILABLE
 */
@Composable
private fun OwnerChatDialog(
    ownerName: String,
    ownerEmail: String,
    onContactOwner: () -> Unit,
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "💬 Hubungi Pemilik",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                Text(
                    text = "Pemilik: $ownerName",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                
                Text(
                    text = "Driver tidak tersedia. Silakan hubungi pemilik untuk konfirmasi sewa kendaraan.",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                
                // Message input
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Pesan ke pemilik") },
                    placeholder = { Text("Halo, saya ingin menyewa kendaraan...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5F5F5)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Batal",
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onContactOwner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = messageText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFFCCCCCC)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Kirim",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


