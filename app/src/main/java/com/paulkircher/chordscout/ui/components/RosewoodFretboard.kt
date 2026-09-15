package com.paulkircher.chordscout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulkircher.chordscout.dsp.GuitarSynth
import com.paulkircher.chordscout.model.GuitarChord
import com.paulkircher.chordscout.model.GuitarChordLibrary
import com.paulkircher.chordscout.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun RosewoodFretboard(
    chordName: String,
    capoFret: Int = 0,
    modifier: Modifier = Modifier,
) {
    val chord = GuitarChordLibrary.getChord(chordName)
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgPanel),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Chord Name & Strum Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (chordName.isEmpty() || chordName == "N") "—" else chordName,
                        color = AccentCyan,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (chord != null) "Tab: ${chord.stringDisplay}" else "No active chord",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Button(
                    onClick = {
                        chord?.let {
                            scope.launch { GuitarSynth.playChord(it) }
                        }
                    },
                    enabled = chord != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("🔊 Strum", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas Fretboard
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF131722)),
            ) {
                val padX = 40.dp.toPx()
                val marginTop = 35.dp.toPx()
                val marginBottom = 20.dp.toPx()
                val boardW = size.width - (padX * 2)
                val boardH = size.height - marginTop - marginBottom

                val numFrets = 4
                val numStrings = 6
                val dx = boardW / (numStrings - 1)
                val dy = boardH / numFrets
                val baseFret = chord?.baseFret ?: 1

                // 1. Draw Rosewood block
                drawRoundRect(
                    color = Color(0xFF1F1714),
                    topLeft = Offset(padX - 8, marginTop - 4),
                    size = Size(boardW + 16, boardH + 8),
                    cornerRadius = CornerRadius(8f, 8f),
                )

                // 2. Pearloid inlays
                for (f in 1..numFrets) {
                    val absFret = baseFret + f - 1
                    if (absFret in listOf(3, 5, 7, 9)) {
                        val cy = marginTop + ((f - 0.5f) * dy)
                        val cx = padX + (boardW / 2f)
                        drawCircle(
                            color = Color(0xFFCBD5E1),
                            radius = 5.dp.toPx(),
                            center = Offset(cx, cy),
                        )
                    }
                }

                // 3. Nut or normal fret line
                if (baseFret == 1) {
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(padX - 4, marginTop - 6),
                        size = Size(boardW + 8, 6.dp.toPx()),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                } else {
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(padX, marginTop),
                        end = Offset(padX + boardW, marginTop),
                        strokeWidth = 2.dp.toPx(),
                    )
                }

                // 4. Horizontal Fret Wires
                for (i in 1..numFrets) {
                    val y = marginTop + (i * dy)
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(padX, y),
                        end = Offset(padX + boardW, y),
                        strokeWidth = 1.8f.dp.toPx(),
                    )
                }

                // 5. Vertical Strings
                for (s in 0 until numStrings) {
                    val x = padX + (s * dx)
                    val color = if (s < 3) Color(0xFFD97706) else Color(0xFFE2E8F0)
                    val width = if (s < 3) 3.dp.toPx() - (s * 0.5f.dp.toPx()) else 1.5f.dp.toPx()
                    drawLine(
                        color = color,
                        start = Offset(x, marginTop - 4),
                        end = Offset(x, marginTop + boardH + 4),
                        strokeWidth = width,
                    )
                }

                // 6. Draw Open, Muted, or Finger Dots
                chord?.frets?.forEachIndexed { s, fret ->
                    val x = padX + (s * dx)
                    when (fret) {
                        -1 -> {
                            // Muted 'X'
                            drawCircle(
                                color = Color(0xFF4C1D24),
                                radius = 7.dp.toPx(),
                                center = Offset(x, marginTop - 18.dp.toPx()),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "x",
                                x,
                                marginTop - 14.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#F43F5E")
                                    textSize = 28f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isFakeBoldText = true
                                }
                            )
                        }
                        0 -> {
                            // Open 'O'
                            drawCircle(
                                color = Color(0xFF133629),
                                radius = 7.dp.toPx(),
                                center = Offset(x, marginTop - 18.dp.toPx()),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "o",
                                x,
                                marginTop - 14.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#10B981")
                                    textSize = 28f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isFakeBoldText = true
                                }
                            )
                        }
                        else -> {
                            // Fret dot
                            val relFret = fret - baseFret + 1
                            if (relFret in 1..numFrets) {
                                val cy = marginTop + ((relFret - 0.5f) * dy)
                                drawCircle(
                                    color = Color(0xFF38BDF8),
                                    radius = 9.dp.toPx(),
                                    center = Offset(x, cy),
                                )
                                val finger = if (s < chord.fingers.size) chord.fingers[s] else 0
                                if (finger > 0) {
                                    drawContext.canvas.nativeCanvas.drawText(
                                        finger.toString(),
                                        x,
                                        cy + 4.dp.toPx(),
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 28f
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            isFakeBoldText = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
