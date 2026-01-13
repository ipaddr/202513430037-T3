package com.example.app_jalanin.data.auth

enum class UserRole {
    PENUMPANG,          // Renter/Passenger
    DRIVER,             // Unified Driver role (replaces DRIVER_MOTOR, DRIVER_MOBIL, DRIVER_PENGGANTI)
    PEMILIK_KENDARAAN,  // Vehicle Owner
    ADMIN
}

