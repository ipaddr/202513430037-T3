package com.example.app_jalanin.ui.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.remote.FirestoreRentalSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * ViewModel for managing early return request flow
 */
class EarlyReturnRequestViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    
    private val _rental = MutableStateFlow<Rental?>(null)
    val rental: StateFlow<Rental?> = _rental.asStateFlow()
    
    private val _requestStatus = MutableStateFlow<EarlyReturnRequestStatus>(EarlyReturnRequestStatus.NOT_REQUESTED)
    val requestStatus: StateFlow<EarlyReturnRequestStatus> = _requestStatus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    /**
     * Load rental by ID
     */
    fun loadRental(rentalId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val rental = withContext(Dispatchers.IO) {
                    database.rentalDao().getRentalById(rentalId)
                }
                _rental.value = rental
                
                // Update request status based on rental state
                rental?.let {
                    // ✅ Priority: Check earlyReturnStatus first, then return location
                    when (it.earlyReturnStatus) {
                        "REQUESTED" -> {
                            // Request submitted, check if owner has confirmed
                            if (it.returnLocationLat != null && it.returnLocationLon != null && it.returnAddress != null) {
                                _requestStatus.value = EarlyReturnRequestStatus.OWNER_CONFIRMED
                            } else {
                                _requestStatus.value = EarlyReturnRequestStatus.WAITING_FOR_OWNER
                            }
                        }
                        "CONFIRMED" -> _requestStatus.value = EarlyReturnRequestStatus.OWNER_CONFIRMED
                        "IN_PROGRESS" -> _requestStatus.value = EarlyReturnRequestStatus.IN_PROGRESS
                        "COMPLETED" -> _requestStatus.value = EarlyReturnRequestStatus.COMPLETED
                        "CANCELLED" -> _requestStatus.value = EarlyReturnRequestStatus.CANCELLED
                        else -> {
                            // No request yet - even if return location exists, it's from previous rental
                            _requestStatus.value = EarlyReturnRequestStatus.NOT_REQUESTED
                        }
                    }
                } ?: run {
                    _requestStatus.value = EarlyReturnRequestStatus.NOT_REQUESTED
                }
            } catch (e: Exception) {
                Log.e("EarlyReturnRequestViewModel", "Error loading rental: ${e.message}", e)
                _errorMessage.value = "Error loading rental: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Submit early return request
     */
    fun submitRequest(rentalId: String, userEmail: String, ownerEmail: String?) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                withContext(Dispatchers.IO) {
                    val rental = database.rentalDao().getRentalById(rentalId)
                    if (rental == null) {
                        _errorMessage.value = "Rental not found"
                        return@withContext
                    }
                    
                    // Create or get chat channel with owner for early return
                    if (ownerEmail != null) {
                        try {
                            val channel = com.example.app_jalanin.utils.ChatHelper.getOrCreateDMChannel(
                                database,
                                userEmail,
                                ownerEmail,
                                rentalId, // rentalId
                                rental.status // orderStatus
                            )
                            
                            Log.d("EarlyReturnRequestViewModel", "✅ Chat channel created/updated for early return: ${channel.id} (rentalId: $rentalId)")
                        } catch (e: Exception) {
                            Log.e("EarlyReturnRequestViewModel", "Error creating chat channel: ${e.message}", e)
                        }
                    }
                    
                    // Update rental status to REQUESTED
                    val updatedRental = rental.copy(
                        earlyReturnRequested = true,
                        earlyReturnStatus = "REQUESTED",
                        earlyReturnRequestedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        synced = false
                    )
                    database.rentalDao().update(updatedRental)
                    _rental.value = updatedRental
                    _requestStatus.value = EarlyReturnRequestStatus.WAITING_FOR_OWNER
                    
                    // Sync to Firestore
                    FirestoreRentalSyncManager.syncSingleRental(getApplication(), rentalId)
                    
                    Log.d("EarlyReturnRequestViewModel", "✅ Early return request submitted: $rentalId")
                }
            } catch (e: Exception) {
                Log.e("EarlyReturnRequestViewModel", "Error submitting request: ${e.message}", e)
                _errorMessage.value = "Error submitting request: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh rental status (to check if owner has confirmed)
     */
    fun refreshRentalStatus() {
        _rental.value?.let { rental ->
            loadRental(rental.id)
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * Status of early return request
 */
enum class EarlyReturnRequestStatus {
    NOT_REQUESTED,      // No request yet
    WAITING_FOR_OWNER,  // Request submitted, waiting for owner confirmation
    OWNER_CONFIRMED,    // Owner confirmed and set return location
    IN_PROGRESS,        // Return process started
    COMPLETED,          // Return completed
    CANCELLED           // Request cancelled
}

