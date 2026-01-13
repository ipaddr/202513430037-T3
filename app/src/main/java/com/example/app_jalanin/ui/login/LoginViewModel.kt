package com.example.app_jalanin.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.auth.AuthRepository
import com.example.app_jalanin.data.auth.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(app)

    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val selectedRole = MutableStateFlow(UserRole.PENUMPANG)

    private val _loginSuccess = MutableStateFlow<Boolean?>(null)
    val loginSuccess: StateFlow<Boolean?> = _loginSuccess

    private val _lastEmail = MutableStateFlow<String?>(null)
    val lastEmail: StateFlow<String?> = _lastEmail

    private val _lastRole = MutableStateFlow<UserRole?>(null)
    val lastRole: StateFlow<UserRole?> = _lastRole

    // ✅ NEW: Error message state for email verification
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _showResendButton = MutableStateFlow(false)
    val showResendButton: StateFlow<Boolean> = _showResendButton

    // ✅ Cooldown tracking to prevent spam
    private var lastResendTime = 0L
    private val RESEND_COOLDOWN_MS = 60_000L // 60 seconds cooldown

    private val _resendCooldownSeconds = MutableStateFlow(0)
    val resendCooldownSeconds: StateFlow<Int> = _resendCooldownSeconds

    init {
        viewModelScope.launch {
            repo.ensureDummyPassenger()
            repo.ensureDummyOwner()
            repo.ensureDummyDriver()
        }
    }

    fun ensureDummyPassenger() {
        viewModelScope.launch {
            repo.ensureDummyPassenger()
        }
    }

    fun ensureDummyOwner() {
        viewModelScope.launch {
            repo.ensureDummyOwner()
        }
    }

    fun ensureDummyDriver() {
        viewModelScope.launch {
            repo.ensureDummyDriver()
        }
    }

    fun login() {
        viewModelScope.launch {
            val emailVal = email.value.trim()
            val passwordVal = password.value
            val roleVal = selectedRole.value

            android.util.Log.d("LoginViewModel", "Login clicked - email: '$emailVal', password length: ${passwordVal.length}, role: ${roleVal.name}")

            // Reset error state
            _errorMessage.value = null
            _showResendButton.value = false

            val success = repo.login(getApplication(), emailVal, passwordVal, roleVal)

            android.util.Log.d("LoginViewModel", "Login result: $success")

            _loginSuccess.value = success
            if (success) {
                _lastEmail.value = emailVal
                _lastRole.value = roleVal
            } else {
                // ✅ Check if this is an email verification issue
                if (!com.example.app_jalanin.auth.AuthUtils.isDummyEmail(emailVal)) {
                    // Try to check if user exists in database
                    try {
                        val userExists = repo.userExists(emailVal)
                        if (userExists) {
                            // User exists but login failed - likely email not verified
                            _errorMessage.value = "Email belum diverifikasi. Silakan cek inbox email Anda dan klik link verifikasi."
                            _showResendButton.value = true
                            android.util.Log.w("LoginViewModel", "⚠️ Login blocked - Email not verified: $emailVal")
                        } else {
                            _errorMessage.value = "Login gagal: silahkan masukkan kombinasi role, email, dan password yang tepat"
                        }
                    } catch (e: Exception) {
                        _errorMessage.value = "Login gagal: silahkan masukkan kombinasi role, email, dan password yang tepat"
                    }
                } else {
                    _errorMessage.value = "Login gagal: silahkan masukkan kombinasi role, email, dan password yang tepat"
                }
            }
        }
    }

    /**
     * ✅ Resend verification email to user with cooldown protection
     */
    fun resendVerificationEmail() {
        viewModelScope.launch {
            val emailVal = email.value.trim()
            val passwordVal = password.value

            // ✅ Check cooldown
            val currentTime = System.currentTimeMillis()
            val timeSinceLastResend = currentTime - lastResendTime

            if (timeSinceLastResend < RESEND_COOLDOWN_MS) {
                val remainingSeconds = ((RESEND_COOLDOWN_MS - timeSinceLastResend) / 1000).toInt()
                _errorMessage.value = "Tunggu $remainingSeconds detik sebelum kirim ulang email verifikasi."
                _resendCooldownSeconds.value = remainingSeconds
                android.util.Log.w("LoginViewModel", "⏱️ Cooldown active: $remainingSeconds seconds remaining")
                return@launch
            }

            android.util.Log.d("LoginViewModel", "🔄 Resending verification email to: $emailVal")

            try {
                // Sign in to Firebase to get current user
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(emailVal, passwordVal)
                    .await()

                // ✅ Update last resend time BEFORE sending
                lastResendTime = System.currentTimeMillis()

                // Send verification email using AuthUtils
                com.example.app_jalanin.auth.AuthUtils.sendEmailVerification { success, message ->
                    if (success) {
                        android.util.Log.d("LoginViewModel", "✅ Verification email resent successfully")
                        _errorMessage.value = "Email verifikasi telah dikirim ulang. Silakan cek inbox Anda (termasuk folder spam)."
                        _showResendButton.value = false // Hide button after sending
                    } else {
                        android.util.Log.e("LoginViewModel", "❌ Failed to resend verification email: $message")

                        // ✅ Better error messages
                        _errorMessage.value = when {
                            message.contains("unusual activity") || message.contains("blocked") -> {
                                "Terlalu banyak permintaan. Coba lagi dalam 10-15 menit atau cek folder spam email Anda."
                            }
                            message.contains("network") -> {
                                "Koneksi internet bermasalah. Periksa koneksi Anda dan coba lagi."
                            }
                            else -> {
                                "Gagal mengirim email verifikasi: $message"
                            }
                        }

                        // Reset cooldown on failure
                        lastResendTime = 0L
                    }

                    // Sign out after sending
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginViewModel", "❌ Exception resending verification: ${e.message}")
                _errorMessage.value = "Gagal mengirim email. Pastikan email dan password Anda benar."
                // Reset cooldown on error
                lastResendTime = 0L
            }
        }
    }

    // Debug function to force recreate dummy user
    fun forceRecreateDummyUser() {
        viewModelScope.launch {
            repo.forceRecreateDummyUser()
        }
    }
}
