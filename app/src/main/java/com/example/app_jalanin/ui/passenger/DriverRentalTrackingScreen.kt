package com.example.app_jalanin.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRental
import com.example.app_jalanin.utils.getRouteInfo
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.NumberFormat
import java.util.Locale

/**
 * Data class for driver rental tracking
 */
data class DriverRentalTrackingData(
    val rentalId: String,
    val driverEmail: String,
    val driverName: String?,
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLon: Double,
    val destinationAddress: String?,
    val destinationLat: Double?,
    val destinationLon: Double?,
    val vehicleType: String
)

/**
 * Screen to track driver position and route to pickup location
 * Shows driver's real-time position, route from driver to pickup, and ETA
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRentalTrackingScreen(
    rentalId: String,
    driverEmail: String,
    driverName: String?,
    pickupAddress: String,
    pickupLat: Double,
    pickupLon: Double,
    destinationAddress: String?,
    destinationLat: Double?,
    destinationLon: Double?,
    vehicleType: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    
    // Driver location state (mock for now - should come from Firestore real-time listener)
    var driverLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var driverTransportMode by remember { mutableStateOf("VEHICLE") } // VEHICLE, WALKING, PUBLIC_TRANSPORT
    
    // Route from driver to pickup
    var driverRouteGeometry by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var driverRouteDistance by remember { mutableStateOf<Double?>(null) }
    var driverRouteDuration by remember { mutableStateOf<Double?>(null) }
    var isCalculatingDriverRoute by remember { mutableStateOf(false) }
    
    // Map reference
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Load driver rental
    var driverRental by remember { mutableStateOf<DriverRental?>(null) }
    
    LaunchedEffect(rentalId) {
        scope.launch {
            try {
                val rental = withContext(Dispatchers.IO) {
                    database.driverRentalDao().getRentalById(rentalId)
                }
                driverRental = rental
                android.util.Log.d("DriverRentalTracking", "✅ Loaded driver rental: $rentalId")
            } catch (e: Exception) {
                android.util.Log.e("DriverRentalTracking", "❌ Error loading rental: ${e.message}", e)
            }
        }
    }
    
    // Mock driver location (in production, this should come from Firestore real-time listener)
    // For now, use a location slightly away from pickup
    LaunchedEffect(pickupLat, pickupLon) {
        if (pickupLat != 0.0 && pickupLon != 0.0) {
            // Mock driver location: 2km away from pickup
            val mockDriverLat = pickupLat + 0.018 // ~2km
            val mockDriverLon = pickupLon + 0.018
            driverLocation = GeoPoint(mockDriverLat, mockDriverLon)
            android.util.Log.d("DriverRentalTracking", "📍 Mock driver location: $mockDriverLat, $mockDriverLon")
        }
    }
    
    // Calculate route from driver to pickup
    LaunchedEffect(driverLocation, pickupLat, pickupLon, driverTransportMode) {
        if (driverLocation != null && pickupLat != 0.0 && pickupLon != 0.0) {
            isCalculatingDriverRoute = true
            scope.launch {
                try {
                    val routeInfo = getRouteInfo(
                        driverLocation!!,
                        GeoPoint(pickupLat, pickupLon)
                    )
                    driverRouteGeometry = routeInfo.geometry
                    driverRouteDistance = routeInfo.distance
                    
                    // Calculate ETA based on transport mode
                    val averageSpeed = when (driverTransportMode) {
                        "VEHICLE" -> 40.0 // km/h
                        "PUBLIC_TRANSPORT" -> 25.0 // km/h
                        "WALKING" -> 5.0 // km/h
                        else -> 40.0
                    }
                    driverRouteDuration = if (routeInfo.distance > 0) {
                        (routeInfo.distance / averageSpeed) * 3600 // Convert to seconds
                    } else {
                        routeInfo.duration
                    }
                    
                    android.util.Log.d("DriverRentalTracking", "✅ Driver route calculated: ${routeInfo.distance} km, ${driverRouteDuration} s")
                } catch (e: Exception) {
                    android.util.Log.e("DriverRentalTracking", "❌ Error calculating driver route: ${e.message}", e)
                } finally {
                    isCalculatingDriverRoute = false
                }
            }
        }
    }
    
    // Initialize osmdroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking Driver") },
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
            // Driver Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = driverName ?: driverEmail,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Transport Mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            when (driverTransportMode) {
                                "VEHICLE" -> Icons.Default.DirectionsCar
                                "PUBLIC_TRANSPORT" -> Icons.Default.DirectionsBus
                                "WALKING" -> Icons.Default.DirectionsWalk
                                else -> Icons.Default.DirectionsCar
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when (driverTransportMode) {
                                "VEHICLE" -> "Menggunakan Kendaraan Pribadi"
                                "PUBLIC_TRANSPORT" -> "Menggunakan Transportasi Umum"
                                "WALKING" -> "Berjalan Kaki"
                                else -> "Menggunakan Kendaraan"
                            },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Map View
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            controller.setZoom(13.0)
                            mapViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        mapView.overlays.clear()
                        
                        // Add pickup marker (Blue/Person icon)
                        val pickupMarker = Marker(mapView).apply {
                            position = GeoPoint(pickupLat, pickupLon)
                            title = "Lokasi Penjemputan"
                            snippet = pickupAddress
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                                setTint(android.graphics.Color.parseColor("#2196F3")) // Blue
                            }
                        }
                        mapView.overlays.add(pickupMarker)
                        
                        // Add driver marker (Helmet icon - Orange)
                        driverLocation?.let { driverLoc ->
                            val driverMarker = Marker(mapView).apply {
                                position = driverLoc
                                title = "Posisi Driver"
                                snippet = driverName ?: "Driver"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                // Use a different icon for driver (helmet/head icon)
                                icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                                    setTint(android.graphics.Color.parseColor("#FF9800")) // Orange
                                }
                            }
                            mapView.overlays.add(driverMarker)
                        }
                        
                        // Add destination marker (if set) - Red/Flag icon
                        destinationLat?.let { lat ->
                            destinationLon?.let { lon ->
                                val destMarker = Marker(mapView).apply {
                                    position = GeoPoint(lat, lon)
                                    title = "Lokasi Tujuan"
                                    snippet = destinationAddress ?: "Tujuan"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    icon = context.getDrawable(android.R.drawable.ic_dialog_map)?.apply {
                                        setTint(android.graphics.Color.parseColor("#F44336")) // Red
                                    }
                                }
                                mapView.overlays.add(destMarker)
                            }
                        }
                        
                        // Add driver route polyline (Orange)
                        driverRouteGeometry?.let { geometry ->
                            if (geometry.isNotEmpty()) {
                                val driverRouteLine = Polyline().apply {
                                    setPoints(geometry)
                                    outlinePaint.color = android.graphics.Color.parseColor("#FF9800") // Orange
                                    outlinePaint.strokeWidth = 12f
                                }
                                mapView.overlays.add(driverRouteLine)
                            }
                        }
                        
                        // Update map center to show both driver and pickup
                        val pointsToShow = mutableListOf<GeoPoint>()
                        driverLocation?.let { pointsToShow.add(it) }
                        pointsToShow.add(GeoPoint(pickupLat, pickupLon))
                        destinationLat?.let { lat ->
                            destinationLon?.let { lon ->
                                pointsToShow.add(GeoPoint(lat, lon))
                            }
                        }
                        
                        if (pointsToShow.size > 1) {
                            val boundingBox = BoundingBox.fromGeoPoints(pointsToShow)
                            mapView.post {
                                mapView.zoomToBoundingBox(boundingBox, true, 100)
                                mapView.invalidate()
                            }
                        } else if (pointsToShow.size == 1) {
                            mapView.post {
                                mapView.controller.setCenter(pointsToShow.first())
                                mapView.controller.setZoom(15.0)
                                mapView.invalidate()
                            }
                        }
                        mapView.invalidate()
                    }
                )
            }
            
            // Route Info Card
            if (driverRouteDistance != null && driverRouteDuration != null && !isCalculatingDriverRoute) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Estimasi Kedatangan Driver",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Jarak ke Lokasi Penjemputan:", fontSize = 14.sp)
                            Text(
                                text = "%.2f km".format(driverRouteDistance),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Estimasi Waktu Tiba (ETA):", fontSize = 14.sp)
                            Text(
                                text = formatDuration(driverRouteDuration!!),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Loading indicator
            if (isCalculatingDriverRoute) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return when {
        hours > 0 -> "${hours} jam ${minutes} menit"
        minutes > 0 -> "${minutes} menit"
        else -> "Kurang dari 1 menit"
    }
}

