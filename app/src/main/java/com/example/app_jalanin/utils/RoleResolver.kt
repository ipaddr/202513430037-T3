package com.example.app_jalanin.utils

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper untuk resolve user role dari userId atau email
 * UI harus menggunakan ini untuk mendapatkan role, bukan menggunakan email langsung
 */
object RoleResolver {
    private const val TAG = "RoleResolver"

    /**
     * Resolve role from userId
     * Returns role or "Unknown" if not found
     */
    suspend fun resolveRoleFromUserId(context: Context, userId: Int): String = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val user = db.userDao().getUserById(userId)
            
            if (user != null) {
                user.role
            } else {
                Log.w(TAG, "User not found for userId: $userId")
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving role from userId $userId: ${e.message}", e)
            "Unknown"
        }
    }

    /**
     * Resolve role from email
     * Returns role or "Unknown" if not found
     */
    suspend fun resolveRoleFromEmail(context: Context, email: String?): String = withContext(Dispatchers.IO) {
        if (email.isNullOrBlank()) {
            return@withContext "Unknown"
        }
        
        try {
            val db = AppDatabase.getDatabase(context)
            val user = db.userDao().getUserByEmail(email)
            
            if (user != null) {
                user.role
            } else {
                Log.w(TAG, "User not found for email: $email")
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving role from email $email: ${e.message}", e)
            "Unknown"
        }
    }

    /**
     * Resolve role from userId or email (prefer userId)
     */
    suspend fun resolveRole(context: Context, userId: Int?, email: String?): String = withContext(Dispatchers.IO) {
        if (userId != null && userId > 0) {
            resolveRoleFromUserId(context, userId)
        } else if (!email.isNullOrBlank()) {
            resolveRoleFromEmail(context, email)
        } else {
            "Unknown"
        }
    }

    /**
     * Get display name for role (localized)
     */
    fun getRoleDisplayName(role: String): String {
        return when (role.uppercase()) {
            "PENUMPANG" -> "Penumpang"
            "DRIVER", "DRIVER_MOTOR", "DRIVER_MOBIL", "DRIVER_PENGGANTI" -> "Driver"
            "PEMILIK_KENDARAAN", "PEMILIK KENDARAAN" -> "Pemilik Kendaraan"
            "ADMIN" -> "Admin"
            else -> role
        }
    }

    /**
     * Check if role is Driver
     */
    fun isDriver(role: String): Boolean {
        return role.uppercase() in listOf("DRIVER", "DRIVER_MOTOR", "DRIVER_MOBIL", "DRIVER_PENGGANTI")
    }

    /**
     * Check if role is Owner
     */
    fun isOwner(role: String): Boolean {
        return role.uppercase() in listOf("PEMILIK_KENDARAAN", "PEMILIK KENDARAAN")
    }

    /**
     * Check if role is Passenger
     */
    fun isPassenger(role: String): Boolean {
        return role.uppercase() == "PENUMPANG"
    }
}

