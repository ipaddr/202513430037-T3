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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult as GmsLocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import java.util.Locale

/**
 * Data class untuk hasil lokasi yang dipilih
 */
data class LocationResult(
    val geoPoint: GeoPoint,
    val address: String
)

/**
 * Dialog untuk memilih lokasi kendaraan dengan 3 metode:
 * 1. GPS - ambil lokasi saat ini
 * 2. Search - input teks dengan autocomplete
 * 3. Tap to place - ketuk peta untuk posisikan pin
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleLocationPickerDialog(
    initialLocation: GeoPoint? = null,
    onLocationSelected: (LocationResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // State untuk lokasi
    var selectedLocation by remember { 
        mutableStateOf(initialLocation ?: GeoPoint(-0.9471, 100.4172)) // Default Padang
    }
    var addressText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingAddress by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Request location permission
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Get current GPS location function (will be assigned after launcher is created)
    var getCurrentGPSLocation: () -> Unit by remember { mutableStateOf({}) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        // Auto-load GPS location after permission is granted
        if (granted) {
            getCurrentGPSLocation()
        }
    }

    // Define getCurrentGPSLocation function (after launcher is created)
    getCurrentGPSLocation = {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            isLoadingAddress = true
            android.util.Log.d("VehicleLocationPicker", "🔄 Requesting GPS location...")
            
            @Suppress("MissingPermission")
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        selectedLocation = geoPoint
                        android.util.Log.d("VehicleLocationPicker", "✅ GPS Location received: ${location.latitude}, ${location.longitude}")
                        scope.launch {
                            try {
                                val address = getAddressFromGeoPoint(geocoder, geoPoint)
                                addressText = address ?: "Lokasi GPS saat ini"
                                android.util.Log.d("VehicleLocationPicker", "✅ Address resolved: $addressText")
                            } catch (e: Exception) {
                                android.util.Log.e("VehicleLocationPicker", "❌ Geocoding error: ${e.message}", e)
                                addressText = "Lokasi GPS: ${location.latitude}, ${location.longitude}"
                            } finally {
                                isLoadingAddress = false
                            }
                        }
                    } else {
                        // Try to get current location using LocationRequest if lastLocation is null
                        android.util.Log.d("VehicleLocationPicker", "⚠️ lastLocation is null, requesting current location...")
                        try {
                            val locationRequest = LocationRequest.Builder(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                1000L
                            ).build()
                            
                            @Suppress("MissingPermission")
                            fusedLocationClient.requestLocationUpdates(
                                locationRequest,
                                object : LocationCallback() {
                                    override fun onLocationResult(locationResult: GmsLocationResult) {
                                        val currentLocation = locationResult.lastLocation
                                        if (currentLocation != null) {
                                            val geoPoint = GeoPoint(currentLocation.latitude, currentLocation.longitude)
                                            selectedLocation = geoPoint
                                            android.util.Log.d("VehicleLocationPicker", "✅ Current GPS Location: ${currentLocation.latitude}, ${currentLocation.longitude}")
                                            scope.launch {
                                                try {
                                                    val address = getAddressFromGeoPoint(geocoder, geoPoint)
                                                    addressText = address ?: "Lokasi GPS saat ini"
                                                } catch (e: Exception) {
                                                    addressText = "Lokasi GPS: ${currentLocation.latitude}, ${currentLocation.longitude}"
                                                } finally {
                                                    isLoadingAddress = false
                                                }
                                            }
                                            // Remove location updates after getting location
                                            fusedLocationClient.removeLocationUpdates(this)
                                        } else {
                                            // Default to Padang if still no GPS signal
                                            val defaultLocation = GeoPoint(-0.9471, 100.4172)
                                            selectedLocation = defaultLocation
                                            addressText = "Padang, Sumatera Barat"
                                            isLoadingAddress = false
                                            android.util.Log.d("VehicleLocationPicker", "⚠️ No GPS signal - Using default: Padang")
                                        }
                                    }
                                },
                                android.os.Looper.getMainLooper()
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("VehicleLocationPicker", "❌ Location request error: ${e.message}", e)
                            // Default to Padang on error
                            val defaultLocation = GeoPoint(-0.9471, 100.4172)
                            selectedLocation = defaultLocation
                            addressText = "Padang, Sumatera Barat"
                            isLoadingAddress = false
                        }
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VehicleLocationPicker", "❌ GPS Failed: ${e.message}", e)
                    // Default to Padang on failure
                    val defaultLocation = GeoPoint(-0.9471, 100.4172)
                    selectedLocation = defaultLocation
                    addressText = "Padang, Sumatera Barat"
                    isLoadingAddress = false
                }
        }
    }

    // Initialize osmdroid and auto-load GPS
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
        
        // Auto-focus search field when dialog opens
        kotlinx.coroutines.delay(100) // Small delay to ensure dialog is fully rendered
        searchFocusRequester.requestFocus()
        
        // Auto-load GPS location if permission granted
        if (hasLocationPermission) {
            kotlinx.coroutines.delay(200) // Small delay before GPS request
            getCurrentGPSLocation()
        }
    }

    // Get address from selected location
    LaunchedEffect(selectedLocation) {
        isLoadingAddress = true
        scope.launch {
            try {
                val address = getAddressFromGeoPoint(geocoder, selectedLocation)
                addressText = address ?: "Lokasi dipilih"
            } catch (e: Exception) {
                android.util.Log.e("VehicleLocationPicker", "Error getting address: ${e.message}", e)
                addressText = "Lokasi dipilih"
            } finally {
                isLoadingAddress = false
            }
        }
    }

    // Search location suggestions with debouncing
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            // Debounce search to avoid too many requests
            kotlinx.coroutines.delay(500)
            if (searchQuery.length >= 3) { // Check again after delay
            isSearching = true
            scope.launch {
                try {
                        android.util.Log.d("VehicleLocationPicker", "🔍 Searching for: $searchQuery")
                        val results = searchLocation(geocoder, searchQuery)
                        searchSuggestions = results
                        android.util.Log.d("VehicleLocationPicker", "✅ Found ${results.size} results")
                } catch (e: Exception) {
                        android.util.Log.e("VehicleLocationPicker", "❌ Search error: ${e.message}", e)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "📍 Pilih Lokasi Kendaraan",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search bar dengan autocomplete
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cari lokasi dengan keyword...") },
                    placeholder = { Text("Contoh: Jl. Sudirman, Padang") },
                    leadingIcon = { 
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Cari lokasi")
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                searchSuggestions = emptyList()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    enabled = true
                )

                // Search suggestions
                if (searchSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5)
                        )
                    ) {
                        LazyColumn {
                            items(searchSuggestions) { address ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = address.getAddressLine(0) ?: address.featureName ?: "Lokasi",
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = buildString {
                                                for (i in 0..address.maxAddressLineIndex) {
                                                    if (i > 0) append(", ")
                                                    append(address.getAddressLine(i))
                                                }
                                            },
                                            fontSize = 12.sp,
                                            color = Color(0xFF666666)
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        selectedLocation = GeoPoint(address.latitude, address.longitude)
                                        searchQuery = address.getAddressLine(0) ?: ""
                                        searchSuggestions = emptyList()
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // GPS button (Crosshair icon untuk ambil lokasi sekarang)
                    Button(
                        onClick = { getCurrentGPSLocation() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Gunakan lokasi GPS saat ini", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🎯 GPS", color = Color.White)
                    }

                    // Address display
                    Card(
                        modifier = Modifier.weight(2f),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF0F0F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoadingAddress) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = addressText.ifEmpty { "Memuat alamat..." },
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                        }
                    }
                }

                // Map view
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF0F0F0)
                    )
                ) {
                    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
                    
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                controller.setCenter(selectedLocation)
                                // Make map visible and enable interactions
                                visibility = android.view.View.VISIBLE
                                isClickable = true
                                isFocusable = true
                                
                                // Initialize overlays
                                overlays.clear()
                                
                                // Add marker for selected location
                                val marker = Marker(this).apply {
                                    position = selectedLocation
                                    title = "Lokasi Kendaraan"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(marker)
                                
                                // Add tap-to-place overlay (ketuk layar peta untuk letakkan pin)
                                val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        // Ketuk peta untuk memindahkan pin
                                        selectedLocation = p
                                        scope.launch {
                                            isLoadingAddress = true
                                            try {
                                                val address = getAddressFromGeoPoint(geocoder, p)
                                                addressText = address ?: "Lokasi dipilih"
                                            } catch (e: Exception) {
                                                addressText = "Lokasi: ${p.latitude}, ${p.longitude}"
                                            } finally {
                                                isLoadingAddress = false
                                            }
                                        }
                                        return true
                                    }

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        // Long press juga bisa untuk memindahkan pin
                                        selectedLocation = p
                                        scope.launch {
                                            isLoadingAddress = true
                                            try {
                                                val address = getAddressFromGeoPoint(geocoder, p)
                                                addressText = address ?: "Lokasi dipilih"
                                            } catch (e: Exception) {
                                                addressText = "Lokasi: ${p.latitude}, ${p.longitude}"
                                            } finally {
                                                isLoadingAddress = false
                                            }
                                        }
                                        return true
                                    }
                                })
                                overlays.add(mapEventsOverlay)
                                
                                mapViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mapView ->
                            // Update map center when location changes
                            mapView.controller.setCenter(selectedLocation)
                            
                            mapView.overlays.clear()

                            // Add marker for selected location
                            val marker = Marker(mapView).apply {
                                position = selectedLocation
                                title = "Lokasi Kendaraan"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(marker)

                            // Add tap-to-place overlay (ketuk layar peta untuk letakkan pin)
                            val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    // Ketuk peta untuk memindahkan pin
                                    selectedLocation = p
                                    scope.launch {
                                        isLoadingAddress = true
                                        try {
                                            val address = getAddressFromGeoPoint(geocoder, p)
                                            addressText = address ?: "Lokasi dipilih"
                                        } catch (e: Exception) {
                                            addressText = "Lokasi: ${p.latitude}, ${p.longitude}"
                                        } finally {
                                            isLoadingAddress = false
                                        }
                                    }
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint): Boolean {
                                    // Long press juga bisa untuk memindahkan pin
                                    selectedLocation = p
                                    scope.launch {
                                        isLoadingAddress = true
                                        try {
                                            val address = getAddressFromGeoPoint(geocoder, p)
                                            addressText = address ?: "Lokasi dipilih"
                                        } catch (e: Exception) {
                                            addressText = "Lokasi: ${p.latitude}, ${p.longitude}"
                                        } finally {
                                            isLoadingAddress = false
                                        }
                                    }
                                    return true
                                }
                            })
                            mapView.overlays.add(mapEventsOverlay)
                        }
                    )
                }

                // Instruction text
                Text(
                    text = "💡 Pilih lokasi dengan: 1) Klik tombol GPS untuk lokasi sekarang, 2) Ketik keyword untuk cari alamat, 3) Ketuk peta untuk letakkan pin",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onLocationSelected(
                        LocationResult(
                            geoPoint = selectedLocation,
                            address = addressText
                        )
                    )
                },
                enabled = addressText.isNotEmpty() && !isLoadingAddress
            ) {
                Text("Pilih Lokasi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

/**
 * Helper function untuk search location dengan keyword (autocomplete)
 */
