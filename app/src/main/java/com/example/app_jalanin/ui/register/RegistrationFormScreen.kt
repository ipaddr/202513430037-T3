package com.example.app_jalanin.ui.register

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationFormScreen(
    typeId: Int,
    viewModel: RegistrationFormViewModel,
    onBack: () -> Unit = {},
    onSubmit: (DriverRegistrationData) -> Unit = {}
) {
    // if incoming type invalid, still allow user to choose here
    val initialType = if (typeId in 1..4) typeId else 1
    var selectedType by remember { mutableStateOf(initialType) }

    // initialize VM with selected type
    viewModel.initType(selectedType)
    val data = viewModel.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registrasi " + when(selectedType){1->"Driver Motor";2->"Driver Mobil";3->"Driver Pengganti";4->"Pemilik Kendaraan";else->"Akun"}) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // List terpisah untuk tipe akun
            Text(text = "Pilih Tipe Akun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountTypeCard(title = "Driver", desc = "Mengantarkan penumpang", selected = selectedType in listOf(1,2,3)) {
                    selectedType = 1; viewModel.initType(1)
                }
                AccountTypeCard(title = "Pemilik Kendaraan Sewa", desc = "Sewakan kendaraan Anda", selected = selectedType == 4) {
                    selectedType = 4; viewModel.initType(4)
                }
                AccountTypeCard(title = "Penumpang", desc = "Pengguna layanan", selected = false) {
                    // Untuk penumpang, bisa arahkan ke form berbeda jika ada
                    // Di sini kita tetap pakai data umum
                    selectedType = 1; viewModel.initType(1)
                }
            }

            HorizontalDivider()

            // Field umum
            OutlinedTextField(
                value = data.fullName,
                onValueChange = viewModel::updateFullName,
                label = { Text("Nama Lengkap") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = data.phone,
                onValueChange = viewModel::updatePhone,
                label = { Text("No. Telepon") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = data.email,
                onValueChange = viewModel::updateEmail,
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = data.address,
                onValueChange = viewModel::updateAddress,
                label = { Text("Alamat") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = data.idCardNumber,
                onValueChange = viewModel::updateIdCard,
                label = { Text("No. KTP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Field spesifik menurut tipe
            when (selectedType) {
                1 -> {
                    OutlinedTextField(
                        value = data.motorPlate,
                        onValueChange = viewModel::updateMotorPlate,
                        label = { Text("Plat Motor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                2 -> {
                    OutlinedTextField(
                        value = data.carPlate,
                        onValueChange = viewModel::updateCarPlate,
                        label = { Text("Plat Mobil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                3 -> {
                    OutlinedTextField(
                        value = data.shiftAvailability,
                        onValueChange = viewModel::updateShift,
                        label = { Text("Ketersediaan Shift") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                4 -> {
                    OutlinedTextField(
                        value = data.fleetSize,
                        onValueChange = viewModel::updateFleetSize,
                        label = { Text("Jumlah Armada") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = { onSubmit(viewModel.data) },
                enabled = viewModel.isValid(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kirim") }
        }
    }
}

@Composable
private fun AccountTypeCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Simple placeholder icon using built-in avatar
            Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
