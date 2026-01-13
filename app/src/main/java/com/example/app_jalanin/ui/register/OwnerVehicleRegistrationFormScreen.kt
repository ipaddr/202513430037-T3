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
fun OwnerVehicleRegistrationFormScreen(
    viewModel: RegistrationFormViewModel,
    onBack: () -> Unit = {},
    onSubmit: (DriverRegistrationData) -> Unit = {}
) {
    val data = viewModel.data
    var typeExpanded by remember { mutableStateOf(false) }
    val typeOptions = listOf("Motor", "Mobil")
    val isValid = viewModel.isValid()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Daftar Pemilik Kendaraan") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        })
    }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Upload Foto Kendaraan
            Text("Upload Foto Kendaraan", fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F3F3))
                    .clickable { viewModel.updateVehiclePhoto("placeholder/foto_kendaraan_owner.jpg") },
                contentAlignment = Alignment.Center
            ) { Text(if (data.vehiclePhotoPath.isBlank()) "Tap untuk upload foto\nFoto tampak depan dan samping" else "Foto Kendaraan Terpilih", color = Color.Gray, textAlign = TextAlign.Center) }

            // Jenis Kendaraan dropdown
            Text("Jenis Kendaraan", fontWeight = FontWeight.SemiBold)
            @Suppress("DEPRECATION")
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                OutlinedTextField(
                    value = data.ownerVehicleType.ifBlank { "Motor / Mobil" },
                    onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    typeOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            viewModel.updateOwnerVehicleType(option)
                            typeExpanded = false
                        })
                    }
                }
            }

            OutlinedTextField(value = data.vehicleYear, onValueChange = viewModel::updateVehicleYear, label = { Text("Tahun Kendaraan") }, placeholder = { Text("Contoh: 2022") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = data.vehicleCapacity, onValueChange = viewModel::updateVehicleCapacity, label = { Text("Kapasitas (cc/kursi)") }, placeholder = { Text("Contoh: 150cc atau 7 kursi") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = data.rentalPrice, onValueChange = viewModel::updateRentalPrice, label = { Text("Harga Sewa (hari/minggu/jam)") }, placeholder = { Text("Contoh: 300000/250000/50000") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Upload Foto STNK", fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF9F9F9))
                    .clickable { viewModel.updateStnkPath("placeholder/stnk_owner.jpg") },
                contentAlignment = Alignment.Center
            ) { Text(if (data.stnkDocumentPath.isBlank()) "Upload foto STNK yang jelas" else "STNK Terunggah", color = Color.Gray, textAlign = TextAlign.Center) }

            OutlinedTextField(value = data.locationAddress, onValueChange = viewModel::updateLocationAddress, label = { Text("Lokasi Kendaraan") }, placeholder = { Text("Masukkan Alamat Lengkap") }, modifier = Modifier.fillMaxWidth())

            // Catatan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEFF4F9))
                    .padding(12.dp)
            ) {
                Text("Catatan: Kendaraan akan dipublikasikan setelah disetujui admin.", style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = { onSubmit(viewModel.data) }, enabled = isValid, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kirim Pendaftaran") }
            Text(if (isValid) "Menunggu verifikasi admin." else "Lengkapi semua data untuk mengirim.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
