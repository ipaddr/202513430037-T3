package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi BalanceTransaction antara Room dan Firestore
 */
object FirestoreBalanceTransactionSyncManager {
    private const val TAG = "FirestoreBalanceTransactionSync"
    private const val TRANSACTIONS_COLLECTION = "balance_transactions"

    /**
     * Sync all unsynced transactions to Firestore
     */
    suspend fun syncUnsyncedTransactions(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unsyncedTransactions = db.balanceTransactionDao().getUnsyncedTransactions()

            if (unsyncedTransactions.isEmpty()) {
                Log.d(TAG, "✅ No unsynced transactions to upload")
                return@withContext
            }

            Log.d(TAG, "🔄 Syncing ${unsyncedTransactions.size} transaction records to Firestore...")

            val firestore = FirebaseFirestore.getInstance()
            var successCount = 0
            var failedCount = 0

            for (transaction in unsyncedTransactions) {
                try {
                    val transactionData = hashMapOf(
                        "id" to transaction.id,
                        "userId" to transaction.userId,
                        "userEmail" to transaction.userEmail,
                        "relatedUserId" to (transaction.relatedUserId ?: ""),
                        "relatedUserEmail" to (transaction.relatedUserEmail ?: ""),
                        "transactionType" to transaction.transactionType,
                        "source" to transaction.source,
                        "serviceType" to (transaction.serviceType ?: ""),
                        "amount" to transaction.amount,
                        "balanceBefore" to transaction.balanceBefore,
                        "balanceAfter" to transaction.balanceAfter,
                        "rentalId" to (transaction.rentalId ?: ""),
                        "vehicleId" to (transaction.vehicleId ?: 0),
                        "description" to transaction.description,
                        "createdAt" to transaction.createdAt,
                        "synced" to true
                    )

                    firestore.collection(TRANSACTIONS_COLLECTION)
                        .document(transaction.id.toString())
                        .set(transactionData)
                        .await()

                    db.balanceTransactionDao().updateSyncStatus(transaction.id, true)
                    successCount++

                    Log.d(TAG, "✅ Synced transaction: ${transaction.id}")
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Failed to sync transaction ${transaction.id}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Sync complete: $successCount/${unsyncedTransactions.size} transactions synced successfully")
            if (failedCount > 0) {
                Log.w(TAG, "⚠️ $failedCount transactions failed to sync (will retry later)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing transactions: ${e.message}", e)
        }
    }

    /**
     * Download transactions from Firestore for a user
     */
    suspend fun downloadUserTransactions(context: Context, userEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            val snapshot = firestore.collection(TRANSACTIONS_COLLECTION)
                .whereEqualTo("userEmail", userEmail)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            var downloadedCount = 0
            for (doc in snapshot.documents) {
                try {
                    val transactionId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                    val userId = doc.getLong("userId")?.toInt() ?: 0
                    val relatedUserId = doc.getLong("relatedUserId")?.toInt()
                    val relatedUserEmail = doc.getString("relatedUserEmail")
                    val transactionType = doc.getString("transactionType") ?: "DEBIT"
                    val source = doc.getString("source") ?: "RENTER_PAYMENT"
                    val serviceType = doc.getString("serviceType")?.takeIf { it.isNotBlank() }
                    val amount = doc.getLong("amount") ?: 0L
                    val balanceBefore = doc.getLong("balanceBefore") ?: 0L
                    val balanceAfter = doc.getLong("balanceAfter") ?: 0L
                    val rentalId = doc.getString("rentalId")?.takeIf { it.isNotBlank() }
                    val vehicleId = doc.getLong("vehicleId")?.toInt()
                    val description = doc.getString("description") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                    val transaction = com.example.app_jalanin.data.local.entity.BalanceTransaction(
                        id = transactionId,
                        userId = userId,
                        userEmail = userEmail,
                        relatedUserId = relatedUserId,
                        relatedUserEmail = relatedUserEmail,
                        transactionType = transactionType,
                        source = source,
                        serviceType = serviceType,
                        amount = amount,
                        balanceBefore = balanceBefore,
                        balanceAfter = balanceAfter,
                        rentalId = rentalId,
                        vehicleId = vehicleId,
                        description = description,
                        createdAt = createdAt,
                        synced = true
                    )

                    db.balanceTransactionDao().insert(transaction)
                    downloadedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parsing transaction document ${doc.id}: ${e.message}", e)
                }
            }

            Log.d(TAG, "✅ Downloaded $downloadedCount transactions for $userEmail")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading transactions for $userEmail: ${e.message}", e)
        }
    }
}

