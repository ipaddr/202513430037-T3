package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.app_jalanin.utils.calculateDistance
import com.example.app_jalanin.utils.DurationUtils
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.Rental
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject
import androidx.compose.runtime.rememberCoroutineScope

data class TrackingData(
    val vehicle: RentalVehicle,
    val startLocation: GeoPoint,
    val deliveryLocation: GeoPoint,
    val deliveryAddress: String,
    val totalPrice: Int,
    val duration: String, // Format: "hari|jam|menit" (e.g., "0|7|30" untuk 7 jam 30 menit)
    val withDriver: Boolean,
    // ✅ FIX: Add proper duration breakdown for accurate rental time tracking
    val durationDays: Int = 0,
    val durationHours: Int = 0,
    val durationMinutes: Int = 0,
    // ✅ FIX: Add rentalId to track the rental in database
    val rentalId: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleTrackingScreen(
    trackingData: TrackingData,
    onBackClick: () -> Unit,
    onVehicleArrived: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    // Rental state for countdown
    var currentRental by remember { mutableStateOf<Rental?>(null) }
    var remainingTime by remember { mutableStateOf(0L) }
    var isOvertime by remember { mutableStateOf(false) }
    
    // Load rental if rentalId is available
    LaunchedEffect(trackingData.rentalId) {
        if (trackingData.rentalId != null) {
            scope.launch {
                try {
                    val rental = withContext(Dispatchers.IO) {
                        database.rentalDao().getRentalById(trackingData.rentalId!!)
                    }
                    currentRental = rental
                    android.util.Log.d("VehicleTracking", "✅ Loaded rental: ${rental?.id}, status: ${rental?.status}")
                } catch (e: Exception) {
                    android.util.Log.e("VehicleTracking", "Error loading rental: ${e.message}", e)
                }
            }
        }
    }
    
    // Countdown timer for active rental
    LaunchedEffect(currentRental) {
        while (currentRental != null && currentRental?.status == "ACTIVE") {
            val rental = currentRental ?: break
            val now = System.currentTimeMillis()
            val diff = rental.endDate - now

            if (diff <= 0) {
                remainingTime = Math.abs(diff)
                isOvertime = true
            } else {
                remainingTime = diff
                isOvertime = false
            }

            delay(1000) // Update every second
        }
    }

    // Initialize OSMDroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Vehicle position state (starts at vehicle start location)
    var currentVehiclePosition by remember { mutableStateOf(trackingData.startLocation) }
    var progress by remember { mutableStateOf(0f) }
    var isArrived by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(30) }

    // Route waypoints from OSRM
    var routeWaypoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var totalDistance by remember { mutableStateOf(0.0) }
    var remainingDistance by remember { mutableStateOf(0.0) }

    // Fetch route from OSRM API
    LaunchedEffect(Unit) {
        try {
            val route = fetchRouteFromOSRM(
                trackingData.startLocation,
                trackingData.deliveryLocation
            )
            routeWaypoints = route.waypoints
            totalDistance = route.distance / 1000.0 // Convert to km
            remainingDistance = totalDistance
        } catch (e: Exception) {
            // Fallback to straight line if API fails
            android.util.Log.e("VehicleTracking", "Failed to fetch route: ${e.message}")
            routeWaypoints = listOf(
                trackingData.startLocation,
                trackingData.deliveryLocation
            )
            totalDistance = calculateDistance(
                trackingData.startLocation,
                trackingData.deliveryLocation
            )
            remainingDistance = totalDistance
        }
    }

    // Animate vehicle movement along the route waypoints
    LaunchedEffect(routeWaypoints) {
        if (routeWaypoints.isNotEmpty()) {
            val totalSteps = 30 // 30 seconds total
            val totalWaypoints = routeWaypoints.size

            for (step in 0..totalSteps) {
                progress = step.toFloat() / totalSteps

                // Calculate which segment we're on
                val exactPosition = progress * (totalWaypoints - 1)
                val segmentIndex = exactPosition.toInt().coerceIn(0, totalWaypoints - 2)
                val segmentProgress = exactPosition - segmentIndex

                // Interpolate between two waypoints
                val start = routeWaypoints[segmentIndex]
                val end = routeWaypoints[segmentIndex + 1]

                val lat = start.latitude + (end.latitude - start.latitude) * segmentProgress
                val lon = start.longitude + (end.longitude - start.longitude) * segmentProgress

                currentVehiclePosition = GeoPoint(lat, lon)
                remainingSeconds = totalSteps - step
                remainingDistance = totalDistance * (1 - progress)

                if (step == totalSteps) {
                    isArrived = true
                    remainingDistance = 0.0
                } else {
                    delay(1000) // 1 second per step
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kendaraan Sedang Diantar",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF5F5F5)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ✅ Rental Countdown Card (if rental is ACTIVE)
            if (currentRental != null && currentRental?.status == "ACTIVE") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOvertime) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isOvertime) "⚠️ PERINGATAN KETERLAMBATAN" else "⏱️ Waktu Sewa Tersisa",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOvertime) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                        )

                        Text(
                            text = DurationUtils.formatTime(remainingTime),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOvertime) Color(0xFFEF5350) else Color(0xFF4CAF50)
                        )
                        
                        if (currentRental != null) {
                            LinearProgressIndicator(
                                progress = {
                                    val totalDuration = currentRental!!.endDate - currentRental!!.startDate
                                    val elapsed = System.currentTimeMillis() - currentRental!!.startDate
                                    (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = if (isOvertime) Color(0xFFEF5350) else Color(0xFF4CAF50),
                                trackColor = Color(0xFFC8E6C9)
                            )
                        }

                        if (isOvertime) {
                            Text(
                                text = "⚠️ Keterlambatan dikenakan Rp 50.000/jam",
                                fontSize = 12.sp,
                                color = Color(0xFFD32F2F),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            Text(
                                text = "⚠️ Keterlambatan dikenakan Rp 50.000/jam",
                                fontSize = 11.sp,
                                color = Color(0xFF666666),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
            
            // 1. Status Card (Top Panel)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1)
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Panel status kendaraan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF57C00)
                    )

                    if (!isArrived) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Remaining Distance
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = String.format(Locale.forLanguageTag("id-ID"), "%.2f km", remainingDistance),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333)
                                )
                                Text(
                                    text = "Jarak tersisa",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666)
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(Color(0xFFE0E0E0))
                            )

                            // Remaining Time
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${remainingSeconds}s",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333)
                                )
                                Text(
                                    text = "Est. waktu",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        // Progress bar
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color(0xFFE0E0E0),
                        )
                    } else {
                        Text(
                            text = "✅ Kendaraan Telah Tiba!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // 2. Map Panel (Middle - dalam border/kotak)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F0F0)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // OSM Map
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(14.0)

                                // Center map between start and delivery location initially
                                val midLat = (trackingData.startLocation.latitude + trackingData.deliveryLocation.latitude) / 2
                                val midLon = (trackingData.startLocation.longitude + trackingData.deliveryLocation.longitude) / 2
                                controller.setCenter(GeoPoint(midLat, midLon))

                                // Make map visible immediately
                                visibility = android.view.View.VISIBLE
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { mapView ->
                            mapView.overlays.clear()

                            // Draw the full route if available (using waypoints from OSRM)
                            if (routeWaypoints.isNotEmpty()) {
                                // Calculate which waypoint the vehicle has reached
                                val currentWaypointIndex = (progress * (routeWaypoints.size - 1)).toInt()

                                // Draw completed route (solid blue line from start to current position)
                                if (currentWaypointIndex > 0) {
                                    val completedWaypoints = routeWaypoints.subList(0, currentWaypointIndex + 1) + listOf(currentVehiclePosition)
                                    val completedRoute = Polyline().apply {
                                        setPoints(completedWaypoints)
                                        outlinePaint.color = android.graphics.Color.parseColor("#2196F3")
                                        outlinePaint.strokeWidth = 10f
                                        outlinePaint.style = android.graphics.Paint.Style.STROKE
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    }
                                    mapView.overlays.add(completedRoute)
                                }

                                // Draw remaining route (dashed blue line from current to destination)
                                if (currentWaypointIndex < routeWaypoints.size - 1) {
                                    val remainingWaypoints = listOf(currentVehiclePosition) + routeWaypoints.subList(currentWaypointIndex + 1, routeWaypoints.size)
                                    val remainingRoute = Polyline().apply {
                                        setPoints(remainingWaypoints)
                                        outlinePaint.color = android.graphics.Color.parseColor("#90CAF9")
                                        outlinePaint.strokeWidth = 8f
                                        outlinePaint.style = android.graphics.Paint.Style.STROKE
                                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                    }
                                    mapView.overlays.add(remainingRoute)
                                }
                            } else {
                                // Fallback: Draw simple route while loading
                                if (progress > 0) {
                                    val completedRoute = Polyline().apply {
                                        setPoints(listOf(trackingData.startLocation, currentVehiclePosition))
                                        outlinePaint.color = android.graphics.Color.parseColor("#2196F3")
                                        outlinePaint.strokeWidth = 10f
                                        outlinePaint.style = android.graphics.Paint.Style.STROKE
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    }
                                    mapView.overlays.add(completedRoute)
                                }

                                if (progress < 1.0) {
                                    val remainingRoute = Polyline().apply {
                                        setPoints(listOf(currentVehiclePosition, trackingData.deliveryLocation))
                                        outlinePaint.color = android.graphics.Color.parseColor("#90CAF9")
                                        outlinePaint.strokeWidth = 8f
                                        outlinePaint.style = android.graphics.Paint.Style.STROKE
                                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                    }
                                    mapView.overlays.add(remainingRoute)
                                }
                            }

                            // 1. Start location marker (gray - origin)
                            val startMarker = Marker(mapView).apply {
                                position = trackingData.startLocation
                                title = "Lokasi Awal Kendaraan"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                                    setTint(android.graphics.Color.GRAY)
                                }
                            }
                            mapView.overlays.add(startMarker)

                            // 2. Current vehicle position marker (moving - green)
                            val vehicleMarker = Marker(mapView).apply {
                                position = currentVehiclePosition
                                title = "${trackingData.vehicle.name} - Dalam Perjalanan"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = context.getDrawable(android.R.drawable.ic_menu_directions)?.apply {
                                    setTint(android.graphics.Color.parseColor("#4CAF50"))
                                }
                            }
                            mapView.overlays.add(vehicleMarker)

                            // 3. Delivery location marker (red - destination)
                            val deliveryMarker = Marker(mapView).apply {
                                position = trackingData.deliveryLocation
                                title = "Lokasi Pengantaran"
                                snippet = trackingData.deliveryAddress
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = context.getDrawable(android.R.drawable.ic_dialog_map)?.apply {
                                    setTint(android.graphics.Color.parseColor("#F44336"))
                                }
                            }
                            mapView.overlays.add(deliveryMarker)

                            // Update map center to follow vehicle
                            mapView.controller.animateTo(currentVehiclePosition)

                            mapView.invalidate()
                        }
                    )

                    // Legend overlay on map
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Legend:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.Gray, CircleShape)
                                )
                                Text(
                                    text = "Awal",
                                    fontSize = 9.sp,
                                    color = Color(0xFF666666)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Text(
                                    text = "Kendaraan",
                                    fontSize = 9.sp,
                                    color = Color(0xFF666666)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFFF44336), CircleShape)
                                )
                                Text(
                                    text = "Tujuan",
                                    fontSize = 9.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Vehicle Info Card (Bottom Panel)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Informasi detail mengenai kondisi kendaraan, lokasi dan sebagainya",
                        fontSize = 12.sp,
                        color = Color(0xFFF57C00),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = trackingData.vehicle.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = "${trackingData.duration} • ${if (trackingData.withDriver) "Dengan Driver" else "Tanpa Driver"}",
                                fontSize = 12.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        Text(
                            text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", trackingData.totalPrice).replace(',', '.')}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color(0xFFE0E0E0)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "📍",
                            fontSize = 14.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lokasi Pengantaran:",
                                fontSize = 11.sp,
                                color = Color(0xFF999999)
                            )
                            Text(
                                text = trackingData.deliveryAddress,
                                fontSize = 12.sp,
                                color = Color(0xFF333333),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (!isArrived) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "⏰",
                                fontSize = 14.sp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Status:",
                                    fontSize = 11.sp,
                                    color = Color(0xFF999999)
                                )
                                Text(
                                    text = "Kendaraan sedang dalam perjalanan menuju lokasi Anda",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Tombol Keluar dari Tracking
                        Button(
                            onClick = onBackClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE0E0E0)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Keluar",
                                    tint = Color(0xFF333333),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Keluar dari Halaman Tracking",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF333333)
                                )
                            }
                        }

                        Text(
                            text = "💡 Tracking akan tetap berjalan di belakang. Buka kembali di Riwayat.",
                            fontSize = 10.sp,
                            color = Color(0xFF999999),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 13.sp
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "✅ Kendaraan telah tiba di lokasi pengantaran!",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp
                                )

                                Button(
                                    onClick = onVehicleArrived,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Lanjutkan ke Konfirmasi",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
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

// Data class for route response
data class RouteData(
    val waypoints: List<GeoPoint>,
    val distance: Double // in meters
)

// Fetch route from OSRM API
suspend fun fetchRouteFromOSRM(start: GeoPoint, end: GeoPoint): RouteData = withContext(Dispatchers.IO) {
    try {
        // OSRM API endpoint (using public demo server)
        val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${start.longitude},${start.latitude};" +
                "${end.longitude},${end.latitude}" +
                "?overview=full&geometries=geojson&steps=true"

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            // Parse JSON response
            val jsonResponse = JSONObject(response.toString())
            val routes = jsonResponse.getJSONArray("routes")

            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                val distance = route.getDouble("distance") // in meters
                val geometry = route.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")

                // Convert coordinates to GeoPoints
                val waypoints = mutableListOf<GeoPoint>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    waypoints.add(GeoPoint(lat, lon))
                }

                // Simplify waypoints to reduce processing (take every Nth point)
                val simplifiedWaypoints = if (waypoints.size > 50) {
                    val step = waypoints.size / 50
                    waypoints.filterIndexed { index, _ -> index % step == 0 || index == waypoints.size - 1 }
                } else {
                    waypoints
                }

                return@withContext RouteData(simplifiedWaypoints, distance)
            }
        }

        // Fallback if API fails
        throw Exception("Failed to fetch route from OSRM")
    } catch (e: Exception) {
        android.util.Log.e("OSRM", "Error fetching route: ${e.message}")
        throw e
    }
}
