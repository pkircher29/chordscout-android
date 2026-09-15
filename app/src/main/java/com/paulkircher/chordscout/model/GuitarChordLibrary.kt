package com.paulkircher.chordscout.model

object GuitarChordLibrary {

    private val CHORDS = mapOf(
        // Major chords
        "C" to GuitarChord("C", intArrayOf(-1, 3, 2, 0, 1, 0), intArrayOf(0, 3, 2, 0, 1, 0), 1),
        "C#" to GuitarChord("C#", intArrayOf(-1, 4, 6, 6, 6, 4), intArrayOf(0, 1, 2, 3, 4, 1), 4),
        "Db" to GuitarChord("Db", intArrayOf(-1, 4, 6, 6, 6, 4), intArrayOf(0, 1, 2, 3, 4, 1), 4),
        "D" to GuitarChord("D", intArrayOf(-1, -1, 0, 2, 3, 2), intArrayOf(0, 0, 0, 1, 3, 2), 1),
        "D#" to GuitarChord("D#", intArrayOf(-1, 6, 8, 8, 8, 6), intArrayOf(0, 1, 2, 3, 4, 1), 6),
        "Eb" to GuitarChord("Eb", intArrayOf(-1, 6, 8, 8, 8, 6), intArrayOf(0, 1, 2, 3, 4, 1), 6),
        "E" to GuitarChord("E", intArrayOf(0, 2, 2, 1, 0, 0), intArrayOf(0, 2, 3, 1, 0, 0), 1),
        "F" to GuitarChord("F", intArrayOf(1, 3, 3, 2, 1, 1), intArrayOf(1, 3, 4, 2, 1, 1), 1),
        "F#" to GuitarChord("F#", intArrayOf(2, 4, 4, 3, 2, 2), intArrayOf(1, 3, 4, 2, 1, 1), 2),
        "Gb" to GuitarChord("Gb", intArrayOf(2, 4, 4, 3, 2, 2), intArrayOf(1, 3, 4, 2, 1, 1), 2),
        "G" to GuitarChord("G", intArrayOf(3, 2, 0, 0, 0, 3), intArrayOf(2, 1, 0, 0, 0, 3), 1),
        "G#" to GuitarChord("G#", intArrayOf(4, 6, 6, 5, 4, 4), intArrayOf(1, 3, 4, 2, 1, 1), 4),
        "Ab" to GuitarChord("Ab", intArrayOf(4, 6, 6, 5, 4, 4), intArrayOf(1, 3, 4, 2, 1, 1), 4),
        "A" to GuitarChord("A", intArrayOf(-1, 0, 2, 2, 2, 0), intArrayOf(0, 0, 1, 2, 3, 0), 1),
        "A#" to GuitarChord("A#", intArrayOf(-1, 1, 3, 3, 3, 1), intArrayOf(0, 1, 2, 3, 4, 1), 1),
        "Bb" to GuitarChord("Bb", intArrayOf(-1, 1, 3, 3, 3, 1), intArrayOf(0, 1, 2, 3, 4, 1), 1),
        "B" to GuitarChord("B", intArrayOf(-1, 2, 4, 4, 4, 2), intArrayOf(0, 1, 2, 3, 4, 1), 2),

        // Minor chords
        "Cm" to GuitarChord("Cm", intArrayOf(-1, 3, 5, 5, 4, 3), intArrayOf(0, 1, 3, 4, 2, 1), 3),
        "C#m" to GuitarChord("C#m", intArrayOf(-1, 4, 6, 6, 5, 4), intArrayOf(0, 1, 3, 4, 2, 1), 4),
        "Dbm" to GuitarChord("Dbm", intArrayOf(-1, 4, 6, 6, 5, 4), intArrayOf(0, 1, 3, 4, 2, 1), 4),
        "Dm" to GuitarChord("Dm", intArrayOf(-1, -1, 0, 2, 3, 1), intArrayOf(0, 0, 0, 2, 3, 1), 1),
        "D#m" to GuitarChord("D#m", intArrayOf(-1, 6, 8, 8, 7, 6), intArrayOf(0, 1, 3, 4, 2, 1), 6),
        "Ebm" to GuitarChord("Ebm", intArrayOf(-1, 6, 8, 8, 7, 6), intArrayOf(0, 1, 3, 4, 2, 1), 6),
        "Em" to GuitarChord("Em", intArrayOf(0, 2, 2, 0, 0, 0), intArrayOf(0, 2, 3, 0, 0, 0), 1),
        "Fm" to GuitarChord("Fm", intArrayOf(1, 3, 3, 1, 1, 1), intArrayOf(1, 3, 4, 1, 1, 1), 1),
        "F#m" to GuitarChord("F#m", intArrayOf(2, 4, 4, 2, 2, 2), intArrayOf(1, 3, 4, 1, 1, 1), 2),
        "Gbm" to GuitarChord("Gbm", intArrayOf(2, 4, 4, 2, 2, 2), intArrayOf(1, 3, 4, 1, 1, 1), 2),
        "Gm" to GuitarChord("Gm", intArrayOf(3, 5, 5, 3, 3, 3), intArrayOf(1, 3, 4, 1, 1, 1), 3),
        "G#m" to GuitarChord("G#m", intArrayOf(4, 6, 6, 4, 4, 4), intArrayOf(1, 3, 4, 1, 1, 1), 4),
        "Abm" to GuitarChord("Abm", intArrayOf(4, 6, 6, 4, 4, 4), intArrayOf(1, 3, 4, 1, 1, 1), 4),
        "Am" to GuitarChord("Am", intArrayOf(-1, 0, 2, 2, 1, 0), intArrayOf(0, 0, 2, 3, 1, 0), 1),
        "A#m" to GuitarChord("A#m", intArrayOf(-1, 1, 3, 3, 2, 1), intArrayOf(0, 1, 3, 4, 2, 1), 1),
        "Bbm" to GuitarChord("Bbm", intArrayOf(-1, 1, 3, 3, 2, 1), intArrayOf(0, 1, 3, 4, 2, 1), 1),
        "Bm" to GuitarChord("Bm", intArrayOf(-1, 2, 4, 4, 3, 2), intArrayOf(0, 1, 3, 4, 2, 1), 2),

        // 7th chords
        "C7" to GuitarChord("C7", intArrayOf(-1, 3, 2, 3, 1, 0), intArrayOf(0, 3, 2, 4, 1, 0), 1),
        "D7" to GuitarChord("D7", intArrayOf(-1, -1, 0, 2, 1, 2), intArrayOf(0, 0, 0, 2, 1, 3), 1),
        "E7" to GuitarChord("E7", intArrayOf(0, 2, 0, 1, 0, 0), intArrayOf(0, 2, 0, 1, 0, 0), 1),
        "F7" to GuitarChord("F7", intArrayOf(1, 3, 1, 2, 1, 1), intArrayOf(1, 3, 1, 2, 1, 1), 1),
        "G7" to GuitarChord("G7", intArrayOf(3, 2, 0, 0, 0, 1), intArrayOf(3, 2, 0, 0, 0, 1), 1),
        "A7" to GuitarChord("A7", intArrayOf(-1, 0, 2, 0, 2, 0), intArrayOf(0, 0, 2, 0, 3, 0), 1),
        "B7" to GuitarChord("B7", intArrayOf(-1, 2, 1, 2, 0, 2), intArrayOf(0, 2, 1, 3, 0, 4), 1),

        // Minor 7th chords
        "Am7" to GuitarChord("Am7", intArrayOf(-1, 0, 2, 0, 1, 0), intArrayOf(0, 0, 2, 0, 1, 0), 1),
        "Em7" to GuitarChord("Em7", intArrayOf(0, 2, 0, 0, 0, 0), intArrayOf(0, 1, 0, 0, 0, 0), 1),
        "Dm7" to GuitarChord("Dm7", intArrayOf(-1, -1, 0, 2, 1, 1), intArrayOf(0, 0, 0, 2, 1, 1), 1),
        "Bm7" to GuitarChord("Bm7", intArrayOf(-1, 2, 0, 2, 0, 2), intArrayOf(0, 1, 0, 2, 0, 3), 2),
    )

    val allChordNames: List<String> = CHORDS.keys.toList()

    fun getChord(chordName: String): GuitarChord? {
        val trimmed = chordName.trim()
        if (trimmed == "N" || trimmed.isEmpty()) return null
        return CHORDS[trimmed] ?: run {
            // Enharmonic fallback
            val flatToSharp = mapOf("Db" to "C#", "Eb" to "D#", "Gb" to "F#", "Ab" to "G#", "Bb" to "A#")
            var mapped = trimmed
            for ((flat, sharp) in flatToSharp) {
                if (trimmed.startsWith(flat)) {
                    mapped = sharp + trimmed.removePrefix(flat)
                    break
                }
            }
            CHORDS[mapped]
        }
    }
}
