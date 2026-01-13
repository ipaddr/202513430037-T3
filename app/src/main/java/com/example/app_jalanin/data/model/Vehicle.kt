package com.example.app_jalanin.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VehicleType {
    MOTOR,
    MOBIL
}

enum class VehicleStatus {
    TERSEDIA,      // Siap disewa
    SEDANG_DISEWA, // Sedang disewa pelanggan
    TIDAK_TERSEDIA // Tidak bisa disewa (maintenance, rusak, dll)
}

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ownerId: String,                    // Email owner
    val name: String,                        // Toyota Avanza 2022
    val type: VehicleType,                   // MOTOR / MOBIL
    val brand: String,                       // Toyota
    val model: String,                       // Avanza
    val year: Int,                           // 2022
    val licensePlate: String,                // B 1234 XYZ
    val transmission: String,                // Manual / Automatic
    val seats: Int? = null,                  // Untuk mobil: 7
    val engineCapacity: String? = null,      // Untuk motor: 150cc
    val pricePerHour: Double,                // Rp 50.000/jam
    val pricePerDay: Double,                 // Rp 300.000/hari
    val pricePerWeek: Double,                // Rp 1.800.000/minggu
    val features: String,                    // "AC, GPS, USB Charger" (comma-separated)
    val status: VehicleStatus = VehicleStatus.TERSEDIA,
    val statusReason: String? = null,        // Alasan jika TIDAK_TERSEDIA
    val locationLat: Double,                 // Lokasi kendaraan
    val locationLon: Double,
    val locationAddress: String,
    val imageUrl: String? = null,            // Foto kendaraan
    // ✅ NEW: Driver assignment fields
    val driverId: String? = null,            // Email of assigned driver (nullable - vehicle can exist without driver)
    val driverAvailability: String? = null,  // DriverAvailability enum value as string (NOT_AVAILABLE, AVAILABLE_DELIVERY_ONLY, AVAILABLE_FULL_RENT)
    val driverAssignmentMode: String? = null, // DriverAssignmentMode enum value as string (DELIVERY_ONLY, DELIVERY_AND_RENTAL)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

