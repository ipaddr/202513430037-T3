package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi DriverProfile antara Room database dan Firestore
 * Memastikan data driver (SIM, status online) tetap tersimpan di cloud untuk backup dan cross-device access
 */
object FirestoreDriverProfileSyncManager {
    private const val TAG = "FirestoreDriverProfileSync"
    private const val DRIVER_PROFILES_COLLECTION = "driver_profiles"

    /**
     * Convert DriverProfile entity to Firestore map
     */
    private fun driverProfileToMap(profile: DriverProfile): HashMap<String, Any> {
        return hashMapOf(
            "id" to profile.id,
            "driverEmail" to profile.driverEmail,
            "simCertifications" to (profile.simCertifications ?: ""),
            "isOnline" to profile.isOnline,
            "createdAt" to profile.createdAt,
            "updatedAt" to profile.updatedAt,
            "synced" to true
        )
    }

    /**
     * Convert Firestore document to DriverProfile entity
     */
    private fun mapToDriverProfile(doc: com.google.firebase.firestore.DocumentSnapshot): DriverProfile? {
        return try {
            val id = doc.getLong("id") ?: return null
            val driverEmail = doc.getString("driverEmail") ?: return null
            
            DriverProfile(
                id = id,
                driverEmail = driverEmail,
                simCertifications = doc.getString("simCertifications")?.takeIf { it.isNotEmpty() },
                isOnline = doc.getBoolean("isOnline") ?: false,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                synced = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error converting Firestore document to DriverProfile: ${e.message}", e)
            null
        }
    }

    /**
     * Sync semua unsynced driver profiles ke Firestore
     */
    suspend fun syncUnsyncedProfiles(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val unsyncedProfiles = db.driverProfileDao().getUnsyncedProfiles()

            if (unsyncedProfiles.isEmpty()) {
                Log.d(TAG, "✅ No unsynced driver profiles to upload")
                return@withContext
            }

            Log.d(TAG, "🔄 Syncing ${unsyncedProfiles.size} driver profiles to Firestore...")

            val firestore = FirebaseFirestore.getInstance()
            var successCount = 0
            var failedCount = 0

            for (profile in unsyncedProfiles) {
                try {
                    val profileData = driverProfileToMap(profile)

                    firestore.collection(DRIVER_PROFILES_COLLECTION)
                        .document(profile.driverEmail) // Use email as document ID for easy lookup
                        .set(profileData)
                        .await()

                    // Mark as synced in Room
                    db.driverProfileDao().markSynced(profile.id)
                    successCount++

                    Log.d(TAG, "✅ Synced driver profile: ${profile.driverEmail}")
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "❌ Error syncing driver profile ${profile.driverEmail}: ${e.message}", e)
                }
            }

            Log.d(TAG, "✅ Driver profile sync completed: $successCount success, $failedCount failed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing driver profiles: ${e.message}", e)
        }
    }

    /**
     * Sync single driver profile ke Firestore
     */
    suspend fun syncSingleProfile(context: Context, profileId: Long) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val profile = db.driverProfileDao().getById(profileId)

            if (profile == null) {
                Log.e(TAG, "❌ Driver profile not found: $profileId")
                return@withContext
            }

            val firestore = FirebaseFirestore.getInstance()
            val profileData = driverProfileToMap(profile)

            firestore.collection(DRIVER_PROFILES_COLLECTION)
                .document(profile.driverEmail)
                .set(profileData)
                .await()

            // Mark as synced
            db.driverProfileDao().markSynced(profile.id)

            Log.d(TAG, "✅ Synced single driver profile: ${profile.driverEmail}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing single driver profile: ${e.message}", e)
        }
    }

    /**
     * Download driver profiles dari Firestore untuk email tertentu
     */
    suspend fun downloadDriverProfile(context: Context, driverEmail: String) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            Log.d(TAG, "📥 Downloading driver profile for: $driverEmail from Firestore...")

            val doc = firestore.collection(DRIVER_PROFILES_COLLECTION)
                .document(driverEmail)
                .get()
                .await()

            if (!doc.exists()) {
                Log.d(TAG, "📭 No driver profile found in Firestore for: $driverEmail")
                return@withContext
            }

            val profile = mapToDriverProfile(doc)
            if (profile != null) {
                // Check if profile already exists locally
                val existingProfile = db.driverProfileDao().getByEmail(driverEmail)
                
                if (existingProfile == null) {
                    // Insert new profile
                    db.driverProfileDao().insert(profile)
                    Log.d(TAG, "✅ Downloaded and inserted driver profile: $driverEmail")
                } else {
                    // Update existing profile (keep local ID)
                    val updatedProfile = profile.copy(id = existingProfile.id)
                    db.driverProfileDao().update(updatedProfile)
                    Log.d(TAG, "✅ Downloaded and updated driver profile: $driverEmail")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading driver profile: ${e.message}", e)
        }
    }

    /**
     * Download semua driver profiles dari Firestore
     */
    suspend fun downloadAllDriverProfiles(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()

            Log.d(TAG, "📥 Downloading all driver profiles from Firestore...")

            val snapshot = firestore.collection(DRIVER_PROFILES_COLLECTION)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "📭 No driver profiles found in Firestore")
                return@withContext
            }

            Log.d(TAG, "📦 Found ${snapshot.documents.size} driver profiles in Firestore")

            var downloadedCount = 0
            var updatedCount = 0

            val documents = snapshot.documents
            for (doc in documents) {
                try {
                    val profile = mapToDriverProfile(doc)
                    if (profile != null) {
                        val existingProfile = db.driverProfileDao().getByEmail(profile.driverEmail)
                        
                        if (existingProfile == null) {
                            db.driverProfileDao().insert(profile)
                            downloadedCount++
                            Log.d(TAG, "✅ Downloaded driver profile: ${profile.driverEmail}")
                        } else {
                            val updatedProfile = profile.copy(id = existingProfile.id)
                            db.driverProfileDao().update(updatedProfile)
                            updatedCount++
                            Log.d(TAG, "✅ Updated driver profile: ${profile.driverEmail}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing driver profile document: ${e.message}")
                }
            }

            Log.d(TAG, "✅ Driver profile download complete: $downloadedCount downloaded, $updatedCount updated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading driver profiles: ${e.message}", e)
        }
    }
}

