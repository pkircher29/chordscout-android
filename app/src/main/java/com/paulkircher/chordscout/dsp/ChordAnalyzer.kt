package com.paulkircher.chordscout.dsp

import com.paulkircher.chordscout.model.AnalysisResult
import com.paulkircher.chordscout.model.ChordSegment
import com.paulkircher.chordscout.model.SongMetadata
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ChordAnalyzer {

    val PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Krumhansl-Schmuckler key profiles
    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    private fun normalize(vec: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (v in vec) sumSq += v * v
        val norm = sqrt(sumSq)
        if (norm > 1e-6f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec
    }

    fun buildChordTemplates(includeSevenths: Boolean = false): Map<String, FloatArray> {
        val templates = LinkedHashMap<String, FloatArray>()

        for (i in 0 until 12) {
            val root = PITCH_NAMES[i]

            // Major Triad (Root, Major 3rd +4, 5th +7)
            val maj = FloatArray(12)
            maj[i] = 1.0f
            maj[(i + 4) % 12] = 0.8f
            maj[(i + 7) % 12] = 0.8f
            templates[root] = normalize(maj)

            // Minor Triad (Root, Minor 3rd +3, 5th +7)
            val minT = FloatArray(12)
            minT[i] = 1.0f
            minT[(i + 3) % 12] = 0.8f
            minT[(i + 7) % 12] = 0.8f
            templates["${root}m"] = normalize(minT)

            if (includeSevenths) {
                // Dominant 7th
                val dom7 = FloatArray(12)
                dom7[i] = 1.0f
                dom7[(i + 4) % 12] = 0.75f
                dom7[(i + 7) % 12] = 0.75f
                dom7[(i + 10) % 12] = 0.65f
                templates["${root}7"] = normalize(dom7)

                // Minor 7th
                val m7 = FloatArray(12)
                m7[i] = 1.0f
                m7[(i + 3) % 12] = 0.75f
                m7[(i + 7) % 12] = 0.75f
                m7[(i + 10) % 12] = 0.65f
                templates["${root}m7"] = normalize(m7)
            }
        }
        return templates
    }

    fun estimateKey(chromaMean: FloatArray): String {
        var normSq = 0.0f
        for (v in chromaMean) normSq += v * v
        if (normSq < 1e-6f) return "Unknown"

        val normVec = normalize(chromaMean.clone())
        val normMaj = normalize(MAJOR_PROFILE.clone())
        val normMin = normalize(MINOR_PROFILE.clone())

        var bestScore = -1.0f
        var bestKey = "C major"

        for (shift in 0 until 12) {
            // Major
            var scoreMaj = 0.0f
            for (p in 0 until 12) {
                scoreMaj += normVec[p] * normMaj[(p - shift + 12) % 12]
            }
            if (scoreMaj > bestScore) {
                bestScore = scoreMaj
                bestKey = "${PITCH_NAMES[shift]} major"
            }

            // Minor
            var scoreMin = 0.0f
            for (p in 0 until 12) {
                scoreMin += normVec[p] * normMin[(p - shift + 12) % 12]
            }
            if (scoreMin > bestScore) {
                bestScore = scoreMin
                bestKey = "${PITCH_NAMES[shift]} minor"
            }
        }
        return bestKey
    }

    fun analyze(
        pcm: FloatArray,
        sampleRate: Int = 22050,
        fileName: String = "Track",
        includeSevenths: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): AnalysisResult {
        val totalSec = pcm.size.toFloat() / sampleRate
        onProgress(0.15f, "Extracting chromagram...")

        val extractor = ChromaExtractor(sampleRate = sampleRate, windowSize = 2048, hopSize = 1024)
        val chromagram = extractor.extractChromagram(pcm)

        if (chromagram.isEmpty()) {
            val emptyMeta = SongMetadata(fileName, totalSec, sampleRate)
            return AnalysisResult(emptyMeta, listOf(ChordSegment(0f, totalSec, "N", 1.0f)))
        }

        onProgress(0.50f, "Estimating song key & matching chords...")

        // Compute global mean chroma for key estimation
        val globalChroma = FloatArray(12)
        for (vec in chromagram) {
            for (i in 0 until 12) globalChroma[i] += vec[i]
        }
        val estimatedKey = estimateKey(globalChroma)

        // Template correlation
        val templates = buildChordTemplates(includeSevenths)
        val templateEntries = templates.entries.toList()

        val rawSegments = ArrayList<ChordSegment>(chromagram.size)
        val dt = 1024f / sampleRate

        for (i in chromagram.indices) {
            val tStart = i * dt
            val tEnd = min(totalSec, tStart + dt)
            val chroma = chromagram[i]

            var bestScore = -1.0f
            var bestChord = "N"

            for (entry in templateEntries) {
                var dot = 0.0f
                val tVec = entry.value
                for (p in 0 until 12) {
                    dot += chroma[p] * tVec[p]
                }
                if (dot > bestScore) {
                    bestScore = dot
                    bestChord = entry.key
                }
            }

            val finalChord = if (bestScore < 0.35f) "N" else bestChord
            val conf = max(0.1f, min(1.0f, (bestScore - 0.2f) / 0.8f))
            rawSegments.add(ChordSegment(tStart, tEnd, finalChord, conf))
        }

        onProgress(0.85f, "Consolidating segments...")
        val smoothed = mergeAndSmooth(rawSegments, totalSec, minSegmentDuration = 0.1f)

        onProgress(1.0f, "Done")
        val metadata = SongMetadata(
            fileName = fileName,
            durationSeconds = totalSec,
            sampleRate = sampleRate,
            tempoBpm = 120f,
            estimatedKey = estimatedKey,
        )
        return AnalysisResult(metadata, smoothed)
    }

    private fun mergeAndSmooth(
        raw: List<ChordSegment>,
        totalDuration: Float,
        minSegmentDuration: Float = 0.4f,
    ): List<ChordSegment> {
        if (raw.isEmpty()) return listOf(ChordSegment(0f, totalDuration, "N", 1.0f))

        // 1. Merge adjacent identical chords
        val merged = ArrayList<ChordSegment>()
        var current = raw[0].copy()

        for (i in 1 until raw.size) {
            val next = raw[i]
            if (next.chord == current.chord) {
                current = current.copy(endTime = next.endTime)
            } else {
                merged.add(current)
                current = next.copy()
            }
        }
        merged.add(current)

        // 2. Absorb micro segments
        val cleaned = ArrayList<ChordSegment>()
        var i = 0
        while (i < merged.size) {
            val seg = merged[i]
            if (seg.duration < minSegmentDuration && merged.size > 1) {
                if (cleaned.isNotEmpty()) {
                    val prev = cleaned.removeAt(cleaned.size - 1)
                    cleaned.add(prev.copy(endTime = seg.endTime))
                } else if (i + 1 < merged.size) {
                    merged[i + 1] = merged[i + 1].copy(startTime = seg.startTime)
                } else {
                    cleaned.add(seg)
                }
            } else {
                cleaned.add(seg)
            }
            i++
        }

        // 3. Re-merge adjacent identical formed by absorption
        val finalSegments = ArrayList<ChordSegment>()
        if (cleaned.isNotEmpty()) {
            var cur = cleaned[0]
            for (j in 1 until cleaned.size) {
                val nxt = cleaned[j]
                if (nxt.chord == cur.chord) {
                    cur = cur.copy(endTime = nxt.endTime)
                } else {
                    finalSegments.add(cur)
                    cur = nxt
                }
            }
            finalSegments.add(cur)
        }

        // 4. Enforce exact bounds [0, totalDuration]
        if (finalSegments.isNotEmpty()) {
            finalSegments[0] = finalSegments[0].copy(startTime = 0f)
            val last = finalSegments[finalSegments.size - 1]
            finalSegments[finalSegments.size - 1] = last.copy(endTime = max(last.endTime, totalDuration))
        }

        return finalSegments
    }
}
