package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.UserBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface UserBalanceDao {
    /**
     * Get balance by user email
     */
    @Query("SELECT * FROM user_balances WHERE userEmail = :userEmail LIMIT 1")
    suspend fun getBalanceByEmail(userEmail: String): UserBalance?

    /**
     * Get balance by user email (Flow for reactive updates)
     */
    @Query("SELECT * FROM user_balances WHERE userEmail = :userEmail LIMIT 1")
    fun getBalanceByEmailFlow(userEmail: String): Flow<UserBalance?>

    /**
     * Get balance by user ID
     */
    @Query("SELECT * FROM user_balances WHERE userId = :userId LIMIT 1")
    suspend fun getBalanceByUserId(userId: Int): UserBalance?

    /**
     * Insert or update balance
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(balance: UserBalance): Long

    /**
     * Insert or update balance (non-suspend for use in transactions)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateSync(balance: UserBalance): Long

    /**
     * Update balance amount
     */
    @Query("UPDATE user_balances SET balance = :newBalance, updatedAt = :updatedAt WHERE userEmail = :userEmail")
    suspend fun updateBalance(userEmail: String, newBalance: Long, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update balance amount (non-suspend for use in transactions)
     */
    @Query("UPDATE user_balances SET balance = :newBalance, updatedAt = :updatedAt WHERE userEmail = :userEmail")
    fun updateBalanceSync(userEmail: String, newBalance: Long, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update sync status
     */
    @Query("UPDATE user_balances SET synced = :synced, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update sync status (non-suspend for use in transactions)
     */
    @Query("UPDATE user_balances SET synced = :synced, updatedAt = :updatedAt WHERE id = :id")
    fun updateSyncStatusSync(id: Long, synced: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Get balance by user email (non-suspend for use in transactions)
     */
    @Query("SELECT * FROM user_balances WHERE userEmail = :userEmail LIMIT 1")
    fun getBalanceByEmailSync(userEmail: String): UserBalance?

    /**
     * Get all unsynced balances
     */
    @Query("SELECT * FROM user_balances WHERE synced = 0")
    suspend fun getUnsyncedBalances(): List<UserBalance>

    /**
     * Get all balances (for admin/debugging)
     */
    @Query("SELECT * FROM user_balances ORDER BY updatedAt DESC")
    suspend fun getAllBalances(): List<UserBalance>

    /**
     * Delete balance by email
     */
    @Query("DELETE FROM user_balances WHERE userEmail = :userEmail")
    suspend fun deleteByEmail(userEmail: String)
}

