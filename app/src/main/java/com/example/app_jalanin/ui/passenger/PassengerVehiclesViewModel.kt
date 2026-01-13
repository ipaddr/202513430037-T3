package com.example.app_jalanin.ui.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.model.PassengerVehicle
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class PassengerVehiclesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.passengerVehicleDao()

    private val _passengerEmail = MutableStateFlow<String?>(null)

    // State untuk list kendaraan
    private val _vehicles = MutableStateFlow<List<PassengerVehicle>>(emptyList())
    val vehicles: StateFlow<List<PassengerVehicle>> = _vehicles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun setPassengerEmail(email: String) {
        _passengerEmail.value = email
        // ✅ FIX: Download from Firestore FIRST, then load from local DB
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("PassengerVehiclesViewModel", "🔄 Starting download and load for passenger: $email")
                // Download from Firestore first
                com.example.app_jalanin.data.remote.FirestorePassengerVehicleSyncManager.downloadPassengerVehicles(
                    getApplication(),
                    email
                )
                // Add delay to ensure download completes
                delay(1000)
                // Then load from local DB
                loadVehicles()
                android.util.Log.d("PassengerVehiclesViewModel", "✅ Completed download and load for passenger: $email")
            } catch (e: Exception) {
                android.util.Log.e("PassengerVehiclesViewModel", "❌ Error downloading vehicles: ${e.message}", e)
                // Continue loading from local DB even if download fails
                loadVehicles()
            }
        }
    }

    private fun loadVehicles() {
        val email = _passengerEmail.value ?: return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("PassengerVehiclesViewModel", "Loading vehicles for passenger: $email")

                dao.getAllVehiclesByPassenger(email)
                    .collect { vehicleList ->
                        _vehicles.value = vehicleList
                        _isLoading.value = false
                        Log.d("PassengerVehiclesViewModel", "✅ Loaded ${vehicleList.size} vehicles")
                    }
            } catch (e: Exception) {
                Log.e("PassengerVehiclesViewModel", "❌ Error loading vehicles: ${e.message}", e)
                _errorMessage.value = "Error loading vehicles: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun addVehicle(vehicle: PassengerVehicle) {
        viewModelScope.launch {
            try {
                val vehicleId = dao.insertVehicle(vehicle)
                val vehicleWithId = vehicle.copy(id = vehicleId.toInt())
                Log.d("PassengerVehiclesViewModel", "✅ Vehicle added: ${vehicle.licensePlate}")
                
                // Sync to Firestore
                try {
                    com.example.app_jalanin.data.remote.FirestorePassengerVehicleSyncManager.syncVehicle(
                        vehicleWithId,
                        getApplication()
                    )
                    Log.d("PassengerVehiclesViewModel", "✅ Vehicle synced to Firestore")
                } catch (e: Exception) {
                    Log.e("PassengerVehiclesViewModel", "❌ Error syncing vehicle to Firestore: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("PassengerVehiclesViewModel", "❌ Error adding vehicle: ${e.message}", e)
                _errorMessage.value = "Error adding vehicle: ${e.message}"
            }
        }
    }

    fun updateVehicle(vehicle: PassengerVehicle) {
        viewModelScope.launch {
            try {
                val updatedVehicle = vehicle.copy(updatedAt = System.currentTimeMillis(), synced = false)
                dao.updateVehicle(updatedVehicle)
                Log.d("PassengerVehiclesViewModel", "✅ Vehicle updated: ${vehicle.licensePlate}")
                
                // Sync to Firestore
                try {
                    com.example.app_jalanin.data.remote.FirestorePassengerVehicleSyncManager.syncVehicle(
                        updatedVehicle,
                        getApplication()
                    )
                    Log.d("PassengerVehiclesViewModel", "✅ Vehicle synced to Firestore")
                } catch (e: Exception) {
                    Log.e("PassengerVehiclesViewModel", "❌ Error syncing vehicle to Firestore: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("PassengerVehiclesViewModel", "❌ Error updating vehicle: ${e.message}", e)
                _errorMessage.value = "Error updating vehicle: ${e.message}"
            }
        }
    }

    fun deleteVehicle(vehicle: PassengerVehicle) {
        viewModelScope.launch {
            try {
                dao.deleteVehicle(vehicle)
                Log.d("PassengerVehiclesViewModel", "✅ Vehicle deleted: ${vehicle.licensePlate}")
            } catch (e: Exception) {
                Log.e("PassengerVehiclesViewModel", "❌ Error deleting vehicle: ${e.message}", e)
                _errorMessage.value = "Error deleting vehicle: ${e.message}"
            }
        }
    }

    fun updateVehicleStatus(vehicleId: Int, isActive: Boolean) {
        viewModelScope.launch {
            try {
                dao.updateVehicleStatus(vehicleId, isActive, System.currentTimeMillis())
                Log.d("PassengerVehiclesViewModel", "✅ Vehicle status updated: $isActive")
            } catch (e: Exception) {
                Log.e("PassengerVehiclesViewModel", "❌ Error updating status: ${e.message}", e)
                _errorMessage.value = "Error updating status: ${e.message}"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
