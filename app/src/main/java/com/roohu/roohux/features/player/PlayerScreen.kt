package com.roohu.roohux.features.player

import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.roohu.roohux.core.design.HoloOrb
import com.roohu.roohux.core.design.PlayPauseGlyph
import com.roohu.roohux.core.design.PulsingDots
import com.roohu.roohux.core.design.Spacing
import com.roohu.roohux.core.design.findActivity
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Video: immersive PlayerView with auto-hiding controls (seek bar, ±10s, speed, audio/subtitle tracks, fullscreen/landscape),
 * plus Fit/Fill. No sleep timer. Audio: a now-playing layout with its own controls and the (audio-only) sleep timer.
 */
@Composable
fun PlayerScreen(conn: PlayerConnection, onBack: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        onDispose {
            if (activity?.isInPictureInPictureMode != true) conn.pauseIfVideo() // PiP keeps video playing
            conn.saveNow()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    when {
        conn.currentUri == null && conn.error == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PulsingDots() }
        conn.isVideo -> VideoPlayer(conn, onBack)
        else -> AudioPlayer(conn, onBack)
    }
}

@Composable
internal fun ErrorPanel(message: String, conn: PlayerConnection, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Button({ conn.retry() }) { Text("Retry") }
            OutlinedButton(onBack) { Text("Back") }
        }
    }
}

internal fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AudioPlayer(conn: PlayerConnection, onBack: () -> Unit) {
    val controller = conn.controller
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speed by remember(controller) { mutableFloatStateOf(controller?.playbackParameters?.speed ?: 1f) }
    var shuffle by remember(controller) { mutableStateOf(controller?.shuffleModeEnabled == true) }
    var repeatOne by remember(controller) { mutableStateOf(controller?.repeatMode == Player.REPEAT_MODE_ONE) }
    var sleepMenu by remember { mutableStateOf(false) }

    LaunchedEffect(controller, conn.currentUri) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            if (dragging == null) {
                position = c.currentPosition
                duration = c.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Now playing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        // No fabricated album art: a calm brand visual stands in.
        Surface(Modifier.size(260.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) { HoloOrb(Modifier.size(220.dp)) }
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(conn.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))

        conn.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            TextButton({ conn.retry() }) { Text("Retry") }
        }

        val shown = dragging ?: if (duration > 0) position.toFloat() / duration else 0f
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { f -> controller?.seekTo((f * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(mmss((shown * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("-" + mmss(duration - (shown * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ if (controller?.hasPreviousMediaItem() == true) controller.seekToPreviousMediaItem() else controller?.seekBack() }) { Text("Previous") }
            FilledIconButton({ conn.togglePlay() }, Modifier.size(72.dp)) {
                PlayPauseGlyph(conn.isPlaying, MaterialTheme.colorScheme.onPrimary, glyphSize = 36.dp)
            }
            TextButton({ if (controller?.hasNextMediaItem() == true) controller.seekToNextMediaItem() else controller?.seekForward() }) { Text("Next") }
        }
        Spacer(Modifier.height(Spacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = speed != 1f,
                onClick = {
                    val options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                    val next = options[(options.indexOf(speed).let { if (it < 0) 1 else it } + 1) % options.size]
                    speed = next
                    controller?.setPlaybackSpeed(next)
                },
                label = { Text("${speed}x") },
            )
            FilterChip(
                selected = shuffle,
                onClick = { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle },
                label = { Text(if (shuffle) "Shuffle: on" else "Shuffle: off") },
            )
            FilterChip(
                selected = repeatOne,
                onClick = {
                    repeatOne = !repeatOne
                    controller?.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                },
                label = { Text(if (repeatOne) "Repeat: one" else "Repeat: off") },
            )
            Box {
                FilterChip(
                    selected = conn.sleepEndsAt > 0,
                    onClick = { sleepMenu = true },
                    label = {
                        Text(
                            if (conn.sleepEndsAt > 0) "Sleep " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(conn.sleepEndsAt))
                            else "Sleep timer",
                        )
                    },
                )
                DropdownMenu(sleepMenu, { sleepMenu = false }) {
                    DropdownMenuItem({ Text("Off") }, { conn.setSleepTimer(0); sleepMenu = false })
                    listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                        DropdownMenuItem({ Text("$m minutes") }, { conn.setSleepTimer(m); sleepMenu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xxl).navigationBarsPadding())
    }
}
