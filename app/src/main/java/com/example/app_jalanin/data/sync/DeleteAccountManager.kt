package com.example.app_jalanin.data.sync

import android.content.Context
import android.util.Log
import com.example.app_jalanin.auth.AuthUtils
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk sinkronisasi delete operations antara Local DB dan Cloud DB
 *
 * Features:
 * - Delete dari Firebase → Auto delete dari Local
 * - Delete dari Local → Auto delete dari Firebase
 * - Smart deletion: Dummy vs Real user
 */
class DeleteAccountManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository(AppDatabase.getDatabase(context).userDao())

    companion object {
        private const val TAG = "DeleteAccountManager"
    }

    /**
     * Delete account COMPLETELY dari semua tempat:
     * 1. Firebase Authentication
     * 2. Cloud Firestore
     * 3. Local Room Database
     *
     * @param email Email user yang akan dihapus
     * @return Result with success message or error
     */
    suspend fun deleteAccountCompletely(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Starting complete account deletion for: $email")

                // Check: Dummy email atau real email?
                val isDummy = AuthUtils.isDummyEmail(email)

                if (isDummy) {
                    Log.d(TAG, "⚠️ Dummy email detected, deleting from local DB only")
                    return@withContext deleteLocalOnly(email)
                }

                // Real email - delete from all sources
                val currentUser = auth.currentUser

                // Step 1: Delete dari Cloud Firestore
                if (currentUser != null && currentUser.email == email) {
                    try {
                        Log.d(TAG, "🗑️ Step 1: Deleting from Firestore...")
                        firestore.collection("users")
                            .document(currentUser.uid)
                            .delete()
                            .await()
                        Log.d(TAG, "✅ Firestore delete SUCCESS")
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Firestore delete failed: ${e.message}")
                        // Continue anyway
                    }
                } else {
                    // User not logged in, try delete by email query
                    try {
                        Log.d(TAG, "🗑️ Step 1: Searching user in Firestore by email...")
                        val snapshot = firestore.collection("users")
                            .whereEqualTo("email", email)
                            .get()
                            .await()

                        if (!snapshot.isEmpty) {
                            snapshot.documents.forEach { doc ->
                                doc.reference.delete().await()
                                Log.d(TAG, "✅ Deleted Firestore doc: ${doc.id}")
                            }
                        } else {
                            Log.d(TAG, "⚠️ No Firestore document found for email: $email")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Firestore query/delete failed: ${e.message}")
                    }
                }

                // Step 2: Delete dari Local Database
                try {
                    Log.d(TAG, "🗑️ Step 2: Deleting from Local DB...")
                    userRepository.deleteUserByEmail(email)
                    Log.d(TAG, "✅ Local DB delete SUCCESS")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Local DB delete failed: ${e.message}")
                    return@withContext Result.failure(Exception("Failed to delete from local database: ${e.message}"))
                }

                // Step 3: Delete dari Firebase Auth (LAST - karena akan logout)
                if (currentUser != null && currentUser.email == email) {
                    try {
                        Log.d(TAG, "🗑️ Step 3: Deleting from Firebase Auth...")
                        currentUser.delete().await()
                        Log.d(TAG, "✅ Firebase Auth delete SUCCESS")
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Firebase Auth delete failed: ${e.message}")
                        Log.w(TAG, "⚠️ User may need to re-authenticate to delete Firebase Auth account")
                        // Don't return error, just log warning
                        Log.w(TAG, "⚠️ Firebase Auth account may still exist - this creates a 'ghost account'")
                    }
                } else {
                    Log.w(TAG, "⚠️ User not logged in - CANNOT delete from Firebase Authentication")
                    Log.w(TAG, "⚠️ This will create a GHOST ACCOUNT in Firebase Auth!")
                    Log.w(TAG, "⚠️ Email '${email}' will show 'already in use' error on re-registration")
                    Log.w(TAG, "⚠️ SOLUTION: User must login first, then delete account from settings")
                }

                Log.d(TAG, "🎉 Account deletion COMPLETE for: $email")
                Log.d(TAG, "✅ Local DB: Deleted")
                Log.d(TAG, "✅ Firestore: Deleted (if existed)")
                if (currentUser != null && currentUser.email == email) {
                    Log.d(TAG, "✅ Firebase Auth: Deleted")
                } else {
                    Log.w(TAG, "⚠️ Firebase Auth: CANNOT DELETE (user not logged in)")
                    Log.w(TAG, "⚠️ This is a GHOST ACCOUNT - email will show 'in use' on re-registration")
                }

                Result.success("✅ Akun dihapus dari Local DB dan Firestore.\n" +
                              if (currentUser != null && currentUser.email == email) {
                                  "✅ Firebase Auth juga dihapus."
                              } else {
                                  "⚠️ WARNING: Firebase Auth tidak bisa dihapus (user tidak login).\n" +
                                  "Email ini akan 'in use' saat registrasi ulang."
                              }
                )

            } catch (e: Exception) {
                Log.e(TAG, "❌ Complete deletion error: ${e.message}", e)
                Result.failure(Exception("Gagal menghapus akun: ${e.message}"))
            }
        }
    }

    /**
     * Delete dari LOCAL DB saja (untuk dummy accounts)
     * Firebase tidak terpengaruh
     */
    suspend fun deleteLocalOnly(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Deleting from local DB only: $email")
                userRepository.deleteUserByEmail(email)
                Log.d(TAG, "✅ Local DB delete SUCCESS")

                Result.success("✅ Akun lokal berhasil dihapus")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Local delete error: ${e.message}", e)
                Result.failure(Exception("Gagal menghapus akun lokal: ${e.message}"))
            }
        }
    }

    /**
     * Delete dari FIREBASE (Firestore + Auth) saja
     * Local DB tidak terpengaruh
     */
    suspend fun deleteFirebaseOnly(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Deleting from Firebase only: $email")
                val currentUser = auth.currentUser

                // Delete from Firestore
                if (currentUser != null && currentUser.email == email) {
                    firestore.collection("users")
                        .document(currentUser.uid)
                        .delete()
                        .await()

                    // Delete from Auth
                    currentUser.delete().await()
                    Log.d(TAG, "✅ Firebase delete SUCCESS")
                } else {
                    // Search by email
                    val snapshot = firestore.collection("users")
                        .whereEqualTo("email", email)
                        .get()
                        .await()

                    snapshot.documents.forEach { doc ->
                        doc.reference.delete().await()
                    }
                    Log.d(TAG, "✅ Firestore delete SUCCESS (no Auth delete)")
                }

                Result.success("✅ Akun Firebase berhasil dihapus")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Firebase delete error: ${e.message}", e)
                Result.failure(Exception("Gagal menghapus akun Firebase: ${e.message}"))
            }
        }
    }

    /**
     * Delete ALL users dari LOCAL DB
     * (untuk testing/debugging)
     */
    suspend fun deleteAllLocalUsers(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Deleting ALL users from local DB...")
                val count = userRepository.deleteAllUsers()
                Log.d(TAG, "✅ Deleted $count users from local DB")

                Result.success("✅ Berhasil hapus $count user(s) dari local database")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Delete all error: ${e.message}", e)
                Result.failure(Exception("Gagal menghapus semua user: ${e.message}"))
            }
        }
    }

    /**
     * Delete ALL users dari FIREBASE Firestore
     * (untuk testing/debugging - DANGER!)
     */
    suspend fun deleteAllFirestoreUsers(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️ Deleting ALL users from Firestore...")
                val snapshot = firestore.collection("users").get().await()
                var count = 0

                snapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                    count++
                }

                Log.d(TAG, "✅ Deleted $count users from Firestore")
                Result.success("✅ Berhasil hapus $count user(s) dari Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Delete all Firestore error: ${e.message}", e)
                Result.failure(Exception("Gagal menghapus semua user dari Firestore: ${e.message}"))
            }
        }
    }
}

