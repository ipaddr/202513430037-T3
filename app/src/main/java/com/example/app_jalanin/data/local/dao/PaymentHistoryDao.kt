package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.PaymentHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentHistoryDao {
    @Query("SELECT * FROM payment_history WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    fun getPaymentHistoryByUser(userEmail: String): Flow<List<PaymentHistory>>

    @Query("SELECT * FROM payment_history WHERE rentalId = :rentalId")
    suspend fun getPaymentHistoryByRental(rentalId: String): List<PaymentHistory>
    
    @Query("SELECT * FROM payment_history ORDER BY createdAt DESC")
    suspend fun getAllPayments(): List<PaymentHistory>
    
    @Query("SELECT * FROM payment_history ORDER BY createdAt DESC")
    fun getAllPaymentsFlow(): Flow<List<PaymentHistory>>

    @Query("SELECT * FROM payment_history WHERE id = :id")
    suspend fun getPaymentById(id: Long): com.example.app_jalanin.data.local.entity.PaymentHistory?

    @Query("SELECT * FROM payment_history WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedPayments(): List<PaymentHistory>

    @Query("SELECT SUM(amount) FROM payment_history WHERE userEmail = :userEmail AND status = 'COMPLETED'")
    suspend fun getTotalSpent(userEmail: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(paymentHistory: PaymentHistory): Long

    @Update
    suspend fun update(paymentHistory: PaymentHistory)

    @Query("UPDATE payment_history SET synced = :synced WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean)
}

