package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk sewa driver independen dari penumpang
 * Driver dapat disewa secara terpisah (tidak terikat dengan sewa kendaraan)
 */
@Entity(
    tableName = "driver_rentals",
    indices = [
        Index(value = ["driverEmail"]),
        Index(value = ["passengerEmail"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
        Index(value = ["synced"])
    ]
)
data class DriverRental(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Format: "DRIVER_RENT_{timestamp}_{random}"
    
    // Passenger info
    @ColumnInfo(name = "passengerEmail")
    val passengerEmail: String,
    
    // Driver info
    @ColumnInfo(name = "driverEmail")
    val driverEmail: String,
    
    @ColumnInfo(name = "driverName")
    val driverName: String? = null,
    
    // Vehicle type (MOBIL or MOTOR)
    @ColumnInfo(name = "vehicleType")
    val vehicleType: String, // "MOBIL" or "MOTOR"
    
    // Duration type and count
    @ColumnInfo(name = "durationType")
    val durationType: String, // "PER_HOUR", "PER_DAY", "PER_WEEK"
    
    @ColumnInfo(name = "durationCount")
    val durationCount: Int, // Number of hours/days/weeks
    
    // Pricing (final calculated price, stored deterministically)
    @ColumnInfo(name = "price")
    val price: Long, // Final price in Rupiah
    
    // Payment method
    @ColumnInfo(name = "paymentMethod")
    val paymentMethod: String, // "MBANKING" or "CASH"
    
    // Location info
    @ColumnInfo(name = "pickupAddress")
    val pickupAddress: String,
    
    @ColumnInfo(name = "pickupLat")
    val pickupLat: Double,
    
    @ColumnInfo(name = "pickupLon")
    val pickupLon: Double,
    
    @ColumnInfo(name = "destinationAddress")
    val destinationAddress: String? = null,
    
    @ColumnInfo(name = "destinationLat")
    val destinationLat: Double? = null,
    
    @ColumnInfo(name = "destinationLon")
    val destinationLon: Double? = null,
    
    // Status: PENDING, CONFIRMED, ACTIVE, COMPLETED, CANCELLED
    @ColumnInfo(name = "status")
    val status: String,
    
    // Timestamps
    @ColumnInfo(name = "startDate")
    val startDate: Long? = null, // When rental actually starts
    
    @ColumnInfo(name = "endDate")
    val endDate: Long? = null, // When rental ends (calculated from startDate + duration)
    
    @ColumnInfo(name = "confirmedAt")
    val confirmedAt: Long? = null, // When payment confirmed
    
    @ColumnInfo(name = "completedAt")
    val completedAt: Long? = null,
    
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
    
    // Sync status
    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)

