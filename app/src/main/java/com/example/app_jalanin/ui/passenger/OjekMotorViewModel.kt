package com.example.app_jalanin.ui.passenger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class BookingState(
    val pickupLocation: String = "Lokasi saat ini",
    val destination: String = "",
    val fare: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val bookingConfirmed: Boolean = false
)

class OjekMotorViewModel(app: Application) : AndroidViewModel(app) {
    private val _bookingState = MutableStateFlow(BookingState())
    val bookingState: StateFlow<BookingState> = _bookingState

    fun updateDestination(destination: String) {
        viewModelScope.launch {
            _bookingState.value = _bookingState.value.copy(
                destination = destination,
                fare = if (destination.isNotEmpty()) calculateFare(destination) else null
            )
        }
    }

    fun updatePickupLocation(location: String) {
        _bookingState.value = _bookingState.value.copy(pickupLocation = location)
    }

    fun confirmBooking() {
        viewModelScope.launch {
            _bookingState.value = _bookingState.value.copy(isLoading = true)

            try {
                // Simulate booking process
                kotlinx.coroutines.delay(1000)

                // TODO: Implement actual booking logic with repository
                // Save booking to database
                // Send notification to drivers

                _bookingState.value = _bookingState.value.copy(
                    isLoading = false,
                    bookingConfirmed = true
                )

                android.util.Log.d("OjekMotorViewModel", "✅ Booking confirmed")
            } catch (e: Exception) {
                _bookingState.value = _bookingState.value.copy(
                    isLoading = false,
                    errorMessage = "Gagal membuat pesanan: ${e.message}"
                )
                android.util.Log.e("OjekMotorViewModel", "❌ Booking failed: ${e.message}")
            }
        }
    }

    fun resetBooking() {
        _bookingState.value = BookingState()
    }

    private fun calculateFare(destination: String): String {
        // Simple fare calculation
        // In real app, this would use distance API (Google Maps Distance Matrix)
        val baseFare = 5000
        val perKmRate = 2000
        val estimatedKm = (destination.length / 10).coerceAtLeast(1) // Dummy calculation
        val totalFare = baseFare + (perKmRate * estimatedKm)

        return "Rp ${String.format(Locale.forLanguageTag("id-ID"), "%,d", totalFare).replace(',', '.')}"
    }
}

