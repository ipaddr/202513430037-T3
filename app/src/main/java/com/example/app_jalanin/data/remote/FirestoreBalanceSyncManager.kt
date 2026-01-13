package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi UserBalance antara Room dan Firestore
 */
object FirestoreBalanceSyncManager {
    private const val TAG = "FirestoreBalanceSync"
    private const val BALANCES_COLLECTION = "user_balances"

    /**
     * Sync all unsynced balances to Firestore
     */
    suspend fun syncUnsyncedBalances(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unsyncedBalances = db.userBalanceDao().getUnsyncedBalances()

            if (unsyncedBalances.isEmpty()) {
                Log.d(TAG, "✅ No unsynced balances to upload")
                return@withContext
            }

            Log.d(TAG, "🔄 Syncing ${unsyncedBalances.size} balance records to Firestore...")

            val firestore = FirebaseFirestore.getInstance()
            var successCount = 0
            var failedCount = 0

            for (balance in unsyncedBalances) {
                try {
                    val balanceData = hashMapOf(
                        "id" to balance.id,
                        "userId" to balance.userId,
                        "userEmail" to balance.userEmail,
                        "balance" to balance.balance,
                        "createdAt" to balance.createdAt,
                        "updatedAt" to balance.updatedAt,
                        "synced" to true
                    )

                    // Use userEmail as document ID for easy lookup
                    firestore.collection(BALANCES_COLLECTION)
                        .document(balance.userEmail)
                        .set(balanceData)
                        .await()

                    // Mark as synced in Room
                    db.userBalanceDao().updateSyncStatus(balance.id, true, System.currentTimeMillis())
                    successCount++

                    Log.d(TAG, "✅ Synced balance: ${balance.userEmail} (Rp ${balance.balance})")
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Failed to sync balance ${balance.userEmail}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Sync complete: $successCount/${unsyncedBalances.size} balances synced successfully")
            if (failedCount > 0) {
                Log.w(TAG, "⚠️ $failedCount balances failed to sync (will retry later)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing balances: ${e.message}", e)
        }
    }

    /**
     * Sync single balance to Firestore
     */
    suspend fun syncSingleBalance(context: Context, userEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val balance = db.userBalanceDao().getBalanceByEmail(userEmail) ?: run {
                Log.w(TAG, "⚠️ Balance not found for user: $userEmail")
                return@withContext
            }

            val firestore = FirebaseFirestore.getInstance()
            val balanceData = hashMapOf(
                "id" to balance.id,
                "userId" to balance.userId,
                "userEmail" to balance.userEmail,
                "balance" to balance.balance,
                "createdAt" to balance.createdAt,
                "updatedAt" to balance.updatedAt,
                "synced" to true
            )

            firestore.collection(BALANCES_COLLECTION)
                .document(balance.userEmail)
                .set(balanceData)
                .await()

            db.userBalanceDao().updateSyncStatus(balance.id, true, System.currentTimeMillis())
            Log.d(TAG, "✅ Synced balance for: $userEmail")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing balance for $userEmail: ${e.message}", e)
        }
    }

    /**
     * Download balance from Firestore for a user
     * ✅ CRITICAL FIX: Firestore is the SINGLE SOURCE OF TRUTH
     * IMPORTANT: This is a READ-ONLY operation. Balance is NEVER recalculated from transaction history.
     * Balance only changes when NEW transactions occur (via creditBalance/debitBalance).
     */
    suspend fun downloadUserBalance(context: Context, userEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            val doc = firestore.collection(BALANCES_COLLECTION)
                .document(userEmail)
                .get()
                .await()

            if (doc.exists()) {
                // ✅ CRITICAL: Firestore balance is ALWAYS the source of truth
                val balanceId = doc.getLong("id") ?: 0L
                val userId = doc.getLong("userId")?.toInt() ?: 0
                val firestoreBalance = doc.getLong("balance") ?: 4_500_000L
                val firestoreUpdatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                // ✅ ALWAYS use Firestore balance (no recalculation, no merging)
                val userBalance = com.example.app_jalanin.data.local.entity.UserBalance(
                    id = balanceId,
                    userId = userId,
                    userEmail = userEmail,
                    balance = firestoreBalance, // ✅ Firestore is source of truth
                    createdAt = createdAt,
                    updatedAt = firestoreUpdatedAt,
                    synced = true // Always synced since we're using Firestore value
                )

                db.userBalanceDao().insertOrUpdate(userBalance)
                Log.d(TAG, "✅ Downloaded balance from Firestore for $userEmail: Rp $firestoreBalance (READ-ONLY, no recalculation)")
            } else {
                // Balance doesn't exist in Firestore
                // Only create initial balance if user exists and local balance doesn't exist
                val user = db.userDao().getUserByEmail(userEmail)
                val localBalance = db.userBalanceDao().getBalanceByEmailSync(userEmail)
                
                if (user != null && localBalance == null) {
                    // Create initial balance only if it doesn't exist locally
                    val initialBalance = com.example.app_jalanin.data.local.entity.UserBalance(
                        userId = user.id,
                        userEmail = userEmail,
                        balance = 4_500_000L,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        synced = false // Mark as unsynced to upload to Firestore
                    )
                    db.userBalanceDao().insertOrUpdate(initialBalance)
                    // Sync to Firestore
                    syncSingleBalance(context, userEmail)
                    Log.d(TAG, "✅ Created initial balance for $userEmail: Rp 4,500,000")
                } else if (localBalance != null) {
                    // Local balance exists but Firestore doesn't - sync local to Firestore
                    syncSingleBalance(context, userEmail)
                    Log.d(TAG, "✅ Synced local balance to Firestore for $userEmail: Rp ${localBalance.balance}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading balance for $userEmail: ${e.message}", e)
        }
    }
}

