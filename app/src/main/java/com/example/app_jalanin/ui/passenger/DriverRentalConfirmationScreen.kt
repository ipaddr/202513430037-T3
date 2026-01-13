package com.example.app_jalanin.ui.passenger

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import java.text.NumberFormat
import java.util.Locale

/**
 * Confirmation screen for driver rental
 * Shows driver info, rental details, and payment method selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRentalConfirmationScreen(
    driverEmail: String,
    driverName: String?,
    vehicleType: String,
    durationType: String,
    durationCount: Int,
    price: Long,
    pickupAddress: String,
    destinationAddress: String?,
    onBackClick: () -> Unit = {},
    onConfirmPayment: (
        driverEmail: String,
        driverName: String?,
        vehicleType: String,
        durationType: String,
        durationCount: Int,
        price: Long,
        paymentMethod: String,
        pickupAddress: String,
        pickupLat: Double,
        pickupLon: Double,
        destinationAddress: String?,
        destinationLat: Double?,
        destinationLon: Double?
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    
    var paymentMethod by remember { mutableStateOf("MBANKING") } // "MBANKING" or "CASH"
    var pickupLat by remember { mutableStateOf(0.0) }
    var pickupLon by remember { mutableStateOf(0.0) }
    var destinationLat by remember { mutableStateOf<Double?>(null) }
    var destinationLon by remember { mutableStateOf<Double?>(null) }
    
    // Format price
    val formattedPrice = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(price)
    
    // Format duration
    val durationText = remember(durationType, durationCount) {
        when (durationType.uppercase()) {
            "PER_HOUR" -> "$durationCount ${if (durationCount == 1) "Jam" else "Jam"}"
            "PER_DAY" -> "$durationCount ${if (durationCount == 1) "Hari" else "Hari"}"
            "PER_WEEK" -> "$durationCount ${if (durationCount == 1) "Minggu" else "Minggu"}"
            else -> "$durationCount $durationType"
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konfirmasi Sewa Driver") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Driver Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Driver",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = driverName ?: driverEmail,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = driverEmail,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Rental Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Detail Sewa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tipe Kendaraan:", fontSize = 14.sp)
                        Text(
                            text = if (vehicleType == "MOBIL") "🚗 Mobil" else "🏍️ Motor",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Durasi:", fontSize = 14.sp)
                        Text(
                            text = durationText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Harga",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedPrice,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Location Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Lokasi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Penjemputan",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = pickupAddress,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    if (destinationAddress != null) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tujuan",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = destinationAddress,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // Payment Method Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Metode Pembayaran",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // MBANKING option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paymentMethod = "MBANKING" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (paymentMethod == "MBANKING")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (paymentMethod == "MBANKING")
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = paymentMethod == "MBANKING",
                                onClick = { paymentMethod = "MBANKING" }
                            )
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = if (paymentMethod == "MBANKING")
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "m-Banking",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Pembayaran langsung dari saldo m-banking",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    
                    // CASH option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paymentMethod = "CASH" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (paymentMethod == "CASH")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (paymentMethod == "CASH")
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = paymentMethod == "CASH",
                                onClick = { paymentMethod = "CASH" }
                            )
                            Icon(
                                Icons.Default.Money,
                                contentDescription = null,
                                tint = if (paymentMethod == "CASH")
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tunai",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Bayar langsung ke driver saat bertemu",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Confirm Button
            Button(
                onClick = {
                    // Validate balance for MBANKING
                    if (paymentMethod == "MBANKING") {
                        // Balance validation will be done in MainActivity
                    }
                    
                    onConfirmPayment(
                        driverEmail,
                        driverName,
                        vehicleType,
                        durationType,
                        durationCount,
                        price,
                        paymentMethod,
                        pickupAddress,
                        pickupLat,
                        pickupLon,
                        destinationAddress,
                        destinationLat,
                        destinationLon
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (paymentMethod == "MBANKING") "Bayar Sekarang" else "Konfirmasi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

