package com.paulkircher.chordscout

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.paulkircher.chordscout.audio.AudioDecoder
import com.paulkircher.chordscout.dsp.ChordAnalyzer
import com.paulkircher.chordscout.dsp.GuitarSynth
import com.paulkircher.chordscout.model.AnalysisResult
import com.paulkircher.chordscout.ui.components.*
import com.paulkircher.chordscout.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var currentIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        currentIntent = intent

        setContent {
            ChordScoutTheme {
                MainScreen(
                    exoPlayer = exoPlayer!!,
                    onShareChords = { text -> shareChordSheet(text) },
                    initialIntent = currentIntent,
                    onConsumeIntent = { currentIntent = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
        GuitarSynth.release()
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
        GuitarSynth.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}

fun extractAudioUri(intent: Intent?): Uri? {
    if (intent == null) return null
    intent.data?.let { return it }
    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
    val clip = intent.clipData
    if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0)?.uri?.let { return it }
    }
    return null
}

fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val resolved = cursor.getString(nameIndex)?.trim()
                    if (!resolved.isNullOrEmpty()) return resolved
                }
            }
        } catch (_: Exception) {
        }
    }
    val segment = uri.lastPathSegment?.substringAfterLast('/')?.trim()
    return if (!segment.isNullOrEmpty()) segment else fallback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    exoPlayer: ExoPlayer,
    onShareChords: (String) -> Unit,
    initialIntent: Intent?,
    onConsumeIntent: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf(0f) }
    var progressStatus by remember { mutableStateOf("") }
    var currentResult by remember { mutableStateOf<AnalysisResult?>(null) }

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPlaybackSec by remember { mutableStateOf(0f) }
    var activeSegmentIndex by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isLoopingChord by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0=Fretboard, 1=Lead Sheet

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    fun seekToSeconds(sec: Float) {
        val res = currentResult
        val clamped = if (res != null) {
            sec.coerceIn(0f, res.metadata.durationSeconds)
        } else {
            maxOf(0f, sec)
        }
        exoPlayer.seekTo((clamped * 1000).toLong())
        currentPlaybackSec = clamped
        if (res != null) {
            val seg = res.getChordAtTime(clamped)
            if (seg != null) {
                val idx = res.segments.indexOf(seg)
                if (idx >= 0) activeSegmentIndex = idx
            }
        }
    }

    fun seekToSegment(index: Int) {
        val res = currentResult ?: return
        if (index !in res.segments.indices) return
        activeSegmentIndex = index
        val targetSec = res.segments[index].startTime
        exoPlayer.seekTo((targetSec * 1000).toLong())
        currentPlaybackSec = targetSec
    }

    // Function to analyze a given URI
    fun analyzeUri(uri: Uri, displayName: String) {
        analysisJob?.cancel()
        val job = scope.launch {
            val jobScope = this
            try {
                isAnalyzing = true
                analysisProgress = 0.05f
                progressStatus = "Decoding audio..."
                exoPlayer.stop()
                isPlaying = false
                isLoopingChord = false
                playbackSpeed = 1.0f
                exoPlayer.playbackParameters = PlaybackParameters(1.0f)

                val pcm = AudioDecoder.decodeToMonoPcm(
                    context = context,
                    uri = uri,
                    targetSampleRate = 22050,
                    onProgress = { p ->
                        jobScope.launch {
                            analysisProgress = maxOf(analysisProgress, p)
                            progressStatus = "Decoding audio (${(analysisProgress * 100).roundToInt()}%)..."
                        }
                    }
                )

                val res = runInterruptible(Dispatchers.Default) {
                    ChordAnalyzer.analyze(
                        pcm = pcm,
                        sampleRate = 22050,
                        fileName = displayName,
                        onProgress = { p, msg ->
                            jobScope.launch {
                                analysisProgress = maxOf(analysisProgress, p)
                                progressStatus = msg
                            }
                        }
                    )
                }

                currentResult = res
                activeSegmentIndex = 0
                currentPlaybackSec = 0f

                // Prepare ExoPlayer
                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "Error analyzing audio: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isAnalyzing = false
            }
        }
        analysisJob = job
    }

    // Audio file picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = resolveDisplayName(context, it, "Selected Audio")
            analyzeUri(it, name)
        }
    }

    // Handle initial intent if app opened via 'Open With' or 'Share'
    LaunchedEffect(initialIntent) {
        val uri = extractAudioUri(initialIntent)
        if (uri != null) {
            onConsumeIntent()
            val name = resolveDisplayName(context, uri, "Shared Audio")
            analyzeUri(uri, name)
        }
    }

    // Playback loop polling player position while actively playing
    LaunchedEffect(isPlaying, isLoopingChord, currentResult) {
        while (isPlaying) {
            val sec = exoPlayer.currentPosition / 1000f
            currentPlaybackSec = sec

            currentResult?.let { res ->
                val lockedSeg = res.segments.getOrNull(activeSegmentIndex)
                if (isLoopingChord && lockedSeg != null && sec >= lockedSeg.endTime - 0.08f) {
                    exoPlayer.seekTo((lockedSeg.startTime * 1000).toLong())
                    currentPlaybackSec = lockedSeg.startTime
                } else {
                    val seg = res.getChordAtTime(sec)
                    if (seg != null) {
                        val idx = res.segments.indexOf(seg)
                        if (idx >= 0) activeSegmentIndex = idx
                    }
                }
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
                        // Extract demo song from assets to cache on Dispatchers.IO
                        scope.launch {
                            try {
                                val demoFile = withContext(Dispatchers.IO) {
                                    val target = File(context.cacheDir, "demo_classic_pop.mp3")
                                    if (!target.exists() || target.length() == 0L) {
                                        context.assets.open("demo_song.mp3").use { input ->
                                            FileOutputStream(target).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                    target
                                }
                                analyzeUri(demoFile.toUri(), "Classic Pop Demo (C-G-Am-F)")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not load demo: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
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

                val triggerStrum: () -> Unit = {
                    activeSeg?.chord?.let { chordName ->
                        scope.launch { GuitarSynth.playChord(chordName) }
                    }
                }

                // 1. Timeline & Scrubber
                WaveformTimeline(
                    segments = result.segments,
                    durationSeconds = result.metadata.durationSeconds,
                    currentPlaybackSeconds = currentPlaybackSec,
                    onSeek = { sec -> seekToSeconds(sec) },
                )

                // 2. HUD & Transport
                HudTransportBar(
                    currentChord = activeSeg?.chord ?: "—",
                    nextChord = nextSeg?.chord,
                    nextCountdownSeconds = nextCountdown,
                    keyEstimate = result.metadata.estimatedKey,
                    isPlaying = isPlaying,
                    currentTimeSec = currentPlaybackSec,
                    totalTimeSec = result.metadata.durationSeconds,
                    playbackSpeed = playbackSpeed,
                    isLooping = isLoopingChord,
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                seekToSeconds(0f)
                            }
                            exoPlayer.play()
                        }
                    },
                    onPrevChord = {
                        if (activeSegmentIndex > 0) {
                            seekToSegment(activeSegmentIndex - 1)
                        }
                    },
                    onNextChord = {
                        if (activeSegmentIndex < result.segments.size - 1) {
                            seekToSegment(activeSegmentIndex + 1)
                        }
                    },
                    onToggleLoop = {
                        isLoopingChord = !isLoopingChord
                    },
                    onSpeedSelected = { spd ->
                        playbackSpeed = spd
                        exoPlayer.playbackParameters = PlaybackParameters(spd)
                    },
                    onStrumChord = triggerStrum,
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
                            chordName = activeSeg?.chord ?: "—",
                            onStrumChord = triggerStrum,
                        )
                    } else {
                        LeadSheetGrid(
                            segments = result.segments,
                            activeSegmentIndex = activeSegmentIndex,
                            onSegmentSelected = { idx, _ ->
                                seekToSegment(idx)
                            },
                        )
                    }
                }
            }
        }
    }
}
