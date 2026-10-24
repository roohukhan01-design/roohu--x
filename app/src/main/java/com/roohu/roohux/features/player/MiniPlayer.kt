package com.roohu.roohux.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roohu.roohux.core.design.PlayPauseGlyph
import com.roohu.roohux.core.design.Spacing
import kotlinx.coroutines.delay

/** Audio-only mini-player. Reads the shared [PlayerConnection]; it never starts, restarts or reloads media. */
@Composable
fun MiniPlayer(conn: PlayerConnection, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(conn.controller, conn.isPlaying, conn.currentUri) {
        val c = conn.controller ?: return@LaunchedEffect
        while (true) {
            val d = c.duration
            progress = if (d > 0) (c.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            if (!conn.isPlaying) break
            delay(500)
        }
    }
    Surface(
        modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(Modifier.clickable(onClick = onOpen).padding(start = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = Spacing.s)) {
                    Text(conn.title.ifBlank { "Audio" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (conn.error != null) "Can't play" else "Audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ conn.togglePlay() }) {
                    PlayPauseGlyph(conn.isPlaying, MaterialTheme.colorScheme.primary, glyphSize = 28.dp)
                }
                IconButton({ conn.stop() }) { Icon(Icons.Default.Close, "Close mini-player") }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}
