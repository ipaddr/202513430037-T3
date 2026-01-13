package com.example.app_jalanin.utils

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Utility untuk migrasi username di Firestore
 * Menambahkan field username ke dokumen yang sudah ada dan memperbarui data lama
 */
object FirestoreUsernameMigration {
    private const val TAG = "FirestoreUsernameMigration"
    
    private const val RENTALS_COLLECTION = "rentals"
    private const val PAYMENT_HISTORY_COLLECTION = "payment_history"
    private const val DRIVER_RENTALS_COLLECTION = "driver_rentals"
    private const val USERS_COLLECTION = "users"
    
    /**
     * Resolve username dari email
     * Priority: 1. Room UserEntity, 2. Firestore users collection, 3. "unknown"
     */
    private suspend fun resolveUsernameFromEmail(context: Context, email: String?): String {
        if (email.isNullOrBlank()) return "unknown"
        
        return try {
            // 1. Cek di Room database
            val database = AppDatabase.getDatabase(context)
            val localUser = database.userDao().getUserByEmail(email)
            if (localUser?.username != null) {
                return localUser.username
            }
            
            // 2. Cek di Firestore users collection
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            
            if (!userDoc.isEmpty && userDoc.documents.isNotEmpty()) {
                val username = userDoc.documents[0].getString("username")
                if (!username.isNullOrBlank()) {
                    return username
                }
            }
            
            // 3. Fallback ke "unknown"
            "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving username for $email: ${e.message}", e)
            "unknown"
        }
    }
    
    /**
     * Migrate rentals collection - tambahkan username fields
     */
    suspend fun migrateRentalsCollection(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting rentals collection migration...")
            val firestore = FirebaseFirestore.getInstance()
            
            // Get all rentals
            val snapshot = firestore.collection(RENTALS_COLLECTION)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "✅ No rentals to migrate")
                return@withContext
            }
            
            var updatedCount = 0
            var skippedCount = 0
            
            for (doc in snapshot.documents) {
                try {
                    val data = doc.data ?: continue
                    
                    // Check if already migrated (has username fields)
                    val hasPassengerUsername = data.containsKey("passengerUsername")
                    val hasOwnerUsername = data.containsKey("ownerUsername")
                    val hasDriverUsername = data.containsKey("driverUsername")
                    
                    if (hasPassengerUsername && hasOwnerUsername && hasDriverUsername) {
                        skippedCount++
                        continue
                    }
                    
                    val updates = hashMapOf<String, Any>()
                    
                    // Resolve passenger username
                    if (!hasPassengerUsername) {
                        val userEmail = data["userEmail"] as? String
                        val passengerUsername = resolveUsernameFromEmail(context, userEmail)
                        updates["passengerUsername"] = passengerUsername
                    }
                    
                    // Resolve owner username
                    if (!hasOwnerUsername) {
                        val ownerEmail = data["ownerEmail"] as? String
                        val ownerUsername = resolveUsernameFromEmail(context, ownerEmail)
                        updates["ownerUsername"] = ownerUsername
                    }
                    
                    // Resolve driver username (travelDriverId or deliveryDriverId)
                    if (!hasDriverUsername) {
                        val travelDriverId = data["travelDriverId"] as? String
                        val deliveryDriverId = data["deliveryDriverId"] as? String
                        val driverEmail = travelDriverId ?: deliveryDriverId
                        val driverUsername = resolveUsernameFromEmail(context, driverEmail)
                        updates["driverUsername"] = driverUsername
                    }
                    
                    // Update document
                    if (updates.isNotEmpty()) {
                        firestore.collection(RENTALS_COLLECTION)
                            .document(doc.id)
                            .update(updates)
                            .await()
                        updatedCount++
                        Log.d(TAG, "✅ Updated rental: ${doc.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error migrating rental ${doc.id}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "🎉 Rentals migration complete: $updatedCount updated, $skippedCount skipped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error migrating rentals collection: ${e.message}", e)
        }
    }
    
    /**
     * Migrate payment_history collection - tambahkan username fields
     */
    suspend fun migratePaymentHistoryCollection(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting payment_history collection migration...")
            val firestore = FirebaseFirestore.getInstance()
            
            // Get all payments
            val snapshot = firestore.collection(PAYMENT_HISTORY_COLLECTION)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "✅ No payments to migrate")
                return@withContext
            }
            
            var updatedCount = 0
            var skippedCount = 0
            
            for (doc in snapshot.documents) {
                try {
                    val data = doc.data ?: continue
                    
                    // Check if already migrated
                    val hasUserUsername = data.containsKey("userUsername")
                    val hasOwnerUsername = data.containsKey("ownerUsername")
                    val hasDriverUsername = data.containsKey("driverUsername")
                    
                    if (hasUserUsername && hasOwnerUsername && hasDriverUsername) {
                        skippedCount++
                        continue
                    }
                    
                    val updates = hashMapOf<String, Any>()
                    
                    // Resolve user (passenger) username
                    if (!hasUserUsername) {
                        val userEmail = data["userEmail"] as? String
                        val userUsername = resolveUsernameFromEmail(context, userEmail)
                        updates["userUsername"] = userUsername
                    }
                    
                    // Resolve owner username
                    if (!hasOwnerUsername) {
                        val ownerEmail = data["ownerEmail"] as? String
                        val ownerUsername = resolveUsernameFromEmail(context, ownerEmail)
                        updates["ownerUsername"] = ownerUsername
                    }
                    
                    // Resolve driver username
                    if (!hasDriverUsername) {
                        val driverEmail = data["driverEmail"] as? String
                        val driverUsername = resolveUsernameFromEmail(context, driverEmail)
                        updates["driverUsername"] = driverUsername
                    }
                    
                    // Update document
                    if (updates.isNotEmpty()) {
                        firestore.collection(PAYMENT_HISTORY_COLLECTION)
                            .document(doc.id)
                            .update(updates)
                            .await()
                        updatedCount++
                        Log.d(TAG, "✅ Updated payment: ${doc.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error migrating payment ${doc.id}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "🎉 Payment history migration complete: $updatedCount updated, $skippedCount skipped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error migrating payment_history collection: ${e.message}", e)
        }
    }
    
    /**
     * Migrate driver_rentals collection - tambahkan username fields
     */
    suspend fun migrateDriverRentalsCollection(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting driver_rentals collection migration...")
            val firestore = FirebaseFirestore.getInstance()
            
            // Get all driver rentals
            val snapshot = firestore.collection(DRIVER_RENTALS_COLLECTION)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "✅ No driver rentals to migrate")
                return@withContext
            }
            
            var updatedCount = 0
            var skippedCount = 0
            
            for (doc in snapshot.documents) {
                try {
                    val data = doc.data ?: continue
                    
                    // Check if already migrated
                    val hasPassengerUsername = data.containsKey("passengerUsername")
                    val hasOwnerUsername = data.containsKey("ownerUsername")
                    
                    if (hasPassengerUsername && hasOwnerUsername) {
                        skippedCount++
                        continue
                    }
                    
                    val updates = hashMapOf<String, Any>()
                    
                    // Resolve passenger username
                    if (!hasPassengerUsername) {
                        val passengerEmail = data["passengerEmail"] as? String
                        val passengerUsername = resolveUsernameFromEmail(context, passengerEmail)
                        updates["passengerUsername"] = passengerUsername
                    }
                    
                    // Resolve owner username (if vehicle rental)
                    if (!hasOwnerUsername) {
                        val ownerEmail = data["ownerEmail"] as? String
                        val ownerUsername = resolveUsernameFromEmail(context, ownerEmail)
                        updates["ownerUsername"] = ownerUsername
                    }
                    
                    // Update document
                    if (updates.isNotEmpty()) {
                        firestore.collection(DRIVER_RENTALS_COLLECTION)
                            .document(doc.id)
                            .update(updates)
                            .await()
                        updatedCount++
                        Log.d(TAG, "✅ Updated driver rental: ${doc.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error migrating driver rental ${doc.id}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "🎉 Driver rentals migration complete: $updatedCount updated, $skippedCount skipped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error migrating driver_rentals collection: ${e.message}", e)
        }
    }
    
    /**
     * Run all migrations
     * Dipanggil sekali saat app start atau login
     */
    suspend fun migrateAllCollections(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Starting Firestore username migration for all collections...")
            
            migrateRentalsCollection(context)
            migratePaymentHistoryCollection(context)
            migrateDriverRentalsCollection(context)
            
            Log.d(TAG, "✅ All Firestore migrations complete!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during migration: ${e.message}", e)
        }
    }
}

