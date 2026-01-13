package com.example.app_jalanin.ui.owner

import android.app.Application
import android.location.Address
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_jalanin.data.AppDatabase
import com.example.app_jalanin.data.local.entity.Rental
import com.example.app_jalanin.data.model.CameraMode
import com.example.app_jalanin.data.model.DriverEarlyReturnUiState
import com.example.app_jalanin.data.model.LocationInputSource
import com.example.app_jalanin.data.remote.FirestoreRentalSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * ViewModel for Driver Early Return confirmation flow
 */
class DriverEarlyReturnViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val geocoder = Geocoder(application, Locale.getDefault())
    
    private val _uiState = MutableStateFlow<DriverEarlyReturnUiState?>(null)
    val uiState: StateFlow<DriverEarlyReturnUiState?> = _uiState.asStateFlow()
    
    private val _rental = MutableStateFlow<Rental?>(null)
    val rental: StateFlow<Rental?> = _rental.asStateFlow()
    
    /**
     * Load rental data
     */
    fun loadRental(rentalId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rentalData = database.rentalDao().getRentalById(rentalId)
                _rental.value = rentalData
                
                // Initialize UI state
                val initialLocation = if (rentalData != null && rentalData.returnLocationLat != null && rentalData.returnLocationLon != null) {
                    GeoPoint(rentalData.returnLocationLat, rentalData.returnLocationLon)
                } else {
                    // Default to delivery location or Jakarta
                    if (rentalData != null && rentalData.deliveryLat != 0.0 && rentalData.deliveryLon != 0.0) {
                        GeoPoint(rentalData.deliveryLat, rentalData.deliveryLon)
                    } else {
                        GeoPoint(-6.2088, 106.8456) // Jakarta default
                    }
                }
                
                val initialAddress = rentalData?.returnAddress ?: ""
                
                _uiState.value = DriverEarlyReturnUiState(
                    rentalId = rentalId,
                    selectedLocation = initialLocation,
                    selectedAddress = initialAddress,
                    isValidLocation = initialLocation != null && initialAddress.isNotBlank()
                )
            } catch (e: Exception) {
                android.util.Log.e("DriverEarlyReturnViewModel", "Error loading rental: ${e.message}", e)
            }
        }
    }
    
    /**
     * Update search query and trigger search
     * When user starts typing manually, stop GPS tracking and switch to MANUAL_INPUT mode
     */
    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        val wasInGpsMode = currentState?.cameraMode == CameraMode.FOLLOW_GPS
        
        _uiState.update { state ->
            state?.copy(
                searchQuery = query,
                inputSource = if (query.isNotBlank()) LocationInputSource.MANUAL else LocationInputSource.NONE,
                cameraMode = if (query.isNotBlank()) {
                    // Switch to MANUAL_INPUT when user starts typing
                    CameraMode.MANUAL_INPUT
                } else {
                    // Return to IDLE when query is cleared
                    CameraMode.IDLE
                },
                isGpsTracking = if (query.isNotBlank()) false else currentState?.isGpsTracking ?: false
            )
        }
        
        // Trigger search if query is not empty
        if (query.isNotBlank()) {
            searchLocation(query)
        } else {
            _uiState.update { it?.copy(searchSuggestions = emptyList()) }
        }
    }
    
    /**
     * Search for locations using geocoder
     */
    private fun searchLocation(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it?.copy(isSearching = true) }
                
                val searchQueryWithCountry = if (!query.contains("Indonesia", ignoreCase = true)) {
                    "$query, Indonesia"
                } else {
                    query
                }
                
                val addresses = try {
                    geocoder.getFromLocationName(searchQueryWithCountry, 5)
                } catch (e: Exception) {
                    // Retry without Indonesia if first attempt fails
                    android.util.Log.d("DriverEarlyReturnViewModel", "🔄 Retrying without Indonesia...")
                    geocoder.getFromLocationName(query, 5)
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state?.copy(
                            searchSuggestions = addresses ?: emptyList(),
                            isSearching = false
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverEarlyReturnViewModel", "❌ Search error: ${e.message}", e)
                _uiState.update { state ->
                    state?.copy(
                        searchSuggestions = emptyList(),
                        isSearching = false
                    )
                }
            }
        }
    }
    
    /**
     * Select location from search suggestion
     */
    fun selectLocationFromSuggestion(address: Address) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val location = GeoPoint(address.latitude, address.longitude)
                val fullAddress = buildString {
                    for (i in 0 until address.maxAddressLineIndex) {
                        address.getAddressLine(i)?.let {
                            if (isNotEmpty()) append(", ")
                            append(it)
                        }
                    }
                }.ifEmpty { 
                    "${address.latitude}, ${address.longitude}"
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state?.copy(
                            selectedLocation = location,
                            selectedAddress = fullAddress,
                            searchQuery = fullAddress,
                            searchSuggestions = emptyList(),
                            inputSource = LocationInputSource.MANUAL,
                            cameraMode = CameraMode.MANUAL_INPUT,
                            isValidLocation = true
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DriverEarlyReturnViewModel", "Error selecting location: ${e.message}", e)
            }
        }
    }
    
    /**
     * Update location from GPS
     */
    fun updateLocationFromGps(location: GeoPoint, address: String) {
        val currentState = _uiState.value
        // Only update if we're in FOLLOW_GPS mode
        if (currentState?.cameraMode == CameraMode.FOLLOW_GPS) {
            _uiState.update { state ->
                state?.copy(
                    selectedLocation = location,
                    selectedAddress = address,
                    currentGpsLocation = location,
                    inputSource = LocationInputSource.GPS,
                    isValidLocation = true
                )
            }
        }
    }
    
    /**
     * Update location from map tap
     */
    fun updateLocationFromMapTap(location: GeoPoint, address: String) {
        _uiState.update { state ->
            state?.copy(
                selectedLocation = location,
                selectedAddress = address,
                inputSource = LocationInputSource.MANUAL,
                cameraMode = CameraMode.MANUAL_INPUT,
                isValidLocation = true
            )
        }
    }
    
    /**
     * Start GPS tracking mode
     */
    fun startGpsTracking() {
        _uiState.update { state ->
            state?.copy(
                cameraMode = CameraMode.FOLLOW_GPS,
                inputSource = LocationInputSource.GPS,
                isGpsTracking = true
            )
        }
    }
    
    /**
     * Stop GPS tracking mode
     */
    fun stopGpsTracking() {
        _uiState.update { state ->
            state?.copy(
                cameraMode = CameraMode.IDLE,
                isGpsTracking = false
            )
        }
    }
    
    /**
     * Set return location and confirm
     */
    fun confirmReturnLocation() {
        val state = _uiState.value
        val rentalData = _rental.value
        
        if (state == null || rentalData == null || !state.canConfirm()) {
            android.util.Log.e("DriverEarlyReturnViewModel", "Cannot confirm: invalid state")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedRental = rentalData.copy(
                    returnLocationLat = state.selectedLocation?.latitude,
                    returnLocationLon = state.selectedLocation?.longitude,
                    returnAddress = state.selectedAddress,
                    earlyReturnStatus = "CONFIRMED",
                    updatedAt = System.currentTimeMillis(),
                    synced = false
                )
                
                database.rentalDao().update(updatedRental)
                
                // Sync to Firestore
                FirestoreRentalSyncManager.syncSingleRental(getApplication(), rentalData.id)
                
                android.util.Log.d("DriverEarlyReturnViewModel", "✅ Return location confirmed")
            } catch (e: Exception) {
                android.util.Log.e("DriverEarlyReturnViewModel", "Error confirming location: ${e.message}", e)
            }
        }
    }
}

