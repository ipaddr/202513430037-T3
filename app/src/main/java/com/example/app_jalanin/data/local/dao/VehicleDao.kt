package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.model.Vehicle
import com.example.app_jalanin.data.model.VehicleStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun getAllVehiclesByOwner(ownerId: String): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    suspend fun getAllVehiclesByOwnerSync(ownerId: String): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: Int): Vehicle?

    @Query("SELECT * FROM vehicles WHERE ownerId = :ownerId AND status = :status")
    fun getVehiclesByStatus(ownerId: String, status: VehicleStatus): Flow<List<Vehicle>>

    @Query("SELECT COUNT(*) FROM vehicles WHERE ownerId = :ownerId AND status = :status")
    suspend fun countVehiclesByStatus(ownerId: String, status: VehicleStatus): Int

    @Query("SELECT * FROM vehicles WHERE status = :status")
    fun getAllAvailableVehicles(status: VehicleStatus = VehicleStatus.TERSEDIA): Flow<List<Vehicle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    suspend fun deleteVehicleById(vehicleId: Int)

    @Query("UPDATE vehicles SET status = :status, statusReason = :reason, updatedAt = :updatedAt WHERE id = :vehicleId")
    suspend fun updateVehicleStatus(vehicleId: Int, status: VehicleStatus, reason: String?, updatedAt: Long)
}

