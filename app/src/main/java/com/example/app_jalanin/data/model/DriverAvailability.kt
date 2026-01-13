package com.example.app_jalanin.data.model

/**
 * Driver availability states for vehicle rental
 */
enum class DriverAvailability {
    /**
     * Driver only delivers the vehicle to the user's location
     * Driver does NOT stay as the driver during rental
     */
    AVAILABLE_DELIVERY_ONLY,
    
    /**
     * Driver delivers the vehicle AND stays as the driver during the entire rental duration
     */
    AVAILABLE_FULL_RENT,
    
    /**
     * Driver cannot serve
     * Renter must contact Owner directly via in-app chat
     * Vehicle can still be rented, but requires owner confirmation
     */
    NOT_AVAILABLE
}

