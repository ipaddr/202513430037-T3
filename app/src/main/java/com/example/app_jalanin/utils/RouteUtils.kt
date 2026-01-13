package com.example.app_jalanin.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

/**
 * Data class to hold route information from OSRM API
 */
data class RouteInfo(
    val distance: Double,        // Distance in kilometers
    val duration: Double = 0.0,  // Duration in seconds
    val geometry: List<GeoPoint>? = null,  // Route geometry for drawing
    val timestamp: Long = System.currentTimeMillis()  // Cache timestamp
)

/**
 * Simple cache to avoid redundant API calls
 * Cache expires after 5 minutes to prevent memory bloat
 */
private val routeCache = mutableMapOf<String, RouteInfo>()
private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
private const val MAX_CACHE_SIZE = 20 // Reduced from 50 to prevent memory issues

/**
 * Generate cache key from two points
 */
private fun getCacheKey(start: GeoPoint, end: GeoPoint): String {
    return "${start.latitude},${start.longitude}-${end.latitude},${end.longitude}"
}

/**
 * Calculate straight-line distance between two points (Haversine formula)
 */
fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
    return point1.distanceToAsDouble(point2) / 1000.0 // Convert to km
}

/**
 * Get route information (distance, duration, geometry) in ONE optimized API call
 * Uses OSRM routing API with caching and expiration for better performance
 */
suspend fun getRouteInfo(
    startPoint: GeoPoint,
    endPoint: GeoPoint
): RouteInfo {
    // Check cache first and validate expiry
    val cacheKey = getCacheKey(startPoint, endPoint)
    routeCache[cacheKey]?.let { cached ->
        val age = System.currentTimeMillis() - cached.timestamp
        if (age < CACHE_EXPIRY_MS) {
            android.util.Log.d("RouteUtils", "✅ Using cached route info (age: ${age/1000}s)")
            return cached
        } else {
            android.util.Log.d("RouteUtils", "🗑️ Cache expired, fetching new route")
            routeCache.remove(cacheKey)
        }
    }

    // Clean old cache entries if size limit exceeded
    if (routeCache.size >= MAX_CACHE_SIZE) {
        val now = System.currentTimeMillis()
        routeCache.entries.removeIf { (_, info) ->
            (now - info.timestamp) > CACHE_EXPIRY_MS
        }

        // If still too large, remove oldest entries
        if (routeCache.size >= MAX_CACHE_SIZE) {
            val toRemove = routeCache.size - (MAX_CACHE_SIZE / 2)
            routeCache.entries.sortedBy { it.value.timestamp }
                .take(toRemove)
                .forEach { routeCache.remove(it.key) }
            android.util.Log.d("RouteUtils", "🧹 Cleaned cache: removed $toRemove old entries")
        }
    }

    return withContext(Dispatchers.IO) {
        try {
            // Single API call with full geometry
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${startPoint.longitude},${startPoint.latitude};" +
                    "${endPoint.longitude},${endPoint.latitude}?overview=full&geometries=geojson"

            android.util.Log.d("RouteUtils", "🌐 API call: ${url.take(100)}...")

            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }

                // Parse distance, duration, and geometry from same response
                var distance = 0.0
                var duration = 0.0
                val coordinates = mutableListOf<GeoPoint>()

                // 1. Extract distance
                val distanceRegex = "\"distance\":(\\d+\\.?\\d*)".toRegex()
                val distanceMatch = distanceRegex.find(response)
                if (distanceMatch != null) {
                    val distanceInMeters = distanceMatch.groupValues[1].toDouble()
                    distance = distanceInMeters / 1000.0  // Convert to km
                }

                // 2. Extract duration
                val durationRegex = "\"duration\":(\\d+\\.?\\d*)".toRegex()
                val durationMatch = durationRegex.find(response)
                if (durationMatch != null) {
                    duration = durationMatch.groupValues[1].toDouble()  // seconds
                }

                // 3. Extract geometry coordinates
                val startIdx = response.indexOf("\"coordinates\":")
                if (startIdx != -1) {
                    val coordsStart = response.indexOf("[[", startIdx)
                    val coordsEnd = response.indexOf("]]", coordsStart)

                    if (coordsStart != -1 && coordsEnd != -1) {
                        val coordsString = response.substring(coordsStart + 2, coordsEnd)
                        val pairs = coordsString.split("],[")

                        for (pair in pairs) {
                            val cleanPair = pair.replace("[", "").replace("]", "").trim()
                            val values = cleanPair.split(",")

                            if (values.size >= 2) {
                                try {
                                    val lon = values[0].trim().toDouble()
                                    val lat = values[1].trim().toDouble()
                                    coordinates.add(GeoPoint(lat, lon))
                                } catch (_: Exception) {
                                    // Skip invalid coordinates
                                }
                            }
                        }
                    }
                }

                android.util.Log.d("RouteUtils", "✅ Got: $distance km, $duration s, ${coordinates.size} points")

                val routeInfo = RouteInfo(
                    distance = distance,
                    duration = duration,
                    geometry = if (coordinates.isNotEmpty()) coordinates else null,
                    timestamp = System.currentTimeMillis()
                )

                // Cache the result (cleanup already done at start)
                routeCache[cacheKey] = routeInfo


                return@withContext routeInfo
            }

            // Fallback to straight-line distance with no geometry
            val fallbackDistance = calculateDistance(startPoint, endPoint)
            android.util.Log.d("RouteUtils", "⚠️ Fallback: $fallbackDistance km")

            val routeInfo = RouteInfo(
                distance = fallbackDistance,
                duration = 0.0,
                geometry = null,
                timestamp = System.currentTimeMillis()
            )
            routeCache[cacheKey] = routeInfo
            routeInfo

        } catch (e: Exception) {
            android.util.Log.e("RouteUtils", "❌ Error: ${e.message}")
            // Fallback to straight-line distance
            val fallbackDistance = calculateDistance(startPoint, endPoint)
            val routeInfo = RouteInfo(
                distance = fallbackDistance,
                duration = 0.0,
                geometry = null,
                timestamp = System.currentTimeMillis()
            )
            routeCache[cacheKey] = routeInfo
            routeInfo
        }
    }
}

