package com.paulkircher.chordscout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paulkircher.chordscout.model.ChordSegment
import com.paulkircher.chordscout.ui.theme.*

@Composable
fun LeadSheetGrid(
    segments: List<ChordSegment>,
    activeSegmentIndex: Int,
    onSegmentSelected: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(segments) { idx, seg ->
            val isActive = idx == activeSegmentIndex
            val baseCol = getChordColor(seg.chord)

            Box(
                modifier = Modifier
                    .aspectRatio(1.3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) Color(0xFF1E283D) else Color(0xFF151A27))
                    .border(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) AccentAmber else BorderSubtle,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSegmentSelected(idx, seg.startTime) }
                    .padding(6.dp),
            ) {
                // Top timestamp
                Text(
                    text = ChordSegment.formatTimestamp(seg.startTime, includeMs = false),
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart),
                )

                // Center Chord
                Text(
                    text = seg.chord,
                    color = if (isActive) AccentAmber else TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Bottom confidence
                Text(
                    text = "${(seg.confidence * 100).toInt()}%",
                    color = TextMuted,
                    fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}
