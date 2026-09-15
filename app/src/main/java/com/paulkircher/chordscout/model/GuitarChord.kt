package com.paulkircher.chordscout.model

data class GuitarChord(
    val name: String,
    val frets: IntArray, // 6 items: low E (idx 0) to high e (idx 5). -1=muted, 0=open, >0=fret
    val fingers: IntArray = intArrayOf(0, 0, 0, 0, 0, 0),
    val baseFret: Int = 1,
) {
    val stringDisplay: String
        get() = frets.joinToString(" ") { if (it == -1) "x" else it.toString() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GuitarChord
        return name == other.name && frets.contentEquals(other.frets) && baseFret == other.baseFret
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + frets.contentHashCode()
        result = 31 * result + baseFret
        return result
    }
}
