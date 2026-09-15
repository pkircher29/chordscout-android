package com.paulkircher.chordscout.model

import kotlinx.serialization.Serializable

@Serializable
data class ChordSegment(
    val startTime: Float,
    val endTime: Float,
    var chord: String,
    val confidence: Float = 1.0f,
    var isUserEdited: Boolean = false,
) {
    val duration: Float
        get() = maxOf(0.0f, endTime - startTime)

    val formattedStartTime: String
        get() = formatTimestamp(startTime)

    val formattedEndTime: String
        get() = formatTimestamp(endTime)

    companion object {
        fun formatTimestamp(seconds: Float, includeMs: Boolean = true): String {
            val totalSec = maxOf(0.0f, seconds)
            val minutes = (totalSec / 60).toInt()
            val secs = totalSec % 60
            return if (includeMs) {
                String.format("%02d:%04.1f", minutes, secs)
            } else {
                String.format("%02d:%02d", minutes, secs.toInt())
            }
        }
    }
}
