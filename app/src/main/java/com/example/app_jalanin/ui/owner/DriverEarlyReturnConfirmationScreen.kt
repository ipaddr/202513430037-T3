package com.example.app_jalanin.ui.owner

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import com.example.app_jalanin.data.model.CameraMode
import com.example.app_jalanin.data.model.LocationInputSource
import com.example.app_jalanin.ui.owner.DriverEarlyReturnViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import java.util.Locale

/**
 * Screen for Driver to confirm early return and set return location
 * Uses DriverEarlyReturnViewModel with proper CameraMode and LocationInputSource control
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverEarlyReturnConfirmationScreen(
    rentalId: String,
    onBackClick: () -> Unit,
    onLocationConfirmed: () -> Unit // Called when location is successfully confirmed
) {
    val context = LocalContext.current
    val viewModel: DriverEarlyReturnViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val rental by viewModel.rental.collectAsState()
    
    val scope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Load rental data on first composition
    LaunchedEffect(rentalId) {
        viewModel.loadRental(rentalId)
    }
    
    // Location Callback for GPS updates (only active when FOLLOW_GPS mode)
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val currentState = uiState
                // Only process GPS updates if in FOLLOW_GPS mode
                if (currentState?.cameraMode == CameraMode.FOLLOW_GPS) {
                    val location = locationResult.lastLocation
                    location?.let { loc ->
                        val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                        
                        // Get address from GPS location
                        scope.launch(Dispatchers.IO) {
                            try {
                                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                val fullAddress = addresses?.firstOrNull()?.let { addr ->
                                    val addressLines = mutableListOf<String>()
                                    for (i in 0 until addr.maxAddressLineIndex) {
                                        addr.getAddressLine(i)?.let { addressLines.add(it) }
                                    }
                                    if (addressLines.isEmpty()) {
                                        addr.getAddressLine(0) ?: "Lokasi GPS"
                                    } else {
                                        addressLines.joinToString(", ")
                                    }
                                } ?: "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                                
                                withContext(Dispatchers.Main) {
                                    // Update ViewModel with GPS location
                                    viewModel.updateLocationFromGps(gpsPoint, fullAddress)
                                    
                                    // Center map on GPS location
                                    mapViewRef?.post {
                                        mapViewRef?.controller?.setCenter(gpsPoint)
                                        mapViewRef?.controller?.setZoom(15.0) // Zoom in when tracking GPS
                                        mapViewRef?.invalidate()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DriverEarlyReturnConfirmationScreen", "Error geocoding GPS: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    val fallbackAddress = "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                                    viewModel.updateLocationFromGps(gpsPoint, fallbackAddress)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Request location permission (defined first to avoid circular dependency)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (hasFineLocation || hasCoarseLocation) {
            // Permission granted, start GPS tracking if needed
            val currentState = uiState
            if (currentState?.cameraMode == CameraMode.FOLLOW_GPS) {
                // Call startGpsTracking directly here
                val hasPermission = true // We just got permission
                
                // Get last known location first
                @Suppress("MissingPermission")
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let { loc ->
                        val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                        
                        scope.launch(Dispatchers.IO) {
                            try {
                                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                val fullAddress = addresses?.firstOrNull()?.let { addr ->
                                    val addressLines = mutableListOf<String>()
                                    for (i in 0 until addr.maxAddressLineIndex) {
                                        addr.getAddressLine(i)?.let { addressLines.add(it) }
                                    }
                                    if (addressLines.isEmpty()) {
                                        addr.getAddressLine(0) ?: "Lokasi GPS"
                                    } else {
                                        addressLines.joinToString(", ")
                                    }
                                } ?: "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                                
                                withContext(Dispatchers.Main) {
                                    viewModel.updateLocationFromGps(gpsPoint, fullAddress)
                                    
                                    mapViewRef?.post {
                                        mapViewRef?.controller?.setCenter(gpsPoint)
                                        mapViewRef?.controller?.setZoom(15.0)
                                        mapViewRef?.invalidate()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DriverEarlyReturnConfirmationScreen", "Error geocoding: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    val fallbackAddress = "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                                    viewModel.updateLocationFromGps(gpsPoint, fallbackAddress)
                                }
                            }
                        }
                    }
                    
                    // Request continuous location updates
                    @Suppress("MissingPermission")
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                        .setMinUpdateIntervalMillis(3000L)
                        .build()
                    
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                }.addOnFailureListener { e ->
                    android.util.Log.e("DriverEarlyReturnConfirmationScreen", "Error getting location: ${e.message}", e)
                }
            }
        }
    }
    
    // Function to start GPS tracking
    fun startGpsTracking(
        client: FusedLocationProviderClient,
        callback: LocationCallback
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        
        // Get last known location first
        @Suppress("MissingPermission")
        client.lastLocation.addOnSuccessListener { location ->
            location?.let { loc ->
                val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                
                scope.launch(Dispatchers.IO) {
                    try {
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        val fullAddress = addresses?.firstOrNull()?.let { addr ->
                            val addressLines = mutableListOf<String>()
                            for (i in 0 until addr.maxAddressLineIndex) {
                                addr.getAddressLine(i)?.let { addressLines.add(it) }
                            }
                            if (addressLines.isEmpty()) {
                                addr.getAddressLine(0) ?: "Lokasi GPS"
                            } else {
                                addressLines.joinToString(", ")
                            }
                        } ?: "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                        
                        withContext(Dispatchers.Main) {
                            viewModel.updateLocationFromGps(gpsPoint, fullAddress)
                            
                            mapViewRef?.post {
                                mapViewRef?.controller?.setCenter(gpsPoint)
                                mapViewRef?.controller?.setZoom(15.0)
                                mapViewRef?.invalidate()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DriverEarlyReturnConfirmationScreen", "Error geocoding: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            val fallbackAddress = "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                            viewModel.updateLocationFromGps(gpsPoint, fallbackAddress)
                        }
                    }
                }
            }
            
            // Request continuous location updates
            @Suppress("MissingPermission")
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()
            
            client.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        }.addOnFailureListener { e ->
            android.util.Log.e("DriverEarlyReturnConfirmationScreen", "Error getting location: ${e.message}", e)
        }
    }
    
    // Handle GPS tracking mode changes
    LaunchedEffect(uiState?.cameraMode) {
        val currentMode = uiState?.cameraMode
        if (currentMode == CameraMode.FOLLOW_GPS) {
            startGpsTracking(fusedLocationClient, locationCallback)
        } else {
            // Stop GPS updates when not in FOLLOW_GPS mode
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    
    // Cleanup GPS updates on dispose
    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            android.util.Log.d("DriverEarlyReturnConfirmationScreen", "🛑 GPS tracking stopped")
        }
    }
    
    // Handle search query changes with debounce
    LaunchedEffect(uiState?.searchQuery) {
        val query = uiState?.searchQuery ?: ""
        if (query.isNotBlank() && query.length >= 3) {
            // Search is handled by ViewModel
            // This effect ensures manual input mode is set
            if (uiState?.inputSource != LocationInputSource.MANUAL) {
                // ViewModel should handle this, but we ensure it here
            }
        }
    }
    
    // Determine which panel to show (only one at a time)
    val showSearchPanel = uiState?.searchSuggestions?.isNotEmpty() == true
    val showFeedbackPanel = uiState?.selectedLocation != null && !showSearchPanel
    
    // Handle search suggestion selection
    fun onSuggestionSelected(address: Address) {
        viewModel.selectLocationFromSuggestion(address)
        
        // Center map on selected location
        val location = GeoPoint(address.latitude, address.longitude)
        mapViewRef?.post {
            mapViewRef?.controller?.setCenter(location)
            mapViewRef?.controller?.setZoom(15.0)
            mapViewRef?.invalidate()
        }
    }
    
    // Handle map tap
    fun onMapTap(location: GeoPoint) {
        scope.launch(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val fullAddress = addresses?.firstOrNull()?.let { addr ->
                    val addressLines = mutableListOf<String>()
                    for (i in 0 until addr.maxAddressLineIndex) {
                        addr.getAddressLine(i)?.let { addressLines.add(it) }
                    }
                    if (addressLines.isEmpty()) {
                        addr.getAddressLine(0) ?: "${location.latitude}, ${location.longitude}"
                    } else {
                        addressLines.joinToString(", ")
                    }
                } ?: "${location.latitude}, ${location.longitude}"
                
                withContext(Dispatchers.Main) {
                    viewModel.updateLocationFromMapTap(location, fullAddress)
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverEarlyReturnConfirmationScreen", "Error geocoding map tap: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val fallbackAddress = "${location.latitude}, ${location.longitude}"
                    viewModel.updateLocationFromMapTap(location, fallbackAddress)
                }
            }
        }
    }
    
    // Handle confirm button - lambda
    val onConfirmClick: () -> Unit = {
        if (uiState?.canConfirm() == true) {
            viewModel.confirmReturnLocation()
            onLocationConfirmed()
        }
    }
    
    // Handle GPS button click - lambda
    val onGpsButtonClick: () -> Unit = {
        viewModel.startGpsTracking()
    }
    
    // Handle search query change - lambda
    val onSearchQueryChange: (String) -> Unit = { query ->
        viewModel.updateSearchQuery(query)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konfirmasi Pengembalian Lebih Awal") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GPS Tracking Button (Crosshair)
                FloatingActionButton(
                    onClick = onGpsButtonClick,
                    modifier = Modifier.size(56.dp),
                    containerColor = if (uiState?.cameraMode == CameraMode.FOLLOW_GPS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Gunakan Lokasi GPS",
                        tint = if (uiState?.cameraMode == CameraMode.FOLLOW_GPS) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                // Confirm Button (only visible when location is valid)
                if (uiState?.canConfirm() == true) {
                    FloatingActionButton(
                        onClick = onConfirmClick,
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Konfirmasi Lokasi",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map as background layer
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = "Jalan.in/1.0"
                    
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        minZoomLevel = 5.0
                        maxZoomLevel = 20.0
                        
                        // Set initial center and zoom (city-level, not too close)
                        val initialLocation = uiState?.selectedLocation ?: run {
                            rental?.let {
                                if (it.deliveryLat != 0.0 && it.deliveryLon != 0.0) {
                                    GeoPoint(it.deliveryLat, it.deliveryLon)
                                } else {
                                    GeoPoint(-6.2088, 106.8456) // Jakarta default
                                }
                            } ?: GeoPoint(-6.2088, 106.8456)
                        }
                        
                        controller.setZoom(10.0) // City-level zoom
                        controller.setCenter(initialLocation)
                    }
                },
                update = { mapView ->
                    mapViewRef = mapView
                    mapView.overlays.clear()
                    
                    // Add tap listener
                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            p?.let { location ->
                                // Only allow map tap when not in FOLLOW_GPS mode
                                if (uiState?.cameraMode != CameraMode.FOLLOW_GPS) {
                                    onMapTap(location)
                                }
                            }
                            return true
                        }
                        
                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }
                    mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))
                    
                    // Add marker for selected location
                    uiState?.selectedLocation?.let { location ->
                        val marker = Marker(mapView).apply {
                            position = location
                            title = uiState?.selectedAddress?.ifEmpty { "Lokasi Pengembalian" } ?: "Lokasi Pengembalian"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(marker)
                        
                        // Center map on selected location (only if not in FOLLOW_GPS mode)
                        if (uiState?.cameraMode != CameraMode.FOLLOW_GPS) {
                            mapView.post {
                                mapView.controller.setCenter(location)
                                mapView.controller.setZoom(15.0)
                            }
                        }
                    }
                    
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Search Input Field (always visible at top)
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                OutlinedTextField(
                    value = uiState?.searchQuery ?: "",
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari lokasi...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        when {
                            uiState?.isSearching == true -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            uiState?.searchQuery?.isNotEmpty() == true -> {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            // Search Suggestions Panel (overlay on map)
            if (showSearchPanel) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp)
                        .padding(top = 80.dp), // Below search field
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(uiState?.searchSuggestions ?: emptyList()) { address ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSuggestionSelected(address) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = address.getAddressLine(0) ?: "Lokasi",
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (address.maxAddressLineIndex > 0) {
                                            Text(
                                                text = (1 until address.maxAddressLineIndex)
                                                    .mapNotNull { address.getAddressLine(it) }
                                                    .joinToString(", "),
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
            
            // Location Feedback Panel (overlay on map, bottom)
            if (showFeedbackPanel) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Lokasi Pengembalian",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        
                        Text(
                            text = uiState?.selectedAddress ?: "",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        uiState?.selectedLocation?.let { location ->
                            Text(
                                text = "${location.latitude}, ${location.longitude}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

