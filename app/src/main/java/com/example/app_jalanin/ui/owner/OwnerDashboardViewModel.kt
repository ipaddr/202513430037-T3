package com.example.app_jalanin.ui.owner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.model.Vehicle
import com.example.app_jalanin.data.model.VehicleStatus
import com.example.app_jalanin.data.remote.FirestoreVehicleService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class OwnerDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val dao = database.vehicleDao()

    private val _ownerEmail = MutableStateFlow<String?>(null)

    // State untuk statistik
    private val _countTersedia = MutableStateFlow(0)
    val countTersedia: StateFlow<Int> = _countTersedia

    private val _countSedangDisewa = MutableStateFlow(0)
    val countSedangDisewa: StateFlow<Int> = _countSedangDisewa

    private val _countTidakTersedia = MutableStateFlow(0)
    val countTidakTersedia: StateFlow<Int> = _countTidakTersedia

    // List kendaraan
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    fun setOwnerEmail(email: String) {
        _ownerEmail.value = email
        // ✅ NEW: Download vehicles from Firestore first, then load from local DB
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("OwnerDashboardViewModel", "🔄 Starting download and load for owner: $email")
            downloadVehiclesFromFirestore(email)
            // Add delay to ensure download completes and local DB is updated
            kotlinx.coroutines.delay(1000)
            // Load vehicles after download completes
            loadVehicles()
            loadStatistics()
            android.util.Log.d("OwnerDashboardViewModel", "✅ Completed download and load for owner: $email")
        }
    }

    /**
     * Download vehicles from Firestore to local database
     */
    private suspend fun downloadVehiclesFromFirestore(ownerEmail: String) {
        try {
            android.util.Log.d("OwnerDashboardViewModel", "📥 Downloading vehicles from Firestore for owner: $ownerEmail")
            val downloadedCount = FirestoreVehicleService.downloadVehiclesByOwner(
                getApplication(),
                ownerEmail
            )
            android.util.Log.d("OwnerDashboardViewModel", "✅ Downloaded $downloadedCount vehicles from Firestore")
            
            // ✅ FIX: After downloading, update vehicle status based on active rentals
            updateVehicleStatusBasedOnRentals(ownerEmail)
        } catch (e: Exception) {
            android.util.Log.e("OwnerDashboardViewModel", "❌ Error downloading vehicles from Firestore: ${e.message}", e)
            // Non-critical: Continue loading from local DB even if Firestore download fails
        }
    }

    private fun loadVehicles() {
        val email = _ownerEmail.value ?: return

        viewModelScope.launch {
            try {
                android.util.Log.d("OwnerDashboardViewModel", "📋 Loading vehicles from local DB for owner: $email")
                
                // ✅ FIX: Update vehicle status based on active rentals BEFORE loading
                withContext(Dispatchers.IO) {
                    updateVehicleStatusBasedOnRentals(email)
                }
                
                // First, check how many vehicles exist in local DB (synchronous check)
                val allVehicles = withContext(Dispatchers.IO) {
                    dao.getAllVehiclesByOwnerSync(email)
                }
                android.util.Log.d("OwnerDashboardViewModel", "📊 Found ${allVehicles.size} vehicles in local DB for owner: $email")
                if (allVehicles.isNotEmpty()) {
                    allVehicles.forEach { vehicle ->
                        android.util.Log.d("OwnerDashboardViewModel", "   - Vehicle ID: ${vehicle.id}, Name: ${vehicle.name}, Owner: ${vehicle.ownerId}, Status: ${vehicle.status}")
                    }
                } else {
                    android.util.Log.w("OwnerDashboardViewModel", "⚠️ No vehicles found in local DB for owner: $email")
                    android.util.Log.w("OwnerDashboardViewModel", "💡 TIP: Add a new vehicle through the app, it will automatically sync to Firestore")
                }
                
                // Then subscribe to Flow for real-time updates
                dao.getAllVehiclesByOwner(email)
                    .catch { e ->
                        android.util.Log.e("OwnerDashboardViewModel", "Error loading vehicles: ${e.message}", e)
                        _errorMessage.value = "Gagal memuat kendaraan: ${e.message}"
                    }
                    .collect { vehicleList ->
                        _vehicles.value = vehicleList
                        android.util.Log.d("OwnerDashboardViewModel", "✅ Loaded ${vehicleList.size} vehicles for owner: $email")
                    }
            } catch (e: Exception) {
                android.util.Log.e("OwnerDashboardViewModel", "Error in loadVehicles: ${e.message}", e)
                _errorMessage.value = "Gagal memuat kendaraan: ${e.message}"
            }
        }
    }
    
    /**
     * ✅ NEW: Update vehicle status based on active rentals
     * - If vehicle has an ACTIVE rental, set status to SEDANG_DISEWA
     * - If vehicle has no active rental, set status to TERSEDIA (if not manually set to TIDAK_TERSEDIA)
     */
    private suspend fun updateVehicleStatusBasedOnRentals(ownerEmail: String) {
        try {
            val rentalDao = database.rentalDao()
            val vehicles = dao.getAllVehiclesByOwnerSync(ownerEmail)
            
            android.util.Log.d("OwnerDashboardViewModel", "🔄 Updating vehicle status based on active rentals for ${vehicles.size} vehicles")
            
            // Get all active rentals for this owner
            // Active rentals include: ACTIVE, DRIVER_TRAVELING, OVERDUE, and other in-progress statuses
            val activeRentals = rentalDao.getActiveRentalsByOwner(ownerEmail)
            android.util.Log.d("OwnerDashboardViewModel", "📊 Found ${activeRentals.size} active rentals for owner: $ownerEmail")
            
            // Also check for rentals with status that indicate vehicle is in use
            val allRentals = rentalDao.getRentalsByOwner(ownerEmail)
            val inUseRentals = allRentals.filter { rental ->
                rental.status in listOf("ACTIVE", "DRIVER_TRAVELING", "OVERDUE", "DRIVER_TO_PASSENGER", "ARRIVED")
            }
            android.util.Log.d("OwnerDashboardViewModel", "📊 Found ${inUseRentals.size} rentals in use (including delivery/travel status)")
            
            // Create a map of vehicleId -> hasActiveRental (combine both active and in-use rentals)
            val vehicleIdToActiveRental = (activeRentals + inUseRentals).associate { rental ->
                // vehicleId in Rental is String, need to convert to Int
                val vehicleIdInt = rental.vehicleId.toIntOrNull() ?: 0
                vehicleIdInt to true
            }
            
            vehicles.forEach { vehicle ->
                val hasActiveRental = vehicleIdToActiveRental[vehicle.id] == true
                val currentStatus = vehicle.status
                
                when {
                    hasActiveRental && currentStatus != VehicleStatus.SEDANG_DISEWA -> {
                        // Vehicle has active rental but status is not SEDANG_DISEWA
                        android.util.Log.d("OwnerDashboardViewModel", "🔄 Updating vehicle ${vehicle.id} (${vehicle.name}) to SEDANG_DISEWA (has active rental)")
                        dao.updateVehicleStatus(
                            vehicleId = vehicle.id,
                            status = VehicleStatus.SEDANG_DISEWA,
                            reason = "Kendaraan sedang disewa",
                            updatedAt = System.currentTimeMillis()
                        )
                        
                        // Sync to Firestore
                        val updatedVehicle = vehicle.copy(
                            status = VehicleStatus.SEDANG_DISEWA,
                            statusReason = "Kendaraan sedang disewa",
                            updatedAt = System.currentTimeMillis()
                        )
                        try {
                            FirestoreVehicleService.syncVehicle(updatedVehicle)
                            android.util.Log.d("OwnerDashboardViewModel", "✅ Synced vehicle status to Firestore")
                        } catch (e: Exception) {
                            android.util.Log.e("OwnerDashboardViewModel", "❌ Error syncing vehicle status: ${e.message}", e)
                        }
                    }
                    !hasActiveRental && currentStatus == VehicleStatus.SEDANG_DISEWA -> {
                        // ✅ FIX: Vehicle has no active rental but status is still SEDANG_DISEWA
                        // Update regardless of statusReason to fix stale data in Firestore
                        android.util.Log.d("OwnerDashboardViewModel", "🔄 Updating vehicle ${vehicle.id} (${vehicle.name}) to TERSEDIA (no active rental, was: ${vehicle.status})")
                        dao.updateVehicleStatus(
                            vehicleId = vehicle.id,
                            status = VehicleStatus.TERSEDIA,
                            reason = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        
                        // ✅ FIX: Always sync to Firestore to fix stale data
                        val updatedVehicle = vehicle.copy(
                            status = VehicleStatus.TERSEDIA,
                            statusReason = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        try {
                            FirestoreVehicleService.syncVehicle(updatedVehicle)
                            android.util.Log.d("OwnerDashboardViewModel", "✅ Synced vehicle status to Firestore (fixed from SEDANG_DISEWA to TERSEDIA)")
                        } catch (e: Exception) {
                            android.util.Log.e("OwnerDashboardViewModel", "❌ Error syncing vehicle status: ${e.message}", e)
                        }
                    }
                }
            }
            
            android.util.Log.d("OwnerDashboardViewModel", "✅ Completed vehicle status update based on rentals")
        } catch (e: Exception) {
            android.util.Log.e("OwnerDashboardViewModel", "❌ Error updating vehicle status based on rentals: ${e.message}", e)
        }
    }

    private fun loadStatistics() {
        val email = _ownerEmail.value ?: return

        viewModelScope.launch {
            try {
                // ✅ FIX: Update vehicle status before loading statistics
                withContext(Dispatchers.IO) {
                    updateVehicleStatusBasedOnRentals(email)
                }
                
                _countTersedia.value = dao.countVehiclesByStatus(email, VehicleStatus.TERSEDIA)
                _countSedangDisewa.value = dao.countVehiclesByStatus(email, VehicleStatus.SEDANG_DISEWA)
                _countTidakTersedia.value = dao.countVehiclesByStatus(email, VehicleStatus.TIDAK_TERSEDIA)
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memuat statistik: ${e.message}"
            }
        }
    }

    fun addVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // ✅ CRITICAL: Ensure ownerId is set correctly from current owner email
                val currentOwnerEmail = _ownerEmail.value
                if (currentOwnerEmail.isNullOrBlank()) {
                    Log.e("OwnerDashboardViewModel", "❌ ERROR: ownerEmail is null or blank!")
                    _errorMessage.value = "Gagal: Email owner tidak ditemukan"
                    return@launch
                }
                
                // ✅ FIX: Always use current owner email, not vehicle.ownerId (which might be wrong)
                val vehicleWithCorrectOwner = vehicle.copy(
                    ownerId = currentOwnerEmail,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                Log.d("OwnerDashboardViewModel", "➕ Adding vehicle: ${vehicleWithCorrectOwner.name}")
                Log.d("OwnerDashboardViewModel", "   - Owner ID: ${vehicleWithCorrectOwner.ownerId} (from current owner email)")
                Log.d("OwnerDashboardViewModel", "   - Original vehicle.ownerId: ${vehicle.ownerId}")
                Log.d("OwnerDashboardViewModel", "   - License Plate: ${vehicleWithCorrectOwner.licensePlate}")
                Log.d("OwnerDashboardViewModel", "   - Type: ${vehicleWithCorrectOwner.type}")
                Log.d("OwnerDashboardViewModel", "   - Brand: ${vehicleWithCorrectOwner.brand} ${vehicleWithCorrectOwner.model}")
                
                // Validasi dasar
                if (vehicleWithCorrectOwner.name.isBlank()) {
                    _errorMessage.value = "Nama kendaraan tidak boleh kosong"
                    return@launch
                }
                if (vehicleWithCorrectOwner.licensePlate.isBlank()) {
                    _errorMessage.value = "Plat nomor tidak boleh kosong"
                    return@launch
                }
                
                // 1. Simpan ke database lokal
                val vehicleId = dao.insertVehicle(vehicleWithCorrectOwner)
                Log.d("OwnerDashboardViewModel", "✅ Vehicle saved to local database with ID: $vehicleId")
                Log.d("OwnerDashboardViewModel", "   - Local DB ownerId: ${vehicleWithCorrectOwner.ownerId}")
                
                // 2. Update vehicle dengan ID yang baru
                val vehicleWithId = vehicleWithCorrectOwner.copy(id = vehicleId.toInt())
                
                // 3. Sync ke Firestore (background, non-blocking)
                launch(Dispatchers.IO) {
                    try {
                        Log.d("OwnerDashboardViewModel", "🔄 Syncing vehicle to Firestore...")
                        Log.d("OwnerDashboardViewModel", "   - Vehicle ID: ${vehicleWithId.id}")
                        Log.d("OwnerDashboardViewModel", "   - Owner ID: ${vehicleWithId.ownerId}")
                        val syncSuccess = FirestoreVehicleService.syncVehicle(vehicleWithId)
                        
                        if (syncSuccess) {
                            Log.d("OwnerDashboardViewModel", "✅ Vehicle synced to Firestore successfully")
                            Log.d("OwnerDashboardViewModel", "   - Owner ID in Firestore should be: ${vehicleWithId.ownerId}")
                            _syncStatus.value = "✅ Kendaraan berhasil ditambahkan dan disinkronkan ke cloud"
                        } else {
                            Log.w("OwnerDashboardViewModel", "⚠️ Vehicle saved locally but Firestore sync failed")
                            _syncStatus.value = "✅ Kendaraan berhasil ditambahkan (sync ke cloud gagal)"
                        }
                    } catch (e: Exception) {
                        Log.e("OwnerDashboardViewModel", "❌ Error syncing vehicle to Firestore: ${e.message}", e)
                        _syncStatus.value = "✅ Kendaraan berhasil ditambahkan (sync ke cloud gagal)"
                    }
                }
                
                // Vehicles akan otomatis ter-update via Flow
                loadStatistics() // Refresh statistics
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("OwnerDashboardViewModel", "❌ Error adding vehicle: ${e.message}", e)
                _errorMessage.value = "Gagal menambah kendaraan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // ✅ CRITICAL: Ensure ownerId is set correctly from current owner email
                val currentOwnerEmail = _ownerEmail.value
                if (currentOwnerEmail.isNullOrBlank()) {
                    Log.e("OwnerDashboardViewModel", "❌ ERROR: ownerEmail is null or blank!")
                    _errorMessage.value = "Gagal: Email owner tidak ditemukan"
                    return@launch
                }
                
                Log.d("OwnerDashboardViewModel", "✏️ Updating vehicle ID: ${vehicle.id}, name: ${vehicle.name}")
                Log.d("OwnerDashboardViewModel", "   - Current owner email: $currentOwnerEmail")
                Log.d("OwnerDashboardViewModel", "   - Vehicle ownerId: ${vehicle.ownerId}")
                Log.d("OwnerDashboardViewModel", "   - License Plate: ${vehicle.licensePlate}")
                Log.d("OwnerDashboardViewModel", "   - Status: ${vehicle.status}")
                
                // Validasi dasar
                if (vehicle.name.isBlank()) {
                    _errorMessage.value = "Nama kendaraan tidak boleh kosong"
                    return@launch
                }
                if (vehicle.licensePlate.isBlank()) {
                    _errorMessage.value = "Plat nomor tidak boleh kosong"
                    return@launch
                }
                
                // ✅ FIX: Always use current owner email, not vehicle.ownerId (which might be wrong)
                val updatedVehicle = vehicle.copy(
                    ownerId = currentOwnerEmail,
                    updatedAt = System.currentTimeMillis()
                )
                
                // 1. Update di database lokal
                dao.updateVehicle(updatedVehicle)
                Log.d("OwnerDashboardViewModel", "✅ Vehicle updated in local database")
                Log.d("OwnerDashboardViewModel", "   - Local DB ownerId: ${updatedVehicle.ownerId}")
                
                // 2. Sync ke Firestore (background, non-blocking)
                launch(Dispatchers.IO) {
                    try {
                        Log.d("OwnerDashboardViewModel", "🔄 Syncing updated vehicle to Firestore...")
                        Log.d("OwnerDashboardViewModel", "   - Vehicle ID: ${updatedVehicle.id}")
                        Log.d("OwnerDashboardViewModel", "   - Owner ID: ${updatedVehicle.ownerId}")
                        val syncSuccess = FirestoreVehicleService.syncVehicle(updatedVehicle)
                        
                        if (syncSuccess) {
                            Log.d("OwnerDashboardViewModel", "✅ Vehicle update synced to Firestore successfully")
                            _syncStatus.value = "✅ Kendaraan berhasil diubah dan disinkronkan ke cloud"
                        } else {
                            Log.w("OwnerDashboardViewModel", "⚠️ Vehicle updated locally but Firestore sync failed")
                            _syncStatus.value = "✅ Kendaraan berhasil diubah (sync ke cloud gagal)"
                        }
                    } catch (e: Exception) {
                        Log.e("OwnerDashboardViewModel", "❌ Error syncing vehicle update to Firestore: ${e.message}", e)
                        _syncStatus.value = "✅ Kendaraan berhasil diubah (sync ke cloud gagal)"
                    }
                }
                
                // Vehicles akan otomatis ter-update via Flow
                loadStatistics() // Refresh statistics
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("OwnerDashboardViewModel", "❌ Error updating vehicle: ${e.message}", e)
                _errorMessage.value = "Gagal mengubah kendaraan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("OwnerDashboardViewModel", "🗑️ Deleting vehicle ID: ${vehicle.id}, name: ${vehicle.name}")
                Log.d("OwnerDashboardViewModel", "   - License Plate: ${vehicle.licensePlate}")
                
                // Cek apakah kendaraan sedang disewa
                if (vehicle.status == VehicleStatus.SEDANG_DISEWA) {
                    _errorMessage.value = "Tidak dapat menghapus kendaraan yang sedang disewa"
                    return@launch
                }
                
                val vehicleId = vehicle.id
                
                // 1. Hapus dari database lokal
                dao.deleteVehicle(vehicle)
                Log.d("OwnerDashboardViewModel", "✅ Vehicle deleted from local database")
                
                // 2. Hapus dari Firestore (background, non-blocking)
                launch(Dispatchers.IO) {
                    try {
                        Log.d("OwnerDashboardViewModel", "🔄 Deleting vehicle from Firestore...")
                        val deleteSuccess = FirestoreVehicleService.deleteVehicle(vehicleId)
                        
                        if (deleteSuccess) {
                            Log.d("OwnerDashboardViewModel", "✅ Vehicle deleted from Firestore successfully")
                            _syncStatus.value = "🗑️ Kendaraan berhasil dihapus dari cloud"
                        } else {
                            Log.w("OwnerDashboardViewModel", "⚠️ Vehicle deleted locally but Firestore deletion failed")
                            _syncStatus.value = "🗑️ Kendaraan dihapus (penghapusan dari cloud gagal)"
                        }
                    } catch (e: Exception) {
                        Log.e("OwnerDashboardViewModel", "❌ Error deleting vehicle from Firestore: ${e.message}", e)
                        _syncStatus.value = "🗑️ Kendaraan dihapus (penghapusan dari cloud gagal)"
                    }
                }
                
                // Vehicles akan otomatis ter-update via Flow
                loadStatistics() // Refresh statistics
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("OwnerDashboardViewModel", "❌ Error deleting vehicle: ${e.message}", e)
                _errorMessage.value = "Gagal menghapus kendaraan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete vehicle by ID (alternative method)
     */
    fun deleteVehicleById(vehicleId: Int) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("OwnerDashboardViewModel", "🗑️ Deleting vehicle by ID: $vehicleId")
                
                // Cek dulu apakah kendaraan ada dan statusnya
                val vehicle = dao.getVehicleById(vehicleId)
                if (vehicle != null && vehicle.status == VehicleStatus.SEDANG_DISEWA) {
                    _errorMessage.value = "Tidak dapat menghapus kendaraan yang sedang disewa"
                    return@launch
                }
                
                dao.deleteVehicleById(vehicleId)
                Log.d("OwnerDashboardViewModel", "✅ Vehicle deleted by ID successfully")
                
                // Vehicles akan otomatis ter-update via Flow
                loadStatistics() // Refresh statistics
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("OwnerDashboardViewModel", "❌ Error deleting vehicle by ID: ${e.message}", e)
                _errorMessage.value = "Gagal menghapus kendaraan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateVehicleStatus(vehicleId: Int, status: VehicleStatus, reason: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("OwnerDashboardViewModel", "🔄 Updating vehicle status: ID=$vehicleId, status=$status")
                Log.d("OwnerDashboardViewModel", "   - Reason: ${reason ?: "N/A"}")
                
                // ✅ VALIDASI: Status SEDANG_DISEWA tidak bisa diubah manual oleh owner
                if (status == VehicleStatus.SEDANG_DISEWA) {
                    Log.w("OwnerDashboardViewModel", "⚠️ Status SEDANG_DISEWA tidak dapat diubah manual")
                    _errorMessage.value = "Status 'Sedang Disewa' hanya bisa diatur otomatis oleh sistem ketika ada penumpang yang menyewa kendaraan."
                    return@launch
                }
                
                // 1. Update status di database lokal
                dao.updateVehicleStatus(
                    vehicleId = vehicleId,
                    status = status,
                    reason = reason,
                    updatedAt = System.currentTimeMillis()
                )
                Log.d("OwnerDashboardViewModel", "✅ Vehicle status updated in local database")
                
                // 2. Get vehicle untuk sync ke Firestore
                val vehicle = dao.getVehicleById(vehicleId)
                if (vehicle != null) {
                    // 3. Sync ke Firestore (background, non-blocking)
                    launch(Dispatchers.IO) {
                        try {
                            Log.d("OwnerDashboardViewModel", "🔄 Syncing vehicle status update to Firestore...")
                            val updatedVehicle = vehicle.copy(
                                status = status,
                                statusReason = reason,
                                updatedAt = System.currentTimeMillis()
                            )
                            val syncSuccess = FirestoreVehicleService.syncVehicle(updatedVehicle)
                            
                            if (syncSuccess) {
                                Log.d("OwnerDashboardViewModel", "✅ Vehicle status update synced to Firestore successfully")
                                _syncStatus.value = "✅ Status kendaraan berhasil diubah dan disinkronkan ke cloud"
                            } else {
                                Log.w("OwnerDashboardViewModel", "⚠️ Vehicle status updated locally but Firestore sync failed")
                                _syncStatus.value = "✅ Status kendaraan berhasil diubah (sync ke cloud gagal)"
                            }
                        } catch (e: Exception) {
                            Log.e("OwnerDashboardViewModel", "❌ Error syncing vehicle status update to Firestore: ${e.message}", e)
                            _syncStatus.value = "✅ Status kendaraan berhasil diubah (sync ke cloud gagal)"
                        }
                    }
                }
                
                // Vehicles akan otomatis ter-update via Flow
                loadStatistics() // Refresh statistics
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("OwnerDashboardViewModel", "❌ Error updating vehicle status: ${e.message}", e)
                _errorMessage.value = "Gagal mengubah status: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Get vehicle by ID (for detail view or validation)
     */
    fun getVehicleById(vehicleId: Int, onResult: (Vehicle?) -> Unit) {
        viewModelScope.launch {
            try {
                val vehicle = dao.getVehicleById(vehicleId)
                onResult(vehicle)
            } catch (e: Exception) {
                Log.e("OwnerDashboardViewModel", "❌ Error getting vehicle by ID: ${e.message}", e)
                _errorMessage.value = "Gagal mengambil data kendaraan: ${e.message}"
                onResult(null)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearSyncStatus() {
        _syncStatus.value = null
    }
}

