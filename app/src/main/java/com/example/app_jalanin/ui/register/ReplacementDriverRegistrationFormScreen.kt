package com.example.app_jalanin.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplacementDriverRegistrationFormScreen(
    viewModel: RegistrationFormViewModel,
    onBack: () -> Unit = {},
    onSubmit: (DriverRegistrationData) -> Unit = {}
) {
    val data = viewModel.data
    var expanded by remember { mutableStateOf(false) }
    val simOptions = listOf("SIM A", "SIM B", "SIM C")

    Scaffold(topBar = {
        TopAppBar(title = { Text("Daftar Driver Pengganti") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        })
    }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Upload Foto Diri
            Text("Upload Foto Diri", fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F3F3))
                    .clickable { viewModel.updatePhotoSelf("placeholder/foto_diri.jpg") },
                contentAlignment = Alignment.Center
            ) { Text(if (data.photoSelfPath.isBlank()) "Foto Diri" else "Foto Diri Terpilih", color = Color.Gray) }

            // Data Pribadi
            OutlinedTextField(value = data.fullName, onValueChange = viewModel::updateFullName, label = { Text("Nama Lengkap") }, placeholder = { Text("Masukkan nama lengkap") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = data.phone, onValueChange = viewModel::updatePhone, label = { Text("Nomor HP") }, placeholder = { Text("08xx xxxx xxxx") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // SIM A/B/C Dropdown
            Text("SIM A/B/C", fontWeight = FontWeight.SemiBold)
            @Suppress("DEPRECATION")
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = data.simType.ifBlank { "Pilih jenis SIM" },
                    onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    simOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            viewModel.updateSimType(option)
                            expanded = false
                        })
                    }
                }
            }

            // Lama Pengalaman Mengemudi
            Text("Lama Pengalaman Mengemudi", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = data.experienceYears, onValueChange = viewModel::updateExperienceYears, placeholder = { Text("Contoh: 5 tahun") }, modifier = Modifier.fillMaxWidth())

            // Catatan penting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF3CD))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Catatan Penting", fontWeight = FontWeight.SemiBold)
                    Text("Anda akan mengemudikan kendaraan milik pengguna.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(onClick = { onSubmit(viewModel.data) }, enabled = viewModel.isValid(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kirim Pendaftaran") }
            Text("Menunggu verifikasi admin.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

