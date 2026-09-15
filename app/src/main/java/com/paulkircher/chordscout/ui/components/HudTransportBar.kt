package com.paulkircher.chordscout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulkircher.chordscout.model.ChordSegment
import com.paulkircher.chordscout.ui.theme.*

@Composable
fun HudTransportBar(
    currentChord: String,
    nextChord: String?,
    nextCountdownSeconds: Float?,
    keyEstimate: String?,
    tempoBpm: Float?,
    isPlaying: Boolean,
    currentTimeSec: Float,
    totalTimeSec: Float,
    playbackSpeed: Float,
    isLooping: Boolean,
    onTogglePlay: () -> Unit,
    onPrevChord: () -> Unit,
    onNextChord: () -> Unit,
    onToggleLoop: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgPanel)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top HUD Row: Current Chord + Upcoming Preview + Key/BPM
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Current Chord Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF182236))
                    .border(2.dp, AccentCyan, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text("ACTIVE", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = if (currentChord.isEmpty() || currentChord == "N") "—" else currentChord,
                        color = AccentCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            // Upcoming Chord Badge
            if (!nextChord.isNullOrEmpty() && nextChord != "N") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("UPCOMING", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(nextChord, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (nextCountdownSeconds != null && nextCountdownSeconds > 0) {
                        Text("in ${String.format("%.1f", nextCountdownSeconds)}s", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Key / Tempo
            Column(horizontalAlignment = Alignment.End) {
                Text("Key: ${keyEstimate ?: "N/A"}", color = AccentPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${tempoBpm?.toInt() ?: 120} BPM", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${ChordSegment.formatTimestamp(currentTimeSec)} / ${ChordSegment.formatTimestamp(totalTimeSec)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Bottom Controls Row: Prev, Play, Next, Loop, Speeds
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Transport buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevChord) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev Chord", tint = TextPrimary)
                }

                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = BgMain,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPlaying) "PAUSE" else "PLAY", color = BgMain, fontWeight = FontWeight.Black)
                }

                IconButton(onClick = onNextChord) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Chord", tint = TextPrimary)
                }

                IconButton(
                    onClick = onToggleLoop,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (isLooping) AccentAmber else TextMuted
                    ),
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = "Loop Chord")
                }
            }

            // Practice Speeds (0.75x, 1.0x, 1.25x)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.75f, 1.0f, 1.25f).forEach { speed ->
                    val isSelected = Math.abs(playbackSpeed - speed) < 0.05f
                    Surface(
                        onClick = { onSpeedSelected(speed) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E2638),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                "${speed}x",
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
