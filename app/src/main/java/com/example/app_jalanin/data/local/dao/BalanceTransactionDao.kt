package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.BalanceTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceTransactionDao {
    /**
     * Get all transactions for a user
     */
    @Query("SELECT * FROM balance_transactions WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    fun getTransactionsByEmail(userEmail: String): Flow<List<BalanceTransaction>>

    /**
     * Get transactions by email (synchronous)
     */
    @Query("SELECT * FROM balance_transactions WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    suspend fun getTransactionsByEmailSync(userEmail: String): List<BalanceTransaction>

    /**
     * Get transactions by type
     */
    @Query("SELECT * FROM balance_transactions WHERE userEmail = :userEmail AND transactionType = :type ORDER BY createdAt DESC")
    fun getTransactionsByType(userEmail: String, type: String): Flow<List<BalanceTransaction>>

    /**
     * Get transactions by source
     */
    @Query("SELECT * FROM balance_transactions WHERE userEmail = :userEmail AND source = :source ORDER BY createdAt DESC")
    fun getTransactionsBySource(userEmail: String, source: String): Flow<List<BalanceTransaction>>

    /**
     * Get transaction by ID
     */
    @Query("SELECT * FROM balance_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): BalanceTransaction?

    /**
     * Get all unsynced transactions
     */
    @Query("SELECT * FROM balance_transactions WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedTransactions(): List<BalanceTransaction>

    /**
     * Insert transaction
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: BalanceTransaction): Long

    /**
     * Insert transaction (non-suspend for use in transactions)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(transaction: BalanceTransaction): Long

    /**
     * Update transaction
     */
    @Update
    suspend fun update(transaction: BalanceTransaction)

    /**
     * Update sync status
     */
    @Query("UPDATE balance_transactions SET synced = :synced WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean)

    /**
     * Get total debit amount for a user
     */
    @Query("SELECT SUM(amount) FROM balance_transactions WHERE userEmail = :userEmail AND transactionType = 'DEBIT'")
    suspend fun getTotalDebit(userEmail: String): Long?

    /**
     * Get total credit amount for a user
     */
    @Query("SELECT SUM(amount) FROM balance_transactions WHERE userEmail = :userEmail AND transactionType = 'CREDIT'")
    suspend fun getTotalCredit(userEmail: String): Long?
}

