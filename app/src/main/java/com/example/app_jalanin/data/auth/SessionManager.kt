package com.example.app_jalanin.data.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user session persistence across app restarts
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_FULL_NAME = "user_full_name"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
    }

    /**
     * Save user session after successful login
     */
    fun saveLoginSession(
        email: String,
        role: String,
        fullName: String?
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_ROLE, role)
            putString(KEY_USER_FULL_NAME, fullName)
            putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
        android.util.Log.d("SessionManager", "✅ Session saved for: $email, role: $role")
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Get saved user email
     */
    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Get saved user role
     */
    fun getUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, null)
    }

    /**
     * Get saved user full name
     */
    fun getUserFullName(): String? {
        return prefs.getString(KEY_USER_FULL_NAME, null)
    }

    /**
     * Get login timestamp
     */
    fun getLoginTimestamp(): Long {
        return prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
    }

    /**
     * Get saved session data
     */
    fun getSavedSession(): SessionData? {
        if (!isLoggedIn()) return null

        val email = getUserEmail() ?: return null
        val role = getUserRole() ?: return null

        return SessionData(
            email = email,
            role = role,
            fullName = getUserFullName(),
            loginTimestamp = getLoginTimestamp()
        )
    }

    /**
     * Clear session (logout)
     */
    fun clearSession() {
        prefs.edit().clear().apply()
        android.util.Log.d("SessionManager", "✅ Session cleared (logout)")
    }

    /**
     * Check if session is still valid (optional: add expiry logic)
     */
    fun isSessionValid(maxAgeMillis: Long = Long.MAX_VALUE): Boolean {
        if (!isLoggedIn()) return false

        val loginTime = getLoginTimestamp()
        val currentTime = System.currentTimeMillis()
        val age = currentTime - loginTime

        return age < maxAgeMillis
    }
}

/**
 * Data class for session information
 */
data class SessionData(
    val email: String,
    val role: String,
    val fullName: String?,
    val loginTimestamp: Long
)

