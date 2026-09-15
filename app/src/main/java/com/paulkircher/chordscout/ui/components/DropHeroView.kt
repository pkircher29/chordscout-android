package com.paulkircher.chordscout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulkircher.chordscout.ui.theme.*

@Composable
fun DropHeroView(
    onSelectFile: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131722))
                .border(2.dp, BorderMedium, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pick Icon Emoji
            Text("🎸", fontSize = 48.sp)

            Text(
                "Select Audio File",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )

            Text(
                "Extract time-aligned guitar chords, visual fretboard fingerings, and practice tools from local MP3, WAV, FLAC, or M4A audio files.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )

            // Formats row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("MP3", "WAV", "FLAC", "M4A", "OGG").forEach { fmt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2638))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(fmt, color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Button(
                onClick = onSelectFile,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                Text("📁 Choose Audio File", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onTryDemo,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                Text("⚡ Try Demo Song (C-G-Am-F)", fontWeight = FontWeight.Bold)
            }
        }
    }
}
