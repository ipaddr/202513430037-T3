package com.example.app_jalanin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity untuk riwayat pendapatan owner dan driver
 */
@Entity(
    tableName = "income_history",
    indices = [
        Index(value = ["recipientEmail"]),
        Index(value = ["recipientRole"]),
        Index(value = ["rentalId"]),
        Index(value = ["createdAt"])
    ]
)
data class IncomeHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "recipientEmail")
    val recipientEmail: String, // Email penerima pendapatan (owner atau driver)

    @ColumnInfo(name = "recipientRole")
    val recipientRole: String, // "PEMILIK_KENDARAAN" atau "DRIVER"

    @ColumnInfo(name = "rentalId")
    val rentalId: String, // ID rental yang menghasilkan pendapatan

    @ColumnInfo(name = "paymentHistoryId")
    val paymentHistoryId: Long, // Foreign key ke payment_history.id

    @ColumnInfo(name = "vehicleName")
    val vehicleName: String, // Nama kendaraan

    @ColumnInfo(name = "passengerEmail")
    val passengerEmail: String, // Email penumpang yang membayar

    @ColumnInfo(name = "amount")
    val amount: Int, // Jumlah pendapatan (dalam rupiah)

    @ColumnInfo(name = "paymentMethod")
    val paymentMethod: String, // "M-Banking", "ATM", "Tunai"

    @ColumnInfo(name = "paymentType")
    val paymentType: String, // "DP" atau "FULL"

    @ColumnInfo(name = "status")
    val status: String = "COMPLETED", // "PENDING", "COMPLETED", "FAILED"

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced")
    val synced: Boolean = false, // Apakah sudah sync ke Firestore

    @ColumnInfo(name = "balanceSynced")
    val balanceSynced: Boolean = false // Apakah sudah diproses ke balance m-banking
)

