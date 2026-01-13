package com.example.app_jalanin.ui.passenger

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GpsFixed
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
import com.example.app_jalanin.utils.getRouteInfo
import com.example.app_jalanin.utils.RouteInfo
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URL
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

// Data class untuk route info
data class RouteInfo(
    val distance: Double, // km
    val duration: Double, // seconds
    val geometry: List<GeoPoint>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OjekMotorBookingScreen(
    onBackClick: () -> Unit = {},
    onBookingConfirmed: () -> Unit = {}
) {
    var pickupLocation by remember { mutableStateOf("") }
    var pickupGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var destination by remember { mutableStateOf("") }
    var destinationGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var fare by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showPickupSearch by remember { mutableStateOf(false) }
    var showDestinationSearch by remember { mutableStateOf(false) }
    var pickupSearchSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var destinationSearchSuggestions by remember { mutableStateOf<List<Address>>(emptyList()) }
    var roadRoute by remember { mutableStateOf<RouteInfo?>(null) }
    var isLoadingRoute by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation(fusedLocationClient) { location ->
                currentLocation = location
            }
        }
    }

    // Check and request location permission
    LaunchedEffect(Unit) {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                getCurrentLocation(fusedLocationClient) { location ->
                    currentLocation = location
                }
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header only (StatusBar removed)
        Header(onBackClick = onBackClick)

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Location Section with GPS tracking and search
            LocationSection(
                pickupLocation = pickupLocation,
                destination = destination,
                onPickupGpsClick = {
                    // Track current GPS location
                    getCurrentLocation(fusedLocationClient) { location ->
                        currentLocation = location
                        pickupGeoPoint = location
                        // Reset route and fare when pickup changes
                        roadRoute = null
                        fare = null
                        // Get address name from coordinates
                        scope.launch {
                            val address = getAddressFromGeoPoint(geocoder, location)
                            pickupLocation = address ?: "Lokasi GPS"
                        }
                    }
                },
                onPickupClick = {
                    showPickupSearch = true
                },
                onPickupChange = { query ->
                    pickupLocation = query
                    // Search for pickup location suggestions
                    if (query.length >= 3) {
                        scope.launch {
                            pickupSearchSuggestions = searchLocation(geocoder, query)
                        }
                    } else {
                        pickupSearchSuggestions = emptyList()
                    }
                },
                onDestinationClick = {
                    showDestinationSearch = true
                },
                onDestinationChange = { query ->
                    destination = query
                    // Search for destination location suggestions
                    if (query.length >= 3) {
                        scope.launch {
                            destinationSearchSuggestions = searchLocation(geocoder, query)
                        }
                    } else {
                        destinationSearchSuggestions = emptyList()
                    }
                }
            )

            // Pickup search dialog
            if (showPickupSearch) {
                DestinationSearchDialog(
                    searchQuery = pickupLocation,
                    suggestions = pickupSearchSuggestions,
                    title = "Cari Lokasi Jemput",
                    onQueryChange = { query ->
                        pickupLocation = query
                        if (query.length >= 3) {
                            scope.launch {
                                pickupSearchSuggestions = searchLocation(geocoder, query)
                            }
                        } else {
                            pickupSearchSuggestions = emptyList()
                        }
                    },
                    onSuggestionSelected = { address ->
                        pickupLocation = address.getAddressLine(0) ?: address.featureName ?: ""
                        pickupGeoPoint = GeoPoint(address.latitude, address.longitude)
                        showPickupSearch = false
                        pickupSearchSuggestions = emptyList()

                        // Reset route and fare when pickup changes
                        roadRoute = null
                        fare = null
                    },
                    onDismiss = {
                        showPickupSearch = false
                        pickupSearchSuggestions = emptyList()
                    }
                )
            }

            // Destination search dialog
            if (showDestinationSearch) {
                DestinationSearchDialog(
                    searchQuery = destination,
                    suggestions = destinationSearchSuggestions,
                    title = "Cari Lokasi Tujuan",
                    onQueryChange = { query ->
                        destination = query
                        if (query.length >= 3) {
                            scope.launch {
                                destinationSearchSuggestions = searchLocation(geocoder, query)
                            }
                        } else {
                            destinationSearchSuggestions = emptyList()
                        }
                    },
                    onSuggestionSelected = { address ->
                        destination = address.getAddressLine(0) ?: address.featureName ?: ""
                        destinationGeoPoint = GeoPoint(address.latitude, address.longitude)
                        showDestinationSearch = false
                        destinationSearchSuggestions = emptyList()

                        // Reset route and fare when destination changes
                        roadRoute = null
                        fare = null
                    },
                    onDismiss = {
                        showDestinationSearch = false
                        destinationSearchSuggestions = emptyList()
                    }
                )
            }

            // Fare Section
            FareSection(fare = fare)

            // Route Info Section (show jarak dan waktu jika route sudah ada)
            if (roadRoute != null) {
                RouteInfoCard(routeInfo = roadRoute!!)
            }

            // Route finding button (only show if both locations are set)
            if (pickupGeoPoint != null && destinationGeoPoint != null) {
                Button(
                    onClick = {
                        isLoadingRoute = true
                        scope.launch {
                            roadRoute = findRoute(pickupGeoPoint!!, destinationGeoPoint!!)
                            isLoadingRoute = false

                            // Calculate fare after route is found
                            if (roadRoute != null) {
                                fare = calculateFareFromDistance(roadRoute!!.distance)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isLoadingRoute,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (roadRoute == null) Color(0xFF4CAF50) else Color(0xFF2196F3)
                    )
                ) {
                    if (isLoadingRoute) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mencari rute...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (roadRoute == null) "🗺️ Cari Rute Terdekat" else "🔄 Cari Ulang Rute",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Map Preview with OpenStreetMap (free, no API key needed!)
            MapPreview(
                currentLocation = currentLocation,
                pickupLocation = pickupGeoPoint,
                destinationLocation = destinationGeoPoint,
                route = roadRoute
            )

            // Action Section
            ActionSection(
                isEnabled = destination.isNotEmpty() && fare != null,
                isLoading = false,
                onBookClick = onBookingConfirmed
            )
        }

        // Bottom Safe Area
        BottomSafeArea()
    }
}


@Composable
private fun Header(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
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

        // Title
        Text(
            text = "Ojek Motor",
            color = Color(0xFF333333),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        // Spacer for centering
        Spacer(modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun LocationSection(
    pickupLocation: String,
    destination: String,
    onPickupGpsClick: () -> Unit,
    onPickupClick: () -> Unit,
    onPickupChange: (String) -> Unit,
    onDestinationClick: () -> Unit,
    onDestinationChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Pickup Location with Manual Input + GPS Button
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Lokasi Jemput",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.2.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pickupLocation,
                    onValueChange = onPickupChange,
                    placeholder = {
                        Text(
                            text = "Ketik atau gunakan GPS...",
                            color = Color(0xFF999999),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 19.2.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onPickupClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable { onPickupClick() },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF0F0F0),
                        unfocusedContainerColor = Color(0xFFF0F0F0),
                        disabledContainerColor = Color(0xFFF0F0F0),
                        focusedIndicatorColor = Color(0xFFCCCCCC),
                        unfocusedIndicatorColor = Color(0xFFCCCCCC),
                        disabledIndicatorColor = Color(0xFFCCCCCC),
                        focusedTextColor = Color(0xFF666666),
                        unfocusedTextColor = Color(0xFF666666)
                    ),
                    singleLine = true
                )

                // GPS Track Button
                IconButton(
                    onClick = onPickupGpsClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Track GPS",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Destination with Search
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tujuan",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.2.sp
            )

            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                placeholder = {
                    Text(
                        text = "Cari lokasi tujuan...",
                        color = Color(0xFF999999),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 19.2.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onDestinationClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onDestinationClick() },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF0F0F0),
                    unfocusedContainerColor = Color(0xFFF0F0F0),
                    disabledContainerColor = Color(0xFFF0F0F0),
                    focusedIndicatorColor = Color(0xFFCCCCCC),
                    unfocusedIndicatorColor = Color(0xFFCCCCCC),
                    disabledIndicatorColor = Color(0xFFCCCCCC),
                    focusedTextColor = Color(0xFF666666),
                    unfocusedTextColor = Color(0xFF666666)
                ),
                singleLine = true
            )
        }
    }
}

