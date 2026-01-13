package com.example.app_jalanin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.app_jalanin.data.local.entity.DriverProfile

@Dao
interface DriverProfileDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: DriverProfile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<DriverProfile>): List<Long>

    @Update
    suspend fun update(profile: DriverProfile)

    @Query("SELECT * FROM driver_profiles WHERE driverEmail = :email LIMIT 1")
    suspend fun getByEmail(email: String): DriverProfile?

    @Query("SELECT * FROM driver_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DriverProfile?

    @Query("SELECT * FROM driver_profiles")
    suspend fun getAll(): List<DriverProfile>

    /**
     * Get semua driver yang sedang online
     */
    @Query("SELECT * FROM driver_profiles WHERE isOnline = 1")
    suspend fun getOnlineDrivers(): List<DriverProfile>

    /**
     * Get driver profiles berdasarkan role (dengan join ke users)
     * Note: Ini memerlukan query manual dengan join, atau bisa dilakukan di repository
     */
    @Query("SELECT dp.* FROM driver_profiles dp INNER JOIN users u ON dp.driverEmail = u.email WHERE u.role = :role AND dp.isOnline = 1")
    suspend fun getOnlineDriversByRole(role: String): List<DriverProfile>

    /**
     * Update status online driver
     */
    @Query("UPDATE driver_profiles SET isOnline = :isOnline, updatedAt = :updatedAt, synced = 0 WHERE driverEmail = :email")
    suspend fun updateOnlineStatus(email: String, isOnline: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update SIM certifications
     */
    @Query("UPDATE driver_profiles SET simCertifications = :simCertifications, updatedAt = :updatedAt, synced = 0 WHERE driverEmail = :email")
    suspend fun updateSimCertifications(email: String, simCertifications: String?, updatedAt: Long = System.currentTimeMillis())

    /**
     * Get semua profile yang belum tersinkron ke Firestore
     */
    @Query("SELECT * FROM driver_profiles WHERE synced = 0")
    suspend fun getUnsyncedProfiles(): List<DriverProfile>

    /**
     * Mark profile sebagai sudah tersinkron
     */
    @Query("UPDATE driver_profiles SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    /**
     * Delete profile berdasarkan email
     */
    @Query("DELETE FROM driver_profiles WHERE driverEmail = :email")
    suspend fun deleteByEmail(email: String)

    /**
     * Delete semua profiles
     */
    @Query("DELETE FROM driver_profiles")
    suspend fun deleteAll()
}

