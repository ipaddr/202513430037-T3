package com.example.app_jalanin.utils

/**
 * Utility untuk parse dan format durasi rental
 * Format: "hari|jam|menit"
 */
object DurationUtils {

    /**
     * Data class untuk menyimpan komponen durasi
     */
    data class DurationComponents(
        val days: Int = 0,
        val hours: Int = 0,
        val minutes: Int = 0
    ) {
        /**
         * Convert ke total milliseconds
         */
        fun toMilliseconds(): Long {
            return (days.toLong() * 24 * 60 * 60 * 1000) +
                   (hours.toLong() * 60 * 60 * 1000) +
                   (minutes.toLong() * 60 * 1000)
        }

        /**
         * Format untuk display: "2 Hari 3 Jam 30 Menit"
         */
        fun toFormattedString(): String {
            return buildString {
                if (days > 0) append("$days Hari ")
                if (hours > 0) append("$hours Jam ")
                if (minutes > 0) append("$minutes Menit")
            }.trim().ifEmpty { "0 Menit" }
        }

        /**
         * Format singkat: "7 Jam" atau "2 Hari"
         */
        fun toShortString(): String {
            return when {
                days > 0 -> "$days Hari"
                hours > 0 -> "$hours Jam"
                minutes > 0 -> "$minutes Menit"
                else -> "0 Menit"
            }
        }

        /**
         * Format database: "hari|jam|menit"
         */
        fun toDatabaseFormat(): String {
            return "$days|$hours|$minutes"
        }
    }

    /**
     * Parse dari input user (misal: "7 Jam", "2 Hari", "1 Minggu")
     * Return DurationComponents
     */
    fun parseUserInput(input: String): DurationComponents {
        val trimmed = input.trim()

        // Parse "X Jam"
        val hourMatch = Regex("""(\d+)\s*Jam""", RegexOption.IGNORE_CASE).find(trimmed)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toIntOrNull() ?: 0
            return DurationComponents(hours = hours)
        }

        // Parse "X Hari"
        val dayMatch = Regex("""(\d+)\s*Hari""", RegexOption.IGNORE_CASE).find(trimmed)
        if (dayMatch != null) {
            val days = dayMatch.groupValues[1].toIntOrNull() ?: 0
            return DurationComponents(days = days)
        }

        // Parse "X Minggu" → convert ke hari
        val weekMatch = Regex("""(\d+)\s*Minggu""", RegexOption.IGNORE_CASE).find(trimmed)
        if (weekMatch != null) {
            val weeks = weekMatch.groupValues[1].toIntOrNull() ?: 0
            return DurationComponents(days = weeks * 7)
        }

        // Parse "X Menit"
        val minuteMatch = Regex("""(\d+)\s*Menit""", RegexOption.IGNORE_CASE).find(trimmed)
        if (minuteMatch != null) {
            val minutes = minuteMatch.groupValues[1].toIntOrNull() ?: 0
            return DurationComponents(minutes = minutes)
        }

        // Default: 1 jam
        android.util.Log.w("DurationUtils", "Could not parse duration: '$input', defaulting to 1 Jam")
        return DurationComponents(hours = 1)
    }

    /**
     * Parse dari database format "hari|jam|menit"
     */
    fun parseDatabaseFormat(dbFormat: String): DurationComponents {
        return try {
            val parts = dbFormat.split("|")
            if (parts.size == 3) {
                DurationComponents(
                    days = parts[0].toIntOrNull() ?: 0,
                    hours = parts[1].toIntOrNull() ?: 0,
                    minutes = parts[2].toIntOrNull() ?: 0
                )
            } else {
                android.util.Log.w("DurationUtils", "Invalid DB format: '$dbFormat'")
                DurationComponents()
            }
        } catch (e: Exception) {
            android.util.Log.e("DurationUtils", "Error parsing DB format: ${e.message}")
            DurationComponents()
        }
    }

    /**
     * Convert milliseconds ke DurationComponents
     */
    fun fromMilliseconds(millis: Long): DurationComponents {
        val totalMinutes = millis / (60 * 1000)
        val days = (totalMinutes / (24 * 60)).toInt()
        val hours = ((totalMinutes % (24 * 60)) / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()

        return DurationComponents(days, hours, minutes)
    }

    /**
     * Format milliseconds ke string readable: "2j 3h 30m"
     */
    fun formatMillisToReadable(millis: Long): String {
        if (millis <= 0) return "0m"

        val totalSeconds = millis / 1000
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        val minutes = (totalSeconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}h ")
            if (hours > 0) append("${hours}j ")
            if (minutes > 0) append("${minutes}m")
        }.trim()
    }

    /**
     * Format untuk countdown timer: "HH:MM:SS"
     */
    fun formatCountdown(millis: Long): String {
        val totalSeconds = kotlin.math.abs(millis) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (millis < 0) {
            // Overdue - tambahkan tanda minus
            "-${String.format("%02d:%02d:%02d", hours, minutes, seconds)}"
        } else {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    /**
     * Calculate end time dari start time + duration
     */
    fun calculateEndTime(startTime: Long, duration: DurationComponents): Long {
        return startTime + duration.toMilliseconds()
    }

    /**
     * Format time for rental countdown (same format as RentalHistoryScreen)
     * Format: "Xd Xh Xm" or "Xh Xm Xs" or "Xm Xs" or "Xs"
     */
    fun formatTime(milliseconds: Long): String {
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(milliseconds)
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(milliseconds) % 24
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

