package com.example.app_jalanin.auth

import android.content.Context
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages authentication state and current user session
 */
object AuthStateManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_CURRENT_USER_ID = "current_user_id"
    private const val KEY_CURRENT_USER_EMAIL = "current_user_email"

    /**
     * Save current user to preferences
     */
    fun saveCurrentUser(context: Context, user: User) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_CURRENT_USER_ID, user.id)
            putString(KEY_CURRENT_USER_EMAIL, user.email)
            apply()
        }
    }

    /**
     * Get current logged in user from database
     */
    suspend fun getCurrentUser(context: Context): User? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val userId = prefs.getInt(KEY_CURRENT_USER_ID, -1)
            val userEmail = prefs.getString(KEY_CURRENT_USER_EMAIL, null)

            android.util.Log.d("AuthStateManager", "📍 getCurrentUser - userId from prefs: $userId, email: $userEmail")

            if (userId == -1) {
                android.util.Log.e("AuthStateManager", "❌ No userId in SharedPreferences")
                return@withContext null
            }

            val db = AppDatabase.getDatabase(context)
            val user = db.userDao().getUserById(userId)

            if (user != null) {
                android.util.Log.d("AuthStateManager", "✅ User found in DB: ${user.email} (ID: ${user.id})")
            } else {
                android.util.Log.e("AuthStateManager", "❌ User NOT found in DB for userId: $userId")
                android.util.Log.e("AuthStateManager", "🔍 Attempting to fetch by email: $userEmail")

                // Try to get by email as fallback
                if (userEmail != null) {
                    val userByEmail = db.userDao().getUserByEmail(userEmail)
                    if (userByEmail != null) {
                        android.util.Log.d("AuthStateManager", "✅ User found by email: ${userByEmail.email} (ID: ${userByEmail.id})")
                        // Update the userId in prefs
                        saveCurrentUser(context, userByEmail)
                        return@withContext userByEmail
                    }
                }
            }

            return@withContext user
        } catch (e: Exception) {
            android.util.Log.e("AuthStateManager", "❌ Error getting current user: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Get current user email from preferences (lightweight, no DB access)
     */
    fun getCurrentUserEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CURRENT_USER_EMAIL, null)
    }

    /**
     * Get current user ID from preferences (lightweight, no DB access)
     */
    fun getCurrentUserId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CURRENT_USER_ID, -1)
    }

    /**
     * Clear current user session (logout)
     */
    fun clearCurrentUser(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(context: Context): Boolean {
        return getCurrentUserId(context) != -1
    }
}

