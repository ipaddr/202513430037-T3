package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi IncomeHistory antara Room dan Firestore
 * Memastikan income history tetap tersimpan di cloud untuk backup dan cross-device access
 */
object FirestoreIncomeSyncManager {
    private const val TAG = "FirestoreIncomeSync"
    private const val INCOME_HISTORY_COLLECTION = "income_history"

    /**
     * Sync all unsynced income history to Firestore
     */
    suspend fun syncUnsyncedIncomes(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unsyncedIncomes = db.incomeHistoryDao().getUnsyncedIncomes()

            if (unsyncedIncomes.isEmpty()) {
                Log.d(TAG, "✅ No unsynced incomes to upload")
                return@withContext
            }

            Log.d(TAG, "🔄 Syncing ${unsyncedIncomes.size} income records to Firestore...")

            val firestore = FirebaseFirestore.getInstance()
            var successCount = 0
            var failedCount = 0

            for (income in unsyncedIncomes) {
                try {
                    val incomeData = hashMapOf(
                        "id" to income.id,
                        "recipientEmail" to income.recipientEmail,
                        "recipientRole" to income.recipientRole,
                        "rentalId" to income.rentalId,
                        "paymentHistoryId" to income.paymentHistoryId,
                        "vehicleName" to income.vehicleName,
                        "passengerEmail" to income.passengerEmail,
                        "amount" to income.amount,
                        "paymentMethod" to income.paymentMethod,
                        "paymentType" to income.paymentType,
                        "status" to income.status,
                        "createdAt" to income.createdAt,
                        "synced" to true,
                        "balanceSynced" to income.balanceSynced // ✅ CRITICAL: Sync balanceSynced flag
                    )

                    firestore.collection(INCOME_HISTORY_COLLECTION)
                        .document(income.id.toString())
                        .set(incomeData)
                        .await()

                    // Mark as synced in Room
                    db.incomeHistoryDao().updateSyncStatus(income.id, true)
                    successCount++

                    Log.d(TAG, "✅ Synced income: ${income.id} (${income.rentalId})")
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Failed to sync income ${income.id}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Sync complete: $successCount/${unsyncedIncomes.size} incomes synced successfully")
            if (failedCount > 0) {
                Log.w(TAG, "⚠️ $failedCount incomes failed to sync (will retry later)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing incomes: ${e.message}", e)
        }
    }

    /**
     * Sync single income history to Firestore
     */
    suspend fun syncSingleIncome(context: Context, incomeId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val income = db.incomeHistoryDao().getIncomeById(incomeId)

            if (income == null) {
                Log.e(TAG, "❌ Income not found: $incomeId")
                return@withContext false
            }

            val incomeData = hashMapOf(
                "id" to income.id,
                "recipientEmail" to income.recipientEmail,
                "recipientRole" to income.recipientRole,
                "rentalId" to income.rentalId,
                "paymentHistoryId" to income.paymentHistoryId,
                "vehicleName" to income.vehicleName,
                "passengerEmail" to income.passengerEmail,
                "amount" to income.amount,
                "paymentMethod" to income.paymentMethod,
                "paymentType" to income.paymentType,
                "status" to income.status,
                "createdAt" to income.createdAt,
                "synced" to true,
                "balanceSynced" to income.balanceSynced // ✅ CRITICAL: Sync balanceSynced flag
            )

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(INCOME_HISTORY_COLLECTION)
                .document(income.id.toString())
                .set(incomeData)
                .await()

            // Mark as synced
            db.incomeHistoryDao().updateSyncStatus(income.id, true)

            Log.d(TAG, "✅ Income $incomeId synced successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync income $incomeId: ${e.message}")
            false
        }
    }

    /**
     * Download income history from Firestore for a specific recipient
     */
    suspend fun downloadRecipientIncomes(context: Context, recipientEmail: String, recipientRole: String) = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val db = AppDatabase.getDatabase(context)

            val snapshot = firestore.collection(INCOME_HISTORY_COLLECTION)
                .whereEqualTo("recipientEmail", recipientEmail)
                .whereEqualTo("recipientRole", recipientRole)
                .get()
                .await()

            Log.d(TAG, "📥 Downloading ${snapshot.documents.size} income records for $recipientEmail ($recipientRole)...")

            for (doc in snapshot.documents) {
                try {
                    val incomeId = doc.getLong("id") ?: continue
                    val existingIncome = db.incomeHistoryDao().getIncomeById(incomeId)

                    if (existingIncome == null) {
                        // Create new income history from Firestore
                        // ✅ CRITICAL: Read balanceSynced from Firestore to prevent reprocessing
                        val balanceSynced = doc.getBoolean("balanceSynced") ?: false
                        
                        val income = com.example.app_jalanin.data.local.entity.IncomeHistory(
                            id = incomeId,
                            recipientEmail = doc.getString("recipientEmail") ?: "",
                            recipientRole = doc.getString("recipientRole") ?: "",
                            rentalId = doc.getString("rentalId") ?: "",
                            paymentHistoryId = doc.getLong("paymentHistoryId") ?: 0L,
                            vehicleName = doc.getString("vehicleName") ?: "",
                            passengerEmail = doc.getString("passengerEmail") ?: "",
                            amount = doc.getLong("amount")?.toInt() ?: 0,
                            paymentMethod = doc.getString("paymentMethod") ?: "",
                            paymentType = doc.getString("paymentType") ?: "",
                            status = doc.getString("status") ?: "COMPLETED",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            synced = true,
                            balanceSynced = balanceSynced // ✅ CRITICAL: Preserve balanceSynced flag from Firestore
                        )

                        db.incomeHistoryDao().insert(income)
                        Log.d(TAG, "✅ Downloaded income: $incomeId (balanceSynced: $balanceSynced)")
                        
                        // ✅ FIX: DO NOT credit balance here - let syncIncomeHistoryToBalance handle it
                        // This prevents double-counting when both downloadRecipientIncomes and syncIncomeHistoryToBalance run
                    } else {
                        // ✅ CRITICAL: Update balanceSynced flag if it exists in Firestore but not in local
                        val firestoreBalanceSynced = doc.getBoolean("balanceSynced") ?: false
                        if (firestoreBalanceSynced && !existingIncome.balanceSynced) {
                            db.incomeHistoryDao().markBalanceSynced(existingIncome.id)
                            Log.d(TAG, "✅ Updated balanceSynced flag for income: $incomeId")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing income document: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Income download complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading incomes: ${e.message}", e)
        }
    }
}

