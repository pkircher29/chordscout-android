package com.paulkircher.chordscout.dsp

import com.paulkircher.chordscout.model.AnalysisResult
import com.paulkircher.chordscout.model.ChordSegment
import com.paulkircher.chordscout.model.SongMetadata
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object ChordAnalyzer {

    val PITCH_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Below this centered-correlation score a frame is "N" (no chord). It is
     * not desktop's 0.35: that was a cosine score, and centered scores run
     * lower (a clean triad is ~0.55, a chord in a full mix often 0.25-0.35).
     * At 0.35 real mixes showed 1-3 s "N" gaps while a chord was playing.
     * At 0.20-0.25 "N" is left to silence and intros, as before.
     */
    const val CONFIDENCE_THRESHOLD = 0.25f

    /**
     * Shared with desktop `analyze_chords`. Lowering them is what makes
     * desktop report changes that are not there.
     */
    const val MIN_SEGMENT_DURATION = 0.10f
    const val SILENCE_RMS_THRESHOLD = 0.015f
    const val CHROMA_MEDIAN_WIDTH = 3

    /**
     * Cost of changing chords, in frame-score units, for the Viterbi decode.
     * A new chord is kept only if, summed over every frame it lasts, it beats
     * the alternative by more than this. A real change keeps winning frame
     * after frame and pays it off quickly, so it lands on the frame where the
     * evidence flips. A near-tie leads by a few hundredths at a time and never
     * does. This one number replaces the old per-frame hold, margin and
     * attack rules, which had to trade missed changes against flicker.
     *
     * The shortest change it keeps is about penalty / lead frames. A clean F
     * after Am leads by ~0.10 a frame (they share two notes), so at 0.9 that
     * change needs ~0.4 s. Lower it for faster changes, raise it for fewer
     * blips; on real mixes 0.7-1.0 all behave, with no cliff in between.
     */
    const val SWITCH_PENALTY = 0.9f

    /**
     * Head start for chords in the song's key on the second decode. The key
     * comes from the first decode's chords, not from raw chroma. Where a
     * chord is clearly played it wins by far more than this. Where the mix
     * is thin every chord scores about the same, and without it the decoder
     * picks out-of-key chords by chance (Cm, F, F#m in a D major song).
     * Zero skips the second pass.
     */
    const val KEY_BONUS = 0.05f

    /**
     * How much a frame's evidence counts, by how clearly it holds a chord.
     * A cleanly played chord scores about [CLEAR_SCORE] and counts in full.
     * A thin passage scores ~0.3 and every chord is a near-tie, so it counts
     * for (score / CLEAR_SCORE)^power: changes there need longer to pay the
     * switch penalty and stop flickering, while clear quick changes still
     * switch at the normal cost. Zero turns weighting off.
     */
    const val CLARITY_POWER = 2f
    private const val CLEAR_SCORE = 0.5f

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
        confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
        minSegmentDuration: Float = MIN_SEGMENT_DURATION,
        silenceRmsThreshold: Float = SILENCE_RMS_THRESHOLD,
        chromaMedianWidth: Int = CHROMA_MEDIAN_WIDTH,
        switchPenalty: Float = SWITCH_PENALTY,
        keyBonus: Float = KEY_BONUS,
        clarityPower: Float = CLARITY_POWER,
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): AnalysisResult {
        val totalSec = pcm.size.toFloat() / sampleRate
        onProgress(0.15f, "Extracting chromagram...")

        // Desktop peak-normalizes before the log chroma. Without this, a quiet
        // phone recording stays in the linear part of log1p and the loudest
        // partial decides the chord.
        peakNormalize(pcm)

        val extractor = ChromaExtractor(sampleRate = sampleRate, windowSize = 2048, hopSize = 1024)
        val chromagram = medianSmoothChroma(extractor.extractChromagram(pcm), chromaMedianWidth)

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

        val templates = buildChordTemplates(includeSevenths)
        val chordNames = templates.keys.toTypedArray()
        val templateVecs = templates.values.map { centered(it) }.toTypedArray()
        val dt = extractor.hopSize.toFloat() / sampleRate

        // Frame scores for every chord plus "N" (last state). N scores the
        // confidence threshold, so it wins where no chord clears it, and owns
        // silent frames outright.
        val numStates = chordNames.size + 1
        val noChord = numStates - 1
        val scores = Array(chromagram.size) { FloatArray(numStates) }
        val weights = FloatArray(chromagram.size) { 1f }
        for (i in chromagram.indices) {
            val frame = scores[i]
            val energy = frameRms(pcm, extractor.frameCenter(i) - extractor.windowSize / 2, extractor.windowSize)
            if (energy < silenceRmsThreshold) {
                frame.fill(-1f)
                frame[noChord] = 1f
                continue
            }
            val chroma = centered(chromagram[i])
            for (c in templateVecs.indices) {
                val tVec = templateVecs[c]
                var dot = 0.0f
                for (p in 0 until 12) dot += chroma[p] * tVec[p]
                frame[c] = dot
            }
            frame[noChord] = confidenceThreshold
            var best = 0f
            for (c in templateVecs.indices) best = max(best, frame[c])
            weights[i] = min(1f, (best / CLEAR_SCORE).pow(clarityPower))
        }

        var path = viterbi(scores, weights, switchPenalty)

        // Second pass: find the key the first pass's chords live in, give
        // those chords a head start, and decode again.
        val songKey = keyFromChords(path, chordNames)
        if (songKey != null && keyBonus > 0f) {
            val inKey = BooleanArray(chordNames.size) { isDiatonic(chordNames[it], songKey.first) }
            for (frame in scores) {
                if (frame[noChord] == 1f) continue // silent
                for (c in chordNames.indices) if (inKey[c]) frame[c] += keyBonus
            }
            path = viterbi(scores, weights, switchPenalty)
        }

        val rawSegments = ArrayList<ChordSegment>(chromagram.size)
        for (i in chromagram.indices) {
            val tStart = i * dt
            val tEnd = min(totalSec, tStart + dt)
            val state = path[i]
            if (state == noChord) {
                rawSegments.add(ChordSegment(tStart, tEnd, "N", 1.0f))
            } else {
                // Centered scores run lower than cosine: a clean triad is ~0.55.
                val conf = max(0.1f, min(1.0f, (scores[i][state] - 0.2f) / 0.5f))
                rawSegments.add(ChordSegment(tStart, tEnd, chordNames[state], conf))
            }
        }

        onProgress(0.85f, "Consolidating segments...")
        val smoothed = mergeAndSmooth(rawSegments, totalSec, minSegmentDuration = minSegmentDuration)

        onProgress(1.0f, "Done")
        val metadata = SongMetadata(
            fileName = fileName,
            durationSeconds = totalSec,
            sampleRate = sampleRate,
            tempoBpm = 120f,
            estimatedKey = songKey?.let { keyName(it.first, it.second) } ?: estimatedKey,
        )
        return AnalysisResult(metadata, smoothed)
    }

    /**
     * Mean-removed, unit-length copy. Log-compressed constant-Q chroma has a
     * high floor in every bin, so plain cosine puts C at 0.62 and Cm at 0.59
     * on a clean C triad. Centering both sides scores the shape instead of
     * the floor (Pearson correlation) and spreads those apart.
     */
    /** Root pitch class and minor flag of a chord name ("F#m7" -> 6, true). */
    private fun parseChord(name: String): Pair<Int, Boolean>? {
        val rootName = if (name.length > 1 && name[1] == '#') name.substring(0, 2) else name.substring(0, 1)
        val root = PITCH_NAMES.indexOf(rootName)
        if (root < 0) return null
        return root to name.substring(rootName.length).startsWith("m")
    }

    /** True if [chord] is I, IV, V (major) or ii, iii, vi (minor) of [tonic] major. */
    fun isDiatonic(chord: String, tonic: Int): Boolean {
        val (root, minor) = parseChord(chord) ?: return false
        val degree = (root - tonic + 12) % 12
        return if (minor) degree == 2 || degree == 4 || degree == 9 else degree == 0 || degree == 5 || degree == 7
    }

    /**
     * Major-key tonic whose diatonic chords cover the most decoded frames,
     * and whether the song sits on its relative minor (vi clearly outlasts I).
     * Null when fewer than two seconds of chords were found.
     */
    fun keyFromChords(path: IntArray, chordNames: Array<String>): Pair<Int, Boolean>? {
        val frames = IntArray(chordNames.size)
        for (state in path) if (state < chordNames.size) frames[state]++
        if (frames.sum() < 43) return null
        var bestTonic = 0
        var bestCover = -1
        for (tonic in 0 until 12) {
            var cover = 0
            for (c in chordNames.indices) if (isDiatonic(chordNames[c], tonic)) cover += frames[c]
            // A tie (keys a fifth apart share four chords) goes to the key
            // whose I or vi chord is actually heard more.
            cover = cover * 4 + frames[chordNames.indexOf(PITCH_NAMES[tonic])] +
                frames[chordNames.indexOf("${PITCH_NAMES[(tonic + 9) % 12]}m")]
            if (cover > bestCover) {
                bestCover = cover
                bestTonic = tonic
            }
        }
        val major = frames[chordNames.indexOf(PITCH_NAMES[bestTonic])]
        val relMinor = frames[chordNames.indexOf("${PITCH_NAMES[(bestTonic + 9) % 12]}m")]
        // Only the name depends on this (both share the same chords), and a
        // near-even split is usually a major song visiting vi.
        return bestTonic to (relMinor > major * 5 / 4)
    }

    private fun keyName(tonic: Int, minor: Boolean): String =
        if (minor) "${PITCH_NAMES[(tonic + 9) % 12]} minor" else "${PITCH_NAMES[tonic]} major"

    private fun centered(vec: FloatArray): FloatArray {
        val mean = vec.sum() / vec.size
        return normalize(FloatArray(vec.size) { vec[it] - mean })
    }

    /**
     * Highest-scoring state path when every change costs [penalty]. Staying is
     * free and all changes cost the same, so each step only needs the best
     * previous state, not a full transition matrix: O(frames x states).
     */
    private fun viterbi(scores: Array<FloatArray>, weights: FloatArray, penalty: Float): IntArray {
        val frames = scores.size
        val states = scores[0].size
        val back = Array(frames) { IntArray(states) }
        var total = FloatArray(states) { scores[0][it] * weights[0] }
        var next = FloatArray(states)
        for (t in 1 until frames) {
            var bestPrev = 0
            for (s in 1 until states) if (total[s] > total[bestPrev]) bestPrev = s
            val switchTotal = total[bestPrev] - penalty
            val frame = scores[t]
            val w = weights[t]
            for (s in 0 until states) {
                if (total[s] >= switchTotal) {
                    next[s] = total[s] + w * frame[s]
                    back[t][s] = s
                } else {
                    next[s] = switchTotal + w * frame[s]
                    back[t][s] = bestPrev
                }
            }
            val swap = total
            total = next
            next = swap
        }
        val path = IntArray(frames)
        var state = 0
        for (s in 1 until states) if (total[s] > total[state]) state = s
        for (t in frames - 1 downTo 0) {
            path[t] = state
            state = back[t][state]
        }
        return path
    }

    private fun peakNormalize(pcm: FloatArray) {
        var peak = 0f
        for (sample in pcm) {
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
        }
        if (peak < 1e-5f || abs(peak - 1f) < 1e-4f) return
        val gain = 1f / peak
        for (i in pcm.indices) pcm[i] *= gain
    }

    private fun frameRms(pcm: FloatArray, start: Int, length: Int): Float {
        val offset = max(0, start)
        val end = min(pcm.size, start + length)
        if (end <= offset) return 0f
        var sum = 0.0
        for (i in offset until end) {
            val sample = pcm[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / (end - offset)).toFloat()
    }

    /**
     * Time-median each chroma bin, then re-normalize. Width 3 matches desktop
     * and removes single-frame label flips (~46 ms) without bridging a real
     * change. Width 1 disables it.
     */
    private fun medianSmoothChroma(frames: List<FloatArray>, width: Int): List<FloatArray> {
        if (width <= 1 || frames.size < 3) return frames
        val radius = width / 2
        val span = radius * 2 + 1
        val window = FloatArray(span)
        val smoothedFrames = ArrayList<FloatArray>(frames.size)
        for (i in frames.indices) {
            val smoothed = FloatArray(12)
            for (bin in 0 until 12) {
                for (k in -radius..radius) {
                    val index = (i + k).coerceIn(0, frames.lastIndex)
                    window[k + radius] = frames[index][bin]
                }
                window.sort()
                smoothed[bin] = window[span / 2]
            }
            var normSq = 0f
            for (bin in 0 until 12) normSq += smoothed[bin] * smoothed[bin]
            val norm = sqrt(normSq)
            if (norm > 1e-6f) {
                for (bin in 0 until 12) smoothed[bin] /= norm
            }
            smoothedFrames.add(smoothed)
        }
        return smoothedFrames
    }

    private fun mergeAndSmooth(
        raw: List<ChordSegment>,
        totalDuration: Float,
        minSegmentDuration: Float = MIN_SEGMENT_DURATION,
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