@Composable
private fun DestinationSearchDialog(
    searchQuery: String,
    suggestions: List<Address>,
    title: String = "Cari Lokasi Tujuan",
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (Address) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Search Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color(0xFF333333)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search TextField
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Ketik nama tempat...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF666666)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF0F0F0),
                        unfocusedContainerColor = Color(0xFFF0F0F0)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Suggestions List
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = "Hasil Pencarian:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions.take(10)) { address ->
                            SuggestionItem(
                                address = address,
                                onClick = { onSuggestionSelected(address) }
                            )
                        }
                    }
                } else if (searchQuery.length >= 3) {
                    Text(
                        text = "Tidak ada hasil. Coba kata kunci lain.",
                        fontSize = 14.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Text(
                        text = "Ketik minimal 3 karakter untuk mencari",
                        fontSize = 14.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    address: Address,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F8F8)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = address.featureName ?: address.thoroughfare ?: "Lokasi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )
                Text(
                    text = address.getAddressLine(0) ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationInputField(
    value: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingIconTint: Color,
    enabled: Boolean,
    onValueChange: (String) -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = if (enabled) Color(0xFF999999) else Color(0xFF666666),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 19.2.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = trailingIconTint,
                modifier = Modifier.size(24.dp)
            )
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF0F0F0),
            unfocusedContainerColor = Color(0xFFF0F0F0),
            disabledContainerColor = Color(0xFFF0F0F0),
            focusedIndicatorColor = Color(0xFFCCCCCC),
            unfocusedIndicatorColor = Color(0xFFCCCCCC),
            disabledIndicatorColor = Color(0xFFCCCCCC),
            disabledTextColor = Color(0xFF666666),
            focusedTextColor = Color(0xFF666666),
            unfocusedTextColor = Color(0xFF666666)
        ),
        singleLine = true
    )
}

