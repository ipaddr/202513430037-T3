package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.DriverRental
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverRentalDao {
    
    @Query("SELECT * FROM driver_rentals WHERE passengerEmail = :passengerEmail ORDER BY createdAt DESC")
    fun getRentalsByPassenger(passengerEmail: String): Flow<List<DriverRental>>
    
    @Query("SELECT * FROM driver_rentals WHERE passengerEmail = :passengerEmail AND status = :status ORDER BY createdAt DESC")
    fun getRentalsByPassengerAndStatus(passengerEmail: String, status: String): Flow<List<DriverRental>>
    
    @Query("SELECT * FROM driver_rentals WHERE passengerEmail = :passengerEmail AND status IN ('CONFIRMED', 'ACTIVE') ORDER BY createdAt DESC")
    fun getActiveRentalsByPassenger(passengerEmail: String): Flow<List<DriverRental>>
    
    @Query("SELECT * FROM driver_rentals WHERE driverEmail = :driverEmail ORDER BY createdAt DESC")
    fun getRentalsByDriver(driverEmail: String): Flow<List<DriverRental>>
    
    @Query("SELECT * FROM driver_rentals WHERE driverEmail = :driverEmail AND status = :status ORDER BY createdAt DESC")
    fun getRentalsByDriverAndStatus(driverEmail: String, status: String): Flow<List<DriverRental>>
    
    @Query("SELECT * FROM driver_rentals WHERE driverEmail = :driverEmail AND status IN ('CONFIRMED', 'ACTIVE') ORDER BY createdAt DESC")
    fun getActiveRentalsByDriver(driverEmail: String): Flow<List<DriverRental>>
    
    @Query("SELECT * FROM driver_rentals WHERE id = :rentalId")
    suspend fun getRentalById(rentalId: String): DriverRental?
    
    @Query("SELECT * FROM driver_rentals WHERE id = :rentalId")
    fun getRentalByIdFlow(rentalId: String): Flow<DriverRental?>
    
    @Query("SELECT * FROM driver_rentals WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedRentals(): List<DriverRental>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rental: DriverRental)
    
    @Update
    suspend fun update(rental: DriverRental)
    
    @Delete
    suspend fun delete(rental: DriverRental)
    
    @Query("UPDATE driver_rentals SET status = :status, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun updateStatus(rentalId: String, status: String, updatedAt: Long)
    
    @Query("UPDATE driver_rentals SET status = :status, confirmedAt = :confirmedAt, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun confirmRental(rentalId: String, status: String, confirmedAt: Long, updatedAt: Long)
    
    @Query("UPDATE driver_rentals SET status = :status, startDate = :startDate, endDate = :endDate, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun startRental(rentalId: String, status: String, startDate: Long, endDate: Long, updatedAt: Long)
    
    @Query("UPDATE driver_rentals SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :rentalId")
    suspend fun completeRental(rentalId: String, status: String, completedAt: Long, updatedAt: Long)
    
    @Query("UPDATE driver_rentals SET synced = 1 WHERE id = :rentalId")
    suspend fun markSynced(rentalId: String)
}

