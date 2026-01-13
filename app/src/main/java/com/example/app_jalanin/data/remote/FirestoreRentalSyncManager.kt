package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.utils.UsernameResolver
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi rental data antara Room dan Firestore
 * Memastikan rental history tetap tersimpan di cloud untuk backup dan cross-device access
 * Updated: Fix persistence issues
 */
object FirestoreRentalSyncManager {
    private const val TAG = "FirestoreRentalSync"
    private const val RENTALS_COLLECTION = "rentals"

    /**
     * Sync all unsynced rentals to Firestore
     * Dipanggil setelah rental baru dibuat atau diupdate
     */
    suspend fun syncUnsyncedRentals(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unsyncedRentals = db.rentalDao().getUnsyncedRentals()

            if (unsyncedRentals.isEmpty()) {
                Log.d(TAG, "✅ No unsynced rentals to upload")
                return@withContext
            }

            Log.d(TAG, "🔄 Syncing ${unsyncedRentals.size} rentals to Firestore...")

            val firestore = FirebaseFirestore.getInstance()
            var successCount = 0
            var failedCount = 0

            for (rental in unsyncedRentals) {
                try {
                    // Resolve usernames
                    val passengerUsername = UsernameResolver.resolveUsernameFromEmail(context, rental.userEmail)
                    val ownerUsername = if (rental.ownerEmail != null) {
                        UsernameResolver.resolveUsernameFromEmail(context, rental.ownerEmail)
                    } else {
                        "unknown"
                    }
                    val driverUsername = when {
                        rental.travelDriverId != null -> UsernameResolver.resolveUsernameFromEmail(context, rental.travelDriverId)
                        rental.deliveryDriverId != null -> UsernameResolver.resolveUsernameFromEmail(context, rental.deliveryDriverId)
                        else -> "unknown"
                    }
                    
                    val rentalData = hashMapOf(
                        "userId" to rental.userId,
                        "userEmail" to rental.userEmail,
                        "vehicleId" to rental.vehicleId,
                        "vehicleName" to rental.vehicleName,
                        "vehicleType" to rental.vehicleType,
                        "startDate" to rental.startDate,
                        "endDate" to rental.endDate,
                        "durationDays" to rental.durationDays,
                        "durationHours" to rental.durationHours,
                        "durationMinutes" to rental.durationMinutes,
                        "durationMillis" to rental.durationMillis,
                        "totalPrice" to rental.totalPrice,
                        "status" to rental.status,
                        "overtimeFee" to rental.overtimeFee,
                        "isWithDriver" to rental.isWithDriver,
                "deliveryAddress" to rental.deliveryAddress,
                "deliveryLat" to rental.deliveryLat,
                "deliveryLon" to rental.deliveryLon,
                "createdAt" to rental.createdAt,
                "updatedAt" to rental.updatedAt,
                "ownerEmail" to (rental.ownerEmail ?: ""),
                "driverId" to (rental.driverId ?: ""),
                "driverAvailability" to (rental.driverAvailability ?: ""),
                "ownerContacted" to rental.ownerContacted,
                "ownerConfirmed" to rental.ownerConfirmed,
                "deliveryMode" to (rental.deliveryMode ?: ""),
                "deliveryDriverId" to (rental.deliveryDriverId ?: ""),
                "deliveryStatus" to (rental.deliveryStatus ?: ""),
                "travelDriverId" to (rental.travelDriverId ?: ""),
                "deliveryStartedAt" to (rental.deliveryStartedAt ?: 0L),
                "deliveryArrivedAt" to (rental.deliveryArrivedAt ?: 0L),
                "travelStartedAt" to (rental.travelStartedAt ?: 0L),
                // ✅ NEW: Early return fields
                "returnLocationLat" to (rental.returnLocationLat ?: 0.0),
                "returnLocationLon" to (rental.returnLocationLon ?: 0.0),
                "returnAddress" to (rental.returnAddress ?: ""),
                "earlyReturnRequested" to rental.earlyReturnRequested,
                "earlyReturnStatus" to (rental.earlyReturnStatus ?: ""),
                "earlyReturnRequestedAt" to (rental.earlyReturnRequestedAt ?: 0L),
                // ✅ NEW: Username fields
                "passengerUsername" to passengerUsername,
                "ownerUsername" to ownerUsername,
                "driverUsername" to driverUsername
            )

                    firestore.collection(RENTALS_COLLECTION)
                        .document(rental.id)
                        .set(rentalData)
                        .await()

                    // Mark as synced in Room
                    db.rentalDao().updateSyncStatus(rental.id, true)
                    successCount++

                    Log.d(TAG, "✅ Synced rental: ${rental.id} (${rental.vehicleName})")
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Failed to sync rental ${rental.id}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Sync complete: $successCount/${unsyncedRentals.size} rentals synced successfully")
            if (failedCount > 0) {
                Log.w(TAG, "⚠️ $failedCount rentals failed to sync (will retry later)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing rentals: ${e.message}", e)
        }
    }

    /**
     * Download rentals from Firestore for a specific user
     * Digunakan saat login untuk restore rental history dari cloud
     */
    suspend fun downloadUserRentals(context: Context, userId: Int, userEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            Log.d(TAG, "📥 Downloading rentals for user $userId ($userEmail) from Firestore...")

            // Query by userId OR userEmail untuk lebih reliable
            val snapshot = firestore.collection(RENTALS_COLLECTION)
                .whereEqualTo("userEmail", userEmail)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "📭 No rentals found in Firestore for user $userEmail")
                return@withContext
            }

            Log.d(TAG, "📦 Found ${snapshot.documents.size} rentals in Firestore")

            var downloadedCount = 0
            var skippedCount = 0

            for (doc in snapshot.documents) {
                try {
                    // Check if rental already exists locally
                    val existingRental = db.rentalDao().getRentalById(doc.id)
                    if (existingRental != null) {
                        Log.d(TAG, "⏭️ Rental ${doc.id} already exists locally, skipping")
                        skippedCount++
                        continue
                    }

                    // Helper function to safely extract userId (handle both String and Number types)
                    val extractedUserId = try {
                        // Try to get as Long (Number type)
                        doc.getLong("userId")?.toInt() ?: run {
                            // If null, try to get as String and parse it
                            val userIdString = doc.getString("userId")
                            userIdString?.toIntOrNull() ?: userId
                        }
                    } catch (e: Exception) {
                        // If getLong throws exception (field is String), try parsing as String
                        try {
                            doc.getString("userId")?.toIntOrNull() ?: userId
                        } catch (e2: Exception) {
                            // Fallback to provided userId parameter
                            Log.w(TAG, "⚠️ Could not extract userId from rental ${doc.id}, using provided userId: $userId")
                            userId
                        }
                    }

                    // Create rental entity from Firestore data
                    val rental = com.example.app_jalanin.data.local.entity.Rental(
                        id = doc.id,
                        userId = extractedUserId,
                        userEmail = doc.getString("userEmail") ?: userEmail,
                        vehicleId = doc.getString("vehicleId") ?: "",
                        vehicleName = doc.getString("vehicleName") ?: "",
                        vehicleType = doc.getString("vehicleType") ?: "",
                        startDate = doc.getLong("startDate") ?: 0,
                        endDate = doc.getLong("endDate") ?: 0,
                        durationDays = (doc.getLong("durationDays") ?: 0).toInt(),
                        durationHours = (doc.getLong("durationHours") ?: 0).toInt(),
                        durationMinutes = (doc.getLong("durationMinutes") ?: 0).toInt(),
                        durationMillis = doc.getLong("durationMillis") ?: 0,
                        totalPrice = (doc.getLong("totalPrice") ?: 0).toInt(),
                        status = doc.getString("status") ?: "COMPLETED",
                        overtimeFee = (doc.getLong("overtimeFee") ?: 0).toInt(),
                        isWithDriver = doc.getBoolean("isWithDriver") ?: false,
                        deliveryAddress = doc.getString("deliveryAddress") ?: "",
                        deliveryLat = doc.getDouble("deliveryLat") ?: 0.0,
                        deliveryLon = doc.getDouble("deliveryLon") ?: 0.0,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        synced = true, // Already in Firestore, mark as synced
                        // ✅ FIX: Include all rental fields
                        ownerEmail = doc.getString("ownerEmail"),
                        driverId = doc.getString("driverId"),
                        deliveryDriverId = doc.getString("deliveryDriverId"),
                        travelDriverId = doc.getString("travelDriverId"),
                        deliveryMode = doc.getString("deliveryMode"),
                        deliveryStatus = doc.getString("deliveryStatus"),
                        deliveryStartedAt = doc.getLong("deliveryStartedAt"),
                        deliveryArrivedAt = doc.getLong("deliveryArrivedAt"),
                        travelStartedAt = doc.getLong("travelStartedAt"),
                        driverAvailability = doc.getString("driverAvailability"),
                        ownerContacted = doc.getBoolean("ownerContacted") ?: false,
                        ownerConfirmed = doc.getBoolean("ownerConfirmed") ?: false,
                        // ✅ NEW: Early return fields
                        returnLocationLat = doc.getDouble("returnLocationLat"),
                        returnLocationLon = doc.getDouble("returnLocationLon"),
                        returnAddress = doc.getString("returnAddress"),
                        earlyReturnRequested = doc.getBoolean("earlyReturnRequested") ?: false,
                        earlyReturnStatus = doc.getString("earlyReturnStatus"),
                        earlyReturnRequestedAt = doc.getLong("earlyReturnRequestedAt")
                    )

                    db.rentalDao().insert(rental)
                    downloadedCount++
                    Log.d(TAG, "✅ Downloaded rental: ${rental.id} (${rental.vehicleName})")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to process rental ${doc.id}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Download complete: $downloadedCount new rentals, $skippedCount already exist")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading rentals: ${e.message}", e)
        }
    }