@Composable
private fun FareSection(fare: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F8F8)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCCCCC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tarif",
                color = Color(0xFF333333),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.6.sp
            )

            Text(
                text = fare ?: "Rp ---",
                color = Color(0xFF666666),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 38.4.sp
            )

            Text(
                text = "Akan dihitung setelah cari rute",
                color = Color(0xFF999999),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RouteInfoCard(routeInfo: RouteInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD) // Light blue background
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "📍 Info Rute",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Jarak dan Waktu dalam Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Jarak
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Jarak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f km".format(routeInfo.distance),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(Color(0xFFBBDEFB))
                )

                // Estimasi Waktu
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Estimasi Waktu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDuration(routeInfo.duration),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                }
            }

            // Hint text
            Text(
                text = "⚡ Rute tercepat telah ditemukan!",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF666666),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function to format duration
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes < 60) {
        "$minutes menit"
    } else {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        "${hours}j ${remainingMinutes}m"
    }
}

@Composable
private fun MapPreview(
    currentLocation: GeoPoint?,
    pickupLocation: GeoPoint?,
    destinationLocation: GeoPoint?,
    route: RouteInfo?
) {
    val context = LocalContext.current // Get context here

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F0F0)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCCCCCC))
    ) {
        val locationToShow = pickupLocation ?: currentLocation

        if (locationToShow != null) {
            // Show OpenStreetMap
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)

                        // Set initial position and zoom
                        controller.setZoom(15.0)
                        controller.setCenter(locationToShow)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapView.overlays.clear()

                    // Add pickup marker (green)
                    pickupLocation?.let { pickup ->
                        val pickupMarker = Marker(mapView).apply {
                            position = pickup
                            title = "Lokasi Jemput"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            // Use simple marker without custom icon tinting
                        }
                        mapView.overlays.add(pickupMarker)
                    }

                    // Add destination marker (red)
                    destinationLocation?.let { destination ->
                        val destMarker = Marker(mapView).apply {
                            position = destination
                            title = "Tujuan"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(destMarker)

                        // Focus on destination if both markers exist
                        if (pickupLocation != null) {
                            // Calculate bounds to show both markers
                            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                                listOf(pickupLocation, destination)
                            )
                            mapView.post {
                                mapView.zoomToBoundingBox(boundingBox, true, 100)
                            }
                        } else {
                            // Just focus on destination
                            mapView.controller.animateTo(destination)
                        }
                    }

                    // Draw route if available
                    route?.let { routeInfo ->
                        routeInfo.geometry?.let { geometry ->
                            if (geometry.isNotEmpty()) {
                                val roadOverlay = Polyline(mapView).apply {
                                    setPoints(geometry)
                                    outlinePaint.color = android.graphics.Color.BLUE
                                    outlinePaint.strokeWidth = 12f // Lebih tebal agar terlihat jelas
                                    outlinePaint.style = android.graphics.Paint.Style.STROKE
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                                }
                                mapView.overlays.add(roadOverlay)

                                // Zoom to show entire route
                                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(geometry)
                                mapView.post {
                                    mapView.zoomToBoundingBox(boundingBox, true, 100)
                                }
                            }
                        }
                    }

                    mapView.invalidate()
                }
            )
        } else {
            // Loading state
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF333333),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Mengambil lokasi...",
                    color = Color(0xFF666666),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pastikan GPS aktif",
                    color = Color(0xFF999999),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.8.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ActionSection(
    isEnabled: Boolean,
    isLoading: Boolean,
    onBookClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Book Button
        Button(
            onClick = onBookClick,
            enabled = isEnabled && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF333333),
                disabledContainerColor = Color(0xFF999999)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Pesan Sekarang",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.6.sp
                )
            }
        }

        // Status Text
        Text(
            text = if (isLoading) "Membuat pesanan..." else "Menunggu driver terdekat...",
            color = Color(0xFF666666),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BottomSafeArea() {
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

// Helper function to get current GPS location
// For emulator: Use mock location or default to Jakarta
@Suppress("MissingPermission")
private fun getCurrentLocation(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocationReceived: (GeoPoint) -> Unit
) {
    fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                onLocationReceived(geoPoint)
                android.util.Log.d("OjekMotorBooking", "✅ GPS Location: ${location.latitude}, ${location.longitude}")
            } else {
                // Default to Jakarta (Monas area) for emulator/testing
                val defaultLocation = GeoPoint(-6.1751, 106.8650) // Monas, Jakarta
                onLocationReceived(defaultLocation)
                android.util.Log.d("OjekMotorBooking", "⚠️ No GPS signal - Using default: Monas, Jakarta")
            }
        }
        .addOnFailureListener { e ->
            android.util.Log.e("OjekMotorBooking", "❌ GPS Failed: ${e.message}")
            // Default to Jakarta (Monas area) on failure
            val defaultLocation = GeoPoint(-6.1751, 106.8650) // Monas, Jakarta
            onLocationReceived(defaultLocation)
            android.util.Log.d("OjekMotorBooking", "⚠️ Using fallback location: Monas, Jakarta")
        }
}

