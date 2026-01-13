package com.example.app_jalanin.ui.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.auth.UserRole
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.model.DriverRoleHelper
import com.example.app_jalanin.data.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog untuk memilih driver (hanya driver online dengan SIM yang sesuai)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDriverDialog(
    vehicleType: VehicleType,
    passengerEmail: String,
    onDismiss: () -> Unit,
    onDriverSelected: (User) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    var availableDrivers by remember { mutableStateOf<List<User>>(emptyList()) }
    var driverProfilesMap by remember { mutableStateOf<Map<String, com.example.app_jalanin.data.local.entity.DriverProfile>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load online drivers with matching SIM
    LaunchedEffect(vehicleType) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                
                val allDrivers = withContext(Dispatchers.IO) {
                    database.userDao().getUsersByRole(UserRole.DRIVER.name)
                }
                android.util.Log.d("SelectDriverDialog", "📋 Found ${allDrivers.size} total drivers")
                
                // Load driver profiles
                val profiles = withContext(Dispatchers.IO) {
                    database.driverProfileDao().getAll()
                }
                android.util.Log.d("SelectDriverDialog", "📋 Found ${profiles.size} driver profiles")
                
                val profilesMap = profiles.associateBy { it.driverEmail }
                driverProfilesMap = profilesMap
                
                // Log all driver profiles for debugging
                profiles.forEach { profile ->
                    android.util.Log.d("SelectDriverDialog", "👤 Driver: ${profile.driverEmail}, isOnline: ${profile.isOnline}, SIM: '${profile.simCertifications}'")
                }
                
                // Filter: Only online drivers with matching SIM
                android.util.Log.d("SelectDriverDialog", "🔍 Filtering drivers for vehicle type: ${vehicleType.name}")
                val filtered = DriverRoleHelper.filterAvailableDrivers(allDrivers, vehicleType, profilesMap)
                availableDrivers = filtered
                
                isLoading = false
                android.util.Log.d("SelectDriverDialog", "✅ Loaded ${filtered.size} available drivers for ${vehicleType.name}")
                filtered.forEach { driver ->
                    android.util.Log.d("SelectDriverDialog", "✅ Available driver: ${driver.email} (${driver.fullName})")
                }
            } catch (e: Exception) {
                android.util.Log.e("SelectDriverDialog", "Error loading drivers: ${e.message}", e)
                errorMessage = "Error loading drivers: ${e.message}"
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
                Icon(Icons.Default.Person, contentDescription = null)
                Text("Pilih Driver")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info card
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
                            text = "Hanya menampilkan driver yang sedang ONLINE dengan SIM yang sesuai",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage ?: "Error",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }
                } else if (availableDrivers.isEmpty()) {
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
                                Icons.Default.PersonOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Tidak ada driver tersedia",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Tidak ada driver online dengan SIM yang sesuai untuk ${vehicleType.name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableDrivers) { driver ->
                            val driverProfile = driverProfilesMap[driver.email]
                            DriverSelectionItem(
                                driver = driver,
                                vehicleType = vehicleType,
                                driverProfile = driverProfile,
                                onClick = {
                                    onDriverSelected(driver)
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
private fun DriverSelectionItem(
    driver: User,
    vehicleType: VehicleType,
    driverProfile: com.example.app_jalanin.data.local.entity.DriverProfile?,
    onClick: () -> Unit
) {
    val simTypes = driverProfile?.simCertifications?.split(",")?.mapNotNull { 
        try { com.example.app_jalanin.data.model.SimType.valueOf(it.trim()) } 
        catch (e: Exception) { null }
        } ?: emptyList()
    
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
            // Online status indicator
            Box(
                        modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
            
            Column(modifier = Modifier.weight(1f)) {
                    Text(
                    text = driver.fullName ?: driver.email,
                        fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = driver.email,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                    ) {
                    simTypes.forEach { simType ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = simType.name.replace("SIM_", "SIM "),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
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
