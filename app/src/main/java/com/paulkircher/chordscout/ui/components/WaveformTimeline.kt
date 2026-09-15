package com.paulkircher.chordscout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.paulkircher.chordscout.model.ChordSegment
import com.paulkircher.chordscout.ui.theme.BorderSubtle
import com.paulkircher.chordscout.ui.theme.getChordColor

@Composable
fun WaveformTimeline(
    segments: List<ChordSegment>,
    durationSeconds: Float,
    currentPlaybackSeconds: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1017))
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .pointerInput(durationSeconds) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction * durationSeconds)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val dur = maxOf(0.1f, durationSeconds)

            // 1. Draw Chord Segment Blocks
            for (seg in segments) {
                val x1 = (seg.startTime / dur) * w
                val x2 = (seg.endTime / dur) * w
                val segW = maxOf(1f, x2 - x1)
                val isActive = currentPlaybackSeconds >= seg.startTime && currentPlaybackSeconds < seg.endTime

                val baseColor = getChordColor(seg.chord)
                val fillColor = baseColor.copy(alpha = if (isActive) 0.55f else 0.25f)

                drawRect(
                    color = fillColor,
                    topLeft = Offset(x1, 0f),
                    size = Size(segW, h),
                )

                // Divider line
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(x1, 0f),
                    end = Offset(x1, h),
                    strokeWidth = 1.dp.toPx(),
                )

                // Chord text
                if (segW > 25.dp.toPx() && seg.chord != "N") {
                    drawContext.canvas.nativeCanvas.drawText(
                        seg.chord,
                        x1 + (segW / 2f),
                        (h / 2f) + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                    )
                }
            }

            // 2. Playhead Laser
            val playX = (currentPlaybackSeconds / dur) * w
            drawLine(
                color = Color(0xFFF59E0B),
                start = Offset(playX, 0f),
                end = Offset(playX, h),
                strokeWidth = 2.5f.dp.toPx(),
            )
        }
    }
}