private suspend fun searchLocation(geocoder: Geocoder, query: String): List<Address> {
    return withContext(Dispatchers.IO) {
        try {
            // Cari dengan keyword yang diberikan, tanpa batasan wilayah untuk hasil lebih luas
            // Tambahkan Indonesia untuk hasil lebih relevan
            val searchQuery = if (query.contains("Indonesia", ignoreCase = true) || 
                                 query.contains("Indonesia", ignoreCase = true)) {
                query
            } else {
                "$query, Indonesia"
            }
            
            android.util.Log.d("VehicleLocationPicker", "🔍 Geocoding search: $searchQuery")
            val addresses = geocoder.getFromLocationName(searchQuery, 10)
            val results = addresses ?: emptyList()
            android.util.Log.d("VehicleLocationPicker", "✅ Geocoding returned ${results.size} results")
            
            // If no results with "Indonesia", try without it
            if (results.isEmpty() && searchQuery != query) {
                android.util.Log.d("VehicleLocationPicker", "🔄 Retrying without Indonesia suffix...")
                val addresses2 = geocoder.getFromLocationName(query, 10)
                return@withContext addresses2 ?: emptyList()
            }
            
            results
        } catch (e: Exception) {
            android.util.Log.e("VehicleLocationPicker", "❌ Search failed: ${e.message}", e)
            emptyList()
        }
    }
}

/**
 * Helper function untuk get address from GeoPoint
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
            android.util.Log.e("VehicleLocationPicker", "Geocoding failed: ${e.message}", e)
            null
        }
    }
}

