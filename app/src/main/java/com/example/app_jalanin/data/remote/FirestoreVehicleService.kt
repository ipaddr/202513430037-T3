package com.example.app_jalanin.data.remote

import android.util.Log
import com.example.app_jalanin.data.model.Vehicle
import com.example.app_jalanin.data.model.VehicleStatus
import com.example.app_jalanin.data.model.VehicleType
import com.google.firebase.Firebase
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

/**
 * Service untuk sync data kendaraan ke Firestore
 * Memastikan data kendaraan tersimpan di cloud untuk backup dan cross-device access
 */
object FirestoreVehicleService {
    private const val TAG = "FirestoreVehicleService"
    private const val VEHICLES_COLLECTION = "vehicles"
    
    private val db get() = Firebase.firestore

    /**
     * Sync vehicle ke Firestore (insert atau update)
     * 
     * @param vehicle Vehicle object yang akan di-sync
     * @return true jika berhasil, false jika gagal
     */
    suspend fun syncVehicle(vehicle: Vehicle): Boolean {
        return try {
            Log.d(TAG, "🔄 Syncing vehicle to Firestore:")
            Log.d(TAG, "   - ID: ${vehicle.id}")
            Log.d(TAG, "   - Name: ${vehicle.name}")
            Log.d(TAG, "   - Owner ID: '${vehicle.ownerId}'")
            Log.d(TAG, "   - License Plate: ${vehicle.licensePlate}")
            
            // ✅ CRITICAL: Validate ownerId is not empty
            if (vehicle.ownerId.isBlank()) {
                Log.e(TAG, "❌ ERROR: ownerId is blank! Cannot sync vehicle.")
                return false
            }
            
            val vehicleData = hashMapOf(
                "id" to vehicle.id,
                "ownerId" to vehicle.ownerId, // ✅ CRITICAL: This must be the correct owner email
                "name" to vehicle.name,
                "type" to vehicle.type.name, // Convert enum to string
                "brand" to vehicle.brand,
                "model" to vehicle.model,
                "year" to vehicle.year,
                "licensePlate" to vehicle.licensePlate,
                "transmission" to vehicle.transmission,
                // ✅ FIX: Store seats as Integer if available, or omit if null (for consistency)
                // Note: Some old data might have seats as String, but we'll handle both in parsing
                "seats" to (vehicle.seats?.let { it.toString() } ?: ""),
                "engineCapacity" to (vehicle.engineCapacity ?: ""),
                "pricePerHour" to vehicle.pricePerHour,
                "pricePerDay" to vehicle.pricePerDay,
                "pricePerWeek" to vehicle.pricePerWeek,
                "features" to vehicle.features,
                "status" to vehicle.status.name, // Convert enum to string
                "statusReason" to (vehicle.statusReason ?: ""),
                "locationLat" to vehicle.locationLat,
                "locationLon" to vehicle.locationLon,
                "locationAddress" to vehicle.locationAddress,
                "imageUrl" to (vehicle.imageUrl ?: ""),
                "driverId" to (vehicle.driverId ?: ""),
                "driverAvailability" to (vehicle.driverAvailability ?: ""),
                "driverAssignmentMode" to (vehicle.driverAssignmentMode ?: ""),
                "createdAt" to vehicle.createdAt,
                "updatedAt" to vehicle.updatedAt
            )
            
            // ✅ CRITICAL: Log the ownerId that will be saved
            Log.d(TAG, "📝 Saving to Firestore with ownerId: '${vehicleData["ownerId"]}'")
            
            // Use vehicle ID as document ID for easy lookup
            val documentRef = db.collection(VEHICLES_COLLECTION)
                .document(vehicle.id.toString())
            
            documentRef.set(vehicleData).await()
            
            // ✅ VERIFY: Read back the document to confirm ownerId was saved correctly
            val savedDoc = documentRef.get().await()
            val savedOwnerId = savedDoc.getString("ownerId")
            Log.d(TAG, "✅ Vehicle synced successfully to Firestore")
            Log.d(TAG, "   - Document ID: ${vehicle.id}")
            Log.d(TAG, "   - Saved ownerId: '$savedOwnerId'")
            
            if (savedOwnerId != vehicle.ownerId) {
                Log.e(TAG, "❌ ERROR: ownerId mismatch! Expected: '${vehicle.ownerId}', Got: '$savedOwnerId'")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync vehicle to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Get vehicle from Firestore by ID
     * 
     * @param vehicleId ID kendaraan
     * @return Vehicle object atau null jika tidak ditemukan
     */
    suspend fun getVehicleById(vehicleId: Int): Vehicle? {
        return try {
            Log.d(TAG, "🔍 Getting vehicle from Firestore: ID=$vehicleId")
            
            val document = db.collection(VEHICLES_COLLECTION)
                .document(vehicleId.toString())
                .get()
                .await()
            
            if (document.exists()) {
                Log.d(TAG, "✅ Vehicle found in Firestore")
                documentToVehicle(document)
            } else {
                Log.d(TAG, "❌ Vehicle not found in Firestore: ID=$vehicleId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting vehicle from Firestore: ${e.message}", e)
            null
        }
    }

    /**
     * Get all vehicles by owner from Firestore
     * 
     * @param ownerId Email owner
     * @return List of vehicles
     */
    suspend fun getVehiclesByOwner(ownerId: String): List<Vehicle> {
        return try {
            Log.d(TAG, "🔍 Getting vehicles from Firestore for owner: $ownerId")
            
            // Try query by ownerId first
            val querySnapshot = db.collection(VEHICLES_COLLECTION)
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
            
            Log.d(TAG, "📊 Query result: ${querySnapshot.documents.size} documents found")
            
            // If no results, try to get all vehicles and filter manually (for debugging)
            if (querySnapshot.isEmpty) {
                Log.w(TAG, "⚠️ No vehicles found with ownerId='$ownerId', trying to get all vehicles for debugging...")
                // Remove limit to get ALL vehicles for debugging
                val allVehiclesSnapshot = db.collection(VEHICLES_COLLECTION)
                    .get()
                    .await()
                
                Log.d(TAG, "📊 Total vehicles in Firestore: ${allVehiclesSnapshot.documents.size}")
                
                // Log ALL vehicles to see their ownerId format with all fields
                allVehiclesSnapshot.documents.forEach { doc ->
                    val docOwnerId = doc.getString("ownerId")
                    val docName = doc.getString("name") ?: "N/A"
                    val docId = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: 0
                    Log.d(TAG, "   📋 Document ID: ${doc.id}, Vehicle ID: $docId, ownerId: '$docOwnerId', name: '$docName'")
                    doc.data?.let { data ->
                        Log.d(TAG, "      All fields: ${data.keys.joinToString(", ")}")
                        // Log ownerId field specifically to check for variations
                        data.forEach { (key, value) ->
                            if (key.lowercase().contains("owner") || key.lowercase().contains("email")) {
                                Log.d(TAG, "         $key = '$value' (type: ${value?.javaClass?.simpleName})")
                            }
                        }
                    } ?: Log.d(TAG, "      ⚠️ Document data is null")
                }
                
                // Also try case-insensitive search
                Log.d(TAG, "🔍 Trying case-insensitive search for ownerId...")
                val caseInsensitiveVehicles = allVehiclesSnapshot.documents.mapNotNull { document ->
                    try {
                        val docOwnerId = document.getString("ownerId") ?: ""
                        if (docOwnerId.equals(ownerId, ignoreCase = true)) {
                            Log.d(TAG, "   ✅ Found match (case-insensitive): Document ID: ${document.id}, ownerId: '$docOwnerId'")
                            documentToVehicle(document)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing vehicle document ${document.id}: ${e.message}", e)
                        null
                    }
                }
                
                if (caseInsensitiveVehicles.isNotEmpty()) {
                    Log.d(TAG, "✅ Found ${caseInsensitiveVehicles.size} vehicles with case-insensitive match")
                    return caseInsensitiveVehicles
                }
            }
            
            val vehicles = querySnapshot.documents.mapNotNull { document ->
                try {
                    // ✅ FIX: Log document data for debugging before parsing
                    Log.d(TAG, "   📄 Parsing document ID: ${document.id}")
                    document.data?.let { data ->
                        Log.d(TAG, "      - Fields: ${data.keys.joinToString(", ")}")
                        // Log seats field specifically to help debug
                        data["seats"]?.let { seatsValue ->
                            Log.d(TAG, "      - seats field type: ${seatsValue.javaClass.simpleName}, value: $seatsValue")
                        }
                    }
                    
                    val vehicle = documentToVehicle(document)
                    Log.d(TAG, "   ✅ Parsed vehicle: ${vehicle.name} (ID: ${vehicle.id}, Owner: ${vehicle.ownerId}, Seats: ${vehicle.seats})")
                    vehicle
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parsing vehicle document ${document.id}: ${e.message}", e)
                    Log.e(TAG, "   Document data: ${document.data?.keys?.joinToString(", ") ?: "null"}")
                    e.printStackTrace()
                    null
                }
            }
            
            Log.d(TAG, "✅ Found ${vehicles.size} vehicles in Firestore for owner: $ownerId")
            vehicles
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting vehicles from Firestore: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Delete vehicle from Firestore
     * 
     * @param vehicleId ID kendaraan
     * @return true jika berhasil, false jika gagal
     */
    suspend fun deleteVehicle(vehicleId: Int): Boolean {
        return try {
            Log.d(TAG, "🗑️ Deleting vehicle from Firestore: ID=$vehicleId")
            
            db.collection(VEHICLES_COLLECTION)
                .document(vehicleId.toString())
                .delete()
                .await()
            
            Log.d(TAG, "✅ Vehicle deleted from Firestore successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete vehicle from Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Download vehicles from Firestore for a specific owner and save to local database
     * Digunakan saat login untuk restore vehicle data dari cloud
     * 
     * @param context Context untuk akses database
     * @param ownerId Email owner
     * @return Number of vehicles downloaded
     */
    suspend fun downloadVehiclesByOwner(context: android.content.Context, ownerId: String): Int {
        return try {
            Log.d(TAG, "=" .repeat(80))
            Log.d(TAG, "📥 DOWNLOADING OWNER VEHICLES FROM FIRESTORE")
            Log.d(TAG, "   Collection: $VEHICLES_COLLECTION")
            Log.d(TAG, "   Owner ID: '$ownerId'")
            Log.d(TAG, "=" .repeat(80))
            
            val localDb = com.example.app_jalanin.data.AppDatabase.getDatabase(context)
            
            // ✅ FIX: Try to get vehicles by ownerId
            Log.d(TAG, "🔍 Querying Firestore: collection('$VEHICLES_COLLECTION').whereEqualTo('ownerId', '$ownerId')")
            var vehiclesFromFirestore = getVehiclesByOwner(ownerId)
            
            Log.d(TAG, "📊 Query result: ${vehiclesFromFirestore.size} vehicles found")
            
            // If no results, try case-insensitive search or get all and filter
            if (vehiclesFromFirestore.isEmpty()) {
                Log.w(TAG, "⚠️ No vehicles found with exact ownerId match, trying alternative methods...")
                
                // Try to get all vehicles and filter manually (fallback)
                try {
                    // Remove limit to get ALL vehicles for debugging
                    val allVehiclesSnapshot = db.collection(VEHICLES_COLLECTION)
                        .get()
                        .await()
                    
                    Log.d(TAG, "📊 Found ${allVehiclesSnapshot.documents.size} total vehicles in Firestore")
                    
                    // Log all vehicles for debugging with ALL fields
                    Log.d(TAG, "📋 All vehicles in Firestore (with all fields):")
                    allVehiclesSnapshot.documents.forEach { doc ->
                        val docOwnerId = doc.getString("ownerId") ?: ""
                        val docName = doc.getString("name") ?: "N/A"
                        val docId = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: 0
                        Log.d(TAG, "   📄 Document ID: ${doc.id}")
                        Log.d(TAG, "      - Vehicle ID: $docId")
                        Log.d(TAG, "      - Owner ID: '$docOwnerId'")
                        Log.d(TAG, "      - Name: '$docName'")
                        doc.data?.let { data ->
                            Log.d(TAG, "      - All fields: ${data.keys.joinToString(", ")}")
                            // Log all field values for debugging
                            data.forEach { (key, value) ->
                                Log.d(TAG, "         $key = $value")
                            }
                        } ?: Log.d(TAG, "      ⚠️ Document data is null")
                    }
                    
                    vehiclesFromFirestore = allVehiclesSnapshot.documents.mapNotNull { document ->
                        try {
                            val docOwnerId = document.getString("ownerId") ?: ""
                            // Case-insensitive comparison
                            if (docOwnerId.equals(ownerId, ignoreCase = true)) {
                                val vehicle = documentToVehicle(document)
                                Log.d(TAG, "   ✅ Found vehicle with matching ownerId: ${vehicle.name} (Owner: $docOwnerId, ID: ${vehicle.id})")
                                vehicle
                            } else {
                                Log.d(TAG, "   ⏭️ Skipping vehicle: Owner '$docOwnerId' != '$ownerId'")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing vehicle document ${document.id}: ${e.message}", e)
                            null
                        }
                    }
                    
                    Log.d(TAG, "📦 Found ${vehiclesFromFirestore.size} vehicles after manual filtering for owner: $ownerId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error getting all vehicles: ${e.message}", e)
                }
            }
            
            if (vehiclesFromFirestore.isEmpty()) {
                Log.d(TAG, "📭 No vehicles found in Firestore for owner: $ownerId")
                
                // ✅ NEW: Get ALL vehicles from Firestore (without owner filter) for debugging
                try {
                    Log.w(TAG, "🔍 DEBUGGING: Getting ALL vehicles from Firestore to check ownerId formats...")
                    val allVehiclesDebug = db.collection(VEHICLES_COLLECTION)
                        .get()
                        .await()
                    
                    Log.d(TAG, "📊 DEBUGGING: Total vehicles in Firestore: ${allVehiclesDebug.documents.size}")
                    if (allVehiclesDebug.documents.isNotEmpty()) {
                        Log.w(TAG, "⚠️ DEBUGGING: Found vehicles in Firestore but none match ownerId='$ownerId'")
                        Log.w(TAG, "⚠️ DEBUGGING: Available ownerIds in Firestore:")
                        allVehiclesDebug.documents.forEach { doc ->
                            val docOwnerId = doc.getString("ownerId") ?: "MISSING"
                            val docName = doc.getString("name") ?: "N/A"
                            val docId = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: 0
                            Log.w(TAG, "   - Vehicle ID: $docId, Name: '$docName', OwnerId: '$docOwnerId'")
                            // Check if ownerId is similar (for debugging)
                            if (docOwnerId.contains("@") && docOwnerId.lowercase().contains(ownerId.split("@")[0].lowercase())) {
                                Log.w(TAG, "      ⚠️ WARNING: This vehicle's ownerId might be related to '$ownerId'")
                            }
                        }
                    } else {
                        Log.w(TAG, "⚠️ DEBUGGING: No vehicles found in Firestore at all!")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error getting all vehicles for debugging: ${e.message}", e)
                }
                
                // ✅ NEW: Check if there are local vehicles that need to be synced to Firestore
                try {
                    val localVehicles = localDb.vehicleDao().getAllVehiclesByOwnerSync(ownerId)
                    Log.d(TAG, "📊 Found ${localVehicles.size} local vehicles for owner: $ownerId")
                    
                    if (localVehicles.isNotEmpty()) {
                        Log.w(TAG, "⚠️ Found ${localVehicles.size} local vehicles but none in Firestore")
                        Log.w(TAG, "🔄 Syncing local vehicles to Firestore...")
                        
                        var syncedCount = 0
                        for (vehicle in localVehicles) {
                            try {
                                // Ensure vehicle has valid ID
                                if (vehicle.id > 0) {
                                    val syncSuccess = syncVehicle(vehicle)
                                    if (syncSuccess) {
                                        syncedCount++
                                        Log.d(TAG, "✅ Synced vehicle to Firestore: ${vehicle.name} (ID: ${vehicle.id})")
                                    } else {
                                        Log.w(TAG, "⚠️ Failed to sync vehicle: ${vehicle.name} (ID: ${vehicle.id})")
                                    }
                                } else {
                                    Log.w(TAG, "⚠️ Skipping vehicle with invalid ID: ${vehicle.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error syncing vehicle ${vehicle.id}: ${e.message}", e)
                            }
                        }
                        
                        Log.d(TAG, "✅ Synced $syncedCount/${localVehicles.size} local vehicles to Firestore")
                        
                        // After syncing, vehicles are already in local DB, so we return the count
                        // No need to download again since we already have them locally
                        return syncedCount
                    } else {
                        Log.d(TAG, "💡 No local vehicles found for owner: $ownerId")
                        Log.w(TAG, "💡 TIP: If you have vehicles in Firestore, make sure the ownerId field matches exactly: '$ownerId'")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error checking/syncing local vehicles: ${e.message}", e)
                }
                
                // If no vehicles found in Firestore and no local vehicles to sync, return 0
                return 0
            }
            
            Log.d(TAG, "📦 Found ${vehiclesFromFirestore.size} vehicles in Firestore")
            
            var downloadedCount = 0
            var skippedCount = 0
            var updatedCount = 0
            
            for (vehicle in vehiclesFromFirestore) {
                try {
                    // ✅ FIX: Skip vehicles with invalid ID with better logging
                    if (vehicle.id <= 0) {
                        Log.w(TAG, "⚠️ Skipping vehicle with invalid ID: ${vehicle.id} (${vehicle.name})")
                        Log.w(TAG, "   💡 Vehicle ID must be > 0 to be stored in local database")
                        Log.w(TAG, "   - Owner: ${vehicle.ownerId}")
                        Log.w(TAG, "   - License Plate: ${vehicle.licensePlate}")
                        continue
                    }
                    
                    // ✅ FIX: Validate ownerId is not blank
                    if (vehicle.ownerId.isBlank()) {
                        Log.e(TAG, "❌ ERROR: Skipping vehicle with blank ownerId: ${vehicle.name} (ID: ${vehicle.id})")
                        continue
                    }
                    
                    Log.d(TAG, "🔄 Processing vehicle: ${vehicle.name} (ID: ${vehicle.id}, Owner: '${vehicle.ownerId}')")
                    
                    // ✅ FIX: Check if vehicle already exists locally by ID with better error handling
                    val existingVehicle = try {
                        localDb.vehicleDao().getVehicleById(vehicle.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ ERROR: Failed to query existing vehicle: ${e.message}", e)
                        null
                    }
                    
                    if (existingVehicle != null) {
                        // Vehicle exists, check if needs update (compare updatedAt)
                        if (vehicle.updatedAt > existingVehicle.updatedAt) {
                            // Firestore version is newer, update local
                            try {
                                localDb.vehicleDao().updateVehicle(vehicle)
                                updatedCount++
                                Log.d(TAG, "   ✅ Updated vehicle: ${vehicle.id} (${vehicle.name})")
                            } catch (e: Exception) {
                                Log.e(TAG, "   ❌ ERROR: Failed to update vehicle: ${e.message}", e)
                                e.printStackTrace()
                            }
                        } else {
                            skippedCount++
                            Log.d(TAG, "   ⏭️ Vehicle ${vehicle.id} already exists locally (up-to-date), skipping")
                        }
                    } else {
                        // ✅ FIX: Vehicle doesn't exist locally, insert it with better error handling
                        try {
                            // OnConflictStrategy.REPLACE will handle ID conflicts
                            // IMPORTANT: Vehicle ID from Firestore will be used (must be > 0)
                            val insertedId = localDb.vehicleDao().insertVehicle(vehicle)
                            downloadedCount++
                            Log.d(TAG, "   ✅ Downloaded vehicle: ${vehicle.name} (Firestore ID: ${vehicle.id}, Local ID: $insertedId)")
                            
                            // ✅ FIX: Verify the vehicle was inserted correctly with retry
                            val verifyVehicle = localDb.vehicleDao().getVehicleById(vehicle.id)
                            if (verifyVehicle != null) {
                                Log.d(TAG, "   ✅ Verified: Vehicle exists in local DB with ID: ${verifyVehicle.id}, ownerId: '${verifyVehicle.ownerId}'")
                            } else {
                                Log.e(TAG, "   ❌ ERROR: Vehicle not found in local DB after insert!")
                                Log.e(TAG, "      - Tried to find ID: ${vehicle.id}")
                                Log.e(TAG, "      - Inserted ID returned: $insertedId")
                                
                                // ✅ FIX: Try to find by ownerId and name as fallback
                                val allVehicles = localDb.vehicleDao().getAllVehiclesByOwnerSync(vehicle.ownerId)
                                val matchingVehicle = allVehicles.find { it.name == vehicle.name && it.licensePlate == vehicle.licensePlate }
                                if (matchingVehicle != null) {
                                    Log.w(TAG, "   ⚠️ Found vehicle with different ID: ${matchingVehicle.id} (expected: ${vehicle.id})")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "   ❌ ERROR: Failed to insert vehicle into local DB: ${e.message}", e)
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ ERROR: Exception processing vehicle ${vehicle.id}: ${e.message}", e)
                    e.printStackTrace()
                }
            }
            
            Log.d(TAG, "🎉 Download complete: $downloadedCount new vehicles, $updatedCount updated, $skippedCount already exist")
            
            // ✅ FIX: After downloading, update vehicle status based on active rentals
            // This ensures vehicle status is correct even if Firestore has stale status
            try {
                Log.d(TAG, "🔄 Updating vehicle status based on active rentals after download...")
                val rentalDao = localDb.rentalDao()
                val allVehicles = localDb.vehicleDao().getAllVehiclesByOwnerSync(ownerId)
                
                // Get all active rentals for this owner
                val activeRentals = rentalDao.getActiveRentalsByOwner(ownerId)
                val allRentals = rentalDao.getRentalsByOwner(ownerId)
                val inUseRentals = allRentals.filter { rental ->
                    rental.status in listOf("ACTIVE", "DRIVER_TRAVELING", "OVERDUE", "DRIVER_TO_PASSENGER", "ARRIVED")
                }
                
                // Create a map of vehicleId -> hasActiveRental
                val vehicleIdToActiveRental = (activeRentals + inUseRentals).associate { rental ->
                    val vehicleIdInt = rental.vehicleId.toIntOrNull() ?: 0
                    vehicleIdInt to true
                }
                
                allVehicles.forEach { vehicle ->
                    val hasActiveRental = vehicleIdToActiveRental[vehicle.id] == true
                    val currentStatus = vehicle.status
                    
                    when {
                        hasActiveRental && currentStatus != VehicleStatus.SEDANG_DISEWA -> {
                            Log.d(TAG, "🔄 Updating vehicle ${vehicle.id} to SEDANG_DISEWA (has active rental)")
                            localDb.vehicleDao().updateVehicleStatus(
                                vehicleId = vehicle.id,
                                status = VehicleStatus.SEDANG_DISEWA,
                                reason = "Kendaraan sedang disewa",
                                updatedAt = System.currentTimeMillis()
                            )
                            
                            // ✅ FIX: Sync updated status to Firestore
                            val updatedVehicle = vehicle.copy(
                                status = VehicleStatus.SEDANG_DISEWA,
                                statusReason = "Kendaraan sedang disewa",
                                updatedAt = System.currentTimeMillis()
                            )
                            try {
                                syncVehicle(updatedVehicle)
                                Log.d(TAG, "✅ Synced vehicle status to Firestore: ${vehicle.id}")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error syncing vehicle status to Firestore: ${e.message}", e)
                            }
                        }
                        !hasActiveRental && currentStatus == VehicleStatus.SEDANG_DISEWA -> {
                            // ✅ FIX: Update to TERSEDIA if no active rental, regardless of statusReason
                            // This fixes the case where Firestore has stale SEDANG_DISEWA status
                            Log.d(TAG, "🔄 Updating vehicle ${vehicle.id} to TERSEDIA (no active rental, was: ${vehicle.status})")
                            localDb.vehicleDao().updateVehicleStatus(
                                vehicleId = vehicle.id,
                                status = VehicleStatus.TERSEDIA,
                                reason = null,
                                updatedAt = System.currentTimeMillis()
                            )
                            
                            // ✅ FIX: Sync updated status to Firestore
                            val updatedVehicle = vehicle.copy(
                                status = VehicleStatus.TERSEDIA,
                                statusReason = null,
                                updatedAt = System.currentTimeMillis()
                            )
                            try {
                                syncVehicle(updatedVehicle)
                                Log.d(TAG, "✅ Synced vehicle status to Firestore: ${vehicle.id} (fixed from SEDANG_DISEWA to TERSEDIA)")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error syncing vehicle status to Firestore: ${e.message}", e)
                            }
                        }
                    }
                }
                
                Log.d(TAG, "✅ Completed vehicle status update after download")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating vehicle status after download: ${e.message}", e)
                // Non-critical: Continue even if status update fails
            }
            
            // ✅ FIX: Return total processed count (new + updated)
            val totalProcessed = downloadedCount + updatedCount
            Log.d(TAG, "✅ Final result: $totalProcessed vehicles processed (new: $downloadedCount, updated: $updatedCount)")
            totalProcessed
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading vehicles: ${e.message}", e)
            e.printStackTrace()
            0
        }
    }

    /**
     * Download ALL available vehicles (status = TERSEDIA) from Firestore for passengers
     * This is used in the rental screen to show all available vehicles
     * 
     * @param context Context untuk akses database
     * @return Number of vehicles downloaded
     */
    suspend fun downloadAllAvailableVehicles(context: android.content.Context): Int {
        return try {
            Log.d(TAG, "=" .repeat(80))
            Log.d(TAG, "📥 DOWNLOADING ALL AVAILABLE VEHICLES FROM FIRESTORE")
            Log.d(TAG, "   Collection: $VEHICLES_COLLECTION")
            Log.d(TAG, "   Status Filter: TERSEDIA")
            Log.d(TAG, "=" .repeat(80))
            
            val localDb = com.example.app_jalanin.data.AppDatabase.getDatabase(context)
            
            // Query all vehicles with status TERSEDIA
            Log.d(TAG, "🔍 Querying Firestore: collection('$VEHICLES_COLLECTION').whereEqualTo('status', 'TERSEDIA')")
            val snapshot = db.collection(VEHICLES_COLLECTION)
                .whereEqualTo("status", "TERSEDIA")
                .get()
                .await()
            
            Log.d(TAG, "📊 Query result: ${snapshot.documents.size} available vehicles found")
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "📭 No available vehicles found in Firestore")
                return 0
            }
            
            var downloadedCount = 0
            var updatedCount = 0
            var skippedCount = 0
            
            for (document in snapshot.documents) {
                try {
                    val vehicle = documentToVehicle(document)
                    
                    // Skip vehicles with invalid ID
                    if (vehicle.id <= 0) {
                        Log.w(TAG, "⚠️ Skipping vehicle with invalid ID: ${vehicle.id} (${vehicle.name})")
                        skippedCount++
                        continue
                    }
                    
                    // Validate ownerId is not blank
                    if (vehicle.ownerId.isBlank()) {
                        Log.e(TAG, "❌ ERROR: Skipping vehicle with blank ownerId: ${vehicle.name} (ID: ${vehicle.id})")
                        skippedCount++
                        continue
                    }
                    
                    Log.d(TAG, "🔄 Processing vehicle: ${vehicle.name} (ID: ${vehicle.id}, Owner: '${vehicle.ownerId}')")
                    
                    // Check if vehicle already exists locally by ID
                    val existingVehicle = try {
                        localDb.vehicleDao().getVehicleById(vehicle.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ ERROR: Failed to query existing vehicle: ${e.message}", e)
                        null
                    }
                    
                    if (existingVehicle != null) {
                        // Update existing vehicle if Firestore version is newer
                        if (vehicle.updatedAt > existingVehicle.updatedAt) {
                            localDb.vehicleDao().updateVehicle(vehicle)
                            updatedCount++
                            Log.d(TAG, "✅ Updated vehicle: ${vehicle.name} (ID: ${vehicle.id})")
                        } else {
                            skippedCount++
                            Log.d(TAG, "⏭️ Vehicle ${vehicle.name} already up-to-date, skipping")
                        }
                    } else {
                        // Insert new vehicle
                        localDb.vehicleDao().insertVehicle(vehicle)
                        downloadedCount++
                        Log.d(TAG, "✅ Inserted new vehicle: ${vehicle.name} (ID: ${vehicle.id})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing vehicle document ${document.id}: ${e.message}", e)
                    skippedCount++
                }
            }
            
            val totalProcessed = downloadedCount + updatedCount
            Log.d(TAG, "🎉 Download complete: $downloadedCount new vehicles, $updatedCount updated, $skippedCount skipped")
            Log.d(TAG, "✅ Total processed: $totalProcessed vehicles")
            totalProcessed
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading available vehicles: ${e.message}", e)
            e.printStackTrace()
            0
        }
    }

    /**
     * Convert Firestore document to Vehicle object
     */
    private fun documentToVehicle(document: com.google.firebase.firestore.DocumentSnapshot): Vehicle {
        // Try to get ID from document field first, if not found, try to parse from document ID
        val vehicleId = document.getLong("id")?.toInt() 
            ?: document.id.toIntOrNull() 
            ?: 0
        
        return Vehicle(
            id = vehicleId,
            ownerId = document.getString("ownerId") ?: "",
            name = document.getString("name") ?: "",
            type = try {
                VehicleType.valueOf(document.getString("type") ?: "MOBIL")
            } catch (e: Exception) {
                VehicleType.MOBIL
            },
            brand = document.getString("brand") ?: "",
            model = document.getString("model") ?: "",
            year = document.getLong("year")?.toInt() ?: 2024,
            licensePlate = document.getString("licensePlate") ?: "",
            transmission = document.getString("transmission") ?: "Manual",
            seats = try {
                // ✅ FIX: Handle both String and Long/Integer types for seats
                when {
                    document.contains("seats") -> {
                        val seatsValue = document.get("seats")
                        when (seatsValue) {
                            is String -> seatsValue.takeIf { it.isNotBlank() }?.toIntOrNull()
                            is Long -> seatsValue.toInt()
                            is Number -> seatsValue.toInt()
                            else -> null
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error parsing seats field: ${e.message}, defaulting to null")
                null
            },
            engineCapacity = try {
                // ✅ FIX: Handle both String and other types for engineCapacity
                when {
                    document.contains("engineCapacity") -> {
                        val engineValue = document.get("engineCapacity")
                        when (engineValue) {
                            is String -> engineValue.takeIf { it.isNotBlank() }
                            else -> engineValue?.toString()?.takeIf { it.isNotBlank() }
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error parsing engineCapacity field: ${e.message}, defaulting to null")
                null
            },
            pricePerHour = document.getDouble("pricePerHour") ?: 0.0,
            pricePerDay = document.getDouble("pricePerDay") ?: 0.0,
            pricePerWeek = document.getDouble("pricePerWeek") ?: 0.0,
            features = document.getString("features") ?: "-",
            status = try {
                VehicleStatus.valueOf(document.getString("status") ?: "TERSEDIA")
            } catch (e: Exception) {
                VehicleStatus.TERSEDIA
            },
            statusReason = document.getString("statusReason")?.takeIf { it.isNotBlank() },
            locationLat = document.getDouble("locationLat") ?: 0.0,
            locationLon = document.getDouble("locationLon") ?: 0.0,
            locationAddress = document.getString("locationAddress") ?: "",
            imageUrl = document.getString("imageUrl")?.takeIf { it.isNotBlank() },
            driverId = document.getString("driverId")?.takeIf { it.isNotBlank() },
            driverAvailability = document.getString("driverAvailability")?.takeIf { it.isNotBlank() },
            driverAssignmentMode = document.getString("driverAssignmentMode")?.takeIf { it.isNotBlank() },
            createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = document.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }
}

