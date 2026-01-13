package com.example.app_jalanin.ui.passenger

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.model.PassengerVehicle
import com.example.app_jalanin.data.model.VehicleType
import com.example.app_jalanin.data.model.SimType
import com.example.app_jalanin.data.model.SimCertificationHelper
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.osmdroid.util.GeoPoint
import java.text.NumberFormat
import java.util.*

/**
 * Screen untuk mengelola kendaraan pribadi penumpang
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerVehiclesScreen(
    passengerEmail: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PassengerVehiclesViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )

    // Set passenger email
    LaunchedEffect(passengerEmail) {
        viewModel.setPassengerEmail(passengerEmail)
    }

    // Collect states
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedVehicle by remember { mutableStateOf<PassengerVehicle?>(null) }

    // Show error toast
    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚗 Kendaraan Saya") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tambah Kendaraan") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Kendaraan Pribadi Anda",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kendaraan ini digunakan untuk layanan Sewa Driver",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Info Penting",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Driver harus memiliki SIM yang sesuai dengan jenis kendaraan Anda untuk dapat menjadi Sewa Driver",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Kendaraan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${vehicles.size} kendaraan",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Empty state
            if (!isLoading && vehicles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Belum ada kendaraan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tambahkan kendaraan pribadi Anda untuk menggunakan layanan Sewa Driver",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // Vehicle list
            items(vehicles) { vehicle ->
                PassengerVehicleCard(
                    vehicle = vehicle,
                    onEdit = {
                        selectedVehicle = vehicle
                        showEditDialog = true
                    },
                    onDelete = {
                        selectedVehicle = vehicle
                        showDeleteDialog = true
                    },
                    onToggleStatus = { isActive ->
                        viewModel.updateVehicleStatus(vehicle.id, isActive)
                    }
                )
            }
        }
    }

    // Add Dialog
    if (showAddDialog) {
        AddPassengerVehicleDialog(
            passengerEmail = passengerEmail,
            onDismiss = { showAddDialog = false },
            onConfirm = { vehicle ->
                viewModel.addVehicle(vehicle)
                showAddDialog = false
                Toast.makeText(context, "Kendaraan berhasil ditambahkan", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Dialog
    if (showEditDialog && selectedVehicle != null) {
        EditPassengerVehicleDialog(
            vehicle = selectedVehicle!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { vehicle ->
                viewModel.updateVehicle(vehicle)
                showEditDialog = false
                Toast.makeText(context, "Kendaraan berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog && selectedVehicle != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Kendaraan?") },
            text = {
                Text("Apakah Anda yakin ingin menghapus ${selectedVehicle!!.brand} ${selectedVehicle!!.model}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVehicle(selectedVehicle!!)
                        showDeleteDialog = false
                        Toast.makeText(context, "Kendaraan berhasil dihapus", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * Card untuk menampilkan kendaraan penumpang
 */
