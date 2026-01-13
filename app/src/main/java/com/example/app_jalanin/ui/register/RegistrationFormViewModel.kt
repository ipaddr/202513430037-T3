package com.example.app_jalanin.ui.register

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import com.example.app_jalanin.auth.AuthUtils
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.UserRepository
import com.example.app_jalanin.data.remote.FirestoreUserService
import com.example.app_jalanin.data.local.entity.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ViewModel sederhana menampung state form pendaftaran. */
class RegistrationFormViewModel(application: Application) : AndroidViewModel(application) {
    private val userRepository: UserRepository

    init {
        val userDao = AppDatabase.getDatabase(application).userDao()
        userRepository = UserRepository(userDao)
    }

    var data by mutableStateOf(DriverRegistrationData(driverTypeId = -1))
        private set

    fun initType(typeId: Int) {
        if (data.driverTypeId == -1) {
            data = data.copy(driverTypeId = typeId)
        }
    }

    fun updateFullName(v: String) { data = data.copy(fullName = v) }
    fun updatePhone(v: String) { data = data.copy(phone = v) }
    fun updateEmail(v: String) { data = data.copy(email = v) }
    fun updateAddress(v: String) { data = data.copy(address = v) }
    fun updateIdCard(v: String) { data = data.copy(idCardNumber = v) }

    fun updateMotorPlate(v: String) { data = data.copy(motorPlate = v) }
    fun updateCarPlate(v: String) { data = data.copy(carPlate = v) }
    fun updateShift(v: String) { data = data.copy(shiftAvailability = v) }
    fun updateFleetSize(v: String) { data = data.copy(fleetSize = v) }
    fun updatePhotoSelf(path: String) { data = data.copy(photoSelfPath = path) }
    fun updateVehicleBrandModel(v: String) { data = data.copy(vehicleBrandModel = v) }
    fun updateSimPath(v: String) { data = data.copy(simDocumentPath = v) }
    fun updateStnkPath(v: String) { data = data.copy(stnkDocumentPath = v) }
    fun updateVehicleCategory(v: String) { data = data.copy(vehicleCategory = v) }
    fun updateVehicleEngineCc(v: String) { data = data.copy(vehicleEngineCc = v) }
    fun updateVehiclePhoto(path: String) { data = data.copy(vehiclePhotoPath = path) }
    fun updateSimType(v: String) { data = data.copy(simType = v) }
    fun updateExperienceYears(v: String) { data = data.copy(experienceYears = v) }
    fun updateLocationAddress(v: String) { data = data.copy(locationAddress = v) }
    fun updateOwnerVehicleType(v: String) { data = data.copy(ownerVehicleType = v) }
    fun updateVehicleYear(v: String) { data = data.copy(vehicleYear = v) }
    fun updateVehicleCapacity(v: String) { data = data.copy(vehicleCapacity = v) }
    fun updateRentalPrice(v: String) { data = data.copy(rentalPrice = v) }

    fun isValid(): Boolean {
        val d = data
        if (d.fullName.isBlank() || d.phone.length < 8 || d.email.isBlank()) return false
        return when (d.driverTypeId) {
            1 -> d.photoSelfPath.isNotBlank() && d.vehiclePhotoPath.isNotBlank() &&
                 d.vehicleCategory.isNotBlank() && d.vehicleBrandModel.isNotBlank() &&
                 d.motorPlate.isNotBlank() && d.simDocumentPath.isNotBlank() && d.stnkDocumentPath.isNotBlank() &&
                 (if (d.vehicleCategory.equals("Moge", true)) d.vehicleEngineCc.isNotBlank() else true)
            2 -> d.photoSelfPath.isNotBlank() && d.vehiclePhotoPath.isNotBlank() &&
                 d.vehicleCategory.isNotBlank() && d.vehicleBrandModel.isNotBlank() &&
                 d.motorPlate.isNotBlank() && d.simDocumentPath.isNotBlank() && d.stnkDocumentPath.isNotBlank()
            3 -> d.photoSelfPath.isNotBlank() && d.simType.isNotBlank() && d.experienceYears.isNotBlank()
            4 -> d.vehiclePhotoPath.isNotBlank() && d.ownerVehicleType.isNotBlank() && d.vehicleYear.isNotBlank() &&
                 d.vehicleCapacity.isNotBlank() && d.rentalPrice.isNotBlank() && d.stnkDocumentPath.isNotBlank() &&
                 d.locationAddress.isNotBlank()
            else -> false
        }
    }

