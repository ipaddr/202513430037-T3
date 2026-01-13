package com.example.app_jalanin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity untuk request driver dari penumpang
 */
@Entity(
    tableName = "driver_requests",
    indices = [
        Index(value = ["driverEmail"]),
        Index(value = ["passengerEmail"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class DriverRequest(
    @PrimaryKey
    val id: String,
    
    // Passenger info
    val passengerEmail: String,
    val passengerName: String,
    
    // Driver info
    val driverEmail: String,
    val driverName: String? = null,
    
    // Vehicle info (passenger's vehicle)
    val passengerVehicleId: String,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleType: String, // "MOBIL" or "MOTOR"
    val vehicleLicensePlate: String,
    
    // Location info
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLon: Double,
    val destinationAddress: String? = null,
    val destinationLat: Double? = null,
    val destinationLon: Double? = null,
    
    // Status: PENDING, ACCEPTED, DRIVER_ARRIVING, DRIVER_ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED
    val status: String,
    
    // Driver arrival info
    val driverArrivalMethod: String? = null, // "WALKING", "VEHICLE"
    val estimatedArrivalMinutes: Int? = null,
    val driverCurrentLat: Double? = null,
    val driverCurrentLon: Double? = null,
    
    // Timestamps
    val acceptedAt: Long? = null,
    val startedAt: Long? = null,
    val arrivedAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    
    // Sync status
    val synced: Boolean = false
)