@Composable
fun PassengerVehicleCard(
    vehicle: PassengerVehicle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (vehicle.isActive) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (vehicle.type == VehicleType.MOBIL) Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                        contentDescription = null,
                        tint = if (vehicle.isActive) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Column {
                        Text(
                            text = "${vehicle.brand} ${vehicle.model}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = vehicle.licensePlate,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (vehicle.isActive) 
                        Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else 
                        Color(0xFF9E9E9E).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (vehicle.isActive) "Aktif" else "Tidak Aktif",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (vehicle.isActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    )
                }
            }

            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoItem("Tahun", vehicle.year.toString())
                if (vehicle.type == VehicleType.MOBIL && vehicle.seats != null) {
                    InfoItem("Kursi", "${vehicle.seats} kursi")
                } else if (vehicle.type == VehicleType.MOTOR && vehicle.engineCapacity != null) {
                    InfoItem("CC", vehicle.engineCapacity)
                }
                InfoItem(
                    "SIM", 
                    SimCertificationHelper.getRequiredSimType(vehicle.type).name.replace("SIM_", "SIM ")
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onToggleStatus(!vehicle.isActive) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (vehicle.isActive) "Nonaktifkan" else "Aktifkan")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Hapus")
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Dialog untuk menambahkan kendaraan penumpang baru
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPassengerVehicleDialog(
    passengerEmail: String,
    onDismiss: () -> Unit,
    onConfirm: (PassengerVehicle) -> Unit
) {
    var type by remember { mutableStateOf(VehicleType.MOBIL) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var licensePlate by remember { mutableStateOf("") }
    var transmission by remember { mutableStateOf("Manual") }
    var seats by remember { mutableStateOf("") }
    var engineCapacity by remember { mutableStateOf("") }
    var isActive by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚗 Tambah Kendaraan Pribadi") },
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

                // Status
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isActive,
                        onClick = { isActive = true },
                        label = { Text("✅ Aktif") }
                    )
                    FilterChip(
                        selected = !isActive,
                        onClick = { isActive = false },
                        label = { Text("❌ Tidak Aktif") }
                    )
                }

                // Info SIM
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Driver harus memiliki ${SimCertificationHelper.getRequiredSimType(type).name.replace("SIM_", "SIM ")} untuk mengendarai kendaraan ini",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (brand.isBlank() || model.isBlank() || year.isBlank() || licensePlate.isBlank()) {
                        return@Button
                    }
                    if (year.toIntOrNull() == null) {
                        return@Button
                    }
                    if (type == VehicleType.MOBIL && seats.isBlank()) {
                        return@Button
                    }
                    if (type == VehicleType.MOTOR && engineCapacity.isBlank()) {
                        return@Button
                    }

                    val vehicle = PassengerVehicle(
                        passengerId = passengerEmail,
                        type = type,
                        brand = brand.trim(),
                        model = model.trim(),
                        year = year.toIntOrNull() ?: 2024,
                        licensePlate = licensePlate.trim().uppercase(),
                        transmission = transmission,
                        seats = if (type == VehicleType.MOBIL) seats.toIntOrNull() else null,
                        engineCapacity = if (type == VehicleType.MOTOR) engineCapacity.ifBlank { null } else null,
                        isActive = isActive
                    )

                    onConfirm(vehicle)
                },
                enabled = brand.isNotBlank() && model.isNotBlank() && year.isNotBlank() &&
                        licensePlate.isNotBlank() &&
                        (type == VehicleType.MOTOR || seats.isNotBlank()) &&
                        (type == VehicleType.MOBIL || engineCapacity.isNotBlank())
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
}

/**
 * Dialog untuk mengedit kendaraan penumpang
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPassengerVehicleDialog(
    vehicle: PassengerVehicle,
    onDismiss: () -> Unit,
    onConfirm: (PassengerVehicle) -> Unit
) {
    var type by remember { mutableStateOf(vehicle.type) }
    var brand by remember { mutableStateOf(vehicle.brand) }
    var model by remember { mutableStateOf(vehicle.model) }
    var year by remember { mutableStateOf(vehicle.year.toString()) }
    var licensePlate by remember { mutableStateOf(vehicle.licensePlate) }
    var transmission by remember { mutableStateOf(vehicle.transmission ?: "Manual") }
    var seats by remember { mutableStateOf(vehicle.seats?.toString() ?: "") }
    var engineCapacity by remember { mutableStateOf(vehicle.engineCapacity ?: "") }
    var isActive by remember { mutableStateOf(vehicle.isActive) }

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
                // Similar fields as AddPassengerVehicleDialog
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

                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isActive,
                        onClick = { isActive = true },
                        label = { Text("✅ Aktif") }
                    )
                    FilterChip(
                        selected = !isActive,
                        onClick = { isActive = false },
                        label = { Text("❌ Tidak Aktif") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (brand.isBlank() || model.isBlank() || year.isBlank() || licensePlate.isBlank()) {
                        return@Button
                    }
                    if (year.toIntOrNull() == null) {
                        return@Button
                    }

                    val updatedVehicle = vehicle.copy(
                        type = type,
                        brand = brand.trim(),
                        model = model.trim(),
                        year = year.toIntOrNull() ?: vehicle.year,
                        licensePlate = licensePlate.trim().uppercase(),
                        transmission = transmission,
                        seats = if (type == VehicleType.MOBIL) seats.toIntOrNull() else null,
                        engineCapacity = if (type == VehicleType.MOTOR) engineCapacity.ifBlank { null } else null,
                        isActive = isActive,
                        updatedAt = System.currentTimeMillis()
                    )

                    onConfirm(updatedVehicle)
                },
                enabled = brand.isNotBlank() && model.isNotBlank() && year.isNotBlank() &&
                        licensePlate.isNotBlank()
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
}
