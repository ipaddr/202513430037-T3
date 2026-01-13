package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk menyimpan saldo m-banking user
 * Setiap user (Driver, Renter, Owner) memiliki saldo terpisah
 */
@Entity(
    tableName = "user_balances",
    indices = [
        Index(value = ["userEmail"], unique = true),
        Index(value = ["synced"])
    ]
)
data class UserBalance(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "userId")
    val userId: Int, // Foreign key ke users.id

    @ColumnInfo(name = "userEmail")
    val userEmail: String, // Email user (unique)

    @ColumnInfo(name = "balance")
    val balance: Long, // Saldo dalam rupiah (Long untuk menghindari overflow)

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    val synced: Boolean = false // Apakah sudah sync ke Firestore
)

