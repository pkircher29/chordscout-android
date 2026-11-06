package com.paulkircher.chordscout.model

import java.util.Locale
import kotlin.math.roundToInt

data class ChordSegment(
    val startTime: Float,
    val endTime: Float,
    val chord: String,
    val confidence: Float = 1.0f,
) {
    val duration: Float
        get() = maxOf(0.0f, endTime - startTime)

    val formattedStartTime: String
        get() = formatTimestamp(startTime)

    val formattedEndTime: String
        get() = formatTimestamp(endTime)

    companion object {
        fun formatTimestamp(seconds: Float, includeMs: Boolean = true): String {
            val clamped = maxOf(0.0f, seconds)
            return if (includeMs) {
                val totalTenths = (clamped * 10.0f).roundToInt()
                val minutes = totalTenths / 600
                val secs = (totalTenths % 600) / 10.0f
                String.format(Locale.US, "%02d:%04.1f", minutes, secs)
            } else {
                val totalSec = clamped.toInt()
                val minutes = totalSec / 60
                val secs = totalSec % 60
                String.format(Locale.US, "%02d:%02d", minutes, secs)
            }
        }
    }
}
