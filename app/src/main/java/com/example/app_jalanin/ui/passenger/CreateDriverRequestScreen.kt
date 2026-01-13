package com.example.app_jalanin.ui.passenger

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
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.model.PassengerVehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.app_jalanin.ui.owner.LocationResult
import com.example.app_jalanin.ui.owner.VehicleLocationPickerDialog
import org.osmdroid.util.GeoPoint

/**
 * Screen untuk penumpang membuat request driver
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDriverRequestScreen(
    passengerEmail: String,
    driver: User,
    vehicle: PassengerVehicle,
    onBackClick: () -> Unit = {},
    onRequestCreated: (DriverRequest) -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var pickupAddress by remember { mutableStateOf("") }
    var pickupLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationAddress by remember { mutableStateOf("") }
    var destinationLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showPickupMap by remember { mutableStateOf(false) }
    var showDestinationMap by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load passenger info
    var passengerName by remember { mutableStateOf("") }
    LaunchedEffect(passengerEmail) {
        scope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserByEmail(passengerEmail)
                }
                passengerName = user?.fullName ?: passengerEmail
            } catch (e: Exception) {
                android.util.Log.e("CreateDriverRequest", "Error loading passenger: ${e.message}", e)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Driver") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Driver Info Card
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
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = driver.fullName ?: driver.email,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = driver.email,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Vehicle Info Card
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
                    Icon(
                        if (vehicle.type.name == "MOBIL") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${vehicle.brand} ${vehicle.model}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = vehicle.licensePlate,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Pickup Location
            OutlinedTextField(
                value = pickupAddress,
                onValueChange = { pickupAddress = it },
                label = { Text("Lokasi Penjemputan") },
                placeholder = { Text("Masukkan alamat penjemputan") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showPickupMap = true }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Pilih Lokasi")
                    }
                },
                readOnly = true
            )
            
            // Destination Location (Optional)
            OutlinedTextField(
                value = destinationAddress,
                onValueChange = { destinationAddress = it },
                label = { Text("Tujuan (Opsional)") },
                placeholder = { Text("Masukkan alamat tujuan") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { showDestinationMap = true }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Pilih Lokasi")
                    }
                },
                readOnly = true
            )
            
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
            
            // Submit Button
            Button(
                onClick = {
                    if (pickupAddress.isEmpty() || pickupLocation == null) {
                        errorMessage = "Lokasi penjemputan harus diisi"
                        return@Button
                    }
                    
                    scope.launch {
                        try {
                            isLoading = true
                            errorMessage = null
                            
                            val requestId = "DRIVER_REQ_${System.currentTimeMillis()}_${(1000..9999).random()}"
                            val now = System.currentTimeMillis()
                            
                            val request = DriverRequest(
                                id = requestId,
                                passengerEmail = passengerEmail,
                                passengerName = passengerName,
                                driverEmail = driver.email,
                                driverName = driver.fullName,
                                passengerVehicleId = vehicle.id.toString(),
                                vehicleBrand = vehicle.brand,
                                vehicleModel = vehicle.model,
                                vehicleType = vehicle.type.name,
                                vehicleLicensePlate = vehicle.licensePlate,
                                pickupAddress = pickupAddress,
                                pickupLat = pickupLocation!!.latitude,
                                pickupLon = pickupLocation!!.longitude,
                                destinationAddress = if (destinationAddress.isNotEmpty()) destinationAddress else null,
                                destinationLat = destinationLocation?.latitude,
                                destinationLon = destinationLocation?.longitude,
                                status = "PENDING",
                                createdAt = now,
                                updatedAt = now,
                                synced = false
                            )
                            
                            withContext(Dispatchers.IO) {
                                database.driverRequestDao().insert(request)
                            }
                            
                            android.util.Log.d("CreateDriverRequest", "✅ Request created: $requestId")
                            onRequestCreated(request)
                        } catch (e: Exception) {
                            android.util.Log.e("CreateDriverRequest", "Error creating request: ${e.message}", e)
                            errorMessage = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && pickupAddress.isNotEmpty() && pickupLocation != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Kirim Request")
                }
            }
        }
    }
    
    // Pickup Location Picker Dialog
    if (showPickupMap) {
        VehicleLocationPickerDialog(
            initialLocation = pickupLocation,
            onLocationSelected = { locationResult: LocationResult ->
                pickupLocation = locationResult.geoPoint
                pickupAddress = locationResult.address
                showPickupMap = false
            },
            onDismiss = { showPickupMap = false }
        )
    }
    
    // Destination Location Picker Dialog
    if (showDestinationMap) {
        VehicleLocationPickerDialog(
            initialLocation = destinationLocation,
            onLocationSelected = { locationResult: LocationResult ->
                destinationLocation = locationResult.geoPoint
                destinationAddress = locationResult.address
                showDestinationMap = false
            },
            onDismiss = { showDestinationMap = false }
        )
    }
}
