package com.example.app_jalanin.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.app_jalanin.utils.getRouteInfo
import com.example.app_jalanin.utils.RouteInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Data class untuk jenis mobil
data class CarType(
    val id: String,
    val name: String,
    val description: String,
    val capacity: Int,
    val priceMultiplier: Double, // 1.0 = base, 1.5 = 50% lebih mahal, dll
    val icon: String // emoji atau icon
)

// Predefined car types
val carTypes = listOf(
    CarType("city_car", "City Car", "4 seat", 4, 1.0, "🚗"),
    CarType("mpv", "MPV", "7 seat", 7, 1.3, "🚐"),
    CarType("suv", "SUV", "5-7 seat", 7, 1.5, "🚙")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OjekMobilBookingScreen(
    onBackClick: () -> Unit = {},
    onBookingConfirmed: () -> Unit = {}
) {
    var selectedCarType by remember { mutableStateOf(carTypes[0]) } // Default: City Car
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
            getCurrentLocationMobil(fusedLocationClient) { location ->
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
                getCurrentLocationMobil(fusedLocationClient) { location ->
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
        // Header
        HeaderMobil(onBackClick = onBackClick)

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Car Type Selection Section (NEW!)
            CarTypeSelectionSection(
                selectedCarType = selectedCarType,
                onCarTypeSelected = {
                    selectedCarType = it
                    // Reset fare when car type changes
                    if (roadRoute != null) {
                        fare = calculateFareFromDistanceMobil(roadRoute!!.distance, selectedCarType.priceMultiplier)
                    }
                }
            )

            // Location Section
            LocationSectionMobil(
                pickupLocation = pickupLocation,
                destination = destination,
                onPickupGpsClick = {
                    getCurrentLocationMobil(fusedLocationClient) { location ->
                        currentLocation = location
                        pickupGeoPoint = location
                        roadRoute = null
                        fare = null
                        scope.launch {
                            val address = getAddressFromGeoPointMobil(geocoder, location)
                            pickupLocation = address ?: "Lokasi GPS"
                        }
                    }
                },
                onPickupClick = { showPickupSearch = true },
                onPickupChange = { query ->
                    pickupLocation = query
                    if (query.length >= 3) {
                        scope.launch {
                            pickupSearchSuggestions = searchLocationMobil(geocoder, query)
                        }
                    } else {
                        pickupSearchSuggestions = emptyList()
                    }
                },
                onDestinationClick = { showDestinationSearch = true },
                onDestinationChange = { query ->
                    destination = query
                    if (query.length >= 3) {
                        scope.launch {
                            destinationSearchSuggestions = searchLocationMobil(geocoder, query)
                        }
                    } else {
                        destinationSearchSuggestions = emptyList()
                    }
                }
            )

            // Pickup search dialog
            if (showPickupSearch) {
                SearchDialogMobil(
                    searchQuery = pickupLocation,
                    suggestions = pickupSearchSuggestions,
                    title = "Cari Lokasi Jemput",
                    onQueryChange = { query ->
                        pickupLocation = query
                        if (query.length >= 3) {
                            scope.launch {
                                pickupSearchSuggestions = searchLocationMobil(geocoder, query)
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
                SearchDialogMobil(
                    searchQuery = destination,
                    suggestions = destinationSearchSuggestions,
                    title = "Cari Lokasi Tujuan",
                    onQueryChange = { query ->
                        destination = query
                        if (query.length >= 3) {
                            scope.launch {
                                destinationSearchSuggestions = searchLocationMobil(geocoder, query)
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
            FareSectionMobil(fare = fare)

            // Route Info Section
            if (roadRoute != null) {
                RouteInfoCardMobil(routeInfo = roadRoute!!)
            }

            // Route finding button
            if (pickupGeoPoint != null && destinationGeoPoint != null) {
                Button(
                    onClick = {
                        isLoadingRoute = true
                        scope.launch {
                            roadRoute = findRouteMobil(pickupGeoPoint!!, destinationGeoPoint!!)
                            isLoadingRoute = false

                            if (roadRoute != null) {
                                fare = calculateFareFromDistanceMobil(roadRoute!!.distance, selectedCarType.priceMultiplier)
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
                        Text("Mencari rute...", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (roadRoute == null) "🗺️ Cari Rute Terdekat" else "🔄 Cari Ulang Rute",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Map Preview
            MapPreviewMobil(
                currentLocation = currentLocation,
                pickupLocation = pickupGeoPoint,
                destinationLocation = destinationGeoPoint,
                route = roadRoute
            )

            // Action Section
            ActionSectionMobil(
                isEnabled = destination.isNotEmpty() && fare != null,
                isLoading = false,
                onBookClick = onBookingConfirmed
            )
        }

        // Bottom Safe Area
        BottomSafeAreaMobil()
    }
}

// NEW: Car Type Selection Section
@Composable
private fun CarTypeSelectionSection(
    selectedCarType: CarType,
    onCarTypeSelected: (CarType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Pilih Jenis Mobil",
            color = Color(0xFF333333),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.2.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            carTypes.forEach { carType ->
                CarTypeCard(
                    carType = carType,
                    isSelected = selectedCarType.id == carType.id,
                    onSelected = { onCarTypeSelected(carType) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CarTypeCard(
    carType: CarType,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onSelected() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF4CAF50) else Color(0xFFCCCCCC)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon/Emoji
            Text(
                text = carType.icon,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Car name
            Text(
                text = carType.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFF2E7D32) else Color(0xFF333333),
                textAlign = TextAlign.Center
            )

            // Capacity
            Text(
                text = carType.description,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )

            // Check icon if selected
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderMobil(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF333333),
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = "Ojek Mobil",
            color = Color(0xFF333333),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun LocationSectionMobil(
    pickupLocation: String,
    destination: String,
    onPickupGpsClick: () -> Unit,
    onPickupClick: () -> Unit,
    onPickupChange: (String) -> Unit,
    onDestinationClick: () -> Unit,
    onDestinationChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Pickup Location
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            fontSize = 16.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF666666), modifier = Modifier.size(24.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = onPickupClick) {
                            Icon(Icons.Default.Search, "Search", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
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
                        focusedTextColor = Color(0xFF666666),
                        unfocusedTextColor = Color(0xFF666666)
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = onPickupGpsClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.GpsFixed, "Track GPS", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Destination
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Text("Cari lokasi tujuan...", color = Color(0xFF999999), fontSize = 16.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF666666), modifier = Modifier.size(24.dp))
                },
                trailingIcon = {
                    IconButton(onClick = onDestinationClick) {
                        Icon(Icons.Default.Search, "Search", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
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
                    focusedIndicatorColor = Color(0xFFCCCCCC),
                    unfocusedIndicatorColor = Color(0xFFCCCCCC),
                    focusedTextColor = Color(0xFF666666),
                    unfocusedTextColor = Color(0xFF666666)
                ),
                singleLine = true
            )
        }
    }
}

@Composable
private fun SearchDialogMobil(
    searchQuery: String,
    suggestions: List<Address>,
    title: String,
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color(0xFF333333))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Ketik nama tempat...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF666666))
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
                            SuggestionItemMobil(address = address, onClick = { onSuggestionSelected(address) })
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
private fun SuggestionItemMobil(address: Address, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
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

@Composable
private fun FareSectionMobil(fare: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
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
private fun RouteInfoCardMobil(routeInfo: RouteInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📍 Info Rute",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Jarak", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF666666))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f km".format(routeInfo.distance),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(50.dp)
                        .background(Color(0xFFBBDEFB))
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Estimasi Waktu", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF666666))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDurationMobil(routeInfo.duration),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                }
            }

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

@Composable
private fun MapPreviewMobil(
    currentLocation: GeoPoint?,
    pickupLocation: GeoPoint?,
    destinationLocation: GeoPoint?,
    route: RouteInfo?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCCCCCC))
    ) {
        val locationToShow = pickupLocation ?: currentLocation

        if (locationToShow != null) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(locationToShow)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapView.overlays.clear()

                    pickupLocation?.let { pickup ->
                        val pickupMarker = Marker(mapView).apply {
                            position = pickup
                            title = "Lokasi Jemput"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(pickupMarker)
                    }

                    destinationLocation?.let { destination ->
                        val destMarker = Marker(mapView).apply {
                            position = destination
                            title = "Tujuan"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(destMarker)

                        if (pickupLocation != null) {
                            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                                listOf(pickupLocation, destination)
                            )
                            mapView.post {
                                mapView.zoomToBoundingBox(boundingBox, true, 100)
                            }
                        } else {
                            mapView.controller.animateTo(destination)
                        }
                    }

                    route?.let { routeInfo ->
                        routeInfo.geometry?.let { geometry ->
                            if (geometry.isNotEmpty()) {
                                val roadOverlay = Polyline(mapView).apply {
                                    setPoints(geometry)
                                    outlinePaint.color = android.graphics.Color.BLUE
                                    outlinePaint.strokeWidth = 12f
                                    outlinePaint.style = android.graphics.Paint.Style.STROKE
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                                }
                                mapView.overlays.add(roadOverlay)

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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF333333), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Mengambil lokasi...", color = Color(0xFF666666), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pastikan GPS aktif", color = Color(0xFF999999), fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ActionSectionMobil(
    isEnabled: Boolean,
    isLoading: Boolean,
    onBookClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Pesan Mobil Sekarang",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.6.sp
                )
            }
        }

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
private fun BottomSafeAreaMobil() {
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
private fun formatDurationMobil(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes < 60) {
        "$minutes menit"
    } else {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        "${hours}j ${remainingMinutes}m"
    }
}

@Suppress("MissingPermission")
private fun getCurrentLocationMobil(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocationReceived: (GeoPoint) -> Unit
) {
    fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                onLocationReceived(geoPoint)
            } else {
                val defaultLocation = GeoPoint(-6.1751, 106.8650)
                onLocationReceived(defaultLocation)
            }
        }
        .addOnFailureListener {
            val defaultLocation = GeoPoint(-6.1751, 106.8650)
            onLocationReceived(defaultLocation)
        }
}

private suspend fun searchLocationMobil(geocoder: Geocoder, query: String): List<Address> {
    return withContext(Dispatchers.IO) {
        try {
            geocoder.getFromLocationName(query, 10) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private suspend fun getAddressFromGeoPointMobil(geocoder: Geocoder, geoPoint: GeoPoint): String? {
    return withContext(Dispatchers.IO) {
        try {
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            null
        }
    }
}

// Calculate fare with multiplier for car type (LEBIH MAHAL dari motor!)
private fun calculateFareFromDistanceMobil(distanceKm: Double, priceMultiplier: Double): String {
    val baseFare = 8000 // Lebih mahal dari motor (5000)
    val perKmRate = 3000 // Lebih mahal dari motor (2000)
    val totalFare = ((baseFare + (perKmRate * distanceKm)) * priceMultiplier).toInt()

    return "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalFare).replace(',', '.')}"
}

private suspend fun findRouteMobil(start: GeoPoint, end: GeoPoint): RouteInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${start.longitude},${start.latitude};" +
                    "${end.longitude},${end.latitude}" +
                    "?overview=full&geometries=polyline"

            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            if (json.getString("code") == "Ok") {
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val distance = route.getDouble("distance") / 1000.0
                    val duration = route.getDouble("duration")
                    val geometryString = route.getString("geometry")
                    val geometryPoints = decodePolylineMobil(geometryString)

                    RouteInfo(distance = distance, duration = duration, geometry = geometryPoints)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

private fun decodePolylineMobil(encoded: String): List<GeoPoint> {
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

