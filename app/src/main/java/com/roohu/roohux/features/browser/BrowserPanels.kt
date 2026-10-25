package com.roohu.roohux.features.browser

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roohu.roohux.core.design.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(manager: BrowserManager, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tabs (${manager.tabs.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Button({ manager.newTab(); onDismiss() }) { Text("New tab") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(manager.tabs.toList(), key = { it.id }) { t ->
                    val selected = t.id == manager.activeId
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(Modifier.fillMaxWidth().clickable { manager.select(t.id); onDismiss() }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (t.blank) "New tab" else t.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ manager.closeTab(t.id) }) { Icon(Icons.Default.Close, "Close tab") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksHistorySheet(manager: BrowserManager, startOnHistory: Boolean, onDismiss: () -> Unit) {
    var showHistory by remember { mutableStateOf(startOnHistory) }
    val bookmarks by manager.bookmarks.collectAsStateWithLifecycle()
    val history by manager.history.collectAsStateWithLifecycle()
    fun open(url: String) {
        (manager.active ?: manager.newTab()).let { manager.load(it, url) }
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(!showHistory, { showHistory = false }, { Text("Bookmarks") })
                FilterChip(showHistory, { showHistory = true }, { Text("History") })
                Spacer(Modifier.weight(1f))
                if (showHistory && history.isNotEmpty()) TextButton({ manager.clearHistory() }) { Text("Clear all") }
            }
            val empty = if (showHistory) history.isEmpty() else bookmarks.isEmpty()
            if (empty) Text(if (showHistory) "No history yet." else "No bookmarks yet. Use the menu → Add bookmark.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn {
                if (showHistory) {
                    items(history, key = { it.id }) { h ->
                        EntryRow(h.title, h.url, onClick = { open(h.url) }, onDelete = { manager.deleteHistory(h.id) })
                    }
                } else {
                    items(bookmarks, key = { it.id }) { b ->
                        EntryRow(b.title, b.url, onClick = { open(b.url) }, onDelete = { manager.deleteBookmark(b.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(title: String, url: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onDelete) { Icon(Icons.Default.Close, "Delete") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSheet(manager: BrowserManager, tab: BrowserTab, onDismiss: () -> Unit) {
    val probes = remember { mutableStateMapOf<String, MediaProbe>() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Media on this page", style = MaterialTheme.typography.titleMedium)
            val pageHost = remember(tab.url) { runCatching { Uri.parse(tab.url).host.orEmpty().lowercase() }.getOrDefault("") }
            val isYouTube = pageHost == "youtube.com" || pageHost.endsWith(".youtube.com") ||
                pageHost == "youtu.be" || pageHost.endsWith(".youtu.be")
            if (isYouTube) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "YouTube streams are not ordinary video-file links, so RoohuX can't save them from this media panel. Use YouTube's official Download option where available.",
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (tab.drm) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "This page uses DRM-protected playback. RoohuX can't download or play protected content.",
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (tab.media.isEmpty()) Text("No downloadable media was found. Detection only sees media the page exposes to the browser.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tab.media, key = { it.url }) { m ->
                    LaunchedEffect(m.url) {
                        if (m.kind == MediaKind.DIRECT_VIDEO || m.kind == MediaKind.DIRECT_AUDIO) {
                            probes[m.url] = manager.probe(m.url, tab.url.ifBlank { null }, tab.webView.settings.userAgentString)
                        }
                    }
                    MediaRow(m, probes[m.url], onDownload = { manager.downloadMedia(tab, m); onDismiss() }, onPlay = { manager.playMedia(m); onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun MediaRow(m: DetectedMedia, probe: MediaProbe?, onDownload: () -> Unit, onPlay: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            val kind = when (m.kind) {
                MediaKind.DIRECT_VIDEO -> "Video file"
                MediaKind.DIRECT_AUDIO -> "Audio file"
                MediaKind.STREAM_PLAYLIST -> "Streaming playlist (HLS/DASH)"
                MediaKind.BLOB -> "In-page stream (blob)"
            }
            val size = probe?.sizeBytes?.let { formatBytes(it) } ?: "size unknown"
            Text(if (m.kind == MediaKind.DIRECT_VIDEO || m.kind == MediaKind.DIRECT_AUDIO) "$kind · $size" + (probe?.contentType?.let { " · $it" } ?: "") else kind,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val type = probe?.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val isHtml = type == "text/html" || type == "application/xhtml+xml"
            val isYouTubeInternal = type.contains("yt-ump") || type.contains("googlevideo") && type.contains("html")
            val rejectedAsPage = isHtml || isYouTubeInternal
            when (m.kind) {
                MediaKind.DIRECT_VIDEO, MediaKind.DIRECT_AUDIO -> {
                    if (rejectedAsPage) {
                        Text(
                            if (isYouTubeInternal) "This is an internal streaming response, not a normal media file. RoohuX cannot save it as a direct download." else "The server returned a web page instead of a media file. The download is hidden to avoid saving an HTML error/confirmation page as a video.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onDownload) { Text("Download") }
                        OutlinedButton(onPlay) { Text("Play") }
                    }
                }
                MediaKind.STREAM_PLAYLIST -> {
                    Text("Saving playlists isn't supported, and the stream may be protected. You can try playing it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onPlay) { Text("Try playing") }
                }
                MediaKind.BLOB -> Text("This is a temporary browser stream, not a video file link. RoohuX cannot download this blob directly. Try the website's official Download/Offline option, or use a direct media-file link when the site provides one. DRM-protected streams cannot be saved here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
