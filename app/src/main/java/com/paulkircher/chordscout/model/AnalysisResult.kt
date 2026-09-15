package com.paulkircher.chordscout.model

import kotlinx.serialization.Serializable

@Serializable
data class SongMetadata(
    val fileName: String,
    val durationSeconds: Float,
    val sampleRate: Int = 22050,
    val tempoBpm: Float? = null,
    val estimatedKey: String? = null,
    val tuningOffsetCents: Float = 0.0f,
)

@Serializable
data class AnalysisResult(
    val metadata: SongMetadata,
    val segments: List<ChordSegment>,
    val disclaimer: String = "Initial algorithmic analysis — verify against your ear.",
) {
    fun getChordAtTime(seconds: Float): ChordSegment? {
        for (seg in segments) {
            if (seconds >= seg.startTime && seconds < seg.endTime) {
                return seg
            }
        }
        return segments.lastOrNull()
    }

    fun uniqueChords(): List<String> {
        return segments.map { it.chord }.filter { it.isNotEmpty() && it != "N" }.distinct()
    }
}
