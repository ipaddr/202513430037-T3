package com.example.app_jalanin.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.model.VehicleAnimationState
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
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
import com.example.app_jalanin.utils.getRouteInfo

/**
 * Screen for renter to return vehicle after early return request is CONFIRMED
 * Features:
 * - Auto-start GPS tracking (no crosshair button)
 * - Blue pin (Person icon) for renter location
 * - Red pin (Finish icon) for return location
 * - Automatic route calculation with BLUE polyline
 * - Vehicle animation along route after button press
 * - Split route: traveled (darker blue) vs remaining (lighter blue)
 * - Camera follows vehicle smoothly
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarlyReturnScreen(
    rental: Rental,
    returnLocation: GeoPoint,
    returnAddress: String,
    onBackClick: () -> Unit,
    onReturnCompleted: () -> Unit,
    onChatClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val database = remember { AppDatabase.getDatabase(context) }
    
    // GPS tracking state (auto-started)
    var currentGpsLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var initialLocation by remember { mutableStateOf<GeoPoint?>(null) } // Store initial location for animation
    
    // Route state
    var routeGeometry by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var routeDistance by remember { mutableStateOf(0.0) }
    var routeDuration by remember { mutableStateOf(0.0) }
    
    // Animation state
    var animationState by remember { mutableStateOf(VehicleAnimationState()) }
    
    // UI state
    var isReturning by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var currentUserEmail by remember { mutableStateOf<String?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    
    // Location callback for continuous GPS updates
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val location = locationResult.lastLocation
                location?.let { loc ->
                    val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                    currentGpsLocation = gpsPoint
                    // Store initial location only once
                    if (initialLocation == null && !animationState.isAnimating) {
                        initialLocation = gpsPoint
                    }
                    android.util.Log.d("EarlyReturnScreen", "📍 GPS Location updated: ${loc.latitude}, ${loc.longitude}")
                }
            }
        }
    }
    
    // Request location permission (defined first)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (hasFineLocation || hasCoarseLocation) {
            // Start GPS tracking automatically after permission granted (inline logic)
            @Suppress("MissingPermission")
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { loc ->
                    val gpsPoint = GeoPoint(loc.latitude, loc.longitude)
                    currentGpsLocation = gpsPoint
                    if (initialLocation == null) {
                        initialLocation = gpsPoint
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
                currentGpsLocation = gpsPoint
                if (initialLocation == null) {
                    initialLocation = gpsPoint
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
            android.util.Log.e("EarlyReturnScreen", "Error getting location: ${e.message}", e)
        }
    }
    
    // Auto-start GPS tracking on screen entry
    LaunchedEffect(Unit) {
        // Get current user email
        val user = withContext(Dispatchers.IO) {
            com.example.app_jalanin.auth.AuthStateManager.getCurrentUser(context)
        }
        currentUserEmail = user?.email
        
        // Start GPS tracking automatically
        startGpsTracking(fusedLocationClient, locationCallback)
    }
    
    // Cleanup GPS updates on dispose
    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            android.util.Log.d("EarlyReturnScreen", "🛑 GPS tracking stopped")
        }
    }
    
    // Calculate route automatically when GPS location is available
    LaunchedEffect(currentGpsLocation, returnLocation) {
        if (currentGpsLocation != null && !animationState.isAnimating) {
            try {
                android.util.Log.d("EarlyReturnScreen", "🛣️ Calculating route from GPS to return location...")
                val routeInfo = getRouteInfo(currentGpsLocation!!, returnLocation)
                routeGeometry = routeInfo.geometry
                routeDistance = routeInfo.distance
                routeDuration = routeInfo.duration
                android.util.Log.d("EarlyReturnScreen", "✅ Route calculated: ${routeInfo.distance} km with ${routeInfo.geometry?.size ?: 0} points")
            } catch (e: Exception) {
                android.util.Log.e("EarlyReturnScreen", "Error calculating route: ${e.message}", e)
                routeGeometry = null
            }
        }
    }
    
    // Start vehicle animation
    fun startVehicleAnimation() {
        if (routeGeometry == null || initialLocation == null) {
            android.util.Log.e("EarlyReturnScreen", "Cannot start animation: missing route or initial location")
            return
        }
        
        isReturning = true
        animationState = VehicleAnimationState(
            isAnimating = true,
            initialLocation = initialLocation,
            totalDistance = routeDistance,
            remainingDistance = routeDistance
        )
        
        // Update rental status
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                
                // Create or get chat channel with owner
                val ownerEmail = rental.ownerEmail
                if (ownerEmail != null && currentUserEmail != null) {
                    try {
                        val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                            db,
                            currentUserEmail!!,
                            ownerEmail,
                            rental.id, // rentalId
                            rental.status // orderStatus
                        )
                        android.util.Log.d("EarlyReturnScreen", "✅ Chat channel created/retrieved: ${channel.id}")
                    } catch (e: Exception) {
                        android.util.Log.e("EarlyReturnScreen", "Error creating chat channel: ${e.message}", e)
                    }
                }
                
                // Update to IN_PROGRESS
                val updatedRental = rental.copy(
                    earlyReturnRequested = true,
                    earlyReturnStatus = "IN_PROGRESS",
                    earlyReturnRequestedAt = rental.earlyReturnRequestedAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    synced = false
                )
                db.rentalDao().update(updatedRental)
                
                // Sync to Firestore
                com.example.app_jalanin.data.remote.FirestoreRentalSyncManager.syncSingleRental(
                    context,
                    rental.id
                )
            } catch (e: Exception) {
                android.util.Log.e("EarlyReturnScreen", "Error updating rental: ${e.message}", e)
            }
        }
        
        // Animate vehicle along route
        scope.launch {
            val routePoints = routeGeometry ?: return@launch
            val totalSteps = 30 // 30 seconds animation
            val totalPoints = routePoints.size
            
            for (step in 0..totalSteps) {
                val progress = step.toFloat() / totalSteps
                
                // Calculate vehicle position along route
                val exactPosition = progress * (totalPoints - 1)
                val segmentIndex = exactPosition.toInt().coerceIn(0, totalPoints - 2)
                val segmentProgress = exactPosition - segmentIndex
                
                // Interpolate between two route points
                val start = routePoints[segmentIndex]
                val end = routePoints[segmentIndex + 1]
                
                val lat = start.latitude + (end.latitude - start.latitude) * segmentProgress
                val lon = start.longitude + (end.longitude - start.longitude) * segmentProgress
                
                val vehiclePosition = GeoPoint(lat, lon)
                
                // Split route: traveled vs remaining
                val traveledPoints = routePoints.take(segmentIndex + 1) + vehiclePosition
                val remainingPoints = listOf(vehiclePosition) + routePoints.drop(segmentIndex + 1)
                
                // Update animation state
                animationState = animationState.copy(
                    vehiclePosition = vehiclePosition,
                    progress = progress,
                    traveledRoutePoints = traveledPoints,
                    remainingRoutePoints = remainingPoints,
                    remainingDistance = routeDistance * (1 - progress),
                    estimatedTimeRemaining = totalSteps - step
                )
                
                // Smoothly follow vehicle with camera
                mapViewRef?.post {
                    mapViewRef?.controller?.setCenter(vehiclePosition)
                    mapViewRef?.controller?.setZoom(15.0) // Zoom in during animation
                    mapViewRef?.invalidate()
                }
                
                if (step < totalSteps) {
                    delay(1000) // 1 second per step
                }
            }
            
            // Animation completed
            animationState = animationState.copy(
                isAnimating = false,
                remainingDistance = 0.0,
                estimatedTimeRemaining = 0
            )
            isReturning = false
            onReturnCompleted()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kembalikan Kendaraan") },
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
                .background(Color.White)
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
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Lokasi Pengembalian",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = returnAddress,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (animationState.isAnimating) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Estimasi Waktu: ${animationState.estimatedTimeRemaining} detik",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Jarak Tersisa: ${String.format("%.2f", animationState.remainingDistance)} km",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                            Configuration.getInstance().userAgentValue = "Jalan.in/1.0"
                            
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                minZoomLevel = 5.0
                                maxZoomLevel = 20.0
                                controller.setZoom(12.0)
                                controller.setCenter(returnLocation)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mapView ->
                            mapViewRef = mapView
                            mapView.overlays.clear()
                            
                            val pointsToShow = mutableListOf<GeoPoint>()
                            
                            // Pin 1: Renter's current location (BLUE, Person icon)
                            // Show initial location if animating, otherwise current GPS location
                            val renterLocation = if (animationState.isAnimating) {
                                animationState.initialLocation
                            } else {
                                currentGpsLocation
                            }
                            
                            renterLocation?.let { location ->
                                val renterMarker = Marker(mapView).apply {
                                    position = location
                                    title = "Lokasi Anda"
                                    snippet = "Lokasi terkini berdasarkan GPS"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    // Use Person icon with blue color
                                    icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                                        setTint(android.graphics.Color.BLUE)
                                    }
                                }
                                mapView.overlays.add(renterMarker)
                                pointsToShow.add(location)
                            }
                            
                            // Pin 2: Return location (RED, Finish icon)
                            val returnMarker = Marker(mapView).apply {
                                position = returnLocation
                                title = "Lokasi Pengembalian"
                                snippet = returnAddress
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                // Use Finish/Flag icon with red color
                                icon = context.getDrawable(android.R.drawable.ic_dialog_map)?.apply {
                                    setTint(android.graphics.Color.RED)
                                }
                            }
                            mapView.overlays.add(returnMarker)
                            pointsToShow.add(returnLocation)
                            
                            // Pin 3: Vehicle position (only during animation)
                            if (animationState.isAnimating && animationState.vehiclePosition != null) {
                                val vehicleMarker = Marker(mapView).apply {
                                    position = animationState.vehiclePosition!!
                                    title = "Posisi Kendaraan"
                                    snippet = "Sedang dalam perjalanan"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    // Use car icon
                                    icon = context.getDrawable(android.R.drawable.ic_menu_directions)?.apply {
                                        setTint(android.graphics.Color.parseColor("#4CAF50"))
                                    }
                                }
                                mapView.overlays.add(vehicleMarker)
                                pointsToShow.add(animationState.vehiclePosition!!)
                            }
                            
                            // Route visualization
                            if (routeGeometry != null && routeGeometry!!.isNotEmpty()) {
                                if (animationState.isAnimating) {
                                    // Split route: traveled (darker blue) vs remaining (lighter blue)
                                    if (animationState.traveledRoutePoints.isNotEmpty()) {
                                        val traveledRoute = Polyline().apply {
                                            setPoints(animationState.traveledRoutePoints)
                                            outlinePaint.color = android.graphics.Color.parseColor("#1976D2") // Darker blue
                                            outlinePaint.strokeWidth = 12f
                                        }
                                        mapView.overlays.add(traveledRoute)
                                    }
                                    
                                    if (animationState.remainingRoutePoints.size > 1) {
                                        val remainingRoute = Polyline().apply {
                                            setPoints(animationState.remainingRoutePoints)
                                            outlinePaint.color = android.graphics.Color.parseColor("#90CAF9") // Lighter blue
                                            outlinePaint.strokeWidth = 10f
                                        }
                                        mapView.overlays.add(remainingRoute)
                                    }
                                } else {
                                    // Full route (BLUE) before animation starts
                                    val routeLine = Polyline().apply {
                                        setPoints(routeGeometry!!)
                                        outlinePaint.color = android.graphics.Color.parseColor("#2196F3") // Blue
                                        outlinePaint.strokeWidth = 10f
                                    }
                                    mapView.overlays.add(routeLine)
                                }
                            } else if (currentGpsLocation != null) {
                                // Fallback: direct line if no route geometry
                                val directLine = Polyline().apply {
                                    setPoints(listOf(currentGpsLocation!!, returnLocation))
                                    outlinePaint.color = android.graphics.Color.parseColor("#2196F3")
                                    outlinePaint.strokeWidth = 8f
                                }
                                mapView.overlays.add(directLine)
                            }
                            
                            // Zoom to show all points
                            val pointsForBoundingBox = mutableListOf<GeoPoint>().apply {
                                addAll(pointsToShow)
                                routeGeometry?.let { geometry ->
                                    if (geometry.isNotEmpty()) {
                                        addAll(geometry)
                                    }
                                }
                            }
                            
                            if (pointsForBoundingBox.size >= 2 && !animationState.isAnimating) {
                                val boundingBox = BoundingBox.fromGeoPoints(pointsForBoundingBox)
                                mapView.post {
                                    mapView.zoomToBoundingBox(boundingBox, true, 200)
                                }
                            } else if (animationState.isAnimating && animationState.vehiclePosition != null) {
                                // Camera follows vehicle during animation (handled in animation loop)
                            } else if (pointsToShow.isNotEmpty()) {
                                mapView.post {
                                    mapView.controller.setCenter(pointsToShow.first())
                                    mapView.controller.setZoom(12.0)
                                }
                            }
                            
                            mapView.invalidate()
                        }
                    )
                }
            }
            
            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Chat Button
                if (rental.ownerEmail != null && currentUserEmail != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                                        database,
                                        currentUserEmail!!,
                                        rental.ownerEmail!!,
                                        rental.id, // rentalId
                                        rental.status // orderStatus
                                    )
                                    onChatClick(channel.id)
                                } catch (e: Exception) {
                                    android.util.Log.e("EarlyReturnScreen", "Error opening chat: ${e.message}", e)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chat dengan Owner", fontSize = 16.sp)
                    }
                }
                
                // Return Button - Start vehicle animation
                Button(
                    onClick = {
                        if (!animationState.isAnimating && !isReturning) {
                            showConfirmDialog = true
                        }
                    },
                    enabled = !animationState.isAnimating && !isReturning && currentGpsLocation != null && routeGeometry != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (animationState.isAnimating) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = when {
                            animationState.isAnimating -> "Mengantarkan ke Lokasi Pengembalian..."
                            else -> "Mulai Kembalikan Kendaraan"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Konfirmasi Mulai Pengembalian",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Apakah Anda yakin ingin mulai mengembalikan kendaraan ke lokasi yang ditentukan owner?")
                    Text(
                        text = "Lokasi pengembalian:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = returnAddress,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "⚠️ Pastikan Anda sudah siap untuk mengantarkan kendaraan ke lokasi tersebut.",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        startVehicleAnimation()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Ya, Mulai")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
