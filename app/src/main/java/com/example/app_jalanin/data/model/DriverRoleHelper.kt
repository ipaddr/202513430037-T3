package com.example.app_jalanin.data.model

import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.local.entity.DriverProfile

/**
 * Helper untuk validasi dan manajemen 3 peran driver:
 * 1. Delivery Driver - Antar kendaraan untuk owner
 * 2. Travel Driver - Antar penumpang menggunakan kendaraan sewa
 * 3. Personal Driver - Mengemudi kendaraan pribadi penumpang
 */
object DriverRoleHelper {
    
    /**
     * Validasi apakah driver dapat menjadi Delivery Driver untuk kendaraan tertentu
     * Syarat:
     * - Driver harus online
     * - Driver harus memiliki SIM yang sesuai dengan jenis kendaraan
     * - Driver harus sudah di-assign oleh owner
     */
    fun canBeDeliveryDriver(
        driver: User,
        vehicleType: VehicleType,
        driverProfile: DriverProfile? = null
    ): ValidationResult {
        val isOnline = driverProfile?.isOnline ?: false
        val simCertifications = driverProfile?.simCertifications
        
        if (!isOnline) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver sedang offline. Driver harus online untuk menerima order."
            )
        }
        
        val driverSims = simCertifications?.split(",")?.mapNotNull { 
            try { SimType.valueOf(it.trim()) } catch (e: Exception) { null }
        } ?: emptyList()
        
        if (driverSims.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak memiliki SIM yang valid"
            )
        }
        
        val canDrive = SimCertificationHelper.canDriveVehicle(driverSims, vehicleType)
        if (!canDrive) {
            val requiredSim = SimCertificationHelper.getRequiredSimType(vehicleType)
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak memiliki ${requiredSim.name.replace("SIM_", "SIM ")} yang diperlukan untuk mengendarai ${vehicleType.name}"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validasi apakah driver dapat menjadi Travel Driver untuk kendaraan sewa tertentu
     * Syarat:
     * - Driver harus online
     * - Driver harus memiliki SIM yang sesuai dengan jenis kendaraan
     * - Kendaraan harus memiliki driver yang di-assign
     */
    fun canBeTravelDriver(
        driver: User?,
        vehicle: Vehicle,
        driverProfile: DriverProfile? = null
    ): ValidationResult {
        if (driver == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak ditemukan"
            )
        }
        
        val isOnline = driverProfile?.isOnline ?: false
        val simCertifications = driverProfile?.simCertifications
        
        if (!isOnline) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver sedang offline. Driver harus online untuk menerima order."
            )
        }
        
        val driverSims = simCertifications?.split(",")?.mapNotNull { 
            try { SimType.valueOf(it.trim()) } catch (e: Exception) { null }
        } ?: emptyList()
        
        if (driverSims.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak memiliki SIM yang valid"
            )
        }
        
        val canDrive = SimCertificationHelper.canDriveVehicle(driverSims, vehicle.type)
        if (!canDrive) {
            val requiredSim = SimCertificationHelper.getRequiredSimType(vehicle.type)
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak memiliki ${requiredSim.name.replace("SIM_", "SIM ")} yang diperlukan untuk mengendarai ${vehicle.type.name}"
            )
        }
        
        // Check driver availability
        val driverAvailability = vehicle.driverAvailability?.let {
            try { DriverAvailability.valueOf(it) } catch (e: Exception) { null }
        }
        
        if (driverAvailability == DriverAvailability.NOT_AVAILABLE) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak tersedia. Harap hubungi pemilik kendaraan."
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Validasi apakah driver dapat menjadi Personal Driver untuk kendaraan pribadi penumpang
     * Syarat:
     * - Driver harus online
     * - Driver harus memiliki SIM yang sesuai dengan jenis kendaraan penumpang
     * - Penumpang harus memiliki kendaraan pribadi yang valid dan aktif
     */
    fun canBePersonalDriver(
        driver: User?,
        passengerVehicle: PassengerVehicle?,
        driverProfile: DriverProfile? = null
    ): ValidationResult {
        if (passengerVehicle == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Penumpang tidak memiliki kendaraan pribadi yang valid"
            )
        }
        
        if (!passengerVehicle.isActive) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Kendaraan penumpang tidak aktif"
            )
        }
        
        if (driver == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak ditemukan"
            )
        }
        
        val isOnline = driverProfile?.isOnline ?: false
        val simCertifications = driverProfile?.simCertifications
        
        if (!isOnline) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver sedang offline. Driver harus online untuk menerima order."
            )
        }
        
        val driverSims = simCertifications?.split(",")?.mapNotNull { 
            try { SimType.valueOf(it.trim()) } catch (e: Exception) { null }
        } ?: emptyList()
        
        if (driverSims.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak memiliki SIM yang valid"
            )
        }
        
        val canDrive = passengerVehicle.canBeDrivenBy(driverSims)
        if (!canDrive) {
            val requiredSim = passengerVehicle.getRequiredSimType()
            return ValidationResult(
                isValid = false,
                errorMessage = "Driver tidak memiliki ${requiredSim.name.replace("SIM_", "SIM ")} yang diperlukan untuk mengendarai kendaraan penumpang"
            )
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Filter drivers berdasarkan kriteria:
     * - Online status
     * - SIM compatibility dengan vehicle type
     * 
     * @param drivers List of User entities
     * @param vehicleType Vehicle type to filter
     * @param driverProfilesMap Optional map of driver email to DriverProfile for faster lookup
     */
    fun filterAvailableDrivers(
        drivers: List<User>,
        vehicleType: VehicleType,
        driverProfilesMap: Map<String, DriverProfile>? = null
    ): List<User> {
        return drivers.filter { driver ->
            val profile = driverProfilesMap?.get(driver.email)
            val isOnline = profile?.isOnline ?: false
            
            android.util.Log.d("DriverRoleHelper", "🔍 Checking driver: ${driver.email}, isOnline: $isOnline, profile: ${profile != null}")
            
            if (!isOnline) {
                android.util.Log.d("DriverRoleHelper", "❌ Driver ${driver.email} is offline, skipping")
                return@filter false
            }
            
            val simCertifications = profile?.simCertifications
            android.util.Log.d("DriverRoleHelper", "📋 Driver ${driver.email} SIM string: '$simCertifications'")
            
            val driverSims = simCertifications?.split(",")?.mapNotNull { simStr ->
                val trimmed = simStr.trim()
                try {
                    val simType = SimType.valueOf(trimmed)
                    android.util.Log.d("DriverRoleHelper", "✅ Parsed SIM: $trimmed -> $simType")
                    simType
                } catch (e: Exception) {
                    android.util.Log.w("DriverRoleHelper", "⚠️ Failed to parse SIM: '$trimmed', error: ${e.message}")
                    null
                }
            } ?: emptyList()
            
            android.util.Log.d("DriverRoleHelper", "📋 Driver ${driver.email} parsed SIMs: ${driverSims.map { it.name }}")
            
            val canDrive = SimCertificationHelper.canDriveVehicle(driverSims, vehicleType)
            android.util.Log.d("DriverRoleHelper", "🚗 Driver ${driver.email} can drive ${vehicleType.name}: $canDrive")
            
            canDrive
        }
    }
}

/**
 * Result dari validasi driver
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
