package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi DriverRental antara Room dan Firestore
 */
object FirestoreDriverRentalSyncManager {
    private const val TAG = "FirestoreDriverRental"
    private const val DRIVER_RENTALS_COLLECTION = "driver_rentals"
    
    /**
     * Sync all unsynced driver rentals to Firestore
     */
    suspend fun syncUnsyncedRentals(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unsyncedRentals = db.driverRentalDao().getUnsyncedRentals()
            
            if (unsyncedRentals.isEmpty()) {
                Log.d(TAG, "✅ No unsynced driver rentals to upload")
                return@withContext
            }
            
            Log.d(TAG, "🔄 Syncing ${unsyncedRentals.size} driver rental records to Firestore...")
            
            val firestore = FirebaseFirestore.getInstance()
            var successCount = 0
            var failedCount = 0
            
            for (rental in unsyncedRentals) {
                try {
                    // Resolve passenger username
                    val passengerUsername = com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(context, rental.passengerEmail)
                    
                    val rentalData = hashMapOf(
                        "id" to rental.id,
                        "passengerEmail" to rental.passengerEmail,
                        "passengerUsername" to passengerUsername,
                        "driverEmail" to rental.driverEmail,
                        "driverName" to rental.driverName,
                        "vehicleType" to rental.vehicleType,
                        "durationType" to rental.durationType,
                        "durationCount" to rental.durationCount,
                        "price" to rental.price,
                        "paymentMethod" to rental.paymentMethod,
                        "pickupAddress" to rental.pickupAddress,
                        "pickupLat" to rental.pickupLat,
                        "pickupLon" to rental.pickupLon,
                        "destinationAddress" to rental.destinationAddress,
                        "destinationLat" to rental.destinationLat,
                        "destinationLon" to rental.destinationLon,
                        "status" to rental.status,
                        "startDate" to rental.startDate,
                        "endDate" to rental.endDate,
                        "confirmedAt" to rental.confirmedAt,
                        "completedAt" to rental.completedAt,
                        "createdAt" to rental.createdAt,
                        "updatedAt" to rental.updatedAt,
                        "synced" to true
                    )
                    
                    firestore.collection(DRIVER_RENTALS_COLLECTION)
                        .document(rental.id)
                        .set(rentalData)
                        .await()
                    
                    // Mark as synced in Room
                    db.driverRentalDao().markSynced(rental.id)
                    successCount++
                    
                    Log.d(TAG, "✅ Synced driver rental: ${rental.id}")
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Failed to sync driver rental ${rental.id}: ${e.message}")
                }
            }
            
            Log.d(TAG, "🎉 Sync complete: $successCount/${unsyncedRentals.size} driver rentals synced successfully")
            if (failedCount > 0) {
                Log.w(TAG, "⚠️ $failedCount driver rentals failed to sync (will retry later)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing driver rentals: ${e.message}", e)
        }
    }
    
