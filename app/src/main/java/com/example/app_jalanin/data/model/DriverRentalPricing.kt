package com.example.app_jalanin.data.model

/**
 * Helper untuk menghitung harga sewa driver independen
 * ✅ HARD-CODED PRICING (deterministic, no recalculation)
 */
object DriverRentalPricing {
    
    // ✅ MOBIL Pricing (hard-coded)
    private const val MOBIL_HOURLY = 13_000L // Rp 13,000 per hour
    private const val MOBIL_DAILY = 250_000L // Rp 250,000 per day
    private const val MOBIL_WEEKLY = 560_000L // Rp 560,000 per week
    
    // ✅ MOTOR Pricing (hard-coded, cheaper)
    private const val MOTOR_HOURLY = 10_000L // Rp 10,000 per hour
    private const val MOTOR_DAILY = 180_000L // Rp 180,000 per day
    private const val MOTOR_WEEKLY = 420_000L // Rp 420,000 per week
    
    /**
     * Calculate total price for driver rental
     * @param vehicleType "MOBIL" or "MOTOR"
     * @param durationType "PER_HOUR", "PER_DAY", or "PER_WEEK"
     * @param durationCount Number of units (hours/days/weeks)
     * @return Total price in Rupiah (Long)
     */
    fun calculatePrice(
        vehicleType: String,
        durationType: String,
        durationCount: Int
    ): Long {
        if (durationCount <= 0) {
            return 0L
        }
        
        val pricePerUnit = when (vehicleType.uppercase()) {
            "MOBIL" -> {
                when (durationType.uppercase()) {
                    "PER_HOUR" -> MOBIL_HOURLY
                    "PER_DAY" -> MOBIL_DAILY
                    "PER_WEEK" -> MOBIL_WEEKLY
                    else -> MOBIL_HOURLY
                }
            }
            "MOTOR" -> {
                when (durationType.uppercase()) {
                    "PER_HOUR" -> MOTOR_HOURLY
                    "PER_DAY" -> MOTOR_DAILY
                    "PER_WEEK" -> MOTOR_WEEKLY
                    else -> MOTOR_HOURLY
                }
            }
            else -> MOBIL_HOURLY // Default to MOBIL pricing
        }
        
        return pricePerUnit * durationCount
    }
    
    /**
     * Get price per unit for display
     */
    fun getPricePerUnit(vehicleType: String, durationType: String): Long {
        return when (vehicleType.uppercase()) {
            "MOBIL" -> {
                when (durationType.uppercase()) {
                    "PER_HOUR" -> MOBIL_HOURLY
                    "PER_DAY" -> MOBIL_DAILY
                    "PER_WEEK" -> MOBIL_WEEKLY
                    else -> MOBIL_HOURLY
                }
            }
            "MOTOR" -> {
                when (durationType.uppercase()) {
                    "PER_HOUR" -> MOTOR_HOURLY
                    "PER_DAY" -> MOTOR_DAILY
                    "PER_WEEK" -> MOTOR_WEEKLY
                    else -> MOTOR_HOURLY
                }
            }
            else -> MOBIL_HOURLY
        }
    }
}

