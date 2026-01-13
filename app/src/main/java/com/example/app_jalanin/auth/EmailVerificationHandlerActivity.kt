package com.example.app_jalanin.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity untuk menangani deep link verifikasi email dari Firebase
 * Link format: https://jalanin-app.web.app/verify?oobCode=xxx&mode=verifyEmail
 */
class EmailVerificationHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
    }

    private fun handleIntent() {
        // Ambil URI dari intent
        val intentUri: Uri? = intent?.data

        if (intentUri != null) {
            handleEmailVerificationLink(intentUri)
        } else {
            Toast.makeText(this, "Link verifikasi tidak valid", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleEmailVerificationLink(uri: Uri) {
        // Ambil oobCode dari query parameter
        val oobCode = uri.getQueryParameter("oobCode")
        val mode = uri.getQueryParameter("mode")

        if (oobCode.isNullOrEmpty()) {
            Toast.makeText(this, "Kode verifikasi tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verifikasi mode adalah verifyEmail
        if (mode != "verifyEmail") {
            Toast.makeText(this, "Mode verifikasi tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Apply action code untuk verifikasi email
        FirebaseAuth.getInstance().applyActionCode(oobCode)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Reload user untuk update status verifikasi
                    FirebaseAuth.getInstance().currentUser?.reload()?.addOnCompleteListener {
                        Toast.makeText(
                            this,
                            "Email berhasil diverifikasi! Silakan login.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Redirect ke halaman login
                        navigateToLogin()
                    }
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("expired") == true ->
                            "Link verifikasi sudah kadaluarsa. Silakan kirim ulang."
                        task.exception?.message?.contains("invalid") == true ->
                            "Link verifikasi tidak valid."
                        else ->
                            "Verifikasi gagal: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
    }

    private fun navigateToLogin() {
        // Buat intent ke MainActivity (halaman login)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}

