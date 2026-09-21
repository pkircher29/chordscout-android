package com.paulkircher.chordscout

import com.paulkircher.chordscout.dsp.ChordAnalyzer
import com.paulkircher.chordscout.dsp.ChromaExtractor
import com.paulkircher.chordscout.model.GuitarChordLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ChordAnalyzerTest {

    @Test
    fun testBuildTemplates() {
        val templates = ChordAnalyzer.buildChordTemplates(includeSevenths = false)
        assertEquals(24, templates.size)
        assertTrue(templates.containsKey("C"))
        assertTrue(templates.containsKey("Am"))
        assertTrue(templates.containsKey("G"))

        val cVec = templates["C"]!!
        assertEquals(12, cVec.size)
        assertTrue(cVec[0] > 0) // C root
        assertTrue(cVec[4] > 0) // E (major 3rd)
        assertTrue(cVec[7] > 0) // G (5th)
    }

    @Test
    fun testGuitarChordLibrary() {
        val c = GuitarChordLibrary.getChord("C")
        assertNotNull(c)
        assertEquals("x 3 2 0 1 0", c!!.stringDisplay)

        val am = GuitarChordLibrary.getChord("Am")
        assertNotNull(am)
        assertEquals("x 0 2 2 1 0", am!!.stringDisplay)

        // Enharmonic Db -> C#
        val db = GuitarChordLibrary.getChord("Db")
        assertNotNull(db)
        assertEquals(4, db!!.baseFret)
    }

    @Test
    fun testAnalyzeSyntheticProgression() {
        val sr = 22050
        val duration = 4.0f
        val numSamples = (sr * duration).toInt()
        val pcm = FloatArray(numSamples)
        val half = numSamples / 2

        // First 2 seconds: C major (C4=261.63, E4=329.63, G4=392.00)
        for (i in 0 until half) {
            val t = i.toFloat() / sr
            pcm[i] = (sin(2.0 * PI * 261.63 * t) + sin(2.0 * PI * 329.63 * t) + sin(2.0 * PI * 392.00 * t)).toFloat() / 3f
        }

        // Second 2 seconds: G major (G3=196.00, B3=246.94, D4=293.66)
        for (i in half until numSamples) {
            val t = i.toFloat() / sr
            pcm[i] = (sin(2.0 * PI * 196.00 * t) + sin(2.0 * PI * 246.94 * t) + sin(2.0 * PI * 293.66 * t)).toFloat() / 3f
        }

        val result = ChordAnalyzer.analyze(pcm, sampleRate = sr, fileName = "synthetic.wav")

        assertTrue("Progression should have detected segments", result.segments.isNotEmpty())
        val chords = result.segments.map { it.chord }
        assertTrue("Progression should include C", chords.contains("C"))
        assertTrue("Progression should include G", chords.contains("G"))
    }

    @Test
    fun testChromaExtractorIncludesExactWindow() {
        val windowSize = 2048
        val sampleRate = 22050
        val pcm = FloatArray(windowSize) { sampleIndex ->
            sin(2.0 * PI * 261.63 * sampleIndex / sampleRate).toFloat()
        }

        val chromagram = ChromaExtractor(sampleRate = sampleRate, windowSize = windowSize, hopSize = 1024)
            .extractChromagram(pcm)

        assertEquals(1, chromagram.size)
    }

    @Test
    fun testAnalyzePreservesQuickChordChanges() {
        val sampleRate = 22050
        val chordDuration = 0.25f
        val notesByChord = listOf(
            "C" to doubleArrayOf(261.63, 329.63, 392.00),
            "G" to doubleArrayOf(196.00, 246.94, 293.66),
            "Am" to doubleArrayOf(220.00, 261.63, 329.63),
            "F" to doubleArrayOf(174.61, 220.00, 261.63),
        )
        val samplesPerChord = (sampleRate * chordDuration).toInt()
        val pcm = FloatArray(samplesPerChord * notesByChord.size)

        notesByChord.forEachIndexed { chordIndex, (_, notes) ->
            for (sampleIndex in 0 until samplesPerChord) {
                val time = sampleIndex.toDouble() / sampleRate
                pcm[chordIndex * samplesPerChord + sampleIndex] =
                    notes.sumOf { frequency -> sin(2.0 * PI * frequency * time) }.toFloat() / notes.size
            }
        }

        val result = ChordAnalyzer.analyze(pcm, sampleRate = sampleRate)

        assertEquals(notesByChord.map { it.first }, result.segments.map { it.chord })
    }

    @Test
    fun testChromaOfC4PeaksOnC() {
        val sampleRate = 22050
        val pcm = FloatArray(sampleRate / 2) { sampleIndex ->
            sin(2.0 * PI * 261.63 * sampleIndex / sampleRate).toFloat()
        }

        val chromagram = ChromaExtractor(sampleRate = sampleRate).extractChromagram(pcm)
        val mean = FloatArray(12)
        for (frame in chromagram) {
            for (pitch in 0 until 12) mean[pitch] += frame[pitch]
        }
        val loudest = mean.indices.maxBy { mean[it] }
        assertEquals(0, loudest)
    }

    @Test
    fun testHarmonicOpenFStaysF() {
        // Bright open F used to tie with Am: high FFT bins pile the 5th harmonic
        // (a major third) into the minor triad, the label flickers, and the
        // smoother deletes the real chord.
        val sampleRate = 22050
        val pcm = synthGuitarChord(
            midiNotes = intArrayOf(41, 45, 48, 53, 57, 60),
            sampleCount = (sampleRate * 1.6f).toInt(),
            sampleRate = sampleRate,
            detuneCents = 15.0,
        )
        val result = ChordAnalyzer.analyze(pcm, sampleRate = sampleRate)
        val voiced = result.segments.filter { it.chord != "N" }

        assertEquals(listOf("F"), voiced.map { it.chord })
        val covered = voiced.sumOf { it.duration.toDouble() }
        assertTrue("F should cover the strum, covered=$covered", covered > 1.2)
    }

    @Test
    fun testHarmonicProgressionKeepsEachChange() {
        val sampleRate = 22050
        val chordSeconds = 0.55f
        val notes = listOf(
            "C" to intArrayOf(48, 52, 55, 60, 64),
            "G" to intArrayOf(43, 47, 50, 55, 59, 67),
            "Am" to intArrayOf(45, 52, 57, 60, 64),
            "F" to intArrayOf(41, 45, 48, 53, 57, 60),
        )
        val pcm = concatChords(notes.map { it.second }, sampleRate, chordSeconds, detuneCents = 12.0)
        val result = ChordAnalyzer.analyze(pcm, sampleRate = sampleRate)

        assertEquals(notes.map { it.first }, result.segments.map { it.chord }.filter { it != "N" })
    }

    @Test
    fun testShortBurstDoesNotCreateAChange() {
        val sampleRate = 22050
        val pcm = synthGuitarChord(
            midiNotes = intArrayOf(48, 52, 55, 60, 64),
            sampleCount = (sampleRate * 2.0f).toInt(),
            sampleRate = sampleRate,
            detuneCents = 8.0,
        )
        val burst = synthGuitarChord(
            midiNotes = intArrayOf(43, 47, 50, 55, 59, 67),
            sampleCount = (sampleRate * 0.05f).toInt(),
            sampleRate = sampleRate,
            detuneCents = 8.0,
        )
        val at = sampleRate
        for (i in burst.indices) pcm[at + i] = burst[i]

        val result = ChordAnalyzer.analyze(pcm, sampleRate = sampleRate)
        assertEquals(listOf("C"), result.segments.map { it.chord }.filter { it != "N" })
    }

    @Test
    fun testGuitarProgressionIgnoresLowBassAndHighPad() {
        // A full mix: the guitar changes about twice a second, a louder bass
        // walks roots that are not the guitar's, and a high pad holds a
        // different triad. The chart should stay on the guitar changes.
        val sampleRate = 22050
        val chordSeconds = 0.55f
        val guitar = listOf(
            "C" to intArrayOf(48, 52, 55, 60, 64),
            "G" to intArrayOf(43, 47, 50, 55, 59, 67),
            "Am" to intArrayOf(45, 52, 57, 60, 64),
            "F" to intArrayOf(41, 45, 48, 53, 57, 60),
        )
        val guitarPcm = concatChords(guitar.map { it.second }, sampleRate, chordSeconds, detuneCents = 10.0)
        val samplesPerChord = (sampleRate * chordSeconds).toInt()
        // Bass roots chosen to pull a different triad (F, Bb, D, A).
        val bassRoots = intArrayOf(29, 34, 26, 33)
        val pad = synthChordTones(
            midiNotes = intArrayOf(85, 89, 92), // C#6 E6 F#6, a high triad, not C/G/Am/F
            sampleCount = guitarPcm.size,
            sampleRate = sampleRate,
            harmonicRolloff = 1.2,
            maxHarmonic = 4,
            decay = 0.15,
        )
        val bassGain = 1.3f
        val padGain = 1.6f
        val mix = guitarPcm.copyOf()
        bassRoots.forEachIndexed { index, midi ->
            val bass = synthHarmonicTone(
                midi = midi,
                sampleCount = samplesPerChord,
                sampleRate = sampleRate,
                harmonicRolloff = 1.0,
                maxHarmonic = 12,
                decay = 0.8,
            )
            addScaled(mix, bass, index * samplesPerChord, gain = bassGain)
        }
        addScaled(mix, pad, 0, gain = padGain)

        val guitarChords = guitar.map { it.first }
        val withoutFocus = voicedChords(mix, focusLow = 36, focusHigh = 96)
        val withFocus = voicedChords(mix, focusLow = ChromaExtractor.FOCUS_MIDI_LOW, focusHigh = ChromaExtractor.FOCUS_MIDI_HIGH)
        assertNotEquals(
            "flat chroma should follow the bass or the pad, got $withoutFocus",
            guitarChords,
            withoutFocus,
        )
        assertEquals(
            "guitar-band weighting should keep $guitarChords, got $withFocus (flat was $withoutFocus)",
            guitarChords,
            withFocus,
        )
    }

    @Test
    fun testPickClicksDoNotSplitARingingChord() {
        val sampleRate = 22050
        val seconds = 2.0f
        val pcm = synthGuitarChord(
            midiNotes = intArrayOf(48, 52, 55, 60, 64),
            sampleCount = (sampleRate * seconds).toInt(),
            sampleRate = sampleRate,
            detuneCents = 8.0,
        )
        addPickClicks(pcm, sampleRate, spacingSeconds = 0.28f)

        val voiced = ChordAnalyzer.analyze(pcm, sampleRate = sampleRate)
            .segments.map { it.chord }.filter { it != "N" }
        assertEquals(listOf("C"), voiced)
    }

    @Test
    fun testGuitarFocusKeepsOpenChords() {
        val sampleRate = 22050
        val held = listOf(
            "E" to intArrayOf(40, 47, 52, 56, 59, 64),
            "A" to intArrayOf(45, 52, 57, 61, 64),
            "G" to intArrayOf(43, 47, 50, 55, 59, 67),
            "F" to intArrayOf(41, 45, 48, 53, 57, 60),
        )
        held.forEach { (name, notes) ->
            val pcm = synthGuitarChord(
                midiNotes = notes,
                sampleCount = (sampleRate * 1.2f).toInt(),
                sampleRate = sampleRate,
                detuneCents = 10.0,
            )
            val voiced = ChordAnalyzer.analyze(pcm, sampleRate = sampleRate)
                .segments.map { it.chord }.filter { it != "N" }
            assertEquals(name, listOf(name), voiced)
        }
    }

    @Test
    fun testGuitarFocusWeightTapersBassRegister() {
        val lowBass = ChromaExtractor.guitarFocusWeight(36)
        val openLowE = ChromaExtractor.guitarFocusWeight(40)
        val chordBody = ChromaExtractor.guitarFocusWeight(60)
        val topOfBank = ChromaExtractor.guitarFocusWeight(96)
        assertTrue(chordBody == 1f)
        assertTrue(topOfBank == 1f)
        assertTrue(lowBass < openLowE)
        assertTrue(openLowE < chordBody)
    }

    @Test
    fun testConfidenceThresholdCanRejectEverything() {
        val sampleRate = 22050
        val pcm = FloatArray(sampleRate) { sampleIndex ->
            sin(2.0 * PI * 261.63 * sampleIndex / sampleRate).toFloat()
        }
        val result = ChordAnalyzer.analyze(
            pcm,
            sampleRate = sampleRate,
            confidenceThreshold = 0.99f,
        )
        assertEquals(listOf("N"), result.segments.map { it.chord })
    }

    private fun concatChords(
        chords: List<IntArray>,
        sampleRate: Int,
        chordSeconds: Float,
        detuneCents: Double,
    ): FloatArray {
        val samplesPerChord = (sampleRate * chordSeconds).toInt()
        val pcm = FloatArray(samplesPerChord * chords.size)
        chords.forEachIndexed { index, notes ->
            val chord = synthGuitarChord(notes, samplesPerChord, sampleRate, detuneCents + index)
            chord.copyInto(pcm, index * samplesPerChord)
        }
        return pcm
    }

    private fun synthGuitarChord(
        midiNotes: IntArray,
        sampleCount: Int,
        sampleRate: Int,
        detuneCents: Double,
        decay: Double = 1.5,
    ): FloatArray {
        val pcm = FloatArray(sampleCount)
        midiNotes.forEachIndexed { stringIndex, midi ->
            val cents = detuneCents + (stringIndex - 2.5) * 4.0
            val fundamental = 440.0 * 2.0.pow((midi - 69) / 12.0) * 2.0.pow(cents / 1200.0)
            val delay = (0.012 * stringIndex * sampleRate).toInt()
            for (sampleIndex in delay until sampleCount) {
                val time = sampleIndex.toDouble() / sampleRate
                val envelope = exp(-decay * time)
                var partial = 0.0
                var harmonic = 1
                while (harmonic <= 28) {
                    val frequency = fundamental * harmonic * sqrt(1.0 + 0.00012 * harmonic * harmonic)
                    if (frequency >= sampleRate / 2.0 - 30) break
                    val phase = stringIndex * 0.7 + harmonic * 0.35
                    partial += harmonic.toDouble().pow(-0.45) * sin(2.0 * PI * frequency * time + phase)
                    harmonic++
                }
                pcm[sampleIndex] += (partial * envelope).toFloat()
            }
        }
        return pcm
    }

    private fun synthHarmonicTone(
        midi: Int,
        sampleCount: Int,
        sampleRate: Int,
        harmonicRolloff: Double,
        maxHarmonic: Int,
        decay: Double,
    ): FloatArray = synthChordTones(
        midiNotes = intArrayOf(midi),
        sampleCount = sampleCount,
        sampleRate = sampleRate,
        harmonicRolloff = harmonicRolloff,
        maxHarmonic = maxHarmonic,
        decay = decay,
    )

    private fun synthChordTones(
        midiNotes: IntArray,
        sampleCount: Int,
        sampleRate: Int,
        harmonicRolloff: Double,
        maxHarmonic: Int,
        decay: Double,
    ): FloatArray {
        val pcm = FloatArray(sampleCount)
        midiNotes.forEachIndexed { voice, midi ->
            val fundamental = 440.0 * 2.0.pow((midi - 69) / 12.0)
            for (sampleIndex in pcm.indices) {
                val time = sampleIndex.toDouble() / sampleRate
                val envelope = exp(-decay * time)
                var partial = 0.0
                var harmonic = 1
                while (harmonic <= maxHarmonic) {
                    val frequency = fundamental * harmonic
                    if (frequency >= sampleRate / 2.0 - 40) break
                    partial += harmonic.toDouble().pow(-harmonicRolloff) *
                        sin(2.0 * PI * frequency * time + voice * 0.4)
                    harmonic++
                }
                pcm[sampleIndex] += (partial * envelope).toFloat()
            }
        }
        return pcm
    }

    private fun addScaled(dest: FloatArray, source: FloatArray, offset: Int, gain: Float) {
        for (i in source.indices) {
            val at = offset + i
            if (at >= dest.size) break
            dest[at] += source[i] * gain
        }
    }

    private fun addPickClicks(pcm: FloatArray, sampleRate: Int, spacingSeconds: Float) {
        val spacing = (sampleRate * spacingSeconds).toInt().coerceAtLeast(1)
        val click = (sampleRate * 0.004f).toInt()
        var at = spacing / 3
        while (at < pcm.size) {
            for (i in 0 until click) {
                if (at + i >= pcm.size) break
                val env = 1.0 - i.toDouble() / click
                pcm[at + i] += (sin(2.0 * PI * 2400.0 * i / sampleRate) * env * 6.0).toFloat()
            }
            at += spacing
        }
    }

    private fun voicedChords(pcm: FloatArray, focusLow: Int, focusHigh: Int): List<String> {
        return ChordAnalyzer.analyze(
            pcm.copyOf(),
            sampleRate = 22050,
            guitarFocusMidiLow = focusLow,
            guitarFocusMidiHigh = focusHigh,
        ).segments.map { it.chord }.filter { it != "N" }
    }
}
