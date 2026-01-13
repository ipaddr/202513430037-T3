package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.Rental
import kotlinx.coroutines.flow.Flow

/**
 * DAO untuk Rental operations
 */
@Dao
interface RentalDao {
    /**
     * Insert rental baru
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rental: Rental): Long

    /**
     * Update rental existing
     */
    @Update
    suspend fun update(rental: Rental)

    /**
     * Delete rental
     */
    @Delete
    suspend fun delete(rental: Rental)

    /**
     * Get rental by ID
     */
    @Query("SELECT * FROM rentals WHERE id = :rentalId LIMIT 1")
    suspend fun getRentalById(rentalId: String): Rental?

    /**
     * Get all rentals untuk user tertentu
     */
    @Query("SELECT * FROM rentals WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getRentalsByUserId(userId: Int): List<Rental>

    /**
     * Get all rentals untuk user tertentu (Flow untuk real-time update)
     */
    @Query("SELECT * FROM rentals WHERE userId = :userId ORDER BY createdAt DESC")
    fun getRentalsByUserIdFlow(userId: Int): Flow<List<Rental>>

    /**
     * Get all rentals by email (more reliable for dummy users)
     */
    @Query("SELECT * FROM rentals WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    suspend fun getRentalsByEmail(userEmail: String): List<Rental>

    /**
     * Get all rentals by email (Flow)
     */
    @Query("SELECT * FROM rentals WHERE userEmail = :userEmail ORDER BY createdAt DESC")
    fun getRentalsByEmailFlow(userEmail: String): Flow<List<Rental>>

    /**
     * Get active rentals (status = ACTIVE atau DELIVERING)
     */
    @Query("SELECT * FROM rentals WHERE userId = :userId AND (status = 'ACTIVE' OR status = 'DELIVERING') ORDER BY createdAt DESC")
    suspend fun getActiveRentals(userId: Int): List<Rental>

    /**
     * Get active rentals (Flow)
     */
    @Query("SELECT * FROM rentals WHERE userId = :userId AND (status = 'ACTIVE' OR status = 'DELIVERING') ORDER BY createdAt DESC")
    fun getActiveRentalsFlow(userId: Int): Flow<List<Rental>>

    /**
     * Get active rentals by email
     */
    @Query("SELECT * FROM rentals WHERE userEmail = :userEmail AND (status = 'ACTIVE' OR status = 'DELIVERING') ORDER BY createdAt DESC")
    suspend fun getActiveRentalsByEmail(userEmail: String): List<Rental>

    /**
     * Get active rentals by email (Flow)
     */
    @Query("SELECT * FROM rentals WHERE userEmail = :userEmail AND (status = 'ACTIVE' OR status = 'DELIVERING') ORDER BY createdAt DESC")
    fun getActiveRentalsByEmailFlow(userEmail: String): Flow<List<Rental>>

    /**
     * Update rental status
     */
    @Query("UPDATE rentals SET status = :newStatus, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun updateStatus(rentalId: String, newStatus: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update rental start and end times
     */
    @Query("UPDATE rentals SET startDate = :startDate, endDate = :endDate, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun updateRentalTimes(rentalId: String, startDate: Long, endDate: Long, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update overtime fee
     */
    @Query("UPDATE rentals SET overtimeFee = :overtimeFee, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun updateOvertimeFee(rentalId: String, overtimeFee: Int, updatedAt: Long = System.currentTimeMillis())

    /**
     * Update sync status
     */
    @Query("UPDATE rentals SET synced = :synced, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun updateSyncStatus(rentalId: String, synced: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Get unsynced rentals (untuk sync ke Firestore)
     */
    @Query("SELECT * FROM rentals WHERE synced = 0")
    suspend fun getUnsyncedRentals(): List<Rental>

    /**
     * Get all rentals
     */
    @Query("SELECT * FROM rentals ORDER BY createdAt DESC")
    suspend fun getAllRentals(): List<Rental>

    /**
     * Delete all rentals (untuk testing/development)
     */
    @Query("DELETE FROM rentals")
    suspend fun deleteAll()

    /**
     * Delete rentals older than timestamp
     */
    @Query("DELETE FROM rentals WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Delete completed rentals
     */
    @Query("DELETE FROM rentals WHERE status = 'COMPLETED' OR status = 'CANCELLED'")
    suspend fun deleteCompletedRentals()

    /**
     * Count rentals by status
     */
    @Query("SELECT COUNT(*) FROM rentals WHERE userId = :userId AND status = :status")
    suspend fun countByStatus(userId: Int, status: String): Int

    /**
     * Get overdue rentals
     */
    @Query("SELECT * FROM rentals WHERE userId = :userId AND status = 'ACTIVE' AND endDate < :currentTime")
    suspend fun getOverdueRentals(userId: Int, currentTime: Long = System.currentTimeMillis()): List<Rental>

    /**
     * Get overdue rentals by email (more reliable)
     */
    @Query("SELECT * FROM rentals WHERE userEmail = :userEmail AND status = 'ACTIVE' AND endDate < :currentTime")
    suspend fun getOverdueRentalsByEmail(userEmail: String, currentTime: Long = System.currentTimeMillis()): List<Rental>
    
    /**
     * Get active rentals assigned to a driver
     */
    @Query("SELECT * FROM rentals WHERE driverId = :driverEmail AND (status = 'ACTIVE' OR status = 'DELIVERING') ORDER BY createdAt DESC")
    suspend fun getActiveRentalsByDriver(driverEmail: String): List<Rental>
    
    /**
     * Get active rentals assigned to a driver (Flow)
     */
    @Query("SELECT * FROM rentals WHERE driverId = :driverEmail AND (status = 'ACTIVE' OR status = 'DELIVERING') ORDER BY createdAt DESC")
    fun getActiveRentalsByDriverFlow(driverEmail: String): Flow<List<Rental>>
    
    /**
     * Get all rentals assigned to a driver (for history)
     */
    @Query("SELECT * FROM rentals WHERE driverId = :driverEmail ORDER BY createdAt DESC")
    suspend fun getRentalsByDriver(driverEmail: String): List<Rental>
    
    /**
     * Get all rentals assigned to a driver (Flow)
     */
    @Query("SELECT * FROM rentals WHERE driverId = :driverEmail ORDER BY createdAt DESC")
    fun getRentalsByDriverFlow(driverEmail: String): Flow<List<Rental>>
    
    /**
     * Get rentals by owner email
     */
    @Query("""
        SELECT r.* FROM rentals r
        WHERE r.ownerEmail = :ownerEmail 
        OR (
            (r.ownerEmail IS NULL OR r.ownerEmail = '') 
            AND EXISTS (
                SELECT 1 FROM vehicles v 
                WHERE v.ownerId = :ownerEmail 
                AND CAST(r.vehicleId AS INTEGER) = v.id
            )
        )
        ORDER BY r.createdAt DESC
    """)
    suspend fun getRentalsByOwner(ownerEmail: String): List<Rental>
    
    /**
     * Get rentals by owner email (Flow)
     * Returns rentals where ownerEmail matches exactly
     * Also includes rentals where ownerEmail is null/empty but vehicle belongs to owner (fallback for old data)
     */
    @Query("""
        SELECT r.* FROM rentals r
        WHERE r.ownerEmail = :ownerEmail 
        OR (
            (r.ownerEmail IS NULL OR r.ownerEmail = '') 
            AND EXISTS (
                SELECT 1 FROM vehicles v 
                WHERE v.ownerId = :ownerEmail 
                AND CAST(r.vehicleId AS INTEGER) = v.id
            )
        )
        ORDER BY r.createdAt DESC
    """)
    fun getRentalsByOwnerFlow(ownerEmail: String): Flow<List<Rental>>
    
    /**
     * Get pending rentals by owner (waiting for delivery option selection)
     */
    @Query("SELECT * FROM rentals WHERE ownerEmail = :ownerEmail AND status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingRentalsByOwner(ownerEmail: String): List<Rental>
    
    /**
     * Get pending rentals by owner (Flow)
     */
    @Query("SELECT * FROM rentals WHERE ownerEmail = :ownerEmail AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingRentalsByOwnerFlow(ownerEmail: String): Flow<List<Rental>>
    
    /**
     * Get active rentals by owner (status = ACTIVE)
     */
    @Query("""
        SELECT r.* FROM rentals r
        WHERE (r.ownerEmail = :ownerEmail
        OR (
            (r.ownerEmail IS NULL OR r.ownerEmail = '')
            AND EXISTS (
                SELECT 1 FROM vehicles v
                WHERE v.ownerId = :ownerEmail
                AND CAST(r.vehicleId AS INTEGER) = v.id
            )
        ))
        AND r.status = 'ACTIVE'
        ORDER BY r.createdAt DESC
    """)
    suspend fun getActiveRentalsByOwner(ownerEmail: String): List<Rental>
    
    /**
     * Get active rentals by owner (Flow)
     */
    @Query("""
        SELECT r.* FROM rentals r
        WHERE (r.ownerEmail = :ownerEmail
        OR (
            (r.ownerEmail IS NULL OR r.ownerEmail = '')
            AND EXISTS (
                SELECT 1 FROM vehicles v
                WHERE v.ownerId = :ownerEmail
                AND CAST(r.vehicleId AS INTEGER) = v.id
            )
        ))
        AND r.status = 'ACTIVE'
        ORDER BY r.createdAt DESC
    """)
    fun getActiveRentalsByOwnerFlow(ownerEmail: String): Flow<List<Rental>>
    
    /**
     * Get rentals with early return requested by owner
     */
    @Query("""
        SELECT r.* FROM rentals r
        WHERE (r.ownerEmail = :ownerEmail
        OR (
            (r.ownerEmail IS NULL OR r.ownerEmail = '')
            AND EXISTS (
                SELECT 1 FROM vehicles v
                WHERE v.ownerId = :ownerEmail
                AND CAST(r.vehicleId AS INTEGER) = v.id
            )
        ))
        AND r.earlyReturnRequested = 1
        AND (r.earlyReturnStatus = 'REQUESTED' OR r.earlyReturnStatus = 'IN_PROGRESS')
        ORDER BY r.earlyReturnRequestedAt DESC
    """)
    suspend fun getEarlyReturnRequestsByOwner(ownerEmail: String): List<Rental>
    
    /**
     * Get rentals with early return requested by owner (Flow)
     */
    @Query("""
        SELECT r.* FROM rentals r
        WHERE (r.ownerEmail = :ownerEmail
        OR (
            (r.ownerEmail IS NULL OR r.ownerEmail = '')
            AND EXISTS (
                SELECT 1 FROM vehicles v
                WHERE v.ownerId = :ownerEmail
                AND CAST(r.vehicleId AS INTEGER) = v.id
            )
        ))
        AND r.earlyReturnRequested = 1
        AND (r.earlyReturnStatus = 'REQUESTED' OR r.earlyReturnStatus = 'IN_PROGRESS')
        ORDER BY r.earlyReturnRequestedAt DESC
    """)
    fun getEarlyReturnRequestsByOwnerFlow(ownerEmail: String): Flow<List<Rental>>
}

