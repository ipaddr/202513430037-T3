package com.example.app_jalanin.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen: Pemilihan tipe registrasi akun.
 * Hanya 3 pilihan sederhana:
 * 1. Driver
 * 2. Owner
 * 3. Penumpang
 */
@Composable
fun AccountRegistrationTypeScreen(
    modifier: Modifier = Modifier,
    onTypeSelected: (RegistrationAccountType) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0)) // Light gray background seperti gambar
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // DRIVER - Card pertama
        SimpleRegistrationCard(
            title = "driver",
            onClick = { onTypeSelected(RegistrationAccountType.Driver) }
        )

        Spacer(Modifier.height(16.dp))

        // OWNER - Card kedua
        SimpleRegistrationCard(
            title = "owner",
            onClick = { onTypeSelected(RegistrationAccountType.OwnerKendaraan) }
        )

        Spacer(Modifier.height(16.dp))

        // PENUMPANG - Card ketiga
        SimpleRegistrationCard(
            title = "penumpang",
            onClick = { onTypeSelected(RegistrationAccountType.Penumpang) }
        )
    }
}

// Data / Enum tipe akun dengan role string untuk database
sealed class RegistrationAccountType(val role: String) {
    object Penumpang : RegistrationAccountType("PENUMPANG")
    object Driver : RegistrationAccountType("DRIVER")
    object OwnerKendaraan : RegistrationAccountType("PEMILIK_KENDARAAN")
}


@Composable
private fun SimpleRegistrationCard(
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp
                ),
                color = Color.Black
            )
        }
    }
}
