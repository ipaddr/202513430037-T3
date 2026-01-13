package com.example.app_jalanin.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kendaraan pribadi milik penumpang
 * Digunakan untuk personal driver service
 */
@Entity(tableName = "passenger_vehicles")
data class PassengerVehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val passengerId: String,              // Email penumpang
    val type: VehicleType,                 // MOTOR / MOBIL
    val brand: String,                     // Toyota
    val model: String,                     // Avanza
    val year: Int,                         // 2022
    val licensePlate: String,             // B 1234 XYZ
    val transmission: String? = null,     // Manual / Automatic (optional)
    val seats: Int? = null,                // Untuk mobil: 7
    val engineCapacity: String? = null,   // Untuk motor: 150cc
    val imageUrl: String? = null,          // Foto kendaraan (optional)
    val isActive: Boolean = true,          // Status aktif/tidak aktif
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false            // Apakah sudah tersinkron ke Firestore
) {
    /**
     * Get required SIM type for this vehicle
     */
    fun getRequiredSimType(): SimType {
        return SimCertificationHelper.getRequiredSimType(type)
    }
    
    /**
     * Check if driver with given SIM types can drive this vehicle
     */
    fun canBeDrivenBy(driverSimTypes: List<SimType>): Boolean {
        return SimCertificationHelper.canDriveVehicle(driverSimTypes, type)
    }
}
