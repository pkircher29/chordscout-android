package com.paulkircher.chordscout.ui.theme

import androidx.compose.ui.graphics.Color

val BgMain = Color(0xFF0B0D14)
val BgPanel = Color(0xFF131722)
val BgCard = Color(0xFF1A202F)
val BgCardHover = Color(0xFF232B3E)
val BorderSubtle = Color(0xFF1E2638)
val BorderMedium = Color(0xFF2E3952)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val AccentCyan = Color(0xFF38BDF8)
val AccentAmber = Color(0xFFF59E0B)
val AccentGold = Color(0xFFFBBF24)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFF43F5E)
val AccentPurple = Color(0xFFA855F7)

fun getChordColor(chord: String): Color {
    if (chord.isEmpty() || chord == "N") return Color(0xFF1E2330)
    val isMinor = chord.contains("m") && !chord.contains("maj")

    return when {
        chord.startsWith("C#") || chord.startsWith("Db") -> Color(0xFF3B82F6)
        chord.startsWith("C") -> Color(0xFF0284C7)
        chord.startsWith("D#") || chord.startsWith("Eb") -> Color(0xFF8B5CF6)
        chord.startsWith("D") -> Color(0xFF6366F1)
        chord.startsWith("E") -> Color(0xFFEC4899)
        chord.startsWith("F#") || chord.startsWith("Gb") -> Color(0xFFF97316)
        chord.startsWith("F") -> Color(0xFFEF4444)
        chord.startsWith("G#") || chord.startsWith("Ab") -> Color(0xFF84CC16)
        chord.startsWith("G") -> Color(0xFFEAB308)
        chord.startsWith("A#") || chord.startsWith("Bb") -> Color(0xFF14B8A6)
        chord.startsWith("A") -> Color(0xFF10B981)
        chord.startsWith("B") -> Color(0xFF06B6D4)
        else -> if (isMinor) AccentPurple else AccentCyan
    }
}
