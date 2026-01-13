package com.example.app_jalanin.data.model

import org.osmdroid.util.GeoPoint

/**
 * Camera mode for map control
 */
enum class CameraMode {
    IDLE,           // Camera is idle, no automatic movement
    FOLLOW_GPS,     // Camera follows GPS location updates
    MANUAL_INPUT    // Camera is controlled by manual input (search/typing)
}

/**
 * Location input source
 */
enum class LocationInputSource {
    NONE,           // No input source active
    GPS,            // GPS-based location
    MANUAL          // Manual text input
}

/**
 * UI state for Driver Early Return confirmation screen
 */
data class DriverEarlyReturnUiState(
    val rentalId: String,
    val selectedLocation: GeoPoint? = null,
    val selectedAddress: String = "",
    val searchQuery: String = "",
    val searchSuggestions: List<android.location.Address> = emptyList(),
    val isSearching: Boolean = false,
    val currentGpsLocation: GeoPoint? = null,
    val cameraMode: CameraMode = CameraMode.IDLE,
    val inputSource: LocationInputSource = LocationInputSource.NONE,
    val isGpsTracking: Boolean = false,
    val isSettingLocation: Boolean = false,
    val isValidLocation: Boolean = false // True when location and address are set
) {
    /**
     * Check if location is valid for confirmation
     */
    fun canConfirm(): Boolean {
        return selectedLocation != null && selectedAddress.isNotBlank()
    }
}

