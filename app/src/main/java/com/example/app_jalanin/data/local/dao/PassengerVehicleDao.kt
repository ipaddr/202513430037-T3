package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.model.PassengerVehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface PassengerVehicleDao {
    
    @Query("SELECT * FROM passenger_vehicles WHERE passengerId = :passengerId ORDER BY createdAt DESC")
    fun getAllVehiclesByPassenger(passengerId: String): Flow<List<PassengerVehicle>>
    
    @Query("SELECT * FROM passenger_vehicles WHERE passengerId = :passengerId AND isActive = 1 ORDER BY createdAt DESC")
    fun getActiveVehiclesByPassenger(passengerId: String): Flow<List<PassengerVehicle>>
    
    @Query("SELECT * FROM passenger_vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: Int): PassengerVehicle?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: PassengerVehicle): Long
    
    @Update
    suspend fun updateVehicle(vehicle: PassengerVehicle)
    
    @Delete
    suspend fun deleteVehicle(vehicle: PassengerVehicle)
    
    @Query("DELETE FROM passenger_vehicles WHERE id = :vehicleId")
    suspend fun deleteVehicleById(vehicleId: Int)
    
    @Query("UPDATE passenger_vehicles SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :vehicleId")
    suspend fun updateVehicleStatus(vehicleId: Int, isActive: Boolean, updatedAt: Long)
    
    @Query("SELECT * FROM passenger_vehicles WHERE passengerId = :passengerId AND synced = 0")
    suspend fun getUnsyncedVehicles(passengerId: String): List<PassengerVehicle>
    
    @Query("SELECT * FROM passenger_vehicles WHERE passengerId = :passengerId ORDER BY createdAt DESC")
    suspend fun getAllVehiclesByPassengerSync(passengerId: String): List<PassengerVehicle>
}
