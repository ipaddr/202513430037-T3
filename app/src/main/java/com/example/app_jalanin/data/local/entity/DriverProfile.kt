package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk menyimpan data profil driver yang spesifik
 * Terpisah dari tabel users untuk struktur data yang lebih clean
 */
@Entity(
    tableName = "driver_profiles",
    indices = [
        Index(value = ["driverEmail"], unique = true),
        Index(value = ["isOnline"]),
        Index(value = ["synced"])
    ]
)
data class DriverProfile(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "driverEmail")
    val driverEmail: String, // Foreign key ke users.email (unique)

    /**
     * SIM certifications yang dimiliki driver
     * Format: comma-separated string, contoh: "SIM_A,SIM_C"
     * - SIM_A: Untuk mobil
     * - SIM_C: Untuk motor
     */
    @ColumnInfo(name = "simCertifications")
    val simCertifications: String? = null,

    /**
     * Status online/offline driver
     * true = driver sedang online dan siap menerima order
     * false = driver sedang offline
     */
    @ColumnInfo(name = "isOnline")
    val isOnline: Boolean = false, // Default offline for safety

    /**
     * Timestamp kapan profile dibuat
     */
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp kapan profile terakhir di-update
     */
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * Apakah data sudah tersinkron ke Firestore
     */
    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)

