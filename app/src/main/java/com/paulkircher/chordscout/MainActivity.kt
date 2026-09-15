package com.paulkircher.chordscout

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.paulkircher.chordscout.audio.AudioDecoder
import com.paulkircher.chordscout.dsp.ChordAnalyzer
import com.paulkircher.chordscout.model.AnalysisResult
import com.paulkircher.chordscout.ui.components.*
import com.paulkircher.chordscout.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).build()

        setContent {
            ChordScoutTheme {
                MainScreen(
                    exoPlayer = exoPlayer!!,
                    onShareChords = { text -> shareChordSheet(text) },
                    initialIntent = intent,
                )
            }
        }
    }

    private fun shareChordSheet(text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Chord Progression"))
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    exoPlayer: ExoPlayer,
    onShareChords: (String) -> Unit,
    initialIntent: Intent?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf(0f) }
    var progressStatus by remember { mutableStateOf("") }
    var currentResult by remember { mutableStateOf<AnalysisResult?>(null) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackSec by remember { mutableStateOf(0f) }
    var activeSegmentIndex by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isLoopingChord by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0=Grid, 1=Fretboard

    // Function to analyze a given URI
    fun analyzeUri(uri: Uri, displayName: String) {
        scope.launch {
            try {
                isAnalyzing = true
                analysisProgress = 0.05f
                progressStatus = "Decoding audio..."
                exoPlayer.stop()

                val pcm = AudioDecoder.decodeToMonoPcm(
                    context = context,
                    uri = uri,
                    targetSampleRate = 22050,
                    onProgress = { p ->
                        analysisProgress = p
                        progressStatus = "Decoding audio (${(p * 100).toInt()}%)..."
                    }
                )

                val res = ChordAnalyzer.analyze(
                    pcm = pcm,
                    sampleRate = 22050,
                    fileName = displayName,
                    onProgress = { p, msg ->
                        analysisProgress = p
                        progressStatus = msg
                    }
                )

                currentResult = res
                activeSegmentIndex = 0

                // Prepare ExoPlayer
                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
            } catch (e: Exception) {
                Toast.makeText(context, "Error analyzing audio: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isAnalyzing = false
            }
        }
    }

    // Audio file picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "Selected Audio"
            analyzeUri(it, name)
        }
    }

    // Handle initial intent if app opened via 'Open With'
    LaunchedEffect(initialIntent) {
        val uri = initialIntent?.data ?: initialIntent?.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) {
            analyzeUri(uri, "Shared Audio")
        }
    }

    // Playback loop polling player position
    LaunchedEffect(isPlaying, isLoopingChord) {
        while (true) {
            if (exoPlayer.isPlaying) {
                isPlaying = true
                val sec = exoPlayer.currentPosition / 1000f
                currentPlaybackSec = sec

                currentResult?.let { res ->
                    val seg = res.getChordAtTime(sec)
                    if (seg != null) {
                        val idx = res.segments.indexOf(seg)
                        if (idx >= 0) activeSegmentIndex = idx

                        // Loop check
                        if (isLoopingChord && sec >= seg.endTime - 0.08f) {
                            exoPlayer.seekTo((seg.startTime * 1000).toLong())
                        }
                    }
                }
            } else {
                isPlaying = false
            }
            delay(50)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ChordScout", fontWeight = FontWeight.Black, color = TextPrimary, fontSize = 20.sp)
                        Text(" 🎸", fontSize = 18.sp)
                    }
                },
                actions = {
                    if (currentResult != null) {
                        IconButton(onClick = {
                            currentResult?.let { res ->
                                val chart = buildString {
                                    appendLine("# ChordScout: ${res.metadata.fileName}")
                                    appendLine("# Key: ${res.metadata.estimatedKey ?: "N/A"}")
                                    appendLine("---")
                                    res.segments.forEach {
                                        appendLine("${it.formattedStartTime} - ${it.formattedEndTime}  ${it.chord}")
                                    }
                                }
                                onShareChords(chart)
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Chords", tint = TextPrimary)
                        }
                    }

                    IconButton(onClick = { audioPicker.launch("audio/*") }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open Audio", tint = AccentCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgMain),
            )
        },
        containerColor = BgMain,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isAnalyzing) {
                // Progress view
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(color = AccentCyan)
                        Text(progressStatus, color = TextPrimary, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { analysisProgress },
                            color = AccentCyan,
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }
            } else if (currentResult == null) {
                // Hero Drop View
                DropHeroView(
                    onSelectFile = { audioPicker.launch("audio/*") },
                    onTryDemo = {
                        // Extract demo song from assets to cache
                        try {
                            val demoFile = File(context.cacheDir, "demo_classic_pop.mp3")
                            if (!demoFile.exists()) {
                                context.assets.open("demo_song.mp3").use { input ->
                                    FileOutputStream(demoFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            analyzeUri(demoFile.toUri(), "Classic Pop Demo (C-G-Am-F)")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not load demo: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            } else {
                val result = currentResult!!
                val activeSeg = if (activeSegmentIndex in result.segments.indices) {
                    result.segments[activeSegmentIndex]
                } else null

                val nextSeg = if (activeSegmentIndex + 1 in result.segments.indices) {
                    result.segments[activeSegmentIndex + 1]
                } else null

                val nextCountdown = if (activeSeg != null) {
                    maxOf(0f, activeSeg.endTime - currentPlaybackSec)
                } else null

                // 1. Timeline & Scrubber
                WaveformTimeline(
                    segments = result.segments,
                    durationSeconds = result.metadata.durationSeconds,
                    currentPlaybackSeconds = currentPlaybackSec,
                    onSeek = { sec ->
                        exoPlayer.seekTo((sec * 1000).toLong())
                        currentPlaybackSec = sec
                    },
                )

                // 2. HUD & Transport
                HudTransportBar(
                    currentChord = activeSeg?.chord ?: "—",
                    nextChord = nextSeg?.chord,
                    nextCountdownSeconds = nextCountdown,
                    keyEstimate = result.metadata.estimatedKey,
                    tempoBpm = result.metadata.tempoBpm,
                    isPlaying = isPlaying,
                    currentTimeSec = currentPlaybackSec,
                    totalTimeSec = result.metadata.durationSeconds,
                    playbackSpeed = playbackSpeed,
                    isLooping = isLoopingChord,
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPlaying = false
                        } else {
                            exoPlayer.play()
                            isPlaying = true
                        }
                    },
                    onPrevChord = {
                        if (activeSegmentIndex > 0) {
                            activeSegmentIndex--
                            val t = result.segments[activeSegmentIndex].startTime
                            exoPlayer.seekTo((t * 1000).toLong())
                        }
                    },
                    onNextChord = {
                        if (activeSegmentIndex < result.segments.size - 1) {
                            activeSegmentIndex++
                            val t = result.segments[activeSegmentIndex].startTime
                            exoPlayer.seekTo((t * 1000).toLong())
                        }
                    },
                    onToggleLoop = {
                        isLoopingChord = !isLoopingChord
                    },
                    onSpeedSelected = { spd ->
                        playbackSpeed = spd
                        exoPlayer.playbackParameters = PlaybackParameters(spd)
                    },
                )

                // 3. Tab Switcher: Fretboard vs Lead Sheet Grid
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = BgPanel,
                    contentColor = AccentCyan,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("🎸 Fretboard", fontWeight = FontWeight.Bold) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("🎼 Lead Sheet", fontWeight = FontWeight.Bold) },
                    )
                }

                // 4. Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        RosewoodFretboard(
                            chordName = activeSeg?.chord ?: "C",
                        )
                    } else {
                        LeadSheetGrid(
                            segments = result.segments,
                            activeSegmentIndex = activeSegmentIndex,
                            onSegmentSelected = { idx, startTime ->
                                activeSegmentIndex = idx
                                exoPlayer.seekTo((startTime * 1000).toLong())
                            },
                        )
                    }
                }
            }
        }
    }
}
