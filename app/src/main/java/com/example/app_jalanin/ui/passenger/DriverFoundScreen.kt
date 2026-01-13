package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverFoundScreen(
    driverName: String = "Budi Santoso",
    driverRating: Float = 4.8f,
    vehicleType: String = "Honda Vario 150",
    vehiclePlate: String = "B 1234 ABC",
    estimatedArrival: String = "5 menit",
    onBackClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Header(onBackClick = onBackClick)

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Driver Info Section
            DriverInfoSection(
                driverName = driverName,
                driverRating = driverRating,
                vehicleType = vehicleType,
                vehiclePlate = vehiclePlate
            )

            // Map Section (Live Tracking)
            MapSection()

            // Contact Buttons
            ContactSection(
                onChatClick = onChatClick,
                onCallClick = onCallClick
            )

            // Status Info
            StatusInfoSection(estimatedArrival = estimatedArrival)

            // Cancel Button
            CancelButton(onCancelClick = onCancelClick)
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
            .height(56.dp)
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF333333)
            )
        }

        Text(
            text = "Ojek Motor",
            color = Color(0xFF333333),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun DriverInfoSection(
    driverName: String,
    driverRating: Float,
    vehicleType: String,
    vehiclePlate: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F8F8)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCCCCCC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Driver Ditemukan",
                color = Color(0xFF333333),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.6.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Driver Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE0E0E0), CircleShape)
                        .border(2.dp, Color(0xFFCCCCCC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Driver Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Driver Name
                    Text(
                        text = driverName,
                        color = Color(0xFF333333),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 19.2.sp
                    )

                    // Rating
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) { index ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (index < driverRating.toInt()) Color(0xFFFFD700) else Color(0xFFE0E0E0),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = driverRating.toString(),
                            color = Color(0xFF666666),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.8.sp
                        )
                    }

                    // Vehicle Type
                    Text(
                        text = vehicleType,
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.8.sp
                    )

                    // Vehicle Plate
                    Text(
                        text = vehiclePlate,
                        color = Color(0xFF666666),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F0F0)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFCCCCCC))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person, // Placeholder for map icon
                contentDescription = null,
                tint = Color(0xFF999999),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "[PETA LIVE TRACKING]",
                color = Color(0xFF999999),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Menunjukkan lokasi driver menuju titik jemput",
                color = Color(0xFF999999),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 14.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun ContactSection(
    onChatClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        // Chat Button
        Button(
            onClick = onChatClick,
            modifier = Modifier
                .width(160.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE8F5E8)
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Call, // Placeholder for chat icon
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Chat Driver",
                    color = Color(0xFF4CAF50),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.2.sp
                )
            }
        }

        // Call Button
        Button(
            onClick = onCallClick,
            modifier = Modifier
                .width(160.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE8F0FF)
            ),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Call Driver",
                    color = Color(0xFF2196F3),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.2.sp
                )
            }
        }
    }
}

@Composable
private fun StatusInfoSection(estimatedArrival: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Driver akan tiba dalam $estimatedArrival",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.2.sp
            )

            Text(
                text = "Helm dan masker disediakan",
                color = Color(0xFF666666),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.8.sp
            )
        }
    }
}

@Composable
private fun CancelButton(onCancelClick: () -> Unit) {
    Button(
        onClick = onCancelClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFEBEE)
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF44336))
    ) {
        Text(
            text = "Batalkan Pesanan",
            color = Color(0xFFF44336),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.2.sp
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

