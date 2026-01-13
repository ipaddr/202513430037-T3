package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.model.PassengerVehicle
import com.example.app_jalanin.data.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog untuk memilih kendaraan pribadi penumpang
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPassengerVehicleDialog(
    passengerEmail: String,
    vehicleType: VehicleType? = null, // Filter by type if specified
    onDismiss: () -> Unit,
    onVehicleSelected: (PassengerVehicle) -> Unit,
    onAddVehicle: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var passengerVehicles by remember { mutableStateOf<List<PassengerVehicle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load passenger vehicles
    LaunchedEffect(passengerEmail, vehicleType) {
        if (passengerEmail.isEmpty()) return@LaunchedEffect
        
        scope.launch {
            try {
                isLoading = true
                
                // Use Flow to get real-time updates
                database.passengerVehicleDao().getActiveVehiclesByPassenger(passengerEmail)
                    .collect { vehicleList ->
                        // Filter by type if specified
                        val filtered = if (vehicleType != null) {
                            vehicleList.filter { it.type == vehicleType && it.isActive }
        } else {
                            vehicleList.filter { it.isActive }
                        }
                        
                        passengerVehicles = filtered
                        isLoading = false
                        android.util.Log.d("SelectPassengerVehicleDialog", "✅ Loaded ${filtered.size} vehicles")
                    }
            } catch (e: Exception) {
                android.util.Log.e("SelectPassengerVehicleDialog", "Error loading vehicles: ${e.message}", e)
                isLoading = false
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                Text("Pilih Kendaraan Pribadi")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (passengerVehicles.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Belum ada kendaraan pribadi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tambahkan kendaraan pribadi Anda terlebih dahulu",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Button(
                                onClick = onAddVehicle,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tambah Kendaraan")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(passengerVehicles) { vehicle ->
                            PassengerVehicleSelectionItem(
                                vehicle = vehicle,
                                onClick = {
                                    onVehicleSelected(vehicle)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        dismissButton = null
    )
}

@Composable
private fun PassengerVehicleSelectionItem(
    vehicle: PassengerVehicle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (vehicle.type == VehicleType.MOBIL) Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                        contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
                    )
            Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${vehicle.brand} ${vehicle.model}",
                        fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = vehicle.licensePlate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tahun: ${vehicle.year}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (vehicle.type == VehicleType.MOBIL && vehicle.seats != null) {
                            Text(
                            text = "• ${vehicle.seats} Kursi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        } else if (vehicle.type == VehicleType.MOTOR && vehicle.engineCapacity != null) {
                            Text(
                                text = "• ${vehicle.engineCapacity}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
