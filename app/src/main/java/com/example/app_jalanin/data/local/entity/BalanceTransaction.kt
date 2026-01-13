package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk riwayat transaksi saldo m-banking
 * Mencatat semua perubahan saldo (debit/credit) untuk audit trail
 */
@Entity(
    tableName = "balance_transactions",
    indices = [
        Index(value = ["userEmail"]),
        Index(value = ["relatedUserEmail"]),
        Index(value = ["transactionType"]),
        Index(value = ["source"]),
        Index(value = ["createdAt"]),
        Index(value = ["synced"])
    ]
)
data class BalanceTransaction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "userId")
    val userId: Int, // Foreign key ke users.id (user yang melakukan transaksi)

    @ColumnInfo(name = "userEmail")
    val userEmail: String, // Email user yang melakukan transaksi

    @ColumnInfo(name = "relatedUserId")
    val relatedUserId: Int? = null, // ID user terkait (payer/payee)

    @ColumnInfo(name = "relatedUserEmail")
    val relatedUserEmail: String? = null, // Email user terkait (payer/payee)

    @ColumnInfo(name = "transactionType")
    val transactionType: String, // "DEBIT" atau "CREDIT"

    @ColumnInfo(name = "source")
    val source: String, // BalanceTransactionSource enum value

    @ColumnInfo(name = "serviceType")
    val serviceType: String? = null, // DriverServiceType enum value (jika applicable)

    @ColumnInfo(name = "amount")
    val amount: Long, // Jumlah transaksi dalam rupiah

    @ColumnInfo(name = "balanceBefore")
    val balanceBefore: Long, // Saldo sebelum transaksi

    @ColumnInfo(name = "balanceAfter")
    val balanceAfter: Long, // Saldo setelah transaksi

    @ColumnInfo(name = "rentalId")
    val rentalId: String? = null, // ID rental terkait (jika applicable)

    @ColumnInfo(name = "vehicleId")
    val vehicleId: Int? = null, // ID kendaraan terkait (jika applicable)

    @ColumnInfo(name = "description")
    val description: String, // Deskripsi transaksi

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    val synced: Boolean = false // Apakah sudah sync ke Firestore
)

