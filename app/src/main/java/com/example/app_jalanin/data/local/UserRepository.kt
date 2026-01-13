package com.example.app_jalanin.data.local

import com.example.app_jalanin.data.local.dao.UserDao
import com.example.app_jalanin.data.local.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserRepository(private val userDao: UserDao) {

    /**
     * Register user baru
     */
    suspend fun registerUser(
        email: String,
        username: String,
        password: String,
        role: String,
        fullName: String? = null,
        phoneNumber: String? = null
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("UserRepository", "=" .repeat(60))
                android.util.Log.d("UserRepository", "📝 REGISTER USER CALLED")
                android.util.Log.d("UserRepository", "  Email: $email")
                android.util.Log.d("UserRepository", "  Username: $username")
                android.util.Log.d("UserRepository", "  Password length: ${password.length}") // ✅ DEBUG
                android.util.Log.d("UserRepository", "  Password isEmpty: ${password.isEmpty()}") // ✅ DEBUG
                android.util.Log.d("UserRepository", "  Role: $role")
                android.util.Log.d("UserRepository", "  FullName: $fullName")
                android.util.Log.d("UserRepository", "  Phone: $phoneNumber")
                android.util.Log.d("UserRepository", "=" .repeat(60))

                if (password.isEmpty()) {
                    android.util.Log.e("UserRepository", "❌ CRITICAL: Password is EMPTY at repository level!")
                    return@withContext Result.failure(Exception("Password tidak boleh kosong"))
                }

                if (username.isBlank()) {
                    android.util.Log.e("UserRepository", "❌ CRITICAL: Username is EMPTY!")
                    return@withContext Result.failure(Exception("Username tidak boleh kosong"))
                }

                // Cek apakah email sudah ada
                android.util.Log.d("UserRepository", "🔍 Checking if email exists in DB...")
                val existingEmail = userDao.getUserByEmail(email)
                if (existingEmail != null) {
                    android.util.Log.e("UserRepository", "❌ Email sudah terdaftar: $email")
                    return@withContext Result.failure(Exception("Email sudah terdaftar"))
                }
                android.util.Log.d("UserRepository", "✅ Email tidak ada, proceed to insert")

                // Cek apakah username sudah ada
                android.util.Log.d("UserRepository", "🔍 Checking if username exists in DB...")
                val existingUsername = userDao.getUserByUsername(username)
                if (existingUsername != null) {
                    android.util.Log.e("UserRepository", "❌ Username sudah terdaftar: $username")
                    return@withContext Result.failure(Exception("Username sudah digunakan"))
                }
                android.util.Log.d("UserRepository", "✅ Username tidak ada, proceed to insert")

                val user = User(
                    email = email,
                    password = password,  // TODO: Hash password untuk production
                    role = role,
                    username = username,
                    fullName = fullName,
                    phoneNumber = phoneNumber
                )

                android.util.Log.d("UserRepository", "💾 Inserting user to database...")
                val userId = userDao.insertUser(user)
                android.util.Log.d("UserRepository", "✅ User inserted with ID: $userId")
                android.util.Log.d("UserRepository", "=" .repeat(60))

                Result.success(userId)
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "❌ REGISTER FAILED: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Login user dengan validasi role
     */
    suspend fun login(context: android.content.Context, email: String, password: String, role: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("UserRepository", "=" .repeat(60))
                android.util.Log.d("UserRepository", "🔍 LOGIN ATTEMPT")
                android.util.Log.d("UserRepository", "  Input email: '$email'")
                android.util.Log.d("UserRepository", "  Input password: '$password'")
                android.util.Log.d("UserRepository", "  Input role: '$role'")
                android.util.Log.d("UserRepository", "=" .repeat(60))

                // Cek apakah user ada by email dulu
                val userByEmail = userDao.getUserByEmail(email)

                if (userByEmail == null) {
                    android.util.Log.e("UserRepository", "❌ User TIDAK DITEMUKAN dengan email: $email")
                    android.util.Log.d("UserRepository", "💡 Tip: Cek apakah seeding berhasil di MainActivity")
                    return@withContext Result.failure(Exception("User tidak ditemukan"))
                }

                android.util.Log.d("UserRepository", "✅ User DITEMUKAN dengan email: $email")
                android.util.Log.d("UserRepository", "  DB email: '${userByEmail.email}'")
                android.util.Log.d("UserRepository", "  DB password: '${userByEmail.password}'")
                android.util.Log.d("UserRepository", "  DB role: '${userByEmail.role}'")

                // Cek password match
                if (userByEmail.password != password) {
                    android.util.Log.e("UserRepository", "❌ PASSWORD TIDAK MATCH")
                    android.util.Log.d("UserRepository", "  Expected: '${userByEmail.password}'")
                    android.util.Log.d("UserRepository", "  Got: '$password'")
                    return@withContext Result.failure(Exception("Password salah"))
                }
                android.util.Log.d("UserRepository", "✅ Password MATCH")

                // Cek role match (case insensitive)
                if (!userByEmail.role.equals(role, ignoreCase = true)) {
                    android.util.Log.e("UserRepository", "❌ ROLE TIDAK MATCH")
                    android.util.Log.d("UserRepository", "  Expected (DB): '${userByEmail.role}'")
                    android.util.Log.d("UserRepository", "  Got (Input): '$role'")
                    return@withContext Result.failure(Exception("Role salah. Anda login sebagai '$role' tapi user terdaftar sebagai '${userByEmail.role}'"))
                }
                android.util.Log.d("UserRepository", "✅ Role MATCH")

                // Semua match - login berhasil
                android.util.Log.d("UserRepository", "🎉 LOGIN BERHASIL untuk ${userByEmail.email}")

                // ✅ FIX: Download rental history from Firestore after successful login
                // This ensures rental history persists across app restarts and device changes
                // Use GlobalScope to prevent cancellation when login completes
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        android.util.Log.d("UserRepository", "📥 Downloading rental history from Firestore...")
                        // Download passenger rentals (rentals where user is the passenger)
                        com.example.app_jalanin.data.remote.FirestoreRentalSyncManager.downloadUserRentals(
                            context,
                            userByEmail.id,
                            userByEmail.email
                        )
                        
                        // ✅ FIX: If user is owner, also download owner rentals (rentals of owner's vehicles)
                        if (userByEmail.role.uppercase() == "PEMILIK_KENDARAAN" || userByEmail.role.uppercase() == "PEMILIK KENDARAAN") {
                            android.util.Log.d("UserRepository", "📥 Downloading owner rental history from Firestore...")
                            com.example.app_jalanin.data.remote.FirestoreRentalSyncManager.downloadOwnerRentals(
                                context,
                                userByEmail.email
                            )
                        }
                        
                        // ✅ FIX: If user is passenger, download driver rentals
                        if (userByEmail.role.uppercase() == "PENUMPANG") {
                            android.util.Log.d("UserRepository", "📥 Downloading driver rentals from Firestore...")
                            com.example.app_jalanin.data.remote.FirestoreDriverRentalSyncManager.downloadPassengerRentals(
                                context,
                                userByEmail.email
                            )
                        }
                        
                        // ✅ FIX: If user is driver, download driver rentals (orders assigned to driver)
                        if (userByEmail.role.uppercase() == "DRIVER") {
                            android.util.Log.d("UserRepository", "📥 Downloading driver rentals from Firestore...")
                            com.example.app_jalanin.data.remote.FirestoreDriverRentalSyncManager.downloadDriverRentals(
                                context,
                                userByEmail.email
                            )
                        }
                        
                        android.util.Log.d("UserRepository", "✅ Rental history download completed")
                    } catch (e: Exception) {
                        // Non-critical: User can still login even if Firestore sync fails
                        android.util.Log.w("UserRepository", "⚠️ Failed to download rentals from Firestore: ${e.message}", e)
                    }
                }

                // ✅ NEW: Download vehicles from Firestore for owner after successful login
                // This ensures vehicle data persists across app restarts and device changes
                // Use GlobalScope to prevent cancellation when login completes
                if (userByEmail.role.uppercase() == "PEMILIK_KENDARAAN" || userByEmail.role.uppercase() == "PEMILIK KENDARAAN") {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            android.util.Log.d("UserRepository", "📥 Downloading vehicles from Firestore for owner: ${userByEmail.email}...")
                            val downloadedCount = com.example.app_jalanin.data.remote.FirestoreVehicleService.downloadVehiclesByOwner(
                                context,
                                userByEmail.email
                            )
                            android.util.Log.d("UserRepository", "✅ Downloaded $downloadedCount vehicles from Firestore")
                        } catch (e: Exception) {
                            // Non-critical: User can still login even if Firestore sync fails
                            android.util.Log.w("UserRepository", "⚠️ Failed to download vehicles from Firestore: ${e.message}", e)
                        }
                    }
                }

                // ✅ NEW: Download driver profile from Firestore if user is a driver
                if (userByEmail.role.uppercase() == "DRIVER") {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            android.util.Log.d("UserRepository", "📥 Downloading driver profile from Firestore for: ${userByEmail.email}...")
                            com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.downloadDriverProfile(
                                context,
                                userByEmail.email
                            )
                            android.util.Log.d("UserRepository", "✅ Driver profile download completed")
                        } catch (e: Exception) {
                            // Non-critical: User can still login even if Firestore sync fails
                            android.util.Log.w("UserRepository", "⚠️ Failed to download driver profile from Firestore: ${e.message}", e)
                        }
                    }
                }
                
                // ✅ NEW: Sync unsynced rentals to Firestore after successful login
                // This ensures all local rental data is synced to Firestore
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        android.util.Log.d("UserRepository", "🔄 Syncing unsynced rentals to Firestore...")
                        com.example.app_jalanin.data.remote.FirestoreRentalSyncManager.syncUnsyncedRentals(context)
                        android.util.Log.d("UserRepository", "✅ Rental sync to Firestore completed")
                    } catch (e: Exception) {
                        // Non-critical: User can still login even if Firestore sync fails
                        android.util.Log.w("UserRepository", "⚠️ Failed to sync rentals to Firestore: ${e.message}", e)
                    }
                }

                android.util.Log.d("UserRepository", "=" .repeat(60))
                Result.success(userByEmail)

            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "❌ Login exception", e)
                Result.failure(e)
            }
        }
    }


    /**
     * Get user by email
     */
    suspend fun getUserByEmail(email: String): User? {
        return withContext(Dispatchers.IO) {
            userDao.getUserByEmail(email)
        }
    }

    /**
     * Get all users
     */
    suspend fun getAllUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            userDao.getAllUsers()
        }
    }

    /**
     * Get users by role
     */
    suspend fun getUsersByRole(role: String): List<User> {
        return withContext(Dispatchers.IO) {
            userDao.getUsersByRole(role)
        }
    }

    /**
     * Delete user
     */
    suspend fun deleteUser(userId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                userDao.deleteUser(userId)
                android.util.Log.d("UserRepository", "✅ User deleted successfully: ID $userId")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "❌ Error deleting user", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Delete user by email
     */
    suspend fun deleteUserByEmail(email: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                // Check if user exists
                val userBefore = userDao.getUserByEmail(email)
                if (userBefore == null) {
                    android.util.Log.w("UserRepository", "⚠️ User not found: $email")
                    return@withContext 0
                }

                android.util.Log.d("UserRepository", "🗑️ Deleting user: $email (ID: ${userBefore.id})")

                // Delete by email
                userDao.deleteByEmail(email)

                // Also try delete by ID to ensure deletion
                userDao.deleteUser(userBefore.id)

                // Small delay to ensure transaction commits
                kotlinx.coroutines.delay(50)

                // Verify deletion
                val userAfter = userDao.getUserByEmail(email)
                if (userAfter == null) {
                    android.util.Log.d("UserRepository", "✅ User deleted successfully: $email")
                    1
                } else {
                    android.util.Log.e("UserRepository", "❌ Delete verification FAILED: User still exists!")
                    // Try one more time
                    try {
                        userDao.deleteUser(userAfter.id)
                        kotlinx.coroutines.delay(50)
                        val finalCheck = userDao.getUserByEmail(email)
                        if (finalCheck == null) {
                            android.util.Log.d("UserRepository", "✅ User deleted on second attempt")
                            1
                        } else {
                            android.util.Log.e("UserRepository", "❌ Delete FAILED after 2 attempts")
                            0
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UserRepository", "❌ Second delete attempt error: ${e.message}")
                        0
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "❌ Error deleting user by email: ${e.message}", e)
                0
            }
        }
    }

    /**
     * Delete all users (for testing/debugging)
     */
    suspend fun deleteAllUsers(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val count = userDao.getAllUsers().size
                userDao.deleteAll()
                android.util.Log.d("UserRepository", "✅ Deleted $count users from database")
                count
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "❌ Error deleting all users", e)
                0
            }
        }
    }

    /**
     * Update password
     */
    suspend fun updatePassword(email: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                userDao.updatePassword(email, newPassword)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get unsynced users
     */
    suspend fun getUnsyncedUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            userDao.getUnsyncedUsers()
        }
    }

    /**
     * Tandai user sebagai sudah disinkronisasi
     */
    suspend fun markSynced(userId: Int) {
        return withContext(Dispatchers.IO) {
            userDao.markUserSynced(userId)
        }
    }
}
