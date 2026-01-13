package com.example.app_jalanin.utils

import android.content.Context
import android.util.Log
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.User
import com.example.app_jalanin.data.remote.FirestoreUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper untuk migrasi username dari email
 * Memastikan semua user memiliki username (default dari email jika belum ada)
 */
object UsernameMigrationHelper {
    private const val TAG = "UsernameMigration"

    /**
     * Migrate username untuk semua user yang belum memiliki username
     * Default: email substring sebelum '@'
     */
    suspend fun migrateUsernames(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val users = db.userDao().getAllUsers()
            
            var migratedCount = 0
            var syncedCount = 0
            
            for (user in users) {
                try {
                    // Skip jika sudah ada username
                    if (!user.username.isNullOrBlank()) {
                        continue
                    }
                    
                    // Generate username dari email
                    val defaultUsername = user.email.substringBefore("@")
                    
                    // Update user dengan username
                    val updatedUser = user.copy(username = defaultUsername, synced = false)
                    db.userDao().update(updatedUser)
                    
                    // Sync ke Firestore
                    try {
                        FirestoreUserService.upsertUser(updatedUser)
                        syncedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing username to Firestore for ${user.email}: ${e.message}", e)
                    }
                    
                    migratedCount++
                    Log.d(TAG, "✅ Migrated username for ${user.email}: $defaultUsername")
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating username for ${user.email}: ${e.message}", e)
                }
            }
            
            if (migratedCount > 0) {
                Log.d(TAG, "✅ Username migration complete: $migratedCount users migrated, $syncedCount synced to Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during username migration: ${e.message}", e)
        }
    }

    /**
     * Ensure user has username, create from email if missing
     */
    suspend fun ensureUsername(context: Context, userEmail: String): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val user = db.userDao().getUserByEmail(userEmail)
        
        if (user != null) {
            if (user.username.isNullOrBlank()) {
                val defaultUsername = userEmail.substringBefore("@")
                val updatedUser = user.copy(username = defaultUsername, synced = false)
                db.userDao().update(updatedUser)
                
                // Sync to Firestore
                try {
                    FirestoreUserService.upsertUser(updatedUser)
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing username to Firestore: ${e.message}", e)
                }
                
                defaultUsername
            } else {
                user.username!!
            }
        } else {
            // User not found, return email prefix as fallback
            userEmail.substringBefore("@")
        }
    }
}

