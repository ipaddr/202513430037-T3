package com.example.app_jalanin.ui.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.model.Vehicle
import com.example.app_jalanin.data.model.VehicleStatus
import com.example.app_jalanin.data.model.VehicleType
import com.example.app_jalanin.data.model.DriverAvailability
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint

class SewaKendaraanViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val vehicleDao = database.vehicleDao()
    private val userDao = database.userDao()

    // State untuk list kendaraan
    private val _vehicles = MutableStateFlow<List<RentalVehicle>>(emptyList())
    val vehicles: StateFlow<List<RentalVehicle>> = _vehicles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        // ✅ FIX: Download from Firestore FIRST, then load from local DB
        downloadAndLoadAvailableVehicles()
    }

    /**
     * Download all available vehicles from Firestore, then load from local database
     */
    private fun downloadAndLoadAvailableVehicles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                Log.d("SewaKendaraanViewModel", "🔄 Starting download and load for available vehicles...")
                
                // Download from Firestore first
                com.example.app_jalanin.data.remote.FirestoreVehicleService.downloadAllAvailableVehicles(
                    getApplication()
                )
                
                // Add delay to ensure download completes and local DB is updated
                delay(1000)
                
                // Then load from local DB
                loadAvailableVehicles()
                
                Log.d("SewaKendaraanViewModel", "✅ Completed download and load for available vehicles")
            } catch (e: Exception) {
                Log.e("SewaKendaraanViewModel", "❌ Error downloading vehicles: ${e.message}", e)
                // Continue loading from local DB even if download fails
                loadAvailableVehicles()
            }
        }
    }

    /**
     * Load semua kendaraan yang tersedia (status = TERSEDIA) from local database
     */
    private fun loadAvailableVehicles() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("SewaKendaraanViewModel", "Loading available vehicles from local DB...")

                vehicleDao.getAllAvailableVehicles(VehicleStatus.TERSEDIA)
                    .catch { e ->
                        Log.e("SewaKendaraanViewModel", "Error loading vehicles: ${e.message}", e)
                        _errorMessage.value = "Gagal memuat kendaraan: ${e.message}"
                        _isLoading.value = false
                    }
                    .collect { vehicleList ->
                        try {
                            Log.d("SewaKendaraanViewModel", "✅ Loaded ${vehicleList.size} available vehicles")
                            
                            // Convert Vehicle to RentalVehicle with owner name
                            val rentalVehicles = vehicleList.mapNotNull { vehicle ->
                                try {
                                    convertToRentalVehicle(vehicle)
                                } catch (e: Exception) {
                                    Log.e("SewaKendaraanViewModel", "Error converting vehicle ${vehicle.id}: ${e.message}", e)
                                    null // Skip this vehicle if conversion fails
                                }
                            }
                            
                            _vehicles.value = rentalVehicles
                            _isLoading.value = false
                        } catch (e: Exception) {
                            Log.e("SewaKendaraanViewModel", "Error processing vehicles: ${e.message}", e)
                            _errorMessage.value = "Gagal memproses kendaraan: ${e.message}"
                            _isLoading.value = false
                        }
                    }
            } catch (e: Exception) {
                Log.e("SewaKendaraanViewModel", "Error in loadAvailableVehicles: ${e.message}", e)
                _errorMessage.value = "Gagal memuat kendaraan: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Convert Vehicle entity to RentalVehicle with owner name
     */
    private suspend fun convertToRentalVehicle(vehicle: Vehicle): RentalVehicle {
        try {
            // Get owner name from database
            val owner = try {
                userDao.getUserByEmail(vehicle.ownerId)
            } catch (e: Exception) {
                Log.e("SewaKendaraanViewModel", "Error getting owner: ${e.message}", e)
                null
            }
            val ownerName = owner?.fullName ?: vehicle.ownerId.split("@").firstOrNull() ?: "Owner"

            // Build specs string
            val specs = buildString {
                append(vehicle.transmission)
                if (vehicle.type == VehicleType.MOBIL && vehicle.seats != null) {
                    append(" • ${vehicle.seats} Kursi")
                } else if (vehicle.type == VehicleType.MOTOR && vehicle.engineCapacity != null) {
                    append(" • ${vehicle.engineCapacity}")
                }
                if (vehicle.features.isNotBlank() && vehicle.features != "-") {
                    append(" • ${vehicle.features.split(",").firstOrNull()?.trim() ?: ""}")
                }
            }

            // Determine icon
            val icon = when (vehicle.type) {
                VehicleType.MOBIL -> "🚗"
                VehicleType.MOTOR -> "🏍️"
            }

            // Convert type
            val typeString = when (vehicle.type) {
                VehicleType.MOBIL -> "Mobil"
                VehicleType.MOTOR -> "Motor"
            }

            // Safely convert driver availability
            val driverAvailabilityString = vehicle.driverAvailability?.let { availabilityStr ->
                try {
                    // Try to validate the enum value
                    DriverAvailability.valueOf(availabilityStr)
                    availabilityStr // Return original string if valid
                } catch (e: IllegalArgumentException) {
                    Log.w("SewaKendaraanViewModel", "Invalid driver availability: $availabilityStr, using null")
                    null
                } catch (e: Exception) {
                    Log.e("SewaKendaraanViewModel", "Error parsing driver availability: ${e.message}", e)
                    null
                }
            }

            return RentalVehicle(
                id = vehicle.id.toString(),
                name = vehicle.name,
                type = typeString,
                specs = specs,
                pricePerDay = vehicle.pricePerDay.toInt(),
                pricePerWeek = vehicle.pricePerWeek.toInt(),
                pricePerHour = vehicle.pricePerHour.toInt(),
                isAvailable = vehicle.status == VehicleStatus.TERSEDIA,
                location = GeoPoint(vehicle.locationLat, vehicle.locationLon),
                locationName = vehicle.locationAddress,
                icon = icon,
                ownerName = ownerName, // ✅ Add owner name
                ownerEmail = vehicle.ownerId, // ✅ Add owner email
                driverId = vehicle.driverId, // ✅ Driver assignment
                driverAvailability = driverAvailabilityString, // ✅ Driver availability state
                driverAssignmentMode = vehicle.driverAssignmentMode, // ✅ Driver assignment mode
                driverPricePerHour = 0, // Default, bisa ditambahkan di Vehicle entity nanti
                driverPricePerDay = 0,
                driverPricePerWeek = 0
            )
        } catch (e: Exception) {
            Log.e("SewaKendaraanViewModel", "Error converting vehicle ${vehicle.id}: ${e.message}", e)
            throw e // Re-throw to be caught by mapNotNull
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

