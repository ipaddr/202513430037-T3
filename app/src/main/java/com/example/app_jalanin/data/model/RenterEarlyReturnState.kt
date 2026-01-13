package com.example.app_jalanin.data.model

import org.osmdroid.util.GeoPoint

/**
 * UI state for Renter Early Return confirmation screen
 */
data class RenterEarlyReturnUiState(
    val rentalId: String,
    val currentGpsLocation: GeoPoint? = null,
    val returnLocation: GeoPoint,
    val returnAddress: String,
    val routeGeometry: List<GeoPoint>? = null,
    val routeDistance: Double = 0.0,
    val routeDuration: Double = 0.0,
    val isGpsTracking: Boolean = false,
    val isRouteCalculated: Boolean = false
)

/**
 * Vehicle animation state during return process
 */
data class VehicleAnimationState(
    val isAnimating: Boolean = false,
    val vehiclePosition: GeoPoint? = null,
    val initialLocation: GeoPoint? = null,
    val progress: Float = 0f, // 0.0 to 1.0
    val traveledRoutePoints: List<GeoPoint> = emptyList(),
    val remainingRoutePoints: List<GeoPoint> = emptyList(),
    val totalDistance: Double = 0.0,
    val remainingDistance: Double = 0.0,
    val estimatedTimeRemaining: Int = 0 // seconds
)

