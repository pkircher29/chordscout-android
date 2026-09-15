package com.paulkircher.chordscout

import com.paulkircher.chordscout.dsp.ChordAnalyzer
import com.paulkircher.chordscout.dsp.ChromaExtractor
import com.paulkircher.chordscout.model.GuitarChordLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

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
}
