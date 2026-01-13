package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk riwayat pembayaran penumpang
 */
@Entity(
    tableName = "payment_history",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userEmail"]),
        Index(value = ["rentalId"]),
        Index(value = ["createdAt"])
    ]
)
data class PaymentHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "userId")
    val userId: Int, // Foreign key ke users.id

    @ColumnInfo(name = "userEmail")
    val userEmail: String, // Email penumpang yang melakukan pembayaran

    @ColumnInfo(name = "rentalId")
    val rentalId: String, // ID rental yang dibayar

    @ColumnInfo(name = "vehicleName")
    val vehicleName: String, // Nama kendaraan

    @ColumnInfo(name = "amount")
    val amount: Int, // Jumlah pembayaran (dalam rupiah)

    @ColumnInfo(name = "paymentMethod")
    val paymentMethod: String, // "M-Banking", "ATM", "Tunai"

    @ColumnInfo(name = "paymentType")
    val paymentType: String, // "DP" (Down Payment) atau "FULL" (Bayar Penuh)

    @ColumnInfo(name = "ownerEmail")
    val ownerEmail: String, // Email owner kendaraan

    @ColumnInfo(name = "driverEmail")
    val driverEmail: String? = null, // Email driver (jika ada)

    @ColumnInfo(name = "ownerIncome")
    val ownerIncome: Int, // Pendapatan owner dari pembayaran ini

    @ColumnInfo(name = "driverIncome")
    val driverIncome: Int = 0, // Pendapatan driver dari pembayaran ini (jika ada)

    @ColumnInfo(name = "senderRole")
    val senderRole: String? = null, // "passenger", "owner" - role of payment sender

    @ColumnInfo(name = "receiverRole")
    val receiverRole: String? = null, // "driver", "owner" - role of payment receiver

    @ColumnInfo(name = "status")
    val status: String = "COMPLETED", // "PENDING", "COMPLETED", "FAILED", "REFUNDED"

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    val synced: Boolean = false // Apakah sudah sync ke Firestore
)

