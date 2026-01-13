package com.example.app_jalanin.ui.owner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.app_jalanin.data.model.Vehicle
import com.example.app_jalanin.data.model.VehicleStatus
import com.example.app_jalanin.data.model.VehicleType
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleDialog(
    ownerEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (Vehicle) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(VehicleType.MOBIL) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var transmission by remember { mutableStateOf("Manual") }
    var seats by remember { mutableStateOf("") }
    var engineCapacity by remember { mutableStateOf("") }
    var pricePerHour by remember { mutableStateOf("") }
    var pricePerDay by remember { mutableStateOf("") }
    var pricePerWeek by remember { mutableStateOf("") }
    var features by remember { mutableStateOf("") }
    var locationAddress by remember { mutableStateOf("") }
    var locationGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(VehicleStatus.TERSEDIA) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚗 Tambah Kendaraan Baru") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Jenis Kendaraan
                Text("Jenis Kendaraan", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == VehicleType.MOBIL,
                        onClick = { type = VehicleType.MOBIL },
                        label = { Text("🚗 Mobil") }
                    )
                    FilterChip(
                        selected = type == VehicleType.MOTOR,
                        onClick = { type = VehicleType.MOTOR },
                        label = { Text("🏍️ Motor") }
                    )
                }

                // Nama Kendaraan
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Kendaraan") },
                    placeholder = { Text("Toyota Avanza 2022") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Brand & Model
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Merek") },
                        placeholder = { Text("Toyota") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        placeholder = { Text("Avanza") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Tahun & Plat Nomor
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Tahun") },
                        placeholder = { Text("2022") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = licensePlate,
                        onValueChange = { licensePlate = it },
                        label = { Text("Plat Nomor") },
                        placeholder = { Text("B 1234 XYZ") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Transmisi
                Text("Transmisi", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transmission == "Manual",
                        onClick = { transmission = "Manual" },
                        label = { Text("Manual") }
                    )
                    FilterChip(
                        selected = transmission == "Automatic",
                        onClick = { transmission = "Automatic" },
                        label = { Text("Automatic") }
                    )
                }

                // Seats (untuk mobil) atau Engine Capacity (untuk motor)
                if (type == VehicleType.MOBIL) {
                    OutlinedTextField(
                        value = seats,
                        onValueChange = { seats = it },
                        label = { Text("Jumlah Kursi") },
                        placeholder = { Text("7") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = engineCapacity,
                        onValueChange = { engineCapacity = it },
                        label = { Text("Kapasitas Mesin") },
                        placeholder = { Text("150cc") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Harga
                OutlinedTextField(
                    value = pricePerHour,
                    onValueChange = { pricePerHour = it },
                    label = { Text("Harga Per Jam (Rp)") },
                    placeholder = { Text("50000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pricePerDay,
                    onValueChange = { pricePerDay = it },
                    label = { Text("Harga Per Hari (Rp)") },
                    placeholder = { Text("300000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pricePerWeek,
                    onValueChange = { pricePerWeek = it },
                    label = { Text("Harga Per Minggu (Rp)") },
                    placeholder = { Text("1800000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Fitur
                OutlinedTextField(
                    value = features,
                    onValueChange = { features = it },
                    label = { Text("Fitur") },
                    placeholder = { Text("AC, GPS, USB Charger") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Lokasi dengan Location Picker
                Text("Lokasi Kendaraan", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = locationAddress,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Alamat Lokasi") },
                    placeholder = { Text("Klik untuk pilih lokasi di peta...") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showLocationPicker = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Pilih lokasi")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLocationPicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color(0xFF333333),
                        disabledBorderColor = Color(0xFFCCCCCC),
                        disabledLabelColor = Color(0xFF666666)
                    )
                )
                if (locationAddress.isEmpty()) {
                    Text(
                        text = "💡 Klik 'Pilih' untuk memilih lokasi dengan GPS, search, atau tap peta",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                }

                // Status
                Text("Status Awal", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == VehicleStatus.TERSEDIA,
                        onClick = { status = VehicleStatus.TERSEDIA },
                        label = { Text("✅ Siap Sewa") }
                    )
                    FilterChip(
                        selected = status == VehicleStatus.TIDAK_TERSEDIA,
                        onClick = { status = VehicleStatus.TIDAK_TERSEDIA },
                        label = { Text("🔧 Belum Siap") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // ✅ Validasi lengkap
                    if (name.isBlank()) {
                        return@Button
                    }
                    if (brand.isBlank()) {
                        return@Button
                    }
                    if (year.isBlank() || year.toIntOrNull() == null) {
                        return@Button
                    }
                    if (licensePlate.isBlank()) {
                        return@Button
                    }
                    if (pricePerHour.isBlank() || pricePerHour.toDoubleOrNull() == null || pricePerHour.toDoubleOrNull()!! <= 0) {
                        return@Button
                    }
                    if (pricePerDay.isBlank() || pricePerDay.toDoubleOrNull() == null || pricePerDay.toDoubleOrNull()!! <= 0) {
                        return@Button
                    }
                    if (pricePerWeek.isBlank() || pricePerWeek.toDoubleOrNull() == null || pricePerWeek.toDoubleOrNull()!! <= 0) {
                        return@Button
                    }
                    if (locationGeoPoint == null) {
                        return@Button
                    }

                    // ✅ Buat vehicle object dengan data yang valid
                    val finalLocation = locationGeoPoint!!
                    
                    val vehicle = Vehicle(
                        ownerId = ownerEmail,
                        name = name.trim(),
                        type = type,
                        brand = brand.trim(),
                        model = model.trim(),
                        year = year.toIntOrNull() ?: 2024,
                        licensePlate = licensePlate.trim().uppercase(),
                        transmission = transmission,
                        seats = if (type == VehicleType.MOBIL) seats.toIntOrNull() else null,
                        engineCapacity = if (type == VehicleType.MOTOR) engineCapacity.ifBlank { null } else null,
                        pricePerHour = pricePerHour.toDoubleOrNull() ?: 0.0,
                        pricePerDay = pricePerDay.toDoubleOrNull() ?: 0.0,
                        pricePerWeek = pricePerWeek.toDoubleOrNull() ?: 0.0,
                        features = features.trim().ifBlank { "-" },
                        status = status,
                        locationLat = finalLocation.latitude,
                        locationLon = finalLocation.longitude,
                        locationAddress = locationAddress.trim().ifBlank { "Lokasi dipilih" }
                    )

                    onConfirm(vehicle)
                },
                enabled = name.isNotBlank() && brand.isNotBlank() && year.isNotBlank() &&
                        licensePlate.isNotBlank() && pricePerHour.isNotBlank() &&
                        pricePerDay.isNotBlank() && pricePerWeek.isNotBlank() &&
                        locationAddress.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )

    // Location Picker Dialog
    if (showLocationPicker) {
        VehicleLocationPickerDialog(
            initialLocation = locationGeoPoint,
            onLocationSelected = { locationResult: com.example.app_jalanin.ui.owner.LocationResult ->
                locationGeoPoint = locationResult.geoPoint
                locationAddress = locationResult.address
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVehicleDialog(
    vehicle: Vehicle,
    onDismiss: () -> Unit,
    onConfirm: (Vehicle) -> Unit
) {
    var name by remember { mutableStateOf(vehicle.name) }
    var type by remember { mutableStateOf(vehicle.type) }
    var brand by remember { mutableStateOf(vehicle.brand) }
    var model by remember { mutableStateOf(vehicle.model) }
    var year by remember { mutableStateOf(vehicle.year.toString()) }
    var licensePlate by remember { mutableStateOf(vehicle.licensePlate) }
    var transmission by remember { mutableStateOf(vehicle.transmission) }
    var seats by remember { mutableStateOf(vehicle.seats?.toString() ?: "") }
    var engineCapacity by remember { mutableStateOf(vehicle.engineCapacity ?: "") }
    var pricePerHour by remember { mutableStateOf(vehicle.pricePerHour.toString()) }
    var pricePerDay by remember { mutableStateOf(vehicle.pricePerDay.toString()) }
    var pricePerWeek by remember { mutableStateOf(vehicle.pricePerWeek.toString()) }
    var features by remember { mutableStateOf(vehicle.features) }
    var locationAddress by remember { mutableStateOf(vehicle.locationAddress) }
    var locationGeoPoint by remember { mutableStateOf<GeoPoint?>(GeoPoint(vehicle.locationLat, vehicle.locationLon)) }
    var showLocationPicker by remember { mutableStateOf(false) }
    

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Edit Kendaraan") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Similar fields as AddVehicleDialog
                Text("Jenis Kendaraan", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == VehicleType.MOBIL,
                        onClick = { type = VehicleType.MOBIL },
                        label = { Text("🚗 Mobil") }
                    )
                    FilterChip(
                        selected = type == VehicleType.MOTOR,
                        onClick = { type = VehicleType.MOTOR },
                        label = { Text("🏍️ Motor") }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Kendaraan") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Merek") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Tahun") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = licensePlate,
                        onValueChange = { licensePlate = it },
                        label = { Text("Plat Nomor") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Transmisi", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transmission == "Manual",
                        onClick = { transmission = "Manual" },
                        label = { Text("Manual") }
                    )
                    FilterChip(
                        selected = transmission == "Automatic",
                        onClick = { transmission = "Automatic" },
                        label = { Text("Automatic") }
                    )
                }

                if (type == VehicleType.MOBIL) {
                    OutlinedTextField(
                        value = seats,
                        onValueChange = { seats = it },
                        label = { Text("Jumlah Kursi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = engineCapacity,
                        onValueChange = { engineCapacity = it },
                        label = { Text("Kapasitas Mesin") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = pricePerHour,
                    onValueChange = { pricePerHour = it },
                    label = { Text("Harga Per Jam (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pricePerDay,
                    onValueChange = { pricePerDay = it },
                    label = { Text("Harga Per Hari (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pricePerWeek,
                    onValueChange = { pricePerWeek = it },
                    label = { Text("Harga Per Minggu (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = features,
                    onValueChange = { features = it },
                    label = { Text("Fitur") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Lokasi dengan Location Picker
                Text("Lokasi Kendaraan", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = locationAddress,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Alamat Lokasi") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    trailingIcon = {
                        TextButton(onClick = { showLocationPicker = true }) {
                            Text("Ubah", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color(0xFF333333),
                        disabledBorderColor = Color(0xFFCCCCCC),
                        disabledLabelColor = Color(0xFF666666)
                    )
                )
                if (locationAddress.isEmpty()) {
                    Text(
                        text = "💡 Klik 'Ubah' untuk memilih lokasi dengan GPS, search, atau tap peta",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // ✅ Validasi lengkap
                    if (name.isBlank()) {
                        return@Button
                    }
                    if (brand.isBlank()) {
                        return@Button
                    }
                    if (year.isBlank() || year.toIntOrNull() == null) {
                        return@Button
                    }
                    if (licensePlate.isBlank()) {
                        return@Button
                    }
                    if (pricePerHour.isBlank() || pricePerHour.toDoubleOrNull() == null || pricePerHour.toDoubleOrNull()!! <= 0) {
                        return@Button
                    }
                    if (pricePerDay.isBlank() || pricePerDay.toDoubleOrNull() == null || pricePerDay.toDoubleOrNull()!! <= 0) {
                        return@Button
                    }
                    if (pricePerWeek.isBlank() || pricePerWeek.toDoubleOrNull() == null || pricePerWeek.toDoubleOrNull()!! <= 0) {
                        return@Button
                    }
                    if (locationGeoPoint == null) {
                        return@Button
                    }

                    val finalLocation = locationGeoPoint!!
                    
                    // ✅ Update vehicle dengan data yang valid
                    // ✅ NOTE: Driver assignment is NOT changed here - use AssignDriverScreen instead
                    val updatedVehicle = vehicle.copy(
                        name = name.trim(),
                        type = type,
                        brand = brand.trim(),
                        model = model.trim(),
                        year = year.toIntOrNull() ?: vehicle.year,
                        licensePlate = licensePlate.trim().uppercase(),
                        transmission = transmission,
                        seats = if (type == VehicleType.MOBIL) seats.toIntOrNull() else null,
                        engineCapacity = if (type == VehicleType.MOTOR) engineCapacity.ifBlank { null } else null,
                        pricePerHour = pricePerHour.toDoubleOrNull() ?: vehicle.pricePerHour,
                        pricePerDay = pricePerDay.toDoubleOrNull() ?: vehicle.pricePerDay,
                        pricePerWeek = pricePerWeek.toDoubleOrNull() ?: vehicle.pricePerWeek,
                        features = features.trim().ifBlank { "-" },
                        locationLat = finalLocation.latitude,
                        locationLon = finalLocation.longitude,
                        locationAddress = locationAddress.trim().ifBlank { vehicle.locationAddress },
                        updatedAt = System.currentTimeMillis()
                        // ✅ NOTE: driverId, driverAvailability, and driverAssignmentMode are NOT modified here
                        // Use AssignDriverScreen to manage driver assignments
                    )

                    onConfirm(updatedVehicle)
                },
                enabled = name.isNotBlank() && brand.isNotBlank() && year.isNotBlank() &&
                        licensePlate.isNotBlank() && pricePerHour.isNotBlank() &&
                        pricePerDay.isNotBlank() && pricePerWeek.isNotBlank() &&
                        locationGeoPoint != null
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )

    // Location Picker Dialog
    if (showLocationPicker) {
        VehicleLocationPickerDialog(
            initialLocation = locationGeoPoint,
            onLocationSelected = { locationResult: com.example.app_jalanin.ui.owner.LocationResult ->
                locationGeoPoint = locationResult.geoPoint
                locationAddress = locationResult.address
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
    }
}

