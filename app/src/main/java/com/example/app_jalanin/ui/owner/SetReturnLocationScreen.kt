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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
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
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.remote.FirestoreRentalSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import java.util.Locale

/**
 * Screen for owner to set return location for early return
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetReturnLocationScreen(
    rental: Rental,
    onBackClick: () -> Unit,
    onLocationSet: () -> Unit // Called when location is successfully set
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    var selectedLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedAddress by remember { mutableStateOf<String>("") }
    var isSettingLocation by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var currentGpsLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isGpsTracking by remember { mutableStateOf(false) }
    
    // Location Callback for continuous GPS updates
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val location = locationResult.lastLocation
                location?.let { loc ->
                    val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                    currentGpsLocation = gpsPoint
                    selectedLocation = gpsPoint
                    
                    android.util.Log.d("SetReturnLocationScreen", "📍 GPS Location updated: ${loc.latitude}, ${loc.longitude}")
                    
                    // Get address from GPS location
                    scope.launch(Dispatchers.IO) {
                        try {
                            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                            withContext(Dispatchers.Main) {
                                val fullAddress = addresses?.firstOrNull()?.let { addr ->
                                    // Get full address with multiple lines
                                    val addressLines = mutableListOf<String>()
                                    for (i in 0 until addr.maxAddressLineIndex) {
                                        addr.getAddressLine(i)?.let { addressLines.add(it) }
                                    }
                                    if (addressLines.isEmpty()) {
                                        addr.getAddressLine(0) ?: "Lokasi GPS"
                                    } else {
                                        addressLines.joinToString(", ")
                                    }
                                } ?: "Lokasi GPS"
                                
                                selectedAddress = fullAddress
                                searchQuery = fullAddress
                                
                                // Center map on GPS location
                                mapViewRef?.post {
                                    mapViewRef?.controller?.setCenter(gpsPoint)
                                    mapViewRef?.controller?.setZoom(10.0) // ✅ Reduced zoom for wider view
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SetReturnLocationScreen", "Error geocoding GPS: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                selectedAddress = "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                                searchQuery = selectedAddress
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Function to get current GPS location with continuous tracking
    fun getCurrentGPSLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            android.util.Log.w("SetReturnLocationScreen", "Location permission not granted")
            return
        }
        
        isGpsTracking = true
        
        // Use async callback instead of .result to avoid "Task is not yet complete" error
        @Suppress("MissingPermission")
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                location?.let { loc ->
                    val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                    currentGpsLocation = gpsPoint
                    selectedLocation = gpsPoint
                    
                    android.util.Log.d("SetReturnLocationScreen", "📍 GPS Location received: ${loc.latitude}, ${loc.longitude}")
                    
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
                            } ?: "Lokasi GPS"
                            
                            withContext(Dispatchers.Main) {
                                selectedAddress = fullAddress
                                searchQuery = fullAddress
                                
                                // Center map on GPS location and update marker
                                mapViewRef?.post {
                                    mapViewRef?.controller?.setCenter(gpsPoint)
                                    mapViewRef?.controller?.setZoom(10.0)
                                    mapViewRef?.invalidate() // Force map refresh to show marker
                                }
                                
                                android.util.Log.d("SetReturnLocationScreen", "✅ GPS location set: $fullAddress")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SetReturnLocationScreen", "Error geocoding GPS: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                selectedAddress = "Lokasi GPS: ${loc.latitude}, ${loc.longitude}"
                                searchQuery = selectedAddress
                                
                                // Still center map even if geocoding fails
                                mapViewRef?.post {
                                    mapViewRef?.controller?.setCenter(gpsPoint)
                                    mapViewRef?.controller?.setZoom(10.0)
                                    mapViewRef?.invalidate()
                                }
                            }
                        }
                    }
                    
                    // Request continuous location updates for tracking
                    @Suppress("MissingPermission")
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                        .setMinUpdateIntervalMillis(3000L)
                        .build()
                    
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                    
                    android.util.Log.d("SetReturnLocationScreen", "✅ GPS tracking started")
                } ?: run {
                    // If lastLocation is null, request current location using LocationRequest
                    android.util.Log.w("SetReturnLocationScreen", "⚠️ lastLocation is null, requesting current location...")
                    @Suppress("MissingPermission")
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                        .build()
                    
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        object : LocationCallback() {
                            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                                val currentLocation = locationResult.lastLocation
                                if (currentLocation != null) {
                                    val gpsPoint = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                                    currentGpsLocation = gpsPoint
                                    selectedLocation = gpsPoint
                                    
                                    // Get address
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val addresses = geocoder.getFromLocation(currentLocation.latitude, currentLocation.longitude, 1)
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
                                            } ?: "Lokasi GPS"
                                            
                                            withContext(Dispatchers.Main) {
                                                selectedAddress = fullAddress
                                                searchQuery = fullAddress
                                                
                                                mapViewRef?.post {
                                                    mapViewRef?.controller?.setCenter(gpsPoint)
                                                    mapViewRef?.controller?.setZoom(10.0)
                                                    mapViewRef?.invalidate()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SetReturnLocationScreen", "Error geocoding: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                selectedAddress = "Lokasi GPS: ${currentLocation.latitude}, ${currentLocation.longitude}"
                                                searchQuery = selectedAddress
                                                
                                                mapViewRef?.post {
                                                    mapViewRef?.controller?.setCenter(gpsPoint)
                                                    mapViewRef?.controller?.setZoom(10.0)
                                                    mapViewRef?.invalidate()
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Remove this one-time callback
                                    fusedLocationClient.removeLocationUpdates(this)
                                    
                                    // Start continuous tracking
                                    @Suppress("MissingPermission")
                                    val continuousRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                                        .setMinUpdateIntervalMillis(3000L)
                                        .build()
                                    
                                    fusedLocationClient.requestLocationUpdates(
                                        continuousRequest,
                                        locationCallback,
                                        Looper.getMainLooper()
                                    )
                                }
                            }
                        },
                        Looper.getMainLooper()
                    )
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("SetReturnLocationScreen", "Error getting GPS: ${e.message}", e)
                isGpsTracking = false
            }
    }
    
    // Stop location updates when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            if (isGpsTracking) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                isGpsTracking = false
                android.util.Log.d("SetReturnLocationScreen", "🛑 GPS tracking stopped")
            }
        }
    }
    
    // Location permission launcher (defined after getCurrentGPSLocation)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Permission granted, get GPS location
                getCurrentGPSLocation()
            }
        }
    }
    
    // Function to search location
    suspend fun searchLocation(query: String): List<Address> {
        return withContext(Dispatchers.IO) {
            try {
                val searchQueryWithCountry = if (query.contains("Indonesia", ignoreCase = true)) {
                    query
                } else {
                    "$query, Indonesia"
                }
                android.util.Log.d("SetReturnLocationScreen", "🔍 Searching: $searchQueryWithCountry")
                val addresses = geocoder.getFromLocationName(searchQueryWithCountry, 10)
                val results = addresses ?: emptyList()
                
                // If no results with "Indonesia", try without it
                if (results.isEmpty() && searchQueryWithCountry != query) {
                    android.util.Log.d("SetReturnLocationScreen", "🔄 Retrying without Indonesia...")
                    geocoder.getFromLocationName(query, 10) ?: emptyList()
                } else {
                    results
                }
            } catch (e: Exception) {
                android.util.Log.e("SetReturnLocationScreen", "❌ Search error: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    // Initialize with existing return location if available
    LaunchedEffect(rental.id) {
        if (rental.returnLocationLat != null && rental.returnLocationLon != null) {
            selectedLocation = GeoPoint(rental.returnLocationLat!!, rental.returnLocationLon!!)
            selectedAddress = rental.returnAddress ?: ""
            searchQuery = rental.returnAddress ?: ""
        }
    }
    
    // Auto-load GPS location on first launch (if permission granted)
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission && selectedLocation == null) {
            kotlinx.coroutines.delay(500) // Small delay
            getCurrentGPSLocation()
        }
    }
    
    // Search location suggestions with debouncing
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            kotlinx.coroutines.delay(500) // Debounce
            if (searchQuery.length >= 3) {
                isSearching = true
                scope.launch {
                    try {
                        val results = searchLocation(searchQuery)
                        searchSuggestions = results
                        android.util.Log.d("SetReturnLocationScreen", "✅ Found ${results.size} suggestions")
                    } catch (e: Exception) {
                        android.util.Log.e("SetReturnLocationScreen", "Search error: ${e.message}", e)
                        searchSuggestions = emptyList()
                    } finally {
                        isSearching = false
                    }
                }
            }
        } else {
            searchSuggestions = emptyList()
            isSearching = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tentukan Lokasi Pengembalian") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Kendaraan: ${rental.vehicleName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Penumpang: ${rental.userEmail.split("@").firstOrNull() ?: rental.userEmail}",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Pilih lokasi pengembalian dengan GPS, search, atau ketuk peta",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // ✅ Search Location Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Cari lokasi...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        searchQuery = ""
                                        searchSuggestions = emptyList()
                                    }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        // ✅ GPS Button
                        IconButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasPermission) {
                                    getCurrentGPSLocation()
                                } else {
                                    requestPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "GPS",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
            
            // Map - Made wider with proper height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .fillMaxHeight() // ✅ Ensure full height
                    .padding(horizontal = 8.dp, vertical = 8.dp) // ✅ Reduced horizontal padding for wider map
            ) {
                // ✅ Search Suggestions Dropdown (as overlay on top of map)
                if (searchSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 0.dp, vertical = 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            items(searchSuggestions) { address ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val geoPoint = GeoPoint(
                                                address.latitude,
                                                address.longitude
                                            )
                                            selectedLocation = geoPoint
                                            selectedAddress = address.getAddressLine(0) ?: "Lokasi terpilih"
                                            searchQuery = selectedAddress
                                            searchSuggestions = emptyList()
                                            
                                            // Center map on selected location
                                            mapViewRef?.post {
                                                mapViewRef?.controller?.setCenter(geoPoint)
                                                mapViewRef?.controller?.setZoom(10.0) // ✅ Reduced zoom
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
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = address.getAddressLine(0) ?: "Lokasi",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (address.getAddressLine(1) != null) {
                                            Text(
                                                text = address.getAddressLine(1) ?: "",
                                                fontSize = 12.sp,
                                                color = Color(0xFF666666)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                AndroidView(
                    factory = { ctx ->
                        // ✅ Configure osmdroid properly
                        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                        Configuration.getInstance().userAgentValue = "Jalan.in/1.0"
                        
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            minZoomLevel = 5.0
                            maxZoomLevel = 20.0
                            controller.setZoom(10.0) // ✅ Reduced zoom level for wider view
                            
                            // ✅ Set initial center to rental delivery location or default location
                            val initialLocation = if (rental.deliveryLat != 0.0 && rental.deliveryLon != 0.0) {
                                GeoPoint(rental.deliveryLat, rental.deliveryLon)
                            } else {
                                GeoPoint(-6.2088, 106.8456) // Default: Jakarta
                            }
                            controller.setCenter(initialLocation)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        // ✅ Store reference for GPS tracking
                        mapViewRef = mapView
                        
                        mapView.overlays.clear()
                        
                        // Add tap listener
                        val mapEventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let { point ->
                                    selectedLocation = point
                                    // Geocode to get address with full details
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                                            withContext(Dispatchers.Main) {
                                                val fullAddress = addresses?.firstOrNull()?.let { addr ->
                                                    // Get full address with multiple lines for better feedback
                                                    val addressLines = mutableListOf<String>()
                                                    for (i in 0 until addr.maxAddressLineIndex) {
                                                        addr.getAddressLine(i)?.let { addressLines.add(it) }
                                                    }
                                                    if (addressLines.isEmpty()) {
                                                        addr.getAddressLine(0) ?: "Lokasi terpilih"
                                                    } else {
                                                        addressLines.joinToString(", ")
                                                    }
                                                } ?: "Lokasi terpilih"
                                                
                                                selectedAddress = fullAddress
                                                searchQuery = fullAddress
                                                
                                                android.util.Log.d("SetReturnLocationScreen", "📍 Selected location: $fullAddress")
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("SetReturnLocationScreen", "Error geocoding: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                selectedAddress = "Lokasi terpilih: ${point.latitude}, ${point.longitude}"
                                                searchQuery = selectedAddress
                                            }
                                        }
                                    }
                                }
                                return true
                            }
                            
                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        }
                        
                        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
                        mapView.overlays.add(mapEventsOverlay)
                        
                        // Show selected location marker (always show if location is set)
                        selectedLocation?.let { location ->
                            val marker = Marker(mapView).apply {
                                position = location
                                title = selectedAddress.ifEmpty { "Lokasi Pengembalian" }
                                snippet = "Ketuk untuk memilih lokasi ini"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(marker)
                            mapView.post {
                                mapView.controller.setCenter(location)
                                mapView.controller.setZoom(10.0) // ✅ Reduced zoom level for wider view
                            }
                        } ?: run {
                            // If no location selected, ensure map is centered
                            val defaultLocation = if (rental.deliveryLat != 0.0 && rental.deliveryLon != 0.0) {
                                GeoPoint(rental.deliveryLat, rental.deliveryLon)
                            } else {
                                GeoPoint(-6.2088, 106.8456) // Default: Jakarta
                            }
                            mapView.post {
                                mapView.controller.setCenter(defaultLocation)
                                mapView.controller.setZoom(10.0) // ✅ Reduced zoom level for wider view
                            }
                        }
                        
                        mapView.invalidate()
                    }
                )
                }
                
                // ✅ GPS Tracking Button (Floating Action Button)
                FloatingActionButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (hasPermission) {
                            getCurrentGPSLocation()
                        } else {
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Track GPS Location",
                        tint = Color.White
                    )
                }
            }
            
            // Selected location info
            if (selectedLocation != null && selectedAddress.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lokasi Pengembalian",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = selectedAddress.ifEmpty { "Lokasi terpilih" },
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32),
                                lineHeight = 18.sp // ✅ Better line spacing for multi-line addresses
                            )
                            // ✅ Show coordinates for better feedback
                            selectedLocation?.let { loc ->
                                Text(
                                    text = "Koordinat: ${String.format("%.6f", loc.latitude)}, ${String.format("%.6f", loc.longitude)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Confirm Button
            Button(
                onClick = {
                    if (selectedLocation != null && selectedAddress.isNotEmpty()) {
                        isSettingLocation = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val updatedRental = rental.copy(
                                    returnLocationLat = selectedLocation!!.latitude,
                                    returnLocationLon = selectedLocation!!.longitude,
                                    returnAddress = selectedAddress,
                                    earlyReturnStatus = "CONFIRMED", // ✅ Owner confirmed
                                    updatedAt = System.currentTimeMillis(),
                                    synced = false
                                )
                                db.rentalDao().update(updatedRental)
                                
                                // Sync to Firestore
                                FirestoreRentalSyncManager.syncSingleRental(context, rental.id)
                                
                                withContext(Dispatchers.Main) {
                                    isSettingLocation = false
                                    onLocationSet()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SetReturnLocationScreen", "Error setting location: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    isSettingLocation = false
                                }
                            }
                        }
                    }
                },
                enabled = selectedLocation != null && selectedAddress.isNotEmpty() && !isSettingLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSettingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isSettingLocation) "Menyimpan..." else "Konfirmasi Lokasi Pengembalian",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

