package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.IncomeHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeHistoryDao {
    @Query("SELECT * FROM income_history WHERE recipientEmail = :recipientEmail ORDER BY createdAt DESC")
    fun getIncomeHistoryByRecipient(recipientEmail: String): Flow<List<IncomeHistory>>

    @Query("SELECT * FROM income_history WHERE recipientEmail = :recipientEmail AND recipientRole = :role ORDER BY createdAt DESC")
    fun getIncomeHistoryByRecipientAndRole(recipientEmail: String, role: String): Flow<List<IncomeHistory>>

    @Query("SELECT * FROM income_history WHERE id = :id")
    suspend fun getIncomeById(id: Long): com.example.app_jalanin.data.local.entity.IncomeHistory?

    @Query("SELECT * FROM income_history WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedIncomes(): List<IncomeHistory>

    @Query("SELECT SUM(amount) FROM income_history WHERE recipientEmail = :recipientEmail AND status = 'COMPLETED'")
    suspend fun getTotalIncome(recipientEmail: String): Int?

    @Query("SELECT SUM(amount) FROM income_history WHERE recipientEmail = :recipientEmail AND recipientRole = :role AND status = 'COMPLETED'")
    suspend fun getTotalIncomeByRole(recipientEmail: String, role: String): Int?

    /**
     * Get all completed incomes for a recipient (synchronous, for balance sync)
     */
    @Query("SELECT * FROM income_history WHERE recipientEmail = :recipientEmail AND status = 'COMPLETED' ORDER BY createdAt ASC")
    suspend fun getCompletedIncomesSync(recipientEmail: String): List<IncomeHistory>

    /**
     * Get all completed incomes that haven't been synced to balance yet
     * ✅ CRITICAL: Only returns incomes with balanceSynced = 0 to prevent double-counting
     */
    @Query("SELECT * FROM income_history WHERE recipientEmail = :recipientEmail AND status = 'COMPLETED' AND balanceSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnprocessedIncomesSync(recipientEmail: String): List<IncomeHistory>

    /**
     * Mark income as synced to balance
     */
    @Query("UPDATE income_history SET balanceSynced = 1 WHERE id = :id")
    suspend fun markBalanceSynced(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incomeHistory: IncomeHistory): Long

    @Update
    suspend fun update(incomeHistory: IncomeHistory)

    @Query("UPDATE income_history SET synced = :synced WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, synced: Boolean)
}

