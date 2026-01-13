package com.example.app_jalanin.data.sync

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.local.dao.UserDao
import com.example.app_jalanin.data.local.entity.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ONE-WAY SYNC: Firestore → Local Database
 * Firestore is the source of truth, Local DB is cache only
 */
class FirestoreSyncManager(
    private val userDao: UserDao,
    private val context: Context? = null
) {
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "FirestoreSyncManager"
        private const val USERS_COLLECTION = "users"
    }

    /**
     * One-Way Sync: Firestore → Local Database
     * Clears local DB and replaces with fresh data from Firestore
     */
    suspend fun syncUsersFromFirestore(): Result<Int> {
        return try {
            Log.d(TAG, "=" .repeat(60))
            Log.d(TAG, "🔄 ONE-WAY SYNC: Firestore → Local Database")
            Log.d(TAG, "=" .repeat(60))

            // Step 1: Fetch all users from Firestore
            Log.d(TAG, "📡 Fetching users from Firestore...")
            val snapshot = firestore.collection(USERS_COLLECTION)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.w(TAG, "⚠️ No users found in Firestore")
                Log.d(TAG, "=" .repeat(60))
                return Result.success(0)
            }

            Log.d(TAG, "📦 Found ${snapshot.documents.size} users in Firestore")

            // Step 2: Clear local database (Firestore is source of truth)
            Log.d(TAG, "🗑️ Clearing local database...")
            userDao.deleteAllUsers()
            Log.d(TAG, "✅ Local database cleared")

            // Step 3: Convert Firestore documents to User entities
            val users = snapshot.documents.mapNotNull { doc ->
                try {
                    val email = doc.getString("email")
                    val password = doc.getString("password")
                    val role = doc.getString("role")

                    if (email == null || password == null || role == null) {
                        Log.w(TAG, "⚠️ Skipping incomplete user: ${doc.id}")
                        return@mapNotNull null
                    }

                    User(
                        id = 0, // Auto-generate by Room
                        email = email,
                        password = password,
                        role = role,
                        fullName = doc.getString("fullName"),
                        phoneNumber = doc.getString("phoneNumber"),
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parsing user document: ${doc.id}", e)
                    null
                }
            }

            if (users.isEmpty()) {
                Log.w(TAG, "⚠️ No valid users to sync")
                Log.d(TAG, "=" .repeat(60))
                return Result.success(0)
            }

            // Step 4: Batch insert to Local DB
            Log.d(TAG, "💾 Inserting ${users.size} users to Local DB...")
            val insertedIds = userDao.insertUsers(users)

            Log.d(TAG, "✅ Successfully synced ${insertedIds.size} users to Local DB")
            Log.d(TAG, "")
            Log.d(TAG, "📊 SYNC SUMMARY:")
            users.forEachIndexed { index, user ->
                Log.d(TAG, "   ${index + 1}. ${user.email} → Role: ${user.role}")
            }
            Log.d(TAG, "=" .repeat(60))

            Result.success(insertedIds.size)
        } catch (e: Exception) {
            Log.e(TAG, "=" .repeat(60))
            Log.e(TAG, "❌ SYNC FAILED", e)
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "=" .repeat(60))
            Result.failure(e)
        }
    }

    /**
     * Setup real-time listener for Firestore changes
     * Automatically sync when Firestore data changes
     */
    fun setupRealtimeSync(onSyncComplete: (Result<Int>) -> Unit) {
        Log.d(TAG, "🔔 Setting up real-time sync listener...")

        val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        firestore.collection(USERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Realtime sync listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    Log.d(TAG, "🔔 Firestore data changed, triggering auto-sync...")

                    // Trigger sync in coroutine
                    syncScope.launch {
                        val result = syncUsersFromFirestore()
                        onSyncComplete(result)
                    }
                }
            }
    }

    /**
     * Check if sync is needed (manual trigger option)
     */
    suspend fun isSyncNeeded(): Boolean {
        return try {
            val localCount = userDao.getAllUsers().size
            val firestoreSnapshot = firestore.collection(USERS_COLLECTION)
                .get()
                .await()
            val firestoreCount = firestoreSnapshot.documents.size

            localCount != firestoreCount
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sync status", e)
            true // Assume sync needed on error
        }
    }
}

