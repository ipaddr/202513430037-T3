package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Room Entity untuk Rental History
 * Format durasi: "hari|jam|menit" (contoh: "0|7|30" = 7 jam 30 menit)
 */
@Entity(
    tableName = "rentals",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userEmail"]), // ✅ Added for email-based queries
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class Rental(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // Format: "RENT_<timestamp>_<random>"

    @ColumnInfo(name = "userId")
    val userId: Int, // Foreign key ke users.id

    @ColumnInfo(name = "userEmail")
    val userEmail: String,

    @ColumnInfo(name = "vehicleId")
    val vehicleId: String,

    @ColumnInfo(name = "vehicleName")
    val vehicleName: String,

    @ColumnInfo(name = "vehicleType")
    val vehicleType: String, // "Motor" atau "Mobil"

    @ColumnInfo(name = "startDate")
    val startDate: Long, // Timestamp saat kendaraan tiba & rental dimulai

    @ColumnInfo(name = "endDate")
    val endDate: Long, // Timestamp saat rental seharusnya selesai

    @ColumnInfo(name = "durationDays")
    val durationDays: Int, // Komponen hari

    @ColumnInfo(name = "durationHours")
    val durationHours: Int, // Komponen jam

    @ColumnInfo(name = "durationMinutes")
    val durationMinutes: Int, // Komponen menit

    @ColumnInfo(name = "durationMillis")
    val durationMillis: Long, // Total durasi dalam milliseconds untuk countdown

    @ColumnInfo(name = "totalPrice")
    val totalPrice: Int,

    @ColumnInfo(name = "status")
    val status: String, // "PENDING", "OWNER_DELIVERING", "DRIVER_CONFIRMED", "DRIVER_TO_OWNER", "DRIVER_PICKUP", "DRIVER_TO_PASSENGER", "ARRIVED", "ACTIVE", "DRIVER_TRAVELING", "OVERDUE", "COMPLETED", "CANCELLED"

    @ColumnInfo(name = "overtimeFee")
    val overtimeFee: Int = 0,

    @ColumnInfo(name = "isWithDriver")
    val isWithDriver: Boolean = false,

    @ColumnInfo(name = "deliveryAddress")
    val deliveryAddress: String = "",

    @ColumnInfo(name = "deliveryLat")
    val deliveryLat: Double = 0.0,

    @ColumnInfo(name = "deliveryLon")
    val deliveryLon: Double = 0.0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    val synced: Boolean = false, // Apakah sudah sync ke Firestore
    
    // ✅ NEW: Driver assignment fields
    @ColumnInfo(name = "driverId")
    val driverId: String? = null, // Email of driver assigned to this rental
    
    @ColumnInfo(name = "driverAvailability")
    val driverAvailability: String? = null, // DriverAvailability enum value as string
    
    // ✅ NEW: Owner contact fields (for NOT_AVAILABLE state)
    @ColumnInfo(name = "ownerContacted")
    val ownerContacted: Boolean = false, // Whether renter has contacted owner via chat
    
    @ColumnInfo(name = "ownerConfirmed")
    val ownerConfirmed: Boolean = false, // Whether owner has confirmed the rental via chat
    
    // ✅ NEW: Delivery mode fields
    @ColumnInfo(name = "deliveryMode")
    val deliveryMode: String? = null, // "OWNER_DELIVERY", "DRIVER_DELIVERY_ONLY", "DRIVER_DELIVERY_TRAVEL"
    
    @ColumnInfo(name = "ownerEmail")
    val ownerEmail: String? = null, // Email of vehicle owner
    
    @ColumnInfo(name = "deliveryDriverId")
    val deliveryDriverId: String? = null, // Email of driver assigned for delivery (if mode is DRIVER_DELIVERY_*)
    
    @ColumnInfo(name = "deliveryStatus")
    val deliveryStatus: String? = null, // Detailed delivery status for tracking
    
    @ColumnInfo(name = "travelDriverId")
    val travelDriverId: String? = null, // Email of travel driver (if mode is DRIVER_DELIVERY_TRAVEL)
    
    @ColumnInfo(name = "deliveryStartedAt")
    val deliveryStartedAt: Long? = null, // When delivery started
    
    @ColumnInfo(name = "deliveryArrivedAt")
    val deliveryArrivedAt: Long? = null, // When vehicle arrived at passenger location
    
    @ColumnInfo(name = "travelStartedAt")
    val travelStartedAt: Long? = null, // When travel driver started (if applicable)
    
    // ✅ NEW: Early return fields
    @ColumnInfo(name = "returnLocationLat")
    val returnLocationLat: Double? = null, // Latitude lokasi pengembalian yang ditentukan owner/driver
    
    @ColumnInfo(name = "returnLocationLon")
    val returnLocationLon: Double? = null, // Longitude lokasi pengembalian yang ditentukan owner/driver
    
    @ColumnInfo(name = "returnAddress")
    val returnAddress: String? = null, // Alamat lokasi pengembalian
    
    @ColumnInfo(name = "earlyReturnRequested")
    val earlyReturnRequested: Boolean = false, // Apakah penumpang sudah request early return
    
    @ColumnInfo(name = "earlyReturnStatus")
    val earlyReturnStatus: String? = null, // Status early return: "REQUESTED", "IN_PROGRESS", "COMPLETED", "CANCELLED"
    
    @ColumnInfo(name = "earlyReturnRequestedAt")
    val earlyReturnRequestedAt: Long? = null, // Timestamp saat early return di-request
    
    // ✅ NEW: Payment breakdown fields (for Sewa Kendaraan + Driver)
    @ColumnInfo(name = "vehicleRentalAmount")
    val vehicleRentalAmount: Int? = null, // Vehicle rental cost (owner income)
    
    @ColumnInfo(name = "driverAmount")
    val driverAmount: Int? = null // Driver payment cost (driver income)
) {
    /**
     * Format durasi untuk display: "X Hari Y Jam Z Menit"
     */
    fun getFormattedDuration(): String {
        return buildString {
            if (durationDays > 0) append("$durationDays Hari ")
            if (durationHours > 0) append("$durationHours Jam ")
            if (durationMinutes > 0) append("$durationMinutes Menit")
        }.trim()
    }

    /**
     * Format durasi singkat: "7 Jam" atau "2 Hari"
     */
    fun getShortDuration(): String {
        return when {
            durationDays > 0 -> "$durationDays Hari"
            durationHours > 0 -> "$durationHours Jam"
            durationMinutes > 0 -> "$durationMinutes Menit"
            else -> "0 Menit"
        }
    }

    /**
     * Check apakah rental sudah overdue
     */
    fun isOverdue(): Boolean {
        return System.currentTimeMillis() > endDate && status == "ACTIVE"
    }

    /**
     * Get remaining time in milliseconds
     */
    fun getRemainingTime(): Long {
        val now = System.currentTimeMillis()
        return if (now > endDate) {
            -(now - endDate) // Negative untuk overdue
        } else {
            endDate - now
        }
    }
}