    /**
     * Sync single driver rental to Firestore
     */
    suspend fun syncSingleRental(context: Context, rentalId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val rental = db.driverRentalDao().getRentalById(rentalId)
            
            if (rental == null) {
                Log.e(TAG, "❌ Driver rental not found: $rentalId")
                return@withContext false
            }
            
            // Resolve passenger username
            val passengerUsername = com.example.app_jalanin.utils.UsernameResolver.resolveUsernameFromEmail(context, rental.passengerEmail)
            
            val firestore = FirebaseFirestore.getInstance()
            val rentalData = hashMapOf(
                "id" to rental.id,
                "passengerEmail" to rental.passengerEmail,
                "passengerUsername" to passengerUsername,
                "driverEmail" to rental.driverEmail,
                "driverName" to rental.driverName,
                "vehicleType" to rental.vehicleType,
                "durationType" to rental.durationType,
                "durationCount" to rental.durationCount,
                "price" to rental.price,
                "paymentMethod" to rental.paymentMethod,
                "pickupAddress" to rental.pickupAddress,
                "pickupLat" to rental.pickupLat,
                "pickupLon" to rental.pickupLon,
                "destinationAddress" to rental.destinationAddress,
                "destinationLat" to rental.destinationLat,
                "destinationLon" to rental.destinationLon,
                "status" to rental.status,
                "startDate" to rental.startDate,
                "endDate" to rental.endDate,
                "confirmedAt" to rental.confirmedAt,
                "completedAt" to rental.completedAt,
                "createdAt" to rental.createdAt,
                "updatedAt" to rental.updatedAt,
                "synced" to true
            )
            
            firestore.collection(DRIVER_RENTALS_COLLECTION)
                .document(rentalId)
                .set(rentalData)
                .await()
            
            // Mark as synced
            db.driverRentalDao().markSynced(rentalId)
            
            Log.d(TAG, "✅ Driver rental $rentalId synced successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync driver rental $rentalId: ${e.message}")
            false
        }
    }
    
    /**
     * Download driver rentals from Firestore for a passenger
     */
    suspend fun downloadPassengerRentals(context: Context, passengerEmail: String) = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val db = AppDatabase.getDatabase(context)
            
            val snapshot = firestore.collection(DRIVER_RENTALS_COLLECTION)
                .whereEqualTo("passengerEmail", passengerEmail)
                .get()
                .await()
            
            Log.d(TAG, "📥 Downloading ${snapshot.documents.size} driver rental records for $passengerEmail...")
            
            for (doc in snapshot.documents) {
                try {
                    val rentalId = doc.getString("id") ?: continue
                    val existingRental = db.driverRentalDao().getRentalById(rentalId)
                    
                    if (existingRental == null) {
                        // Create new driver rental from Firestore
                        val rental = com.example.app_jalanin.data.local.entity.DriverRental(
                            id = rentalId,
                            passengerEmail = doc.getString("passengerEmail") ?: "",
                            // passengerName removed - resolved dynamically via passengerEmail
                            driverEmail = doc.getString("driverEmail") ?: "",
                            driverName = doc.getString("driverName"),
                            vehicleType = doc.getString("vehicleType") ?: "",
                            durationType = doc.getString("durationType") ?: "",
                            durationCount = doc.getLong("durationCount")?.toInt() ?: 0,
                            price = doc.getLong("price") ?: 0L,
                            paymentMethod = doc.getString("paymentMethod") ?: "",
                            pickupAddress = doc.getString("pickupAddress") ?: "",
                            pickupLat = doc.getDouble("pickupLat") ?: 0.0,
                            pickupLon = doc.getDouble("pickupLon") ?: 0.0,
                            destinationAddress = doc.getString("destinationAddress"),
                            destinationLat = doc.getDouble("destinationLat"),
                            destinationLon = doc.getDouble("destinationLon"),
                            status = doc.getString("status") ?: "PENDING",
                            startDate = doc.getLong("startDate"),
                            endDate = doc.getLong("endDate"),
                            confirmedAt = doc.getLong("confirmedAt"),
                            completedAt = doc.getLong("completedAt"),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                            synced = true
                        )
                        
                        db.driverRentalDao().insert(rental)
                        Log.d(TAG, "✅ Downloaded driver rental: $rentalId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing driver rental document: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ Driver rental download complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading driver rentals: ${e.message}", e)
        }
    }
    
    /**
     * Download driver rentals from Firestore for a driver
     */
    suspend fun downloadDriverRentals(context: Context, driverEmail: String) = withContext(Dispatchers.IO) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val db = AppDatabase.getDatabase(context)
            
            val snapshot = firestore.collection(DRIVER_RENTALS_COLLECTION)
                .whereEqualTo("driverEmail", driverEmail)
                .get()
                .await()
            
            Log.d(TAG, "📥 Downloading ${snapshot.documents.size} driver rental records for driver $driverEmail...")
            
            for (doc in snapshot.documents) {
                try {
                    val rentalId = doc.getString("id") ?: continue
                    val existingRental = db.driverRentalDao().getRentalById(rentalId)
                    
                    if (existingRental == null) {
                        // Create new driver rental from Firestore
                        val rental = com.example.app_jalanin.data.local.entity.DriverRental(
                            id = rentalId,
                            passengerEmail = doc.getString("passengerEmail") ?: "",
                            // passengerName removed - resolved dynamically via passengerEmail
                            driverEmail = doc.getString("driverEmail") ?: "",
                            driverName = doc.getString("driverName"),
                            vehicleType = doc.getString("vehicleType") ?: "",
                            durationType = doc.getString("durationType") ?: "",
                            durationCount = doc.getLong("durationCount")?.toInt() ?: 0,
                            price = doc.getLong("price") ?: 0L,
                            paymentMethod = doc.getString("paymentMethod") ?: "",
                            pickupAddress = doc.getString("pickupAddress") ?: "",
                            pickupLat = doc.getDouble("pickupLat") ?: 0.0,
                            pickupLon = doc.getDouble("pickupLon") ?: 0.0,
                            destinationAddress = doc.getString("destinationAddress"),
                            destinationLat = doc.getDouble("destinationLat"),
                            destinationLon = doc.getDouble("destinationLon"),
                            status = doc.getString("status") ?: "PENDING",
                            startDate = doc.getLong("startDate"),
                            endDate = doc.getLong("endDate"),
                            confirmedAt = doc.getLong("confirmedAt"),
                            completedAt = doc.getLong("completedAt"),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                            synced = true
                        )
                        
                        db.driverRentalDao().insert(rental)
                        Log.d(TAG, "✅ Downloaded driver rental: $rentalId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing driver rental document: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ Driver rental download complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading driver rentals: ${e.message}", e)
        }
    }
}

