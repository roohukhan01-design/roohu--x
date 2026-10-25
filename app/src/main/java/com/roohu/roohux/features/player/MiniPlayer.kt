package com.roohu.roohux.features.player

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roohu.roohux.core.design.PlayPauseGlyph
import com.roohu.roohux.core.design.Spacing
import kotlinx.coroutines.delay

/** Audio-only mini-player. Reads the shared [PlayerConnection]; it never starts, restarts or reloads media. */
@Composable
fun MiniPlayer(conn: PlayerConnection, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val artwork by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = conn.currentUri) {
        value = withContext(Dispatchers.IO) {
            try {
                val source = conn.currentUri ?: return@withContext null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(source))
                    val bytes = retriever.embeddedPicture ?: return@withContext null
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    var sample = 1
                    while (options.outWidth / sample > 240 || options.outHeight / sample > 240) sample *= 2
                    options.inJustDecodeBounds = false
                    options.inSampleSize = sample
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } finally { retriever.release() }
            } catch (_: Exception) { null }
        }
    }
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
            Row(Modifier.clickable(onClick = onOpen).padding(horizontal = Spacing.s, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    if (artwork != null) Image(artwork!!.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Text("♪", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f).padding(horizontal = Spacing.s, vertical = Spacing.s)) {
                    Text(conn.title.ifBlank { "Audio" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (conn.error != null) "Can't play" else "Audio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ conn.controller?.let { c -> if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekBack() } }) { Text("‹‹") }
                IconButton({ conn.togglePlay() }) {
                    PlayPauseGlyph(conn.isPlaying, MaterialTheme.colorScheme.primary, glyphSize = 28.dp)
                }
                IconButton({ conn.controller?.let { c -> if (c.hasNextMediaItem()) c.seekToNextMediaItem() else c.seekForward() } }) { Text("››") }
                IconButton({ conn.stop() }) { Icon(Icons.Default.Close, "Close mini-player") }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}