// Helper function to search location by name
private suspend fun searchLocation(geocoder: Geocoder, query: String): List<Address> {
    return withContext(Dispatchers.IO) {
        try {
            geocoder.getFromLocationName(query, 10) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("OjekMotorBooking", "Search failed: ${e.message}")
            emptyList()
        }
    }
}

// Helper function to get address from GeoPoint
private suspend fun getAddressFromGeoPoint(geocoder: Geocoder, geoPoint: GeoPoint): String? {
    return withContext(Dispatchers.IO) {
        try {
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            android.util.Log.e("OjekMotorBooking", "Geocoding failed: ${e.message}")
            null
        }
    }
}

// Helper function to calculate fare from distance
private fun calculateFareFromDistance(distanceKm: Double): String {
    val baseFare = 5000
    val perKmRate = 2000
    val totalFare = baseFare + (perKmRate * distanceKm).toInt()

    return "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalFare).replace(',', '.')}"
}

// Helper function to find route between two points using OSRM API
private suspend fun findRoute(start: GeoPoint, end: GeoPoint): RouteInfo? {
    return withContext(Dispatchers.IO) {
        try {
            // OSRM API endpoint (free public server)
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${start.longitude},${start.latitude};" +
                    "${end.longitude},${end.latitude}" +
                    "?overview=full&geometries=polyline"

            android.util.Log.d("OjekMotorBooking", "Requesting route from OSRM: $url")

            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            if (json.getString("code") == "Ok") {
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val distance = route.getDouble("distance") / 1000.0 // Convert to km
                    val duration = route.getDouble("duration") // In seconds

                    // Decode polyline geometry
                    val geometryString = route.getString("geometry")
                    val geometryPoints = decodePolyline(geometryString)

                    android.util.Log.d("OjekMotorBooking", "✅ Route found: ${distance} km, ${duration / 60} minutes, ${geometryPoints.size} points")

                    RouteInfo(
                        distance = distance,
                        duration = duration,
                        geometry = geometryPoints
                    )
                } else {
                    android.util.Log.e("OjekMotorBooking", "No routes found")
                    null
                }
            } else {
                android.util.Log.e("OjekMotorBooking", "OSRM API error: ${json.getString("code")}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("OjekMotorBooking", "Route finding failed: ${e.message}", e)
            null
        }
    }
}

// Decode polyline string to list of GeoPoints
private fun decodePolyline(encoded: String): List<GeoPoint> {
    val poly = ArrayList<GeoPoint>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val geoPoint = GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(geoPoint)
    }

    return poly
}

// Helper function to calculate fare (dummy implementation)
private fun calculateFare(destination: String): String {
    // Simple fare calculation based on destination length
    // In real app, this would use distance API
    val baseFare = 5000
    val perKmRate = 2000
    val estimatedKm = (destination.length / 10).coerceAtLeast(1) // Dummy calculation
    val totalFare = baseFare + (perKmRate * estimatedKm)

    return "Rp ${String.format(java.util.Locale.forLanguageTag("id-ID"), "%,d", totalFare).replace(',', '.')}"
}
