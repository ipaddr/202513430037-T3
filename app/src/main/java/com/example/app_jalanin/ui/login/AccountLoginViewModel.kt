package com.example.app_jalanin.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.auth.AuthUtils
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AccountLoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UserRepository

    init {
        val userDao = AppDatabase.getDatabase(application).userDao()
        repository = UserRepository(userDao)
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String, password: String, role: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            // Cek apakah ini dummy email
            val isDummy = AuthUtils.isDummyEmail(email)

            if (isDummy) {
                // DUMMY EMAIL - SKIP FIREBASE AUTH, LANGSUNG LOGIN KE LOCAL DB
                Log.d("Login", "🔓 Email dummy terdeteksi: $email - SKIP Firebase Auth")

                viewModelScope.launch {
                    try {
                        Log.d("Login", "Attempting local DB login for dummy: $email with role: $role")
                        val result = repository.login(getApplication(), email, password, role)

                        _loginState.value = if (result.isSuccess) {
                            val user = result.getOrNull()!!
                            Log.d("Login", "✅ Dummy login SUCCESS: ${user.email}")
                            LoginState.Success(user.email, user.role, user.fullName)
                        } else {
                            val errorMsg = result.exceptionOrNull()?.message ?: "Login gagal"
                            Log.e("Login", "❌ Dummy login FAILED: $errorMsg")
                            LoginState.Error("Login gagal: $errorMsg")
                        }
                    } catch (e: Exception) {
                        Log.e("Login", "❌ Exception during dummy login: ${e.message}", e)
                        _loginState.value = LoginState.Error("Error: ${e.message}")
                    }
                }
            } else {
                // EMAIL VALID - GUNAKAN FIREBASE AUTH + VERIFICATION
                Log.d("Login", "🔐 Email valid terdeteksi: $email - menggunakan Firebase Auth")
                proceedNormalLogin(email, password, role)
            }
        }
    }

    private fun proceedNormalLogin(email: String, password: String, role: String) {
        viewModelScope.launch {
            // 1. Login ke Firebase Auth untuk cek email verification
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val firebaseUser = FirebaseAuth.getInstance().currentUser

                        if (firebaseUser != null) {
                            // Email valid - reload dan cek verifikasi
                            firebaseUser.reload().addOnCompleteListener { reloadTask ->
                                if (reloadTask.isSuccessful) {
                                    // Cek apakah email sudah terverifikasi
                                    val isDummy = com.example.app_jalanin.auth.AuthUtils.isDummyEmail(email)

                                    // ✅ ONLY dummy users can skip verification
                                    // ✅ Real users MUST verify email before login
                                    if (firebaseUser.isEmailVerified || isDummy) {
                                        if (isDummy) {
                                            Log.d("Login", "🔓 Dummy user - skip verification check: $email")
                                        } else {
                                            Log.d("Login", "✅ Email terverifikasi, lanjutkan login ke local DB")
                                        }

                                        // Login ke local database
                                        viewModelScope.launch {
                                            val result = repository.login(getApplication(), email, password, role)

                                            if (result.isSuccess) {
                                                val user = result.getOrNull()!!
                                                _loginState.value = LoginState.Success(user.email, user.role, user.fullName)
                                            } else {
                                                // Login gagal - coba sync dari Firestore jika user ada disana
                                                val errorMsg = result.exceptionOrNull()?.message ?: ""

                                                if (errorMsg.contains("tidak ditemukan", ignoreCase = true) ||
                                                    errorMsg.contains("not found", ignoreCase = true)) {
                                                    Log.d("Login", "🔄 User not found in Local DB, trying to sync from Firestore...")

                                                    try {
                                                        // Try to get user from Firestore
                                                        val firestoreUser = com.example.app_jalanin.data.remote.FirestoreUserService.getUserByEmail(email)

                                                        if (firestoreUser != null) {
                                                            Log.d("Login", "✅ User found in Firestore, syncing to Local DB...")

                                                            // Register user to local DB
                                                            val registerResult = repository.registerUser(
                                                                email = firestoreUser.email,
                                                                username = firestoreUser.username ?: firestoreUser.email.substringBefore("@"),
                                                                password = password, // Use password from login attempt
                                                                role = firestoreUser.role,
                                                                fullName = firestoreUser.fullName,
                                                                phoneNumber = firestoreUser.phoneNumber
                                                            )

                                                            if (registerResult.isSuccess) {
                                                                Log.d("Login", "✅ User synced to Local DB, retrying login...")

                                                                // Retry login
                                                                val retryResult = repository.login(getApplication(), email, password, role)

                                                                if (retryResult.isSuccess) {
                                                                    val user = retryResult.getOrNull()!!
                                                                    _loginState.value = LoginState.Success(user.email, user.role, user.fullName)
                                                                    Log.d("Login", "🎉 Login SUCCESS after sync from Firestore!")
                                                                } else {
                                                                    FirebaseAuth.getInstance().signOut()
                                                                    _loginState.value = LoginState.Error("Login gagal setelah sync: ${retryResult.exceptionOrNull()?.message}")
                                                                }
                                                            } else {
                                                                FirebaseAuth.getInstance().signOut()
                                                                _loginState.value = LoginState.Error("Gagal sync user dari Firestore ke Local DB")
                                                            }
                                                        } else {
                                                            Log.d("Login", "❌ User not found in Firestore either")
                                                            FirebaseAuth.getInstance().signOut()
                                                            _loginState.value = LoginState.Error("User tidak ditemukan di database")
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("Login", "❌ Error syncing from Firestore: ${e.message}")
                                                        FirebaseAuth.getInstance().signOut()
                                                        _loginState.value = LoginState.Error("User tidak ditemukan di database lokal")
                                                    }
                                                } else {
                                                    FirebaseAuth.getInstance().signOut()
                                                    _loginState.value = LoginState.Error(errorMsg)
                                                }
                                            }
                                        }
                                    } else {
                                        Log.d("Login", "Email belum terverifikasi")
                                        FirebaseAuth.getInstance().signOut()
                                        _loginState.value = LoginState.Error(
                                            "Email belum diverifikasi. Silakan cek inbox email Anda dan klik link verifikasi."
                                        )
                                    }
                                } else {
                                    Log.e("Login", "Gagal reload user: ${reloadTask.exception?.message}")
                                    FirebaseAuth.getInstance().signOut()
                                    _loginState.value = LoginState.Error("Gagal memuat data user. Silakan coba lagi.")
                                }
                            }
                        } else {
                            _loginState.value = LoginState.Error("User tidak ditemukan")
                        }
                    } else {
                        val errorMessage = authTask.exception?.message ?: "Login gagal"
                        Log.e("Login", "Firebase Auth login gagal: $errorMessage")
                        _loginState.value = LoginState.Error("Email atau password salah, atau akun belum terdaftar.")
                    }
                }
        }
    }

    /**
     * Kirim ulang email verifikasi untuk user yang belum verify
     */
    fun resendVerificationEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d("Login", "🔄 Attempting to resend verification email for: $email")

                // Login to Firebase to get user
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            val firebaseUser = FirebaseAuth.getInstance().currentUser

                            if (firebaseUser != null && !firebaseUser.isEmailVerified) {
                                // Send verification email using AuthUtils
                                com.example.app_jalanin.auth.AuthUtils.sendEmailVerification { success, message ->
                                    if (success) {
                                        Log.d("Login", "✅ Verification email resent successfully")
                                        _loginState.value = LoginState.Error(
                                            "Email verifikasi telah dikirim ulang. Silakan cek inbox Anda."
                                        )
                                    } else {
                                        Log.e("Login", "❌ Failed to resend verification email: $message")
                                        _loginState.value = LoginState.Error(
                                            "Gagal mengirim email verifikasi: $message"
                                        )
                                    }

                                    // Sign out after sending
                                    FirebaseAuth.getInstance().signOut()
                                }
                            } else {
                                Log.d("Login", "⚠️ Email sudah terverifikasi atau user tidak ditemukan")
                                _loginState.value = LoginState.Error(
                                    "Email sudah terverifikasi. Silakan login kembali."
                                )
                                FirebaseAuth.getInstance().signOut()
                            }
                        } else {
                            Log.e("Login", "❌ Failed to authenticate for resend: ${authTask.exception?.message}")
                            _loginState.value = LoginState.Error(
                                "Gagal mengirim email. Pastikan email dan password Anda benar."
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e("Login", "❌ Exception resending verification: ${e.message}")
                _loginState.value = LoginState.Error("Gagal mengirim email verifikasi")
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}