    /**
     * Download rentals from Firestore for a vehicle owner
     * Queries by ownerEmail to get rentals of owner's vehicles
     */
    suspend fun downloadOwnerRentals(context: Context, ownerEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            Log.d(TAG, "📥 Downloading rentals for owner $ownerEmail from Firestore...")

            // Query by ownerEmail to get rentals of owner's vehicles
            val snapshot = firestore.collection(RENTALS_COLLECTION)
                .whereEqualTo("ownerEmail", ownerEmail)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "📭 No rentals found in Firestore for owner $ownerEmail")
                return@withContext
            }

            Log.d(TAG, "📦 Found ${snapshot.documents.size} owner rentals in Firestore")

            var downloadedCount = 0
            var skippedCount = 0

            for (doc in snapshot.documents) {
                try {
                    // Check if rental already exists locally
                    val existingRental = db.rentalDao().getRentalById(doc.id)
                    if (existingRental != null) {
                        // Update existing rental if Firestore version is newer
                        val firestoreUpdatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        if (firestoreUpdatedAt > existingRental.updatedAt) {
                            // Extract userId safely
                            val extractedUserId = try {
                                doc.getLong("userId")?.toInt() ?: run {
                                    doc.getString("userId")?.toIntOrNull() ?: existingRental.userId
                                }
                            } catch (e: Exception) {
                                try {
                                    doc.getString("userId")?.toIntOrNull() ?: existingRental.userId
                                } catch (e2: Exception) {
                                    existingRental.userId
                                }
                            }

                            val updatedRental = existingRental.copy(
                                userId = extractedUserId,
                                userEmail = doc.getString("userEmail") ?: existingRental.userEmail,
                                vehicleId = doc.getString("vehicleId") ?: existingRental.vehicleId,
                                vehicleName = doc.getString("vehicleName") ?: existingRental.vehicleName,
                                vehicleType = doc.getString("vehicleType") ?: existingRental.vehicleType,
                                startDate = doc.getLong("startDate") ?: existingRental.startDate,
                                endDate = doc.getLong("endDate") ?: existingRental.endDate,
                                durationDays = (doc.getLong("durationDays") ?: existingRental.durationDays.toLong()).toInt(),
                                durationHours = (doc.getLong("durationHours") ?: existingRental.durationHours.toLong()).toInt(),
                                durationMinutes = (doc.getLong("durationMinutes") ?: existingRental.durationMinutes.toLong()).toInt(),
                                durationMillis = doc.getLong("durationMillis") ?: existingRental.durationMillis,
                                totalPrice = (doc.getLong("totalPrice") ?: existingRental.totalPrice.toLong()).toInt(),
                                status = doc.getString("status") ?: existingRental.status,
                                overtimeFee = (doc.getLong("overtimeFee") ?: existingRental.overtimeFee.toLong()).toInt(),
                                isWithDriver = doc.getBoolean("isWithDriver") ?: existingRental.isWithDriver,
                                deliveryAddress = doc.getString("deliveryAddress") ?: existingRental.deliveryAddress,
                                deliveryLat = doc.getDouble("deliveryLat") ?: existingRental.deliveryLat,
                                deliveryLon = doc.getDouble("deliveryLon") ?: existingRental.deliveryLon,
                                updatedAt = firestoreUpdatedAt,
                                synced = true,
                                ownerEmail = doc.getString("ownerEmail") ?: existingRental.ownerEmail,
                                returnLocationLat = doc.getDouble("returnLocationLat") ?: existingRental.returnLocationLat,
                                returnLocationLon = doc.getDouble("returnLocationLon") ?: existingRental.returnLocationLon,
                                returnAddress = doc.getString("returnAddress") ?: existingRental.returnAddress,
                                earlyReturnRequested = doc.getBoolean("earlyReturnRequested") ?: existingRental.earlyReturnRequested,
                                earlyReturnStatus = doc.getString("earlyReturnStatus") ?: existingRental.earlyReturnStatus,
                                earlyReturnRequestedAt = doc.getLong("earlyReturnRequestedAt") ?: existingRental.earlyReturnRequestedAt
                            )
                            db.rentalDao().update(updatedRental)
                            downloadedCount++
                            Log.d(TAG, "✅ Updated owner rental: ${updatedRental.id}")
                        } else {
                            skippedCount++
                            Log.d(TAG, "⏭️ Owner rental ${doc.id} already up-to-date, skipping")
                        }
                        continue
                    }

                    // Extract userId safely
                    val extractedUserId = try {
                        doc.getLong("userId")?.toInt() ?: run {
                            doc.getString("userId")?.toIntOrNull() ?: 0
                        }
                    } catch (e: Exception) {
                        try {
                            doc.getString("userId")?.toIntOrNull() ?: 0
                        } catch (e2: Exception) {
                            Log.w(TAG, "⚠️ Could not extract userId from owner rental ${doc.id}")
                            0
                        }
                    }

                    // Create rental entity from Firestore data
                    val rental = com.example.app_jalanin.data.local.entity.Rental(
                        id = doc.id,
                        userId = extractedUserId,
                        userEmail = doc.getString("userEmail") ?: "",
                        vehicleId = doc.getString("vehicleId") ?: "",
                        vehicleName = doc.getString("vehicleName") ?: "",
                        vehicleType = doc.getString("vehicleType") ?: "",
                        startDate = doc.getLong("startDate") ?: 0,
                        endDate = doc.getLong("endDate") ?: 0,
                        durationDays = (doc.getLong("durationDays") ?: 0).toInt(),
                        durationHours = (doc.getLong("durationHours") ?: 0).toInt(),
                        durationMinutes = (doc.getLong("durationMinutes") ?: 0).toInt(),
                        durationMillis = doc.getLong("durationMillis") ?: 0,
                        totalPrice = (doc.getLong("totalPrice") ?: 0).toInt(),
                        status = doc.getString("status") ?: "COMPLETED",
                        overtimeFee = (doc.getLong("overtimeFee") ?: 0).toInt(),
                        isWithDriver = doc.getBoolean("isWithDriver") ?: false,
                        deliveryAddress = doc.getString("deliveryAddress") ?: "",
                        deliveryLat = doc.getDouble("deliveryLat") ?: 0.0,
                        deliveryLon = doc.getDouble("deliveryLon") ?: 0.0,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        synced = true,
                        ownerEmail = doc.getString("ownerEmail") ?: ownerEmail,
                        returnLocationLat = doc.getDouble("returnLocationLat"),
                        returnLocationLon = doc.getDouble("returnLocationLon"),
                        returnAddress = doc.getString("returnAddress"),
                        earlyReturnRequested = doc.getBoolean("earlyReturnRequested") ?: false,
                        earlyReturnStatus = doc.getString("earlyReturnStatus"),
                        earlyReturnRequestedAt = doc.getLong("earlyReturnRequestedAt")
                    )

                    db.rentalDao().insert(rental)
                    downloadedCount++
                    Log.d(TAG, "✅ Downloaded owner rental: ${rental.id} (${rental.vehicleName})")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to process owner rental ${doc.id}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Owner rental download complete: $downloadedCount new/updated rentals, $skippedCount already exist")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading owner rentals: ${e.message}", e)
        }
    }

    /**
     * Download rentals from Firestore where driver is assigned (as deliveryDriverId or travelDriverId)
     * Used for driver dashboard to restore rental data after app data is cleared
     */
    suspend fun downloadRentalsByDriver(context: Context, driverEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            Log.d(TAG, "📥 Downloading rentals for driver $driverEmail from Firestore...")

            // Query rentals where driver is assigned as deliveryDriverId or travelDriverId
            // Firestore doesn't support OR queries directly, so we need to query separately and merge
            val deliverySnapshot = firestore.collection(RENTALS_COLLECTION)
                .whereEqualTo("deliveryDriverId", driverEmail)
                .get()
                .await()

            val travelSnapshot = firestore.collection(RENTALS_COLLECTION)
                .whereEqualTo("travelDriverId", driverEmail)
                .get()
                .await()

            // Merge results and remove duplicates
            val allDocs = (deliverySnapshot.documents + travelSnapshot.documents)
                .distinctBy { it.id }

            if (allDocs.isEmpty()) {
                Log.d(TAG, "📭 No rentals found in Firestore for driver $driverEmail")
                return@withContext
            }

            Log.d(TAG, "📦 Found ${allDocs.size} driver rentals in Firestore")

            var downloadedCount = 0
            var skippedCount = 0

            for (doc in allDocs) {
                try {
                    val existingRental = db.rentalDao().getRentalById(doc.id)
                    if (existingRental != null) {
                        skippedCount++
                        continue
                    }

                    // Extract userId safely
                    val extractedUserId = try {
                        doc.getLong("userId")?.toInt() ?: run {
                            doc.getString("userId")?.toIntOrNull() ?: 0
                        }
                    } catch (e: Exception) {
                        try {
                            doc.getString("userId")?.toIntOrNull() ?: 0
                        } catch (e2: Exception) {
                            0
                        }
                    }

                    // Create rental entity from Firestore data
                    val rental = com.example.app_jalanin.data.local.entity.Rental(
                        id = doc.id,
                        userId = extractedUserId,
                        userEmail = doc.getString("userEmail") ?: "",
                        vehicleId = doc.getString("vehicleId") ?: "",
                        vehicleName = doc.getString("vehicleName") ?: "",
                        vehicleType = doc.getString("vehicleType") ?: "",
                        startDate = doc.getLong("startDate") ?: 0,
                        endDate = doc.getLong("endDate") ?: 0,
                        durationDays = (doc.getLong("durationDays") ?: 0).toInt(),
                        durationHours = (doc.getLong("durationHours") ?: 0).toInt(),
                        durationMinutes = (doc.getLong("durationMinutes") ?: 0).toInt(),
                        durationMillis = doc.getLong("durationMillis") ?: 0,
                        totalPrice = (doc.getLong("totalPrice") ?: 0).toInt(),
                        status = doc.getString("status") ?: "COMPLETED",
                        overtimeFee = (doc.getLong("overtimeFee") ?: 0).toInt(),
                        isWithDriver = doc.getBoolean("isWithDriver") ?: false,
                        deliveryAddress = doc.getString("deliveryAddress") ?: "",
                        deliveryLat = doc.getDouble("deliveryLat") ?: 0.0,
                        deliveryLon = doc.getDouble("deliveryLon") ?: 0.0,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        synced = true,
                        ownerEmail = doc.getString("ownerEmail"),
                        driverId = doc.getString("driverId"),
                        driverAvailability = doc.getString("driverAvailability"),
                        ownerContacted = doc.getBoolean("ownerContacted") ?: false,
                        ownerConfirmed = doc.getBoolean("ownerConfirmed") ?: false,
                        deliveryMode = doc.getString("deliveryMode"),
                        deliveryDriverId = doc.getString("deliveryDriverId"),
                        deliveryStatus = doc.getString("deliveryStatus"),
                        travelDriverId = doc.getString("travelDriverId"),
                        deliveryStartedAt = doc.getLong("deliveryStartedAt"),
                        deliveryArrivedAt = doc.getLong("deliveryArrivedAt"),
                        travelStartedAt = doc.getLong("travelStartedAt"),
                        returnLocationLat = doc.getDouble("returnLocationLat"),
                        returnLocationLon = doc.getDouble("returnLocationLon"),
                        returnAddress = doc.getString("returnAddress"),
                        earlyReturnRequested = doc.getBoolean("earlyReturnRequested") ?: false,
                        earlyReturnStatus = doc.getString("earlyReturnStatus"),
                        earlyReturnRequestedAt = doc.getLong("earlyReturnRequestedAt"),
                        vehicleRentalAmount = doc.getLong("vehicleRentalAmount")?.toInt(),
                        driverAmount = doc.getLong("driverAmount")?.toInt()
                    )

                    db.rentalDao().insert(rental)
                    downloadedCount++
                    Log.d(TAG, "✅ Downloaded driver rental: ${rental.id} (${rental.vehicleName})")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to process driver rental ${doc.id}: ${e.message}")
                }
            }

            Log.d(TAG, "🎉 Driver rental download complete: $downloadedCount new rentals, $skippedCount already exist")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading driver rentals: ${e.message}", e)
        }
    }

    /**
     * Sync single rental to Firestore
     * Digunakan saat membuat atau mengupdate rental
     */
    suspend fun syncSingleRental(context: Context, rentalId: String): Boolean {
        return try {
            val db = AppDatabase.getDatabase(context)
            val rental = db.rentalDao().getRentalById(rentalId)

            if (rental == null) {
                Log.w(TAG, "⚠️ Rental $rentalId not found in local database")
                return false
            }

            Log.d(TAG, "🔄 Syncing rental ${rental.id} to Firestore...")

            // Resolve usernames
            val passengerUsername = UsernameResolver.resolveUsernameFromEmail(context, rental.userEmail)
            val ownerUsername = if (rental.ownerEmail != null) {
                UsernameResolver.resolveUsernameFromEmail(context, rental.ownerEmail)
            } else {
                "unknown"
            }
            val driverUsername = when {
                rental.travelDriverId != null -> UsernameResolver.resolveUsernameFromEmail(context, rental.travelDriverId)
                rental.deliveryDriverId != null -> UsernameResolver.resolveUsernameFromEmail(context, rental.deliveryDriverId)
                else -> "unknown"
            }

            val rentalData = hashMapOf(
                "userId" to rental.userId,
                "userEmail" to rental.userEmail,
                "vehicleId" to rental.vehicleId,
                "vehicleName" to rental.vehicleName,
                "vehicleType" to rental.vehicleType,
                "startDate" to rental.startDate,
                "endDate" to rental.endDate,
                "durationDays" to rental.durationDays,
                "durationHours" to rental.durationHours,
                "durationMinutes" to rental.durationMinutes,
                "durationMillis" to rental.durationMillis,
                "totalPrice" to rental.totalPrice,
                "status" to rental.status,
                "overtimeFee" to rental.overtimeFee,
                "isWithDriver" to rental.isWithDriver,
                "deliveryAddress" to rental.deliveryAddress,
                "deliveryLat" to rental.deliveryLat,
                "deliveryLon" to rental.deliveryLon,
                "createdAt" to rental.createdAt,
                "updatedAt" to rental.updatedAt,
                "ownerEmail" to (rental.ownerEmail ?: ""),
                "driverId" to (rental.driverId ?: ""),
                "driverAvailability" to (rental.driverAvailability ?: ""),
                "ownerContacted" to rental.ownerContacted,
                "ownerConfirmed" to rental.ownerConfirmed,
                "deliveryMode" to (rental.deliveryMode ?: ""),
                "deliveryDriverId" to (rental.deliveryDriverId ?: ""),
                "deliveryStatus" to (rental.deliveryStatus ?: ""),
                "travelDriverId" to (rental.travelDriverId ?: ""),
                "deliveryStartedAt" to (rental.deliveryStartedAt ?: 0L),
                "deliveryArrivedAt" to (rental.deliveryArrivedAt ?: 0L),
                "travelStartedAt" to (rental.travelStartedAt ?: 0L),
                // ✅ NEW: Early return fields
                "returnLocationLat" to (rental.returnLocationLat ?: 0.0),
                "returnLocationLon" to (rental.returnLocationLon ?: 0.0),
                "returnAddress" to (rental.returnAddress ?: ""),
                "earlyReturnRequested" to rental.earlyReturnRequested,
                "earlyReturnStatus" to (rental.earlyReturnStatus ?: ""),
                "earlyReturnRequestedAt" to (rental.earlyReturnRequestedAt ?: 0L),
                // ✅ NEW: Username fields
                "passengerUsername" to passengerUsername,
                "ownerUsername" to ownerUsername,
                "driverUsername" to driverUsername
            )

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(RENTALS_COLLECTION)
                .document(rental.id)
                .set(rentalData)
                .await()

            // Mark as synced
            db.rentalDao().updateSyncStatus(rental.id, true)

            Log.d(TAG, "✅ Rental ${rental.id} synced successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync rental $rentalId: ${e.message}", e)
            false
        }
    }

    /**
     * Auto-sync: Periodically sync unsynced rentals in background
     * Dapat dipanggil dari WorkManager atau di onResume activity
     */
    suspend fun autoSync(context: Context) {
        try {
            Log.d(TAG, "🔄 Auto-sync: Checking for unsynced rentals...")
            syncUnsyncedRentals(context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Auto-sync failed: ${e.message}", e)
        }
    }
}

