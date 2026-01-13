package com.example.app_jalanin.data.model

/**
 * SIM (Surat Izin Mengemudi) certification types
 * SIM A: For cars (mobil)
 * SIM C: For motorcycles (motor)
 */
enum class SimType {
    SIM_A,  // For cars
    SIM_C   // For motorcycles
}

/**
 * Helper functions to check SIM compatibility with vehicle types
 */
object SimCertificationHelper {
    /**
     * Check if a driver with given SIM types can drive a vehicle of the specified type
     * @param driverSimTypes List of SIM types the driver has
     * @param vehicleType The type of vehicle (MOBIL or MOTOR)
     * @return true if driver can drive the vehicle, false otherwise
     */
    fun canDriveVehicle(driverSimTypes: List<SimType>, vehicleType: VehicleType): Boolean {
        val result = when (vehicleType) {
            VehicleType.MOBIL -> {
                val canDrive = driverSimTypes.contains(SimType.SIM_A)
                android.util.Log.d("SimCertificationHelper", "🚗 Checking MOBIL: driver has ${driverSimTypes.map { it.name }}, can drive: $canDrive")
                canDrive
            }
            VehicleType.MOTOR -> {
                val canDrive = driverSimTypes.contains(SimType.SIM_C)
                android.util.Log.d("SimCertificationHelper", "🏍️ Checking MOTOR: driver has ${driverSimTypes.map { it.name }}, can drive: $canDrive")
                canDrive
            }
        }
        return result
    }
    
    /**
     * Get required SIM type for a vehicle type
     */
    fun getRequiredSimType(vehicleType: VehicleType): SimType {
        return when (vehicleType) {
            VehicleType.MOBIL -> SimType.SIM_A
            VehicleType.MOTOR -> SimType.SIM_C
        }
    }
    
    /**
     * Convert string list to SimType list (for database storage)
     */
    fun fromStringList(simStrings: List<String>?): List<SimType> {
        if (simStrings == null) return emptyList()
        return simStrings.mapNotNull { simString ->
            try {
                SimType.valueOf(simString)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
    
    /**
     * Convert SimType list to string list (for database storage)
     */
    fun toStringList(simTypes: List<SimType>): List<String> {
        return simTypes.map { it.name }
    }
    
    /**
     * Parse SIM certifications from comma-separated string (e.g., "SIM_A,SIM_C")
     */
    fun parseSimCertifications(simString: String?): List<SimType> {
        if (simString.isNullOrBlank()) {
            android.util.Log.d("SimCertificationHelper", "⚠️ SIM string is null or blank")
            return emptyList()
        }
        
        android.util.Log.d("SimCertificationHelper", "📋 Parsing SIM string: '$simString'")
        
        val result = simString.split(",")
            .mapNotNull { sim ->
                val trimmed = sim.trim()
                try {
                    val simType = SimType.valueOf(trimmed)
                    android.util.Log.d("SimCertificationHelper", "✅ Parsed: '$trimmed' -> $simType")
                    simType
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("SimCertificationHelper", "⚠️ Failed to parse SIM: '$trimmed', error: ${e.message}")
                    null
                }
            }
        
        android.util.Log.d("SimCertificationHelper", "📋 Parsed SIMs: ${result.map { it.name }}")
        return result
    }
}

