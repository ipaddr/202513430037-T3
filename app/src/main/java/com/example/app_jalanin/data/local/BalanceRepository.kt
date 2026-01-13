package com.example.app_jalanin.data.local

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.UserBalance
import com.example.app_jalanin.data.local.entity.BalanceTransaction
import com.example.app_jalanin.data.model.BalanceTransactionType
import com.example.app_jalanin.data.model.BalanceTransactionSource
import com.example.app_jalanin.data.model.DriverServiceType
import com.example.app_jalanin.data.remote.FirestoreBalanceSyncManager
import com.example.app_jalanin.data.remote.FirestoreBalanceTransactionSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction

/**
 * Repository untuk operasi saldo m-banking
 * Memastikan semua operasi saldo bersifat atomic dan konsisten
 */
class BalanceRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val balanceDao = db.userBalanceDao()
    private val transactionDao = db.balanceTransactionDao()
    private val userDao = db.userDao()

    companion object {
        private const val TAG = "BalanceRepository"
        private const val INITIAL_BALANCE = 4_500_000L // Rp 4,500,000
    }

    /**
     * Initialize balance for a user (if not exists)
     * ✅ HARDENED: Never resets existing balance, only creates if missing
     * Called during user registration or first login
     */
    suspend fun initializeBalance(userEmail: String): UserBalance = withContext(Dispatchers.IO) {
        // ✅ CRITICAL: Check if balance already exists FIRST
        val existingBalance = balanceDao.getBalanceByEmail(userEmail)
        if (existingBalance != null) {
            Log.d(TAG, "✅ Balance already exists for $userEmail: Rp ${existingBalance.balance} (NOT resetting)")
            return@withContext existingBalance
        }

        val user = userDao.getUserByEmail(userEmail)
        if (user == null) {
            throw IllegalStateException("User not found: $userEmail")
        }

        // ✅ HARDENED: Use atomic transaction to create balance
        var createdBalance: UserBalance? = null
        db.runInTransaction {
            // ✅ Double-check inside transaction (prevent race condition)
            val checkBalance = balanceDao.getBalanceByEmailSync(userEmail)
            if (checkBalance != null) {
                createdBalance = checkBalance
                return@runInTransaction
            }

            val balance = UserBalance(
                userId = user.id,
                userEmail = userEmail,
                balance = INITIAL_BALANCE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                synced = false
            )

            // ✅ Use sync version for transaction
            val balanceId = balanceDao.insertOrUpdateSync(balance)
            createdBalance = balance.copy(id = balanceId)

            // Create initial balance transaction
            val initialTransaction = BalanceTransaction(
                userId = user.id,
                userEmail = userEmail,
                transactionType = BalanceTransactionType.CREDIT.name,
                source = BalanceTransactionSource.INITIAL_BALANCE.name,
                amount = INITIAL_BALANCE,
                balanceBefore = 0L,
                balanceAfter = INITIAL_BALANCE,
                description = "Saldo awal m-banking",
                createdAt = System.currentTimeMillis(),
                synced = false
            )
            transactionDao.insertSync(initialTransaction)
        }

        val finalBalance = createdBalance ?: throw IllegalStateException("Failed to create balance for $userEmail")
        Log.d(TAG, "✅ Initialized NEW balance for $userEmail: Rp $INITIAL_BALANCE")
        finalBalance
    }

    /**
     * Get balance for a user
     */
    suspend fun getBalance(userEmail: String): UserBalance? = withContext(Dispatchers.IO) {
        balanceDao.getBalanceByEmail(userEmail)
    }

    /**
     * Get balance flow for reactive updates
     */
    fun getBalanceFlow(userEmail: String) = balanceDao.getBalanceByEmailFlow(userEmail)

    /**
     * Debit balance (deduct amount)
     * ✅ HARDENED: Always reads current balance first, then subtracts amount atomically
     * Returns true if successful, false if insufficient balance
     */
    suspend fun debitBalance(
        userEmail: String,
        amount: Long,
        source: BalanceTransactionSource,
        description: String,
        relatedUserEmail: String? = null,
        serviceType: DriverServiceType? = null,
        rentalId: String? = null,
        vehicleId: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            Log.e(TAG, "❌ Invalid debit amount: $amount")
            return@withContext false
        }

        // ✅ HARDENED: Initialize balance if not exists BEFORE transaction
        initializeBalance(userEmail)

        // Fetch user data before transaction
        val user = userDao.getUserByEmail(userEmail)
        val relatedUser = relatedUserEmail?.let { userDao.getUserByEmail(it) }

        // ✅ HARDENED: Use database transaction to ensure atomicity
        // Read current balance INSIDE transaction to prevent race conditions
        var success = false
        var balanceBefore = 0L
        var balanceAfter = 0L
        
        db.runInTransaction {
            // ✅ CRITICAL: Read current balance INSIDE transaction
            val currentBalance = balanceDao.getBalanceByEmailSync(userEmail)
                ?: throw IllegalStateException("Balance not found for $userEmail after initialization")

            balanceBefore = currentBalance.balance
            
            // ✅ Check sufficient balance INSIDE transaction
            if (balanceBefore < amount) {
                Log.w(TAG, "⚠️ Insufficient balance for $userEmail: Rp $balanceBefore < Rp $amount")
                return@runInTransaction
            }
            
            balanceAfter = balanceBefore - amount // ✅ ACCUMULATE, never replace
            val now = System.currentTimeMillis()

            // ✅ Update balance atomically
            balanceDao.updateBalanceSync(userEmail, balanceAfter, now)

            // Create transaction record
            val transaction = BalanceTransaction(
                userId = user?.id ?: 0,
                userEmail = userEmail,
                relatedUserId = relatedUser?.id,
                relatedUserEmail = relatedUserEmail,
                transactionType = BalanceTransactionType.DEBIT.name,
                source = source.name,
                serviceType = serviceType?.name,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                rentalId = rentalId,
                vehicleId = vehicleId,
                description = description,
                createdAt = now,
                synced = false
            )
            transactionDao.insertSync(transaction)

            // Mark balance as unsynced
            balanceDao.updateSyncStatusSync(currentBalance.id, false, now)

            success = true
        }

        if (success) {
            Log.d(TAG, "✅ Debited Rp $amount from $userEmail. Balance: Rp $balanceBefore → Rp $balanceAfter")
        } else {
            Log.w(TAG, "⚠️ Failed to debit balance for $userEmail (insufficient balance or error)")
        }
        success
    }

    /**
     * Credit balance (add amount)
     * ✅ HARDENED: Always reads current balance first, then adds amount atomically
     * This ensures balance accumulates correctly and never resets
     */
    suspend fun creditBalance(
        userEmail: String,
        amount: Long,
        source: BalanceTransactionSource,
        description: String,
        relatedUserEmail: String? = null,
        serviceType: DriverServiceType? = null,
        rentalId: String? = null,
        vehicleId: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            Log.e(TAG, "❌ Invalid credit amount: $amount")
            return@withContext false
        }

        // ✅ HARDENED: Initialize balance if not exists BEFORE transaction
        initializeBalance(userEmail)

        // Fetch user data before transaction
        val user = userDao.getUserByEmail(userEmail)
        val relatedUser = relatedUserEmail?.let { userDao.getUserByEmail(it) }

        // ✅ HARDENED: Use database transaction to ensure atomicity
        // Read current balance INSIDE transaction to prevent race conditions
        var success = false
        var balanceBefore = 0L
        var balanceAfter = 0L
        
        db.runInTransaction {
            // ✅ CRITICAL: Read current balance INSIDE transaction
            val currentBalance = balanceDao.getBalanceByEmailSync(userEmail)
                ?: throw IllegalStateException("Balance not found for $userEmail after initialization")

            balanceBefore = currentBalance.balance
            balanceAfter = balanceBefore + amount // ✅ ACCUMULATE, never replace
            
            val now = System.currentTimeMillis()

            // ✅ Update balance atomically
            balanceDao.updateBalanceSync(userEmail, balanceAfter, now)

            // Create transaction record
            val transaction = BalanceTransaction(
                userId = user?.id ?: 0,
                userEmail = userEmail,
                relatedUserId = relatedUser?.id,
                relatedUserEmail = relatedUserEmail,
                transactionType = BalanceTransactionType.CREDIT.name,
                source = source.name,
                serviceType = serviceType?.name,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                rentalId = rentalId,
                vehicleId = vehicleId,
                description = description,
                createdAt = now,
                synced = false
            )
            transactionDao.insertSync(transaction)

            // Mark balance as unsynced
            balanceDao.updateSyncStatusSync(currentBalance.id, false, now)

            success = true
        }

        if (success) {
            Log.d(TAG, "✅ Credited Rp $amount to $userEmail. Balance: Rp $balanceBefore → Rp $balanceAfter")
        } else {
            Log.e(TAG, "❌ Failed to credit balance for $userEmail")
        }
        success
    }

    /**
     * Transfer balance from one user to another (atomic operation)
     * Used for driver service payments
     */
    suspend fun transferBalance(
        fromUserEmail: String,
        toUserEmail: String,
        amount: Long,
        source: BalanceTransactionSource,
        description: String,
        serviceType: DriverServiceType? = null,
        rentalId: String? = null,
        vehicleId: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            Log.e(TAG, "❌ Invalid transfer amount: $amount")
            return@withContext false
        }

        // Fetch user data before transaction
        val fromUser = userDao.getUserByEmail(fromUserEmail)
        val toUser = userDao.getUserByEmail(toUserEmail)
        val fromBalance = balanceDao.getBalanceByEmail(fromUserEmail)
            ?: throw IllegalStateException("Balance not found for $fromUserEmail")
        val toBalance = balanceDao.getBalanceByEmail(toUserEmail)
            ?: throw IllegalStateException("Balance not found for $toUserEmail")

        if (fromBalance.balance < amount) {
            Log.w(TAG, "⚠️ Insufficient balance for transfer: Rp ${fromBalance.balance} < Rp $amount")
            return@withContext false
        }

        // Use database transaction to ensure atomicity
        var success = false
        db.runInTransaction {
            val currentFromBalance = balanceDao.getBalanceByEmailSync(fromUserEmail)
                ?: throw IllegalStateException("Balance not found for $fromUserEmail")
            val currentToBalance = balanceDao.getBalanceByEmailSync(toUserEmail)
                ?: throw IllegalStateException("Balance not found for $toUserEmail")

            if (currentFromBalance.balance < amount) {
                return@runInTransaction
            }

            val now = System.currentTimeMillis()
            val fromNewBalance = currentFromBalance.balance - amount
            val toNewBalance = currentToBalance.balance + amount

            // Update both balances
            balanceDao.updateBalanceSync(fromUserEmail, fromNewBalance, now)
            balanceDao.updateBalanceSync(toUserEmail, toNewBalance, now)

            // Debit transaction for sender
            val debitTransaction = BalanceTransaction(
                userId = fromUser?.id ?: 0,
                userEmail = fromUserEmail,
                relatedUserId = toUser?.id,
                relatedUserEmail = toUserEmail,
                transactionType = BalanceTransactionType.DEBIT.name,
                source = source.name,
                serviceType = serviceType?.name,
                amount = amount,
                balanceBefore = currentFromBalance.balance,
                balanceAfter = fromNewBalance,
                rentalId = rentalId,
                vehicleId = vehicleId,
                description = description,
                createdAt = now,
                synced = false
            )
            transactionDao.insertSync(debitTransaction)

            // Credit transaction for receiver
            val creditTransaction = BalanceTransaction(
                userId = toUser?.id ?: 0,
                userEmail = toUserEmail,
                relatedUserId = fromUser?.id,
                relatedUserEmail = fromUserEmail,
                transactionType = BalanceTransactionType.CREDIT.name,
                source = source.name,
                serviceType = serviceType?.name,
                amount = amount,
                balanceBefore = currentToBalance.balance,
                balanceAfter = toNewBalance,
                rentalId = rentalId,
                vehicleId = vehicleId,
                description = description,
                createdAt = now,
                synced = false
            )
            transactionDao.insertSync(creditTransaction)

            // Mark both balances as unsynced
            balanceDao.updateSyncStatusSync(currentFromBalance.id, false, now)
            balanceDao.updateSyncStatusSync(currentToBalance.id, false, now)

            success = true
        }

        if (success) {
            Log.d(TAG, "✅ Transferred Rp $amount from $fromUserEmail to $toUserEmail")
        }
        success
    }

    /**
     * Get transaction history for a user
     */
    fun getTransactionHistory(userEmail: String) = transactionDao.getTransactionsByEmail(userEmail)

    /**
     * Sync balance and transactions to Firestore
     */
    suspend fun syncToFirestore() = withContext(Dispatchers.IO) {
        try {
            FirestoreBalanceSyncManager.syncUnsyncedBalances(context)
            FirestoreBalanceTransactionSyncManager.syncUnsyncedTransactions(context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing to Firestore: ${e.message}", e)
        }
    }

    /**
     * Sync income history to balance for owner and driver
     * ✅ HARDENED: Only processes incomes with balanceSynced = false to prevent double-counting
     * This ensures that income is only processed once, even after app data is cleared and re-downloaded
     * Only processes income with status "COMPLETED" (all payment methods use m-banking)
     * 
     * IMPORTANT: This function should be called AFTER downloading balance from Firestore
     * to ensure local balance updates are not overwritten
     */
    suspend fun syncIncomeHistoryToBalance(userEmail: String) = withContext(Dispatchers.IO) {
        try {
            val incomeDao = db.incomeHistoryDao()
            
            // ✅ CRITICAL FIX: Only get incomes that haven't been processed to balance yet
            // This prevents infinite money glitch when data is cleared and re-downloaded
            val unprocessedIncomes = incomeDao.getUnprocessedIncomesSync(userEmail)
            
            if (unprocessedIncomes.isEmpty()) {
                Log.d(TAG, "📊 No unprocessed incomes found for $userEmail (all incomes already synced to balance)")
                return@withContext
            }
            
            Log.d(TAG, "🔄 Found ${unprocessedIncomes.size} unprocessed income records for $userEmail")
            
            // ✅ HARDENED: Initialize balance if not exists (never resets existing)
            initializeBalance(userEmail)
            
            // Get current balance BEFORE processing
            val currentBalance = balanceDao.getBalanceByEmailSync(userEmail)
            val balanceBefore = currentBalance?.balance ?: INITIAL_BALANCE
            
            Log.d(TAG, "📊 Current balance BEFORE sync: Rp $balanceBefore")
            
            var syncedCount = 0
            var errorCount = 0
            var totalAmount = 0L
            
            // ✅ HARDENED: Process each income atomically
            for (income in unprocessedIncomes) {
                try {
                    // ✅ Determine source based on recipient role
                    val source = if (income.recipientRole == "PEMILIK_KENDARAAN") {
                        BalanceTransactionSource.OWNER_PAYMENT
                    } else {
                        BalanceTransactionSource.DRIVER_SERVICE_FEE
                    }
                    
                    // ✅ HARDENED: creditBalance will read current balance and add amount atomically
                    val success = creditBalance(
                        userEmail = income.recipientEmail,
                        amount = income.amount.toLong(),
                        source = source,
                        description = "Pendapatan dari sewa ${income.vehicleName}",
                        relatedUserEmail = income.passengerEmail,
                        serviceType = if (income.recipientRole == "DRIVER") {
                            DriverServiceType.RENTAL_DRIVER
                        } else null,
                        rentalId = income.rentalId,
                        vehicleId = null
                    )
                    
                    if (success) {
                        // ✅ CRITICAL: Mark income as synced to balance to prevent reprocessing
                        incomeDao.markBalanceSynced(income.id)
                        syncedCount++
                        totalAmount += income.amount.toLong()
                        Log.d(TAG, "✅ Synced income ${income.id}: Rp ${income.amount} (rentalId: ${income.rentalId}) and marked as balanceSynced")
                    } else {
                        errorCount++
                        Log.w(TAG, "⚠️ Failed to sync income ${income.id} to balance")
                    }
                } catch (e: Exception) {
                    errorCount++
                    Log.e(TAG, "❌ Error syncing income ${income.id} to balance: ${e.message}", e)
                    e.printStackTrace()
                }
            }
            
            // ✅ Get final balance AFTER sync
            val finalBalance = balanceDao.getBalanceByEmailSync(userEmail)?.balance ?: INITIAL_BALANCE
            
            Log.d(TAG, "✅ Income sync to balance complete for $userEmail:")
            Log.d(TAG, "   - Total unprocessed incomes: ${unprocessedIncomes.size}")
            Log.d(TAG, "   - Synced: $syncedCount (Rp $totalAmount)")
            Log.d(TAG, "   - Errors: $errorCount")
            Log.d(TAG, "   - Balance BEFORE: Rp $balanceBefore")
            Log.d(TAG, "   - Balance AFTER: Rp $finalBalance")
            Log.d(TAG, "   - Balance INCREASE: Rp ${finalBalance - balanceBefore}")
            
            // ✅ Sync updated balances and income history to Firestore (only if changes were made)
            if (syncedCount > 0) {
                syncToFirestore()
                // Also sync income history to update balanceSynced flag in Firestore
                com.example.app_jalanin.data.remote.FirestoreIncomeSyncManager.syncUnsyncedIncomes(context)
                Log.d(TAG, "✅ Synced updated balances and income history to Firestore")
            } else {
                Log.d(TAG, "ℹ️ No new incomes to sync, balance unchanged")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing income history to balance for $userEmail: ${e.message}", e)
            e.printStackTrace()
        }
    }
}

