package com.example.app_jalanin.ui.owner

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_jalanin.data.local.entity.Rental

/**
 * Screen untuk owner memilih opsi pengantaran kendaraan
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDeliveryOptionScreen(
    rental: Rental,
    ownerEmail: String,
    onBackClick: () -> Unit = {},
    onOwnerDeliverySelected: () -> Unit = {},
    onDriverDeliverySelected: () -> Unit = {}
) {
    var selectedOption by remember { mutableStateOf<DeliveryOption?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilih Opsi Pengantaran") },
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
            // Rental Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Informasi Sewa",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kendaraan: ${rental.vehicleName}",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Penumpang: ${rental.userEmail}",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Lokasi: ${rental.deliveryAddress}",
                        fontSize = 14.sp
                    )
                }
            }
            
            Text(
                text = "Pilih cara pengantaran kendaraan:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Option A: Owner Delivery
            DeliveryOptionCard(
                title = "Saya Antar Kendaraan",
                description = "Anda akan mengantarkan kendaraan sendiri ke lokasi penumpang",
                icon = Icons.Default.Person,
                iconColor = Color(0xFF2196F3),
                isSelected = selectedOption == DeliveryOption.OWNER_DELIVERY,
                onClick = { selectedOption = DeliveryOption.OWNER_DELIVERY }
            )
            
            // Option B: Driver Delivery
            DeliveryOptionCard(
                title = "Sewa Driver untuk Antar",
                description = "Driver akan mengantarkan kendaraan ke lokasi penumpang",
                icon = Icons.Default.DirectionsCar,
                iconColor = Color(0xFF4CAF50),
                isSelected = selectedOption == DeliveryOption.DRIVER_DELIVERY,
                onClick = { selectedOption = DeliveryOption.DRIVER_DELIVERY }
            )
            
            // Continue Button
            Button(
                onClick = {
                    when (selectedOption) {
                        DeliveryOption.OWNER_DELIVERY -> onOwnerDeliverySelected()
                        DeliveryOption.DRIVER_DELIVERY -> onDriverDeliverySelected()
                        null -> {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedOption != null
            ) {
                Text("Lanjutkan")
            }
        }
    }
}

enum class DeliveryOption {
    OWNER_DELIVERY,
    DRIVER_DELIVERY
}

@Composable
private fun DeliveryOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                iconColor.copy(alpha = 0.1f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) iconColor else Color(0xFFE0E0E0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
