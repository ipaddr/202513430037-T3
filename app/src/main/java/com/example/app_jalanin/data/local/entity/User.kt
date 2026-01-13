package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["username"], unique = true)
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "password")
    val password: String,  // Di production harus di-hash

    @ColumnInfo(name = "role")
    val role: String,      // "penumpang", "driver_motor", "driver_mobil", "driver_pengganti", "pemilik_kendaraan"

    @ColumnInfo(name = "username")
    val username: String? = null, // Username (unique, editable)

    @ColumnInfo(name = "fullName")
    val fullName: String? = null,

    @ColumnInfo(name = "phoneNumber")
    val phoneNumber: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    val synced: Boolean = false // menandai apakah sudah tersinkron ke Firestore
)
