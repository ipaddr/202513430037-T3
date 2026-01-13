package com.example.app_jalanin.ui.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.DriverRental
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.local.entity.PaymentHistory
import com.example.app_jalanin.data.model.PassengerVehicle
import com.example.app_jalanin.data.model.Vehicle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import android.util.Log

/**
 * Data class for Trip History summary
 * Shows only summary information, not detailed payment breakdown
 */
data class TripHistoryItem(
    val id: String, // rentalId or driverRentalId
    val tripType: TripType, // DRIVER_RENTAL or VEHICLE_RENTAL
    val driverName: String?,
    val driverEmail: String?,
    val paymentMethod: String, // "MBANKING", "CASH", "M-Banking", "Tunai"
    val totalPaymentAmount: Int, // Summary only
    val vehicleInfo: VehicleInfo,
    val tripDate: Long, // Date and time of the trip
    val paymentId: Long? = null // For navigation to payment detail
)

enum class TripType {
    DRIVER_RENTAL,  // Driver rental (passenger's own vehicle)
    VEHICLE_RENTAL  // Vehicle rental (rented vehicle)
}

data class VehicleInfo(
    val vehicleName: String,
    val vehicleType: String, // "MOBIL" or "MOTOR"
    val isPassengerOwned: Boolean, // true if passenger's own vehicle, false if rented
    val licensePlate: String? = null
)

class TripHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    
    private val _tripHistory = MutableStateFlow<List<TripHistoryItem>>(emptyList())
    val tripHistory: StateFlow<List<TripHistoryItem>> = _tripHistory.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun loadTripHistory(passengerEmail: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                Log.d("TripHistoryViewModel", "📥 Loading trip history for: $passengerEmail")
                
                val trips = withContext(Dispatchers.IO) {
                    val tripList = mutableListOf<TripHistoryItem>()
                    
                    // 1. Load Driver Rentals (driver rental trips)
                    val driverRentals = database.driverRentalDao().getRentalsByPassenger(passengerEmail).first()
                    Log.d("TripHistoryViewModel", "📦 Found ${driverRentals.size} driver rentals")
                    
                    for (rental in driverRentals) {
                        // Only show completed rentals
                        if (rental.status == "COMPLETED" || rental.status == "CONFIRMED") {
                            // Get payment info
                            val payment = database.paymentHistoryDao().getPaymentHistoryByRental(rental.id).firstOrNull()
                            
                            // Get passenger vehicle info
                            val passengerVehicles = database.passengerVehicleDao().getActiveVehiclesByPassenger(passengerEmail).first()
                            val passengerVehicle = if (rental.vehicleType == "MOBIL") {
                                passengerVehicles.firstOrNull { it.type.name == "MOBIL" }
                            } else {
                                passengerVehicles.firstOrNull { it.type.name == "MOTOR" }
                            }
                            
                            val vehicleInfo = if (passengerVehicle != null) {
                                VehicleInfo(
                                    vehicleName = "${passengerVehicle.brand} ${passengerVehicle.model}",
                                    vehicleType = rental.vehicleType,
                                    isPassengerOwned = true,
                                    licensePlate = passengerVehicle.licensePlate
                                )
                            } else {
                                VehicleInfo(
                                    vehicleName = "${rental.vehicleType} (Private Vehicle)",
                                    vehicleType = rental.vehicleType,
                                    isPassengerOwned = true
                                )
                            }
                            
                            // Format payment method
                            val paymentMethod = when (rental.paymentMethod.uppercase()) {
                                "MBANKING" -> "M-Banking"
                                "CASH" -> "Tunai"
                                else -> rental.paymentMethod
                            }
                            
                            tripList.add(
                                TripHistoryItem(
                                    id = rental.id,
                                    tripType = TripType.DRIVER_RENTAL,
                                    driverName = rental.driverName,
                                    driverEmail = rental.driverEmail,
                                    paymentMethod = paymentMethod,
                                    totalPaymentAmount = rental.price.toInt(),
                                    vehicleInfo = vehicleInfo,
                                    tripDate = rental.confirmedAt ?: rental.createdAt,
                                    paymentId = payment?.id
                                )
                            )
                        }
                    }
                    
                    // 2. Load Vehicle Rentals (rented vehicle trips)
                    val vehicleRentals = database.rentalDao().getRentalsByEmail(passengerEmail)
                    Log.d("TripHistoryViewModel", "📦 Found ${vehicleRentals.size} vehicle rentals")
                    
                    for (rental in vehicleRentals) {
                        // Only show completed rentals
                        if (rental.status == "COMPLETED") {
                            // Get payment info
                            val payment = database.paymentHistoryDao().getPaymentHistoryByRental(rental.id).firstOrNull()
                            
                            // Get vehicle info
                            val vehicle = try {
                                database.vehicleDao().getVehicleById(rental.vehicleId.toIntOrNull() ?: 0)
                            } catch (e: Exception) {
                                null
                            }
                            
                            val vehicleInfo = if (vehicle != null) {
                                VehicleInfo(
                                    vehicleName = vehicle.name,
                                    vehicleType = vehicle.type.name,
                                    isPassengerOwned = false,
                                    licensePlate = vehicle.licensePlate
                                )
                            } else {
                                VehicleInfo(
                                    vehicleName = rental.vehicleName,
                                    vehicleType = rental.vehicleType,
                                    isPassengerOwned = false
                                )
                            }
                            
                            // Get driver name if available
                            var driverName: String? = null
                            var driverEmail: String? = null
                            if (rental.driverId != null) {
                                try {
                                    val driver = database.userDao().getUserByEmail(rental.driverId)
                                    driverName = driver?.fullName
                                    driverEmail = driver?.email
                                } catch (e: Exception) {
                                    Log.e("TripHistoryViewModel", "Error loading driver: ${e.message}")
                                }
                            }
                            
                            // Format payment method
                            val paymentMethod = when (payment?.paymentMethod?.uppercase()) {
                                "M-BANKING", "MBANKING" -> "M-Banking"
                                "Tunai", "CASH" -> "Tunai"
                                else -> payment?.paymentMethod ?: "Unknown"
                            }
                            
                            tripList.add(
                                TripHistoryItem(
                                    id = rental.id,
                                    tripType = TripType.VEHICLE_RENTAL,
                                    driverName = driverName,
                                    driverEmail = driverEmail,
                                    paymentMethod = paymentMethod,
                                    totalPaymentAmount = payment?.amount ?: rental.totalPrice,
                                    vehicleInfo = vehicleInfo,
                                    tripDate = rental.startDate,
                                    paymentId = payment?.id
                                )
                            )
                        }
                    }
                    
                    // Sort by trip date (newest first)
                    tripList.sortByDescending { it.tripDate }
                    tripList // Return sorted list
                }
                
                _tripHistory.value = trips
                Log.d("TripHistoryViewModel", "✅ Loaded ${trips.size} trip history items")
                
            } catch (e: Exception) {
                Log.e("TripHistoryViewModel", "❌ Error loading trip history: ${e.message}", e)
                _errorMessage.value = "Error loading trip history: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

