package com.example.app_jalanin.data.model

/**
 * Driver assignment mode for vehicles
 * Determines how the assigned driver will be used
 */
enum class DriverAssignmentMode {
    /**
     * Driver only delivers the vehicle to the renter's location
     * Driver does NOT stay as the active driver during rental period
     */
    DELIVERY_ONLY,
    
    /**
     * Driver delivers the vehicle AND stays as the active driver
     * for the entire rental duration
     */
    DELIVERY_AND_RENTAL
}

