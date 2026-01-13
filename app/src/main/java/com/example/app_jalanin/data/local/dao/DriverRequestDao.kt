package com.example.app_jalanin.data.local.dao

import androidx.room.*
import com.example.app_jalanin.data.local.entity.DriverRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverRequestDao {
    
    @Query("SELECT * FROM driver_requests WHERE driverEmail = :driverEmail ORDER BY createdAt DESC")
    fun getRequestsByDriver(driverEmail: String): Flow<List<DriverRequest>>
    
    @Query("SELECT * FROM driver_requests WHERE driverEmail = :driverEmail AND status = :status ORDER BY createdAt DESC")
    fun getRequestsByDriverAndStatus(driverEmail: String, status: String): Flow<List<DriverRequest>>
    
    @Query("SELECT * FROM driver_requests WHERE driverEmail = :driverEmail AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingRequestsByDriver(driverEmail: String): Flow<List<DriverRequest>>
    
    @Query("SELECT * FROM driver_requests WHERE driverEmail = :driverEmail AND status IN ('ACCEPTED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS') ORDER BY createdAt DESC")
    fun getActiveRequestsByDriver(driverEmail: String): Flow<List<DriverRequest>>
    
    @Query("SELECT * FROM driver_requests WHERE passengerEmail = :passengerEmail ORDER BY createdAt DESC")
    fun getRequestsByPassenger(passengerEmail: String): Flow<List<DriverRequest>>
    
    @Query("SELECT * FROM driver_requests WHERE passengerEmail = :passengerEmail AND status IN ('ACCEPTED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS') ORDER BY createdAt DESC")
    fun getActiveRequestsByPassenger(passengerEmail: String): Flow<List<DriverRequest>>
    
    @Query("SELECT * FROM driver_requests WHERE id = :requestId")
    suspend fun getRequestById(requestId: String): DriverRequest?
    
    @Query("SELECT * FROM driver_requests WHERE id = :requestId")
    fun getRequestByIdFlow(requestId: String): Flow<DriverRequest?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: DriverRequest)
    
    @Update
    suspend fun update(request: DriverRequest)
    
    @Delete
    suspend fun delete(request: DriverRequest)
    
    @Query("UPDATE driver_requests SET status = :status, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun updateStatus(requestId: String, status: String, updatedAt: Long)
    
    @Query("UPDATE driver_requests SET status = :status, acceptedAt = :acceptedAt, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun acceptRequest(requestId: String, status: String, acceptedAt: Long, updatedAt: Long)
    
    @Query("UPDATE driver_requests SET status = 'REJECTED', updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun rejectRequest(requestId: String, updatedAt: Long)
    
    @Query("UPDATE driver_requests SET driverArrivalMethod = :method, estimatedArrivalMinutes = :minutes, driverCurrentLat = :lat, driverCurrentLon = :lon, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun updateDriverLocation(requestId: String, method: String, minutes: Int, lat: Double, lon: Double, updatedAt: Long)
    
    @Query("UPDATE driver_requests SET status = :status, arrivedAt = :arrivedAt, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun markDriverArrived(requestId: String, status: String, arrivedAt: Long, updatedAt: Long)
    
    @Query("UPDATE driver_requests SET status = :status, startedAt = :startedAt, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun startTrip(requestId: String, status: String, startedAt: Long, updatedAt: Long)
    
    @Query("UPDATE driver_requests SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun completeTrip(requestId: String, status: String, completedAt: Long, updatedAt: Long)
}
