package com.example.app_jalanin.utils

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper untuk resolve username dari userId atau email
 * UI harus menggunakan ini untuk mendapatkan username, bukan menggunakan email langsung
 */
object UsernameResolver {
    private const val TAG = "UsernameResolver"

    /**
     * Resolve username from userId
     * Returns username or fallback to email prefix if not found
     */
    suspend fun resolveUsernameFromUserId(context: Context, userId: Int): String = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val user = db.userDao().getUserById(userId)
            
            if (user != null) {
                // Ensure username exists
                if (user.username.isNullOrBlank()) {
                    val defaultUsername = user.email.substringBefore("@")
                    val updatedUser = user.copy(username = defaultUsername, synced = false)
                    db.userDao().update(updatedUser)
                    defaultUsername
                } else {
                    user.username!!
                }
            } else {
                Log.w(TAG, "User not found for userId: $userId")
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving username from userId $userId: ${e.message}", e)
            "Unknown"
        }
    }

    /**
     * Resolve username from email
     * Returns username or fallback to email prefix if not found
     */
    suspend fun resolveUsernameFromEmail(context: Context, email: String?): String = withContext(Dispatchers.IO) {
        if (email.isNullOrBlank()) {
            return@withContext "Unknown"
        }
        
        try {
            // Ensure username exists
            UsernameMigrationHelper.ensureUsername(context, email)
            
            val db = AppDatabase.getDatabase(context)
            val user = db.userDao().getUserByEmail(email)
            
            if (user != null) {
                user.username ?: user.email.substringBefore("@")
            } else {
                // Fallback to email prefix
                email.substringBefore("@")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving username from email $email: ${e.message}", e)
            email.substringBefore("@")
        }
    }

    /**
     * Resolve username from userId or email (prefer userId)
     */
    suspend fun resolveUsername(context: Context, userId: Int?, email: String?): String = withContext(Dispatchers.IO) {
        if (userId != null && userId > 0) {
            resolveUsernameFromUserId(context, userId)
        } else if (!email.isNullOrBlank()) {
            resolveUsernameFromEmail(context, email)
        } else {
            "Unknown"
        }
    }
}

