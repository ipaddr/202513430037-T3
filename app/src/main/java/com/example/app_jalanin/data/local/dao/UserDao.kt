package com.example.app_jalanin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.app_jalanin.data.local.entity.User

@Dao
@Suppress("AndroidUnresolvedRoomSqlReference")
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE email = :email AND password = :password AND UPPER(role) = UPPER(:role) LIMIT 1")
    suspend fun login(email: String, password: String, role: String): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    @Query("SELECT * FROM users WHERE role = :role")
    suspend fun getUsersByRole(role: String): List<User>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)

    @Query("DELETE FROM users WHERE email = :email")
    suspend fun deleteByEmail(email: String)

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("UPDATE users SET password = :newPassword WHERE email = :email")
    suspend fun updatePassword(email: String, newPassword: String)

    @Query("SELECT * FROM users WHERE synced = 0")
    suspend fun getUnsyncedUsers(): List<User>

    @Query("UPDATE users SET synced = 1 WHERE id = :userId")
    suspend fun markUserSynced(userId: Int)
    
    // ✅ NEW: Update driver online/offline status
    // ✅ REMOVED: updateDriverOnlineStatus and getOnlineDriversByRole
    // These methods are now in DriverProfileDao
}
