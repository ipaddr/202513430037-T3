package com.example.app_jalanin.data.remote

import android.content.Context
import android.util.Log
import com.example.app_jalanin.auth.AuthUtils
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncAll"
    private var listenerRegistration: ListenerRegistration? = null

    /**
     * Sinkron semua user lokal Room ke Firestore, fire-and-forget style.
     * Skip dummy users (tidak perlu di-sync ke cloud)
     */
    suspend fun syncAllLocalUsers(context: Context) = withContext(Dispatchers.IO) {
        try {
            val dbLocal = AppDatabase.getDatabase(context)
            val users = dbLocal.userDao().getAllUsers()
            if (users.isEmpty()) {
                Log.d(TAG, "Tidak ada user lokal untuk disinkron.")
                return@withContext
            }
            var success = 0
            var skipped = 0
            for (u in users) {
                try {
                    // Skip dummy users - tidak perlu di-sync ke Firestore
                    if (AuthUtils.isDummyEmail(u.email)) {
                        Log.d(TAG, "Skip sync dummy user: ${u.email}")
                        skipped++
                        continue
                    }

                    FirestoreUserService.upsertUser(u)
                    success++
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal sync user ${u.email}: ${e.message}")
                }
            }
            Log.d(TAG, "Sync selesai. Berhasil: $success, Skipped (dummy): $skipped, Total: ${users.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengambil user lokal: ${e.message}")
        }
    }

    /**
     * Listen perubahan real-time dari Firestore dan sync ke local database
     * Menangani: penambahan, update, dan penghapusan data
     */
    fun startRealtimeSync(context: Context) {
        // Stop listener lama jika ada
        stopRealtimeSync()

        val db = FirebaseFirestore.getInstance()
        val localDb = AppDatabase.getDatabase(context)

        Log.d(TAG, "🔄 Starting real-time sync listener from Firestore...")

        listenerRegistration = db.collection("users")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Listen failed: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.w(TAG, "⚠️ Snapshot is null")
                    return@addSnapshotListener
                }

                Log.d(TAG, "📥 Received snapshot with ${snapshots.documents.size} documents from Firestore")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Ambil semua email dari Firestore
                        val firestoreUserEmails = mutableSetOf<String>()

                        // Process setiap document dari Firestore
                        for (doc in snapshots.documents) {
                            try {
                                val email = doc.getString("email")
                                if (email == null || email.isBlank()) {
                                    Log.w(TAG, "⚠️ Document ${doc.id} has no email, skipping")
                                    continue
                                }

                                firestoreUserEmails.add(email)

                                // Skip dummy users - tidak perlu sync ke local
                                if (AuthUtils.isDummyEmail(email)) {
                                    Log.d(TAG, "⏭️ Skipping dummy user from Firestore: $email")
                                    continue
                                }

                                // ✅ CRITICAL FIX: Check if user exists first
                                val existingUser = localDb.userDao().getUserByEmail(email)

                                if (existingUser == null) {
                                    // ❌ SKIP: User baru dari cloud tapi TIDAK ADA password
                                    // Firestore tidak menyimpan password, jadi jangan create user baru
                                    // User harus register via app untuk dapat password
                                    Log.w(TAG, "⚠️ User $email ada di Firestore tapi tidak ada di local")
                                    Log.w(TAG, "⚠️ SKIP sync: User must register via app to set password")
                                    continue
                                } else {
                                    // ✅ Update ONLY non-sensitive fields (jangan touch password!)
                                    val firestoreUsername = doc.getString("username")
                                    val defaultUsername = email.substringBefore("@")
                                    
                                    val updatedUser = existingUser.copy(
                                        role = doc.getString("role") ?: existingUser.role,
                                        username = firestoreUsername ?: existingUser.username ?: defaultUsername,
                                        fullName = doc.getString("fullName") ?: existingUser.fullName,
                                        phoneNumber = doc.getString("phoneNumber") ?: existingUser.phoneNumber,
                                        createdAt = doc.getLong("createdAt") ?: existingUser.createdAt,
                                        synced = true
                                        // ✅ PASSWORD NOT TOUCHED - keep existing password!
                                    )
                                    localDb.userDao().update(updatedUser)
                                    Log.d(TAG, "🔄 Updated user from Firestore: $email (password preserved)")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error processing document ${doc.id}: ${e.message}", e)
                            }
                        }

                        // CRITICAL: Deteksi dan hapus user yang tidak ada di Firestore
                        Log.d(TAG, "🔍 Checking for users to delete from local...")
                        val localUsers = localDb.userDao().getAllUsers()
                        Log.d(TAG, "📊 Local has ${localUsers.size} users, Firestore has ${firestoreUserEmails.size} users")

                        var deletedCount = 0
                        for (localUser in localUsers) {
                            // PROTEKSI: Jangan hapus dummy users dari local database
                            if (AuthUtils.isDummyEmail(localUser.email)) {
                                Log.d(TAG, "🛡️ Protecting dummy user from deletion: ${localUser.email}")
                                continue
                            }

                            // Jika user ada di local tapi TIDAK ada di Firestore → DELETE
                            if (!firestoreUserEmails.contains(localUser.email)) {
                                try {
                                    Log.d(TAG, "🔍 Attempting to delete user: ${localUser.email} (ID: ${localUser.id})")

                                    // Verify user exists before delete
                                    val userBeforeDelete = localDb.userDao().getUserByEmail(localUser.email)
                                    Log.d(TAG, "📝 User exists before delete: ${userBeforeDelete != null}")

                                    if (userBeforeDelete != null) {
                                        // Delete using both methods to ensure deletion
                                        localDb.userDao().deleteByEmail(localUser.email)

                                        // Double verification - try delete by ID too
                                        localDb.userDao().deleteUser(localUser.id)

                                        // Small delay to ensure transaction completes
                                        kotlinx.coroutines.delay(50)

                                        // Verify delete success
                                        val userAfterDelete = localDb.userDao().getUserByEmail(localUser.email)
                                        if (userAfterDelete == null) {
                                            deletedCount++
                                            Log.d(TAG, "🗑️ ✅ DELETED user from local: ${localUser.email} (ID: ${localUser.id}) - NOT FOUND in Firestore")
                                            Log.d(TAG, "🔍 Verification: User no longer exists in database")
                                        } else {
                                            Log.e(TAG, "❌ DELETE FAILED: User ${localUser.email} still exists after delete operation!")
                                            Log.e(TAG, "❌ User ID after delete: ${userAfterDelete.id}, Email: ${userAfterDelete.email}")
                                            // Try one more time with direct delete
                                            try {
                                                localDb.userDao().deleteUser(userAfterDelete.id)
                                                kotlinx.coroutines.delay(50)
                                                val finalCheck = localDb.userDao().getUserByEmail(localUser.email)
                                                if (finalCheck == null) {
                                                    deletedCount++
                                                    Log.d(TAG, "✅ Second attempt successful!")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "❌ Second delete attempt failed: ${e.message}")
                                            }
                                        }
                                    } else {
                                        Log.w(TAG, "⚠️ User ${localUser.email} not found before delete (already deleted?)")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error deleting user ${localUser.email}: ${e.message}", e)
                                    e.printStackTrace()
                                }
                            }
                        }

                        if (deletedCount > 0) {
                            Log.d(TAG, "✅ Deleted $deletedCount user(s) from local database")
                        } else {
                            Log.d(TAG, "✅ No users to delete")
                        }

                        Log.d(TAG, "✅ Real-time sync completed. Firestore: ${firestoreUserEmails.size} users, Local: ${localDb.userDao().getAllUsers().size} users")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error syncing data: ${e.message}", e)
                    }
                }
            }

        Log.d(TAG, "✅ Real-time sync listener registered successfully")
    }

    /**
     * Stop real-time listener
     */
    fun stopRealtimeSync() {
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.d(TAG, "Real-time sync stopped")
    }
}
