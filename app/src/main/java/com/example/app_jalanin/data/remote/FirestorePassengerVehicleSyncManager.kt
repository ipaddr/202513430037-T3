package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.model.PassengerVehicle
import com.example.app_jalanin.data.model.VehicleType
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi passenger vehicle data antara Room dan Firestore
 * Memastikan kendaraan pribadi penumpang tersimpan di cloud untuk backup dan cross-device access
 */
object FirestorePassengerVehicleSyncManager {
    private const val TAG = "FirestorePassengerVehicleSync"
    private const val COLLECTION = "passenger_vehicles"
    
    private val db get() = Firebase.firestore
    
    /**
     * Sync single passenger vehicle to Firestore
     */
    suspend fun syncSingleVehicle(context: Context, vehicleId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val localDb = AppDatabase.getDatabase(context)
            val vehicle = localDb.passengerVehicleDao().getVehicleById(vehicleId)
            
            if (vehicle == null) {
                Log.e(TAG, "❌ Vehicle not found: $vehicleId")
                return@withContext false
            }
            
            return@withContext syncVehicle(vehicle, context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing vehicle $vehicleId: ${e.message}", e)
            false
        }
    }
    
    /**
     * Sync passenger vehicle to Firestore (insert atau update)
     */
    suspend fun syncVehicle(vehicle: PassengerVehicle, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Syncing passenger vehicle to Firestore:")
            Log.d(TAG, "   - ID: ${vehicle.id}")
            Log.d(TAG, "   - Passenger ID: '${vehicle.passengerId}'")
            Log.d(TAG, "   - License Plate: ${vehicle.licensePlate}")
            
            if (vehicle.passengerId.isBlank()) {
                Log.e(TAG, "❌ ERROR: passengerId is blank! Cannot sync vehicle.")
                return@withContext false
            }
            
            val vehicleData = hashMapOf(
                "id" to vehicle.id,
                "passengerId" to vehicle.passengerId,
                "type" to vehicle.type.name, // Convert enum to string
                "brand" to vehicle.brand,
                "model" to vehicle.model,
                "year" to vehicle.year,
                "licensePlate" to vehicle.licensePlate,
                "transmission" to (vehicle.transmission ?: ""),
                "seats" to (vehicle.seats ?: 0),
                "engineCapacity" to (vehicle.engineCapacity ?: ""),
                "imageUrl" to (vehicle.imageUrl ?: ""),
                "isActive" to vehicle.isActive,
                "createdAt" to vehicle.createdAt,
                "updatedAt" to vehicle.updatedAt
            )
            
            // Use vehicle ID as document ID for easy lookup
            val documentRef = db.collection(COLLECTION)
                .document(vehicle.id.toString())
            
            documentRef.set(vehicleData).await()
            
            // Mark as synced in local database
            val localDb = AppDatabase.getDatabase(context)
            val updatedVehicle = vehicle.copy(synced = true)
            localDb.passengerVehicleDao().updateVehicle(updatedVehicle)
            
            Log.d(TAG, "✅ Passenger vehicle synced successfully to Firestore")
            Log.d(TAG, "   - Document ID: ${vehicle.id}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sync passenger vehicle to Firestore: ${e.message}", e)
            false
        }
    }
    
    /**
     * Sync all unsynced passenger vehicles to Firestore for a specific passenger
     */
    suspend fun syncUnsyncedVehicles(context: Context, passengerId: String) = withContext(Dispatchers.IO) {
        try {
            val localDb = AppDatabase.getDatabase(context)
            val unsynced = localDb.passengerVehicleDao().getUnsyncedVehicles(passengerId)
            
            if (unsynced.isEmpty()) {
                Log.d(TAG, "✅ No unsynced passenger vehicles to upload for: $passengerId")
                return@withContext
            }
            
            Log.d(TAG, "🔄 Syncing ${unsynced.size} unsynced passenger vehicles to Firestore...")
            
            var successCount = 0
            var failedCount = 0
            
            for (vehicle in unsynced) {
                try {
                    val success = syncVehicle(vehicle, context)
                    if (success) {
                        successCount++
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error syncing vehicle ${vehicle.id}: ${e.message}", e)
                    failedCount++
                }
            }
            
            Log.d(TAG, "✅ Synced $successCount/${unsynced.size} passenger vehicles to Firestore")
            if (failedCount > 0) {
                Log.w(TAG, "⚠️ Failed to sync $failedCount passenger vehicles")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing unsynced passenger vehicles: ${e.message}", e)
        }
    }
    
    /**
     * Download passenger vehicles from Firestore to local database
     */
    suspend fun downloadPassengerVehicles(context: Context, passengerId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=" .repeat(80))
            Log.d(TAG, "📥 DOWNLOADING PASSENGER VEHICLES FROM FIRESTORE")
            Log.d(TAG, "   Collection: $COLLECTION")
            Log.d(TAG, "   Passenger ID: '$passengerId'")
            Log.d(TAG, "=" .repeat(80))
            
            val localDb = AppDatabase.getDatabase(context)
            var newCount = 0
            var updatedCount = 0
            var skippedCount = 0
            
            // ✅ FIX: Try query by passengerId first
            Log.d(TAG, "🔍 Querying Firestore: collection('$COLLECTION').whereEqualTo('passengerId', '$passengerId')")
            var snapshot = db.collection(COLLECTION)
                .whereEqualTo("passengerId", passengerId)
                .get()
                .await()
            
            Log.d(TAG, "📊 Query result: ${snapshot.documents.size} documents found")
            
            // If no results, try case-insensitive search or get all and filter
            if (snapshot.isEmpty) {
                Log.w(TAG, "⚠️ No passenger vehicles found with exact passengerId match, trying alternative methods...")
                
                // Try to get all vehicles and filter manually (fallback)
                try {
                    val allVehiclesSnapshot = db.collection(COLLECTION)
                        .get()
                        .await()
                    
                    Log.d(TAG, "📊 Found ${allVehiclesSnapshot.documents.size} total passenger vehicles in Firestore")
                    
                    // Filter by case-insensitive passengerId
                    val filteredDocuments = allVehiclesSnapshot.documents.filter { doc ->
                        val docPassengerId = doc.getString("passengerId") ?: ""
                        docPassengerId.equals(passengerId, ignoreCase = true)
                    }
                    
                    Log.d(TAG, "📦 Found ${filteredDocuments.size} passenger vehicles after manual filtering for: $passengerId")
                    
                    if (filteredDocuments.isEmpty()) {
                        Log.d(TAG, "📭 No passenger vehicles found in Firestore for: $passengerId")
                        Log.w(TAG, "💡 TIP: If you have vehicles in Firestore, make sure the passengerId field matches exactly: '$passengerId'")
                        
                        // Log all available passengerIds for debugging
                        if (allVehiclesSnapshot.documents.isNotEmpty()) {
                            Log.w(TAG, "⚠️ Available passengerIds in Firestore:")
                            allVehiclesSnapshot.documents.take(10).forEach { doc ->
                                val docPassengerId = doc.getString("passengerId") ?: "MISSING"
                                val docLicensePlate = doc.getString("licensePlate") ?: "N/A"
                                Log.w(TAG, "   - PassengerId: '$docPassengerId', License Plate: '$docLicensePlate'")
                            }
                        }
                        return@withContext
                    }
                    
                    // Process filtered documents
                    for (document in filteredDocuments) {
                        try {
                            val vehicle = documentToPassengerVehicle(document)
                            if (vehicle != null) {
                                val existing = localDb.passengerVehicleDao().getVehicleById(vehicle.id)
                                
                                if (existing == null) {
                                    // Insert new vehicle
                                    localDb.passengerVehicleDao().insertVehicle(vehicle.copy(synced = true))
                                    newCount++
                                    Log.d(TAG, "✅ Inserted new passenger vehicle: ${vehicle.licensePlate} (ID: ${vehicle.id})")
                                } else {
                                    // Update existing vehicle if Firestore version is newer
                                    if (vehicle.updatedAt > existing.updatedAt) {
                                        localDb.passengerVehicleDao().updateVehicle(vehicle.copy(synced = true))
                                        updatedCount++
                                        Log.d(TAG, "✅ Updated passenger vehicle: ${vehicle.licensePlate} (ID: ${vehicle.id})")
                                    } else {
                                        skippedCount++
                                        Log.d(TAG, "⏭️ Passenger vehicle ${vehicle.licensePlate} already up-to-date, skipping")
                                    }
                                }
                            } else {
                                Log.w(TAG, "⚠️ Failed to parse passenger vehicle document: ${document.id}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing passenger vehicle document ${document.id}: ${e.message}", e)
                        }
                    }
                    
                    Log.d(TAG, "🎉 Download complete: $newCount new vehicles, $updatedCount updated, $skippedCount already exist")
                    return@withContext
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error getting all passenger vehicles: ${e.message}", e)
                    return@withContext
                }
            }
            
            Log.d(TAG, "📦 Found ${snapshot.documents.size} passenger vehicles in Firestore")
            
            if (snapshot.documents.isEmpty()) {
                Log.w(TAG, "⚠️ WARNING: No documents found in query result!")
                Log.w(TAG, "   - Collection: $COLLECTION")
                Log.w(TAG, "   - Query: whereEqualTo('passengerId', '$passengerId')")
                Log.w(TAG, "   - This might mean:")
                Log.w(TAG, "     1. No vehicles exist for this passenger in Firestore")
                Log.w(TAG, "     2. The passengerId field doesn't match exactly")
                Log.w(TAG, "     3. The collection name is incorrect")
            }
            
            for (document in snapshot.documents) {
                try {
                    Log.d(TAG, "📄 Processing document: ${document.id}")
                    Log.d(TAG, "   - Document data keys: ${document.data?.keys?.joinToString(", ") ?: "none"}")
                    
                    val vehicle = documentToPassengerVehicle(document)
                    if (vehicle != null) {
                        Log.d(TAG, "   ✅ Parsed vehicle: ID=${vehicle.id}, passengerId='${vehicle.passengerId}', licensePlate='${vehicle.licensePlate}'")
                        
                        try {
                            val existing = localDb.passengerVehicleDao().getVehicleById(vehicle.id)
                            
                            if (existing == null) {
                                // ✅ FIX: Insert with proper error handling
                                try {
                                    val insertedId = localDb.passengerVehicleDao().insertVehicle(vehicle.copy(synced = true))
                                    newCount++
                                    Log.d(TAG, "   ✅ Inserted new passenger vehicle: ${vehicle.licensePlate} (ID: ${vehicle.id}, Inserted ID: $insertedId)")
                                    
                                    // ✅ FIX: Verify insertion with retry
                                    val verifyVehicle = localDb.passengerVehicleDao().getVehicleById(vehicle.id)
                                    if (verifyVehicle != null) {
                                        Log.d(TAG, "   ✅ Verified: Vehicle exists in local DB with ID: ${verifyVehicle.id}, passengerId: '${verifyVehicle.passengerId}'")
                                    } else {
                                        Log.e(TAG, "   ❌ ERROR: Vehicle not found in local DB after insert!")
                                        Log.e(TAG, "      - Tried to find ID: ${vehicle.id}")
                                        Log.e(TAG, "      - Inserted ID returned: $insertedId")
                                        
                                        // ✅ FIX: Try to find by passengerId and licensePlate as fallback
                                        val allVehicles = localDb.passengerVehicleDao().getAllVehiclesByPassengerSync(vehicle.passengerId)
                                        val matchingVehicle = allVehicles.find { it.licensePlate == vehicle.licensePlate }
                                        if (matchingVehicle != null) {
                                            Log.w(TAG, "   ⚠️ Found vehicle with different ID: ${matchingVehicle.id} (expected: ${vehicle.id})")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "   ❌ ERROR: Failed to insert vehicle into local DB: ${e.message}", e)
                                    e.printStackTrace()
                                }
                            } else {
                                // Update existing vehicle if Firestore version is newer
                                if (vehicle.updatedAt > existing.updatedAt) {
                                    try {
                                        localDb.passengerVehicleDao().updateVehicle(vehicle.copy(synced = true))
                                        updatedCount++
                                        Log.d(TAG, "   ✅ Updated passenger vehicle: ${vehicle.licensePlate} (ID: ${vehicle.id})")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "   ❌ ERROR: Failed to update vehicle: ${e.message}", e)
                                        e.printStackTrace()
                                    }
                                } else {
                                    skippedCount++
                                    Log.d(TAG, "   ⏭️ Passenger vehicle ${vehicle.licensePlate} already up-to-date, skipping")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "   ❌ ERROR: Database operation failed: ${e.message}", e)
                            e.printStackTrace()
                        }
                    } else {
                        Log.w(TAG, "   ⚠️ Failed to parse passenger vehicle document: ${document.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ ERROR: Exception processing document ${document.id}: ${e.message}", e)
                    e.printStackTrace()
                }
            }
            
            Log.d(TAG, "🎉 Download complete: $newCount new vehicles, $updatedCount updated, $skippedCount already exist")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading passenger vehicles: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Convert Firestore document to PassengerVehicle
     */
    private fun documentToPassengerVehicle(document: com.google.firebase.firestore.DocumentSnapshot): PassengerVehicle? {
        return try {
            // ✅ FIX: Better ID parsing with logging
            val id = document.getLong("id")?.toInt() 
                ?: document.id.toIntOrNull() 
                ?: run {
                    Log.e(TAG, "❌ ERROR: Cannot parse vehicle ID from document ${document.id}")
                    Log.e(TAG, "   - Document ID: ${document.id}")
                    Log.e(TAG, "   - 'id' field: ${document.get("id")}")
                    return null
                }
            
            // ✅ FIX: Validate ID is positive
            if (id <= 0) {
                Log.e(TAG, "❌ ERROR: Invalid vehicle ID: $id (must be > 0)")
                Log.e(TAG, "   - Document ID: ${document.id}")
                return null
            }
            
            // ✅ FIX: Better passengerId parsing with logging
            val passengerId = document.getString("passengerId") 
                ?: run {
                    Log.e(TAG, "❌ ERROR: Missing passengerId field in document ${document.id}")
                    Log.e(TAG, "   - Available fields: ${document.data?.keys?.joinToString(", ") ?: "none"}")
                    return null
                }
            
            if (passengerId.isBlank()) {
                Log.e(TAG, "❌ ERROR: passengerId is blank in document ${document.id}")
                return null
            }
            
            // ✅ FIX: Better type parsing
            val typeStr = document.getString("type") 
                ?: run {
                    Log.e(TAG, "❌ ERROR: Missing type field in document ${document.id}")
                    return null
                }
            
            val type = try {
                VehicleType.valueOf(typeStr)
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERROR: Invalid vehicle type: '$typeStr' in document ${document.id}")
                Log.e(TAG, "   - Valid types: ${VehicleType.values().joinToString { it.name }}")
                return null
            }
            
            val brand = document.getString("brand") ?: ""
            val model = document.getString("model") ?: ""
            val year = document.getLong("year")?.toInt() ?: 0
            val licensePlate = document.getString("licensePlate") ?: ""
            val transmission = document.getString("transmission")?.takeIf { it.isNotEmpty() }
            val seats = document.getLong("seats")?.toInt()
            val engineCapacity = document.getString("engineCapacity")?.takeIf { it.isNotEmpty() }
            val imageUrl = document.getString("imageUrl")?.takeIf { it.isNotEmpty() }
            val isActive = document.getBoolean("isActive") ?: true
            val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
            val updatedAt = document.getLong("updatedAt") ?: System.currentTimeMillis()
            
            // ✅ FIX: Validate required fields
            if (licensePlate.isBlank()) {
                Log.w(TAG, "⚠️ WARNING: licensePlate is blank for vehicle ID $id, but continuing...")
            }
            
            val vehicle = PassengerVehicle(
                id = id,
                passengerId = passengerId,
                type = type,
                brand = brand,
                model = model,
                year = year,
                licensePlate = licensePlate,
                transmission = transmission,
                seats = seats,
                engineCapacity = engineCapacity,
                imageUrl = imageUrl,
                isActive = isActive,
                createdAt = createdAt,
                updatedAt = updatedAt,
                synced = true
            )
            
            Log.d(TAG, "✅ Successfully parsed passenger vehicle: ID=$id, passengerId='$passengerId', licensePlate='$licensePlate'")
            vehicle
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: Exception parsing passenger vehicle document ${document.id}: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
}

