package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun VehicleArrivedScreen(
    vehicleName: String,
    duration: String,
    totalPrice: Int,
    rentalEndTime: Long, // Timestamp when rental ends
    onStartRental: () -> Unit
) {
    var hasAcceptedTerms by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Success Icon
        Card(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(60.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎉",
                    fontSize = 64.sp
                )
            }
        }

        // Title
        Text(
            text = "Kendaraan Telah Tiba!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )

        // Vehicle Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = vehicleName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                HorizontalDivider(color = Color(0xFFE0E0E0))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Durasi Sewa:",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = duration,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Biaya:",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalPrice).replace(',', '.')}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }

        // Privacy Notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFBC02D))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 24.sp
                    )
                    Text(
                        text = "Pemberitahuan Penting",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }

                Text(
                    text = "Selama perjalanan Anda, perjalanan Anda di-tracking secara real-time untuk keperluan penjemputan kendaraan yang disewa, agar kendaraan bisa kembali dengan baik ke penyewa kendaraan.",
                    fontSize = 13.sp,
                    color = Color(0xFF555555),
                    lineHeight = 18.sp
                )

                Text(
                    text = "Kami tidak akan mengambil data pribadi Anda, mohon dimaklumi.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFFFBC02D))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = hasAcceptedTerms,
                        onCheckedChange = { hasAcceptedTerms = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4CAF50)
                        )
                    )
                    Text(
                        text = "Saya mengerti dan menyetujui",
                        fontSize = 13.sp,
                        color = Color(0xFF333333)
                    )
                }
            }
        }

        // Important Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📍 Informasi Penting:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Text(
                    text = "• Durasi penyewaan dimulai sekarang",
                    fontSize = 12.sp,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "• Waktu sewa akan berjalan otomatis",
                    fontSize = 12.sp,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "• Keterlambatan pengembalian dikenakan biaya tambahan",
                    fontSize = 12.sp,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "• Tracking lokasi untuk keperluan pengembalian kendaraan",
                    fontSize = 12.sp,
                    color = Color(0xFF555555)
                )
            }
        }

        // Start Rental Button
        Button(
            onClick = {
                if (!isProcessing) {
                    android.util.Log.d("VehicleArrived", "🔘 Start Rental button clicked")
                    isProcessing = true
                    try {
                        android.util.Log.d("VehicleArrived", "📞 Calling onStartRental...")
                        onStartRental()
                        android.util.Log.d("VehicleArrived", "✅ onStartRental called successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("VehicleArrived", "❌ Error calling onStartRental: ${e.message}", e)
                        isProcessing = false
                    }
                }
            },
            enabled = hasAcceptedTerms && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                disabledContainerColor = Color(0xFFCCCCCC)
            )
        ) {
            if (isProcessing) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Memproses...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Text(
                    text = if (hasAcceptedTerms) "Mulai Penyewaan" else "Setujui untuk Melanjutkan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

