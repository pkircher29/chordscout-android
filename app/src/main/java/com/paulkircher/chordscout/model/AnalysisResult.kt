package com.paulkircher.chordscout.model

data class SongMetadata(
    val fileName: String,
    val durationSeconds: Float,
    val sampleRate: Int = 22050,
    val estimatedKey: String? = null,
)

data class AnalysisResult(
    val metadata: SongMetadata,
    val segments: List<ChordSegment>,
) {
    fun getChordAtTime(seconds: Float): ChordSegment? {
        for (seg in segments) {
            if (seconds >= seg.startTime && seconds < seg.endTime) {
                return seg
            }
        }
        return segments.lastOrNull()
    }
}
