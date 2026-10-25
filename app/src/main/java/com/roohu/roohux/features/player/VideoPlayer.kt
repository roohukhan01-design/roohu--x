package com.roohu.roohux.features.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.roohu.roohux.core.design.PlayPauseGlyph
import com.roohu.roohux.core.design.Spacing
import com.roohu.roohux.core.design.findActivity
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

private enum class FitMode(val label: String, val resize: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private fun speedLabel(s: Float) = "%.2f".format(s).trimEnd('0').trimEnd('.') + "x"

private fun initialBrightness(activity: Activity?): Float {
    val own = activity?.window?.attributes?.screenBrightness ?: -1f
    if (own >= 0f) return own
    return try {
        Settings.System.getInt(activity!!.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

private fun volumeFraction(audio: AudioManager): Float {
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

/**
 * Video player with its own controls (the stock PlayerView controller is off so gestures can't conflict):
 * swipe left half = brightness, right half = volume, horizontal swipe = seek, double-tap = -/+10 s, tap = show/hide controls.
 * Settings sheet: brightness, volume, speed presets. Also audio/subtitle tracks, Fit/Fill/Stretch, rotate, control lock.
 * There is deliberately no sleep timer here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoPlayer(conn: PlayerConnection, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val audio = remember { context.getSystemService(AudioManager::class.java) }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    val playerPrefs = remember(context) { context.getSharedPreferences("roohux_video_player", Context.MODE_PRIVATE) }
    var fit by remember { mutableStateOf(FitMode.entries.getOrElse(playerPrefs.getInt("fit_mode", 0)) { FitMode.FIT }) }
    var speed by remember { mutableFloatStateOf(playerPrefs.getFloat("playback_speed", 1f).takeIf { it in 0.25f..3f } ?: 1f) }
    var showSettings by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var brightness by remember { mutableFloatStateOf(initialBrightness(activity)) }
    var volume by remember { mutableFloatStateOf(volumeFraction(audio)) }

    fun showHud(text: String) { hud = text; hudTick++ }

    fun applyBrightness(f: Float) {
        brightness = f.coerceIn(0.02f, 1f)
        activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = brightness } }
    }

    fun applyVolume(f: Float) {
        volume = f.coerceIn(0f, 1f)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
    }

    fun applySpeed(s: Float) {
        speed = s
        playerPrefs.edit().putFloat("playback_speed", s).apply()
        conn.controller?.setPlaybackSpeed(s)
    }

    LaunchedEffect(fit) { playerPrefs.edit().putInt("fit_mode", fit.ordinal).apply() }
    LaunchedEffect(conn.controller) { conn.controller?.setPlaybackSpeed(speed) }

    // Keep the screen on while watching; give brightness back to the system when leaving.
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }

    LaunchedEffect(hudTick) { if (hud != null) { delay(900); hud = null } }

    LaunchedEffect(conn.controller) {
        while (true) {
            val c = conn.controller
            if (c != null && scrub == null) {
                position = c.currentPosition
                duration = c.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    // Auto-hide controls while playing.
    LaunchedEffect(controlsVisible, conn.isPlaying, interaction, showSettings, showTracks) {
        if (controlsVisible && conn.isPlaying && !showSettings && !showTracks) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(AndroidColor.BLACK)
                }
            },
            update = { view ->
                view.player = conn.controller
                view.resizeMode = fit.resize
            },
            onRelease = { view -> view.player = null },
        )

        // Gesture layer (below the controls so buttons keep priority).
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; interaction++ },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                val c = conn.controller
                                if (offset.x < size.width / 2f) { c?.seekBack(); showHud("-10 s") } else { c?.seekForward(); showHud("+10 s") }
                            }
                        },
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var mode = DragMode.NONE
                    var startX = 0f
                    var startPos = 0L
                    var seekDelta = 0L
                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = DragMode.NONE
                            startX = offset.x
                            startPos = conn.controller?.currentPosition ?: 0L
                            seekDelta = 0L
                        },
                        onDragEnd = {
                            if (mode == DragMode.SEEK && duration > 0) {
                                conn.controller?.seekTo((startPos + seekDelta).coerceIn(0L, duration))
                            }
                            mode = DragMode.NONE
                        },
                        onDragCancel = { mode = DragMode.NONE },
                        onDrag = { change, drag ->
                            change.consume()
                            if (mode == DragMode.NONE) {
                                mode = when {
                                    abs(drag.x) > abs(drag.y) -> DragMode.SEEK
                                    startX < size.width / 2f -> DragMode.BRIGHTNESS
                                    else -> DragMode.VOLUME
                                }
                            }
                            when (mode) {
                                DragMode.SEEK -> if (duration > 0) {
                                    seekDelta += (drag.x / size.width * 90_000f).toLong()
                                    val target = (startPos + seekDelta).coerceIn(0L, duration)
                                    val diff = (target - startPos) / 1000
                                    showHud((if (diff >= 0) "+" else "") + "$diff s  ·  " + mmss(target))
                                }
                                DragMode.BRIGHTNESS -> {
                                    applyBrightness(brightness - drag.y / size.height * 1.1f)
                                    showHud("Brightness  ${(brightness * 100).roundToInt()}%")
                                }
                                DragMode.VOLUME -> {
                                    applyVolume(volume - drag.y / size.height * 1.1f)
                                    showHud("Volume  ${(volume * 100).roundToInt()}%")
                                }
                                DragMode.NONE -> Unit
                            }
                        },
                    )
                },
        )

        // Locked: only an unlock button.
        if (locked) {
            AnimatedVisibility(controlsVisible, Modifier.align(Alignment.CenterStart).padding(Spacing.l), enter = fadeIn(), exit = fadeOut()) {
                FilledTonalButton({ locked = false; showHud("Controls unlocked") }) { Text("Unlock") }
            }
        } else {
            AnimatedVisibility(controlsVisible, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.75f))),
                    ),
                ) {
                    // Top bar
                    Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                        Text(conn.title, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (hasSelectableTracks(conn)) TextButton({ showTracks = true; interaction++ }) { Text("Tracks", color = Color.White) }
                        TextButton({ showSettings = true; interaction++ }) { Text("Settings", color = Color.White) }
                    }
                    // Centre transport
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ conn.controller?.seekBack(); interaction++ }) { Text("−10", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                        FilledIconButton({ conn.togglePlay(); interaction++ }, Modifier.size(68.dp)) {
                            PlayPauseGlyph(conn.isPlaying, MaterialTheme.colorScheme.onPrimary, glyphSize = 34.dp)
                        }
                        TextButton({ conn.controller?.seekForward(); interaction++ }) { Text("+10", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                    }
                    // Bottom: seek bar + quick options
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                        val shown = scrub ?: if (duration > 0) position.toFloat() / duration else 0f
                        Slider(
                            value = shown.coerceIn(0f, 1f),
                            onValueChange = { scrub = it; interaction++ },
                            onValueChangeFinished = {
                                scrub?.let { f -> conn.controller?.seekTo((f * duration).toLong()) }
                                scrub = null
                            },
                            enabled = duration > 0,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(mmss((shown * duration).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Text("-" + mmss(duration - (shown * duration).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            AssistChip(
                                onClick = {
                                    val next = SPEEDS[(SPEEDS.indexOfFirst { abs(it - speed) < 0.01f }.let { if (it < 0) 3 else it } + 1) % SPEEDS.size]
                                    applySpeed(next); showHud("Speed ${speedLabel(next)}"); interaction++
                                },
                                label = { Text(speedLabel(speed), color = Color.White) },
                            )
                            AssistChip(
                                onClick = { fit = FitMode.entries[(fit.ordinal + 1) % FitMode.entries.size]; showHud("Aspect: ${fit.label}"); interaction++ },
                                label = { Text(fit.label, color = Color.White) },
                            )
                            AssistChip(
                                onClick = {
                                    landscape = !landscape
                                    activity?.requestedOrientation =
                                        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    interaction++
                                },
                                label = { Text(if (landscape) "Portrait" else "Landscape", color = Color.White) },
                            )
                            AssistChip(
                                onClick = { locked = true; controlsVisible = false; showHud("Controls locked") },
                                label = { Text("Lock", color = Color.White) },
                            )
                        }
                    }
                }
            }
        }

        hud?.let {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.72f)) {
                Text(it, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            }
        }
        conn.error?.let { ErrorPanel(it, conn, onBack, Modifier.align(Alignment.Center)) }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Player settings", style = MaterialTheme.typography.titleMedium)
                Text("Brightness  ${(brightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(brightness, { applyBrightness(it) }, valueRange = 0.02f..1f)
                Text("Volume  ${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(volume, { applyVolume(it) })
                Text("Playback speed  ${speedLabel(speed)}", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SPEEDS.forEach { s -> FilterChip(abs(s - speed) < 0.01f, { applySpeed(s) }, { Text(speedLabel(s)) }) }
                }
                Text("Aspect ratio", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FitMode.entries.forEach { m -> FilterChip(fit == m, { fit = m }, { Text(m.label) }) }
                }
                Text("Tip: swipe the left side of the video for brightness, the right side for volume, sideways to seek, and double-tap to skip 10 s.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showTracks) TracksDialog(conn) { showTracks = false }
}

private fun hasSelectableTracks(conn: PlayerConnection): Boolean {
    val groups = conn.controller?.currentTracks?.groups ?: return false
    return groups.count { it.type == C.TRACK_TYPE_AUDIO } > 1 || groups.any { it.type == C.TRACK_TYPE_TEXT }
}

private fun trackName(group: Tracks.Group, fallback: String): String {
    val f = group.getTrackFormat(0)
    val lang = f.language?.let { Locale.forLanguageTag(it).displayLanguage }?.takeIf { it.isNotBlank() }
    return f.label ?: lang ?: fallback
}

@Composable
private fun TracksDialog(conn: PlayerConnection, onDismiss: () -> Unit) {
    val controller = conn.controller
    val groups = controller?.currentTracks?.groups.orEmpty()
    val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textDisabled = controller?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        title = { Text("Audio & subtitles") },
        text = {
            LazyColumn {
                if (audio.size > 1) {
                    item { Text("Audio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    items(audio.size) { i ->
                        val g = audio[i]
                        TextButton({
                            controller?.let { c ->
                                c.trackSelectionParameters = c.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                            }
                            onDismiss()
                        }) { Text((if (g.isSelected) "● " else "") + trackName(g, "Audio ${i + 1}")) }
                    }
                }
                if (text.isNotEmpty()) {
                    item { Text("Subtitles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    item {
                        TextButton({
                            controller?.let { c ->
                                c.trackSelectionParameters = c.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            }
                            onDismiss()
                        }) { Text((if (textDisabled) "● " else "") + "Off") }
                    }
                    items(text.size) { i ->
                        val g = text[i]
                        TextButton({
                            controller?.let { c ->
                                c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                            }
                            onDismiss()
                        }) { Text((if (g.isSelected && !textDisabled) "● " else "") + trackName(g, "Subtitle ${i + 1}")) }
                    }
                }
            }
        },
    )
}
