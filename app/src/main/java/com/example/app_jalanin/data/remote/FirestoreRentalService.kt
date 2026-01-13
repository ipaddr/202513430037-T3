package com.example.app_jalanin.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Service untuk mengelola rental history di Firestore
 */
object FirestoreRentalService {
    private const val TAG = "FirestoreRental"
    private const val COLLECTION_RENTALS = "rentals"

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    /**
     * Data class untuk Rental di Firestore
     */
    data class RentalData(
        val id: String = "",
        val userId: String = "",
        val userEmail: String = "",
        val vehicleId: String = "",
        val vehicleName: String = "",
        val vehicleType: String = "",
        val startDate: Long = 0L,
        val endDate: Long = 0L,
        val durationDays: Int = 0,
        val durationHours: Int = 0,
        val durationMinutes: Int = 0,
        val durationMillis: Long = 0L,
        val totalPrice: Int = 0,
        val status: String = "ACTIVE", // ACTIVE, OVERDUE, COMPLETED, CANCELLED, DELIVERING
        val overtimeFee: Int = 0,
        val isWithDriver: Boolean = false,
        val deliveryAddress: String = "",
        val deliveryLat: Double = 0.0,
        val deliveryLon: Double = 0.0,
        val duration: String = "", // DEPRECATED: kept for backward compatibility
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Simpan rental baru ke Firestore
     */
    suspend fun createRental(rental: RentalData): Result<String> {
        return try {
            val rentalId = rental.id.ifEmpty {
                "RENT_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            }

            val rentalWithId = rental.copy(
                id = rentalId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_RENTALS)
                .document(rentalId)
                .set(rentalWithId)
                .await()

            Log.d(TAG, "✅ Rental created: $rentalId")
            Result.success(rentalId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create rental: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update status rental
     */
    suspend fun updateRentalStatus(
        rentalId: String,
        newStatus: String,
        overtimeFee: Int = 0
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to System.currentTimeMillis()
            )

            if (overtimeFee > 0) {
                updates["overtimeFee"] = overtimeFee
            }

            firestore.collection(COLLECTION_RENTALS)
                .document(rentalId)
                .update(updates)
                .await()

            Log.d(TAG, "✅ Rental status updated: $rentalId -> $newStatus")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update rental status: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ambil semua rental untuk user tertentu
     */
    suspend fun getUserRentals(userId: String): Result<List<RentalData>> {
        return try {
            // Query tanpa orderBy untuk menghindari composite index requirement
            val snapshot = firestore.collection(COLLECTION_RENTALS)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val rentals = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(RentalData::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse rental document: ${doc.id}")
                    null
                }
            }
            // Sort di client-side (descending by createdAt)
            .sortedByDescending { it.createdAt }

            Log.d(TAG, "✅ Fetched ${rentals.size} rentals for user: $userId")
            Result.success(rentals)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch rentals: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ambil rental berdasarkan ID
     */
    suspend fun getRentalById(rentalId: String): Result<RentalData?> {
        return try {
            val snapshot = firestore.collection(COLLECTION_RENTALS)
                .document(rentalId)
                .get()
                .await()

            val rental = snapshot.toObject(RentalData::class.java)

            if (rental != null) {
                Log.d(TAG, "✅ Fetched rental: $rentalId")
            } else {
                Log.d(TAG, "⚠️ Rental not found: $rentalId")
            }

            Result.success(rental)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch rental: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ambil rental aktif user (ACTIVE atau DELIVERING)
     */
    suspend fun getActiveRentals(userId: String): Result<List<RentalData>> {
        return try {
            val snapshot = firestore.collection(COLLECTION_RENTALS)
                .whereEqualTo("userId", userId)
                .whereIn("status", listOf("ACTIVE", "DELIVERING"))
                .get()
                .await()

            val rentals = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(RentalData::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse rental document: ${doc.id}")
                    null
                }
            }

            Log.d(TAG, "✅ Fetched ${rentals.size} active rentals for user: $userId")
            Result.success(rentals)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch active rentals: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Hapus rental (soft delete - update status ke CANCELLED)
     */
    suspend fun cancelRental(rentalId: String): Result<Unit> {
        return updateRentalStatus(rentalId, "CANCELLED")
    }

    /**
     * Complete rental (update status ke COMPLETED)
     */
    suspend fun completeRental(rentalId: String, overtimeFee: Int = 0): Result<Unit> {
        return updateRentalStatus(rentalId, "COMPLETED", overtimeFee)
    }
}

