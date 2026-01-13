package com.example.app_jalanin.auth

import android.util.Log
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

object AuthUtils {
    private const val TAG = "AuthUtils"

    // List email dummy yang tidak perlu verifikasi (untuk testing/development)
    private val DUMMY_EMAILS = listOf(
        "user123@jalanin.com",
        "owner123@jalanin.com",
        "driver123@jalanin.com",
        "admin@jalanin.com",
        "test@jalanin.com",
        "driver@jalanin.com",
        "owner@jalanin.com"
    )

    /**
     * Cek apakah email adalah dummy email (tidak perlu verifikasi)
     */
    fun isDummyEmail(email: String): Boolean {
        return DUMMY_EMAILS.any { it.equals(email, ignoreCase = true) }
    }

    /**
     * Mengirim email verifikasi ke user yang baru registrasi
     * @param onResult callback (success: Boolean, message: String)
     */
    fun sendEmailVerification(onResult: (Boolean, String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            onResult(false, "User tidak ditemukan. Silakan login kembali.")
            return
        }

        if (user.isEmailVerified) {
            onResult(true, "Email sudah terverifikasi")
            return
        }

        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl("https://jalanin-app.web.app/verify") // host harus ada di Firebase Authorized domains
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                "com.example.app_jalanin",
                true, // install if not available
                null  // minimum version
            )
            .build()

        user.sendEmailVerification(actionCodeSettings)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Email verifikasi berhasil dikirim ke ${user.email}")
                    onResult(true, "Email verifikasi telah dikirim. Silakan cek inbox Anda.")
                } else {
                    val errorMessage = task.exception?.message ?: "Gagal mengirim email verifikasi"
                    Log.e(TAG, "Gagal mengirim email verifikasi: $errorMessage")
                    onResult(false, errorMessage)
                }
            }
    }

    /**
     * Kirim ulang email verifikasi
     */
    fun resendVerificationEmail(onResult: (Boolean, String) -> Unit) {
        sendEmailVerification(onResult)
    }

    /**
     * Cek apakah email user sudah terverifikasi
     */
    fun isEmailVerified(): Boolean {
        return FirebaseAuth.getInstance().currentUser?.isEmailVerified ?: false
    }

    /**
     * Reload user data dari Firebase untuk update status verifikasi
     */
    fun reloadUser(onComplete: (FirebaseUser?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        user?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onComplete(FirebaseAuth.getInstance().currentUser)
            } else {
                onComplete(null)
            }
        } ?: onComplete(null)
    }
}

