package com.roohu.roohux.features.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roohu.roohux.core.design.GlassCard
import com.roohu.roohux.core.design.PulsingDots
import com.roohu.roohux.core.design.formatBytes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onPlay: (uri: String, title: String, isVideo: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val libraryItems by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val favOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRemove by remember { mutableStateOf<LibraryItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFromDevice(context, uris)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.headlineSmall)
                libraryItems?.let { all ->
                    val total = all.filter { it.size > 0 }.sumOf { it.size }
                    Text("${all.size} files" + if (total > 0) " · ${formatBytes(total)}" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton({ picker.launch(arrayOf("video/*", "audio/*")) }) { Text("Add from device") }
        }
        OutlinedTextField(
            value = query, onValueChange = { vm.query.value = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Search media") },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibFilter.entries.forEach { f -> FilterChip(filter == f, { vm.filter.value = f }, { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            FilterChip(favOnly, { vm.favoritesOnly.value = !favOnly }, { Text("Favorites") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            Text("Sort:", Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LibSort.entries.forEach { s -> FilterChip(sort == s, { vm.sort.value = s }, { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
        }

        val list = libraryItems
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PulsingDots() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No media yet.\nDownloaded videos and audio appear here, or tap “Add from device”.",
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(list, key = { it.uri }) { item ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (item.isVideo) "Video" else "Audio") + " · " + formatBytes(item.size) + if (item.downloadId == null) " · from device" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton({ onPlay(item.uri, item.title, item.isVideo) }) { Text("Play") }
                            TextButton({ vm.toggleFavorite(item) }) { Text(if (item.favorite) "Unfavorite" else "Favorite") }
                            TextButton({
                                val send = Intent(Intent.ACTION_SEND).setType(item.mime.ifBlank { "*/*" })
                                    .putExtra(Intent.EXTRA_STREAM, item.uri.toUri()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, "Share ${item.title}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) { Text("Share") }
                            TextButton({ pendingRemove = item }) { Text(if (item.downloadId != null) "Delete" else "Remove") }
                        }
                    }
                }
            }
        }
    }

    pendingRemove?.let { item ->
        val isDownload = item.downloadId != null
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(if (isDownload) "Delete file?" else "Remove from library?") },
            text = { Text(if (isDownload) "“${item.title}” will be permanently deleted from Downloads/RoohuX." else "“${item.title}” stays on your device; it is only removed from this list.") },
            confirmButton = { TextButton({ vm.remove(item); pendingRemove = null }) { Text(if (isDownload) "Delete" else "Remove") } },
            dismissButton = { TextButton({ pendingRemove = null }) { Text("Cancel") } },
        )
    }
}