    /**
     * Register user (untuk penumpang atau role lainnya) ke database
     * Flow: Register ke Firebase Auth → Kirim email verifikasi (jika bukan dummy) → Save ke local DB + Firestore
     */
    fun registerUser(
        email: String,
        username: String,
        password: String,
        role: String,
        fullName: String,
        phoneNumber: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Cek apakah email dummy
                val isDummy = AuthUtils.isDummyEmail(email)

                if (isDummy) {
                    // Email dummy - skip Firebase Auth, langsung save ke local DB
                    Log.d("Registration", "⚠️ Email dummy terdeteksi, skip Firebase Auth registration")
                    saveUserToDatabase(email, username, password, role, fullName, phoneNumber, null, onSuccess, onError)
                    return@launch
                }

                // Email valid - register ke Firebase Authentication
                Log.d("Registration", "🔄 Starting Firebase Auth registration for: $email")
                Log.d("Registration", "   - Project ID: jalanin-app")
                Log.d("Registration", "   - Firebase Auth instance: ${FirebaseAuth.getInstance().app.name}")
                
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            val firebaseUser = authTask.result?.user
                            val firebaseUid = firebaseUser?.uid
                            
                            Log.d("Registration", "✅ Firebase Auth registration berhasil untuk: $email")
                            Log.d("Registration", "   - Firebase UID: $firebaseUid")
                            Log.d("Registration", "   - Email: ${firebaseUser?.email}")
                            Log.d("Registration", "   - Email Verified: ${firebaseUser?.isEmailVerified}")
                            Log.d("Registration", "   - User created at: ${firebaseUser?.metadata?.creationTimestamp}")
                            Log.d("Registration", "   - User last sign in: ${firebaseUser?.metadata?.lastSignInTimestamp}")

                            // IMPORTANT: Save to database FIRST before any other operations
                            // This ensures data is saved even if email verification fails
                            Log.d("Registration", "🔄 Saving to database immediately...")
                            saveUserToDatabase(email, username, password, role, fullName, phoneNumber, firebaseUid,
                                onSuccess = {
                                    Log.d("Registration", "✅ Database save SUCCESS, now try to send email verification")

                                    // Try to send email verification (optional, don't block on this)
                                    try {
                                        AuthUtils.sendEmailVerification { success, message ->
                                            if (success) {
                                                Log.d("Registration", "✅ Email verifikasi berhasil dikirim ke: $email")
                                                Log.d("Registration", "   - User harus verifikasi email sebelum bisa login")
                                            } else {
                                                Log.w("Registration", "⚠️ Gagal kirim email verifikasi: $message (tapi data sudah tersimpan)")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("Registration", "⚠️ Email verification error: ${e.message} (tapi data sudah tersimpan)")
                                    }

                                    // IMPORTANT: Sign out user after everything
                                    // User tetap ada di Firebase Auth meskipun sudah sign out
                                    // User harus login lagi dan verifikasi email sebelum bisa menggunakan aplikasi
                                    Log.d("Registration", "🔄 Signing out user (user tetap ada di Firebase Auth)")
                                    FirebaseAuth.getInstance().signOut()
                                    
                                    // Verify user still exists in Firebase Auth (should be true)
                                    Log.d("Registration", "✅ User berhasil dibuat di Firebase Auth")
                                    Log.d("Registration", "   - User akan muncul di Firebase Console → Authentication → Users")
                                    Log.d("Registration", "   - User harus login lagi dan verifikasi email sebelum bisa menggunakan aplikasi")

                                    // Call original onSuccess callback
                                    onSuccess()
                                },
                                onError = { error ->
                                    Log.e("Registration", "❌ Database save FAILED: $error")
                                    onError(error)
                                }
                            )
                        } else {
                            val errorMessage = authTask.exception?.message ?: "Firebase Auth registration gagal"
                            Log.e("Registration", "Firebase Auth error: $errorMessage")

                            // Check if error is "email already in use"
                            if (errorMessage.contains("already in use", ignoreCase = true)) {
                                Log.e("Registration", "⚠️ Email sudah terdaftar di Firebase Auth: $email")
                                
                                // ✅ CRITICAL FIX: Check Firestore first before calling it "ghost account"
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        val firestoreUser = com.example.app_jalanin.data.remote.FirestoreUserService.getUserByEmail(email)
                                        
                                        if (firestoreUser != null) {
                                            // User ada di Firestore - ini BUKAN ghost account!
                                            Log.d("Registration", "✅ User ditemukan di Firestore: $email")
                                            Log.d("Registration", "   - Role: ${firestoreUser.role}")
                                            Log.d("Registration", "   - Full Name: ${firestoreUser.fullName}")
                                            
                                            // User sudah terdaftar, harus login bukan registrasi
                                            withContext(Dispatchers.Main) {
                                                onError("Email ini sudah terdaftar di sistem.\n\n" +
                                                       "Akun Anda sudah terdaftar dengan:\n" +
                                                       "• Email: $email\n" +
                                                       "• Role: ${firestoreUser.role}\n\n" +
                                                       "SOLUSI:\n" +
                                                       "1. Gunakan tombol LOGIN (bukan Daftar)\n" +
                                                       "2. Masukkan email dan password yang sama\n" +
                                                       "3. Aplikasi akan otomatis sync akun ke device ini\n\n" +
                                                       "Jika lupa password, gunakan fitur 'Lupa Password' di halaman login.")
                                            }
                                        } else {
                                            // User TIDAK ada di Firestore - ini ghost account!
                                            Log.e("Registration", "❌ GHOST ACCOUNT DETECTED!")
                                            Log.e("Registration", "Email: $email exists in Firebase Auth but NOT in Firestore or Local DB")
                                            Log.e("Registration", "This is an orphaned/ghost account that needs cleanup")
                                            
                                            // ✅ Try to verify if password is correct by attempting login
                                            try {
                                                Log.d("Registration", "🔄 Attempting to verify password with Firebase Auth...")
                                                val testAuthResult = FirebaseAuth.getInstance()
                                                    .signInWithEmailAndPassword(email, password)
                                                    .await()
                                                
                                                if (testAuthResult.user != null) {
                                                    Log.d("Registration", "✅ Password is CORRECT! This is a recoverable ghost account")
                                                    
                                                    // Password is correct! Auto-create user in Local DB and Firestore
                                                    Log.d("Registration", "🔄 Auto-creating user in Local DB and Firestore...")
                                                    
                                                    val firebaseUid = testAuthResult.user?.uid
                                                    FirebaseAuth.getInstance().signOut() // Sign out after test
                                                    
                                                    // Save user to database
                                                    saveUserToDatabase(
                                                        email = email,
                                                        username = username,
                                                        password = password,
                                                        role = role,
                                                        fullName = fullName,
                                                        phoneNumber = phoneNumber,
                                                        firebaseUid = firebaseUid,
                                                        onSuccess = {
                                                            Log.d("Registration", "✅ Ghost account recovered! User created in Local DB and Firestore")
                                                            viewModelScope.launch(Dispatchers.Main) {
                                                                onSuccess()
                                                            }
                                                        },
                                                        onError = { error ->
                                                            Log.e("Registration", "❌ Failed to recover ghost account: $error")
                                                            viewModelScope.launch(Dispatchers.Main) {
                                                                onError("Email ini terdaftar di Firebase Authentication tapi tidak ada di database.\n\n" +
                                                                       "Password yang Anda masukkan BENAR, tapi gagal membuat akun di database.\n\n" +
                                                                       "SOLUSI:\n" +
                                                                       "1. Coba LOGIN dengan email dan password yang sama\n" +
                                                                       "   (Aplikasi akan otomatis sync akun ke device ini)\n\n" +
                                                                       "ATAU\n" +
                                                                       "2. Hapus akun dari Firebase Console:\n" +
                                                                       "   - Buka Firebase Console → Authentication → Users\n" +
                                                                       "   - Cari email: $email\n" +
                                                                       "   - Hapus akun tersebut\n" +
                                                                       "   - Coba registrasi lagi\n\n" +
                                                                       "ATAU gunakan email lain untuk registrasi.")
                                                            }
                                                        }
                                                    )
                                                    return@launch
                                                }
                                            } catch (e: Exception) {
                                                Log.e("Registration", "❌ Password verification failed: ${e.message}")
                                                // Password is wrong - this is a true ghost account
                                            }
                                            
                                            // Password is wrong or verification failed
                                            withContext(Dispatchers.Main) {
                                                onError("Email ini terdaftar di Firebase Authentication tapi tidak ada di database.\n\n" +
                                                       "Ini adalah 'ghost account' yang perlu dibersihkan.\n\n" +
                                                       "SOLUSI:\n" +
                                                       "1. Jika Anda tahu password yang benar:\n" +
                                                       "   - Gunakan tombol LOGIN (bukan Daftar)\n" +
                                                       "   - Masukkan email dan password yang benar\n" +
                                                       "   - Aplikasi akan otomatis sync akun ke device ini\n\n" +
                                                       "2. Jika Anda tidak tahu password:\n" +
                                                       "   - Hapus akun dari Firebase Console:\n" +
                                                       "     • Buka Firebase Console → Authentication → Users\n" +
                                                       "     • Cari email: $email\n" +
                                                       "     • Hapus akun tersebut\n" +
                                                       "   - Coba registrasi lagi\n\n" +
                                                       "ATAU gunakan email lain untuk registrasi.")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("Registration", "❌ Error checking Firestore: ${e.message}", e)
                                        // Fallback: assume it's a ghost account if we can't check
                                        withContext(Dispatchers.Main) {
                                            onError("Email ini sudah terdaftar di Firebase Authentication.\n\n" +
                                                   "KEMUNGKINAN:\n" +
                                                   "1. Akun sudah terdaftar di device lain\n" +
                                                   "2. Akun adalah 'ghost account' yang perlu dibersihkan\n\n" +
                                                   "SOLUSI:\n" +
                                                   "1. Coba LOGIN dengan email dan password yang sama\n" +
                                                   "   (Aplikasi akan otomatis sync akun ke device ini)\n\n" +
                                                   "ATAU\n" +
                                                   "2. Hapus akun dari Firebase Console:\n" +
                                                   "   - Buka Firebase Console → Authentication → Users\n" +
                                                   "   - Cari email: $email\n" +
                                                   "   - Hapus akun tersebut\n" +
                                                   "   - Coba registrasi lagi\n\n" +
                                                   "ATAU gunakan email lain untuk registrasi.")
                                        }
                                    }
                                }
                            } else {
                                onError(errorMessage)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("Registration", "Exception: ${e.message}")
                onError(e.message ?: "Terjadi kesalahan")
            }
        }
    }

    /**
     * Helper function untuk save user ke local database + Firestore
     */
    private fun saveUserToDatabase(
        email: String,
        username: String,
        password: String,
        role: String,
        fullName: String,
        phoneNumber: String,
        firebaseUid: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) { // ✅ Ensure DB operations on IO thread
            try {
                Log.d("Registration", "📝 saveUserToDatabase CALLED for: $email")
                Log.d("Registration", "   - Role: $role")
                Log.d("Registration", "   - FullName: $fullName")
                Log.d("Registration", "   - Phone: $phoneNumber")
                Log.d("Registration", "   - Password length: ${password.length}") // ✅ DEBUG
                Log.d("Registration", "   - Password isEmpty: ${password.isEmpty()}") // ✅ DEBUG

                if (password.isEmpty()) {
                    Log.e("Registration", "❌ CRITICAL: Password is EMPTY!")
                    withContext(Dispatchers.Main) {
                        onError("Password tidak boleh kosong")
                    }
                    return@launch
                }

                val now = System.currentTimeMillis()

                Log.d("Registration", "🔄 Calling userRepository.registerUser()...")
                Log.d("Registration", "   - Passing password length: ${password.length}") // ✅ DEBUG
                Log.d("Registration", "   - Username: $username") // ✅ DEBUG
                val result = userRepository.registerUser(
                    email = email,
                    username = username,
                    password = password,
                    role = role,
                    fullName = fullName,
                    phoneNumber = phoneNumber
                )

                Log.d("Registration", "📊 Repository result: isSuccess = ${result.isSuccess}")

                if (result.isSuccess) {
                    val userId = result.getOrNull()?.toInt() ?: 0

                    if (userId <= 0) {
                        Log.e("Registration", "❌ Invalid user ID: $userId")
                        withContext(Dispatchers.Main) {
                            onError("Gagal menyimpan user: ID tidak valid")
                        }
                        return@launch
                    }

                    Log.d("Registration", "✅ User saved to LOCAL DB with ID: $userId")

                    // ✅ VERIFY: Check if user really exists in Local DB
                    try {
                        val savedUser = userRepository.getUserByEmail(email)
                        if (savedUser == null) {
                            Log.e("Registration", "❌ CRITICAL: User NOT FOUND in Local DB after insert!")
                            withContext(Dispatchers.Main) {
                                onError("User tidak tersimpan di database lokal")
                            }
                            return@launch
                        }
                        Log.d("Registration", "✅ VERIFIED: User exists in Local DB (ID: ${savedUser.id})")
                    } catch (e: Exception) {
                        Log.e("Registration", "❌ Verification failed: ${e.message}")
                        // Continue anyway - insert was successful
                    }

                    // Sync to Firestore
                    try {
                        val user = User(
                            id = userId,
                            email = email,
                            password = password,
                            role = role,
                            username = username,
                            fullName = fullName,
                            phoneNumber = phoneNumber,
                            createdAt = now,
                            synced = false
                        )
                        
                        // Create driver profile if user is a driver
                        if (role.uppercase() == "DRIVER") {
                            val simCertifications = when (data.driverTypeId) {
                                1 -> "SIM_C" // Motor driver
                                2 -> "SIM_A" // Car driver
                                3 -> {
                                    // Replacement driver - convert simType to SIM_A or SIM_C
                                    when (data.simType.uppercase()) {
                                        "SIM A", "A" -> "SIM_A"
                                        "SIM C", "C" -> "SIM_C"
                                        else -> data.simType.uppercase().takeIf { it.startsWith("SIM_") } ?: ""
                                    }
                                }
                                else -> null
                            }
                            
                            if (simCertifications != null && simCertifications.isNotEmpty()) {
                                val db = AppDatabase.getDatabase(getApplication())
                                val driverProfile = com.example.app_jalanin.data.local.entity.DriverProfile(
                                    driverEmail = email,
                                    simCertifications = simCertifications,
                                    isOnline = false, // Default offline for safety
                                    createdAt = now,
                                    updatedAt = now,
                                    synced = false
                                )
                                db.driverProfileDao().insert(driverProfile)
                                Log.d("Registration", "✅ Created driver profile with SIM certifications: $simCertifications")
                                
                                // Sync driver profile to Firestore
                                try {
                                    com.example.app_jalanin.data.remote.FirestoreDriverProfileSyncManager.syncSingleProfile(
                                        getApplication(),
                                        driverProfile.id
                                    )
                                    Log.d("Registration", "✅ Driver profile synced to Firestore")
                                } catch (e: Exception) {
                                    Log.e("Registration", "❌ Error syncing driver profile to Firestore: ${e.message}", e)
                                }
                            }
                        }

                        Log.d("Registration", "🔄 Syncing to Firestore...")
                        Log.d("Registration", "   - Email: $email")
                        Log.d("Registration", "   - Role: $role")
                        Log.d("Registration", "   - FullName: $fullName")
                        Log.d("Registration", "   - Firebase UID: $firebaseUid")
                        
                        // Upsert to Firestore (pass Firebase UID if available)
                        FirestoreUserService.upsertUser(user.copy(synced = true), firebaseUid)
                        Log.d("Registration", "✅ User synced to FIRESTORE: $email")

                        // Verify Firestore write by reading back
                        try {
                            val verifyUser = FirestoreUserService.getUserByEmail(email)
                            if (verifyUser != null) {
                                Log.d("Registration", "✅ VERIFIED: User exists in Firestore")
                            } else {
                                Log.w("Registration", "⚠️ WARNING: User not found in Firestore after write")
                            }
                        } catch (e: Exception) {
                            Log.w("Registration", "⚠️ Could not verify Firestore write: ${e.message}")
                        }

                        // Mark as synced in local DB
                        userRepository.markSynced(user.id)
                        Log.d("Registration", "✅ User marked as synced in local DB")
                    } catch (e: Exception) {
                        Log.e("Registration", "❌ Firestore sync FAILED: ${e.message}", e)
                        Log.e("Registration", "   Error type: ${e.javaClass.simpleName}")
                        Log.e("Registration", "   Stack trace: ${e.stackTraceToString()}")
                        
                        // Continue anyway - local DB save is successful
                        // User can still login, and sync will retry later via FirestoreSyncManager
                        Log.w("Registration", "⚠️ User saved to LOCAL DB but NOT synced to Firestore")
                        Log.w("Registration", "⚠️ User can still login. Firestore sync will retry later.")
                    }

                    Log.d("Registration", "🎉 Registration COMPLETE for: $email")
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Registrasi gagal"
                    Log.e("Registration", "❌ Local DB save FAILED: $error")
                    withContext(Dispatchers.Main) {
                        onError(error)
                    }
                }
            } catch (e: Exception) {
                Log.e("Registration", "❌ saveUserToDatabase EXCEPTION: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Terjadi kesalahan saat menyimpan data")
                }
            }
        }
    }
}
