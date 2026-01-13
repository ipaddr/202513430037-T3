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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.RadioButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarDriverRegistrationFormScreen(
    viewModel: RegistrationFormViewModel,
    onBack: () -> Unit = {},
    onSubmit: (DriverRegistrationData) -> Unit = {}
) {
    val data = viewModel.data
    var category by remember { mutableStateOf(data.vehicleCategory) }
    val isValid = viewModel.isValid()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Daftar Driver Mobil") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        })
    }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
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

            Text("Foto Kendaraan", fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F3F3))
                    .clickable { viewModel.updateVehiclePhoto("placeholder/foto_kendaraan.jpg") },
                contentAlignment = Alignment.Center
            ) { Text(if (data.vehiclePhotoPath.isBlank()) "Foto Kendaraan" else "Foto Kendaraan Terpilih", color = Color.Gray) }

            Text("Data Pribadi", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = data.fullName, onValueChange = viewModel::updateFullName, label = { Text("Nama Lengkap") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = data.phone, onValueChange = viewModel::updatePhone, label = { Text("Nomor HP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Informasi Kendaraan", fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sedan", "MPV", "SUV").forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = category.equals(option, true), onClick = {
                            category = option
                            viewModel.updateVehicleCategory(option)
                        })
                        Spacer(Modifier.width(8.dp)); Text(option)
                    }
                }
            }
            OutlinedTextField(value = data.vehicleBrandModel, onValueChange = viewModel::updateVehicleBrandModel, label = { Text("Merk & Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = data.motorPlate, onValueChange = viewModel::updateMotorPlate, label = { Text("Nomor Polisi") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Text("Upload Dokumen", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F3F3)).clickable { viewModel.updateSimPath("placeholder/sim.jpg") }, contentAlignment = Alignment.Center) { Text(if (data.simDocumentPath.isBlank()) "Upload SIM" else "SIM Terunggah", color = Color.Gray, textAlign = TextAlign.Center) }
                Box(modifier = Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F3F3)).clickable { viewModel.updateStnkPath("placeholder/stnk.jpg") }, contentAlignment = Alignment.Center) { Text(if (data.stnkDocumentPath.isBlank()) "Upload STNK" else "STNK Terunggah", color = Color.Gray, textAlign = TextAlign.Center) }
            }

            Button(onClick = { onSubmit(viewModel.data) }, enabled = isValid, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kirim Pendaftaran") }
            Text("Menunggu verifikasi admin.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
