package com.example.app_jalanin.data.remote

import com.example.app_jalanin.data.local.entity.User
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

object FirestoreUserService {

    private val db get() = Firebase.firestore

    /**
     * Get user from Firestore by email
     *
     * ⚠️ WARNING: Firestore does NOT store passwords for security!
     * ⚠️ This function returns user with EMPTY password
     * ⚠️ DO NOT use for login! Only for profile data sync
     * 
     * ✅ FIX: Query by email field instead of document ID
     * This handles cases where document ID is Firebase UID or email-based ID
     */
    suspend fun getUserByEmail(email: String): User? {
        return try {
            android.util.Log.d("FirestoreUserService", "🔍 Searching for user by email: $email")
            
            // ✅ CRITICAL FIX: Query by email field, not document ID
            // Documents can be saved with Firebase UID as ID, so we can't assume email-based ID
            val querySnapshot = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty && querySnapshot.documents.isNotEmpty()) {
                val document = querySnapshot.documents[0]
                android.util.Log.d("FirestoreUserService", "✅ User found in Firestore")
                android.util.Log.d("FirestoreUserService", "   - Document ID: ${document.id}")
                android.util.Log.d("FirestoreUserService", "   - Email: ${document.getString("email")}")
                android.util.Log.d("FirestoreUserService", "   - Role: ${document.getString("role")}")
                
                val firestoreUsername = document.getString("username")
                val defaultUsername = email.substringBefore("@")
                
                User(
                    id = 0, // Will be assigned by Local DB
                    email = document.getString("email") ?: email,
                    password = "", // Password not stored in Firestore
                    role = document.getString("role") ?: "penumpang",
                    username = firestoreUsername ?: defaultUsername, // Use Firestore username or default from email
                    fullName = document.getString("fullName"),
                    phoneNumber = document.getString("phoneNumber"),
                    createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
                    synced = true
                    // ✅ SIM certifications and isOnline are now in driver_profiles collection
                )
            } else {
                android.util.Log.d("FirestoreUserService", "❌ User NOT found in Firestore: $email")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreUserService", "❌ Error getting user by email: ${e.message}", e)
            null
        }
    }

    /**
     * Upsert (insert or update) user to Firestore
     *
     * ✅ SECURITY: Password is NOT synced to Firestore!
     * ✅ Only profile data (email, role, name, phone) is stored in cloud
     * ✅ Password remains ONLY in Local DB (encrypted by Android)
     * 
     * @param user User entity to save
     * @param firebaseUid Optional Firebase Auth UID. If provided, will be used as document ID.
     *                    Otherwise, uses email-based ID (email with @ and . replaced with _)
     */
    suspend fun upsertUser(user: User, firebaseUid: String? = null) {
        // Ensure username exists (migrate from email if needed)
        val username = user.username ?: user.email.substringBefore("@")
        
        val data = hashMapOf(
            "email" to user.email,
            "role" to user.role,
            "username" to username,
            "fullName" to user.fullName,
            "phoneNumber" to user.phoneNumber,
            "createdAt" to user.createdAt
            // ✅ PASSWORD NOT INCLUDED - Security by design!
            // ✅ SIM certifications and isOnline are now in driver_profiles collection
        )
        
        // Prefer Firebase UID as document ID for consistency with Firebase Auth
        // Fallback to email-based ID if UID not available (e.g., for dummy users)
        val docId = firebaseUid ?: user.email.replace("@", "_").replace(".", "_")
        
        android.util.Log.d("FirestoreUserService", "💾 Upserting user to Firestore:")
        android.util.Log.d("FirestoreUserService", "   - Email: ${user.email}")
        android.util.Log.d("FirestoreUserService", "   - Document ID: $docId")
        android.util.Log.d("FirestoreUserService", "   - Using Firebase UID: ${firebaseUid != null}")
        
        db.collection("users").document(docId).set(data).await()
        
        android.util.Log.d("FirestoreUserService", "✅ User successfully saved to Firestore")
    }

    suspend fun ping() {
        val data = mapOf("ts" to System.currentTimeMillis())
        db.collection("diagnostic").document("ping").set(data).await()
    }
}
