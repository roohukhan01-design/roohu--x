package com.roohu.roohux.features.library

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
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
fun LibraryScreen(vm: LibraryViewModel, onPlay: (uri: String, title: String, isVideo: Boolean) -> Unit, onPlayAudioQueue: (List<Pair<String, String>>) -> Unit, modifier: Modifier = Modifier) {
    val libraryItems by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val favOnly by vm.favoritesOnly.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<LibraryItem?>(null) }
    val playlistPrefs = remember(context) { context.getSharedPreferences("roohux_playlists", 0) }
    var playlists by remember { mutableStateOf(readPlaylists(playlistPrefs.getString("data", "[]"))) }
    var newPlaylistName by remember { mutableStateOf("") }
    var creatingPlaylist by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<String?>(null) }
    var managingPlaylist by remember { mutableStateOf<String?>(null) }
    var addingSong by remember { mutableStateOf<LibraryItem?>(null) }
    fun savePlaylists(updated: List<SavedPlaylist>) {
        playlists = updated
        val data = JSONArray()
        updated.forEach { p ->
            data.put(JSONObject().put("name", p.name).put("uris", JSONArray(p.uris)))
        }
        playlistPrefs.edit().putString("data", data.toString()).apply()
    }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            val data = JSONObject().put("format", "roohux-playlists-v1").put("playlists", JSONArray().apply {
                playlists.forEach { put(JSONObject().put("name", it.name).put("uris", JSONArray(it.uris))) }
            })
            context.contentResolver.openOutputStream(uri)?.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
        } catch (_: Exception) { android.widget.Toast.makeText(context, "Backup failed", android.widget.Toast.LENGTH_SHORT).show() }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            if (raw.length > 2000000) throw IllegalArgumentException()
            val root = JSONObject(raw)
            if (root.optString("format") != "roohux-playlists-v1") throw IllegalArgumentException()
            val imported = readPlaylists(root.getJSONArray("playlists").toString())
            if (imported.size > 500 || imported.any { it.uris.size > 10000 }) throw IllegalArgumentException()
            val merged = playlists.toMutableList()
            imported.forEach { item ->
                val index = merged.indexOfFirst { it.name.equals(item.name, true) }
                if (index >= 0) merged[index] = merged[index].copy(uris = (merged[index].uris + item.uris).distinct())
                else merged.add(item)
            }
            savePlaylists(merged)
        } catch (_: Exception) { android.widget.Toast.makeText(context, "Restore failed: invalid file", android.widget.Toast.LENGTH_SHORT).show() }
    }

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

        val allItems = libraryItems
        val folders = remember(allItems) {
            allItems.orEmpty().groupBy { item ->
                try {
                    val path = android.net.Uri.parse(item.uri).path.orEmpty()
                    path.substringBeforeLast('/', "Device media").substringAfterLast('/').ifBlank { "Device media" }
                } catch (_: Exception) { "Device media" }
            }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selectedFolder != null) TextButton(onClick = { selectedFolder = null }) { Text("All folders") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selectedFolder == null, { selectedFolder = null }, label = { Text("All media") }) }
            rowItems(folders.entries.toList(), key = { it.key }) { entry ->
                FilterChip(selectedFolder == entry.key, { selectedFolder = if (selectedFolder == entry.key) null else entry.key },
                    label = { Text(entry.key + " (" + entry.value.size + ")", maxLines = 1) })
            }
        }
        val list = allItems?.let { items -> if (selectedFolder == null) items else folders[selectedFolder].orEmpty() }
        val audioTracks = list.orEmpty().filter { !it.isVideo }.map { it.uri to it.title }
        if (audioTracks.isNotEmpty()) {
            Button(onClick = { onPlayAudioQueue(audioTracks) }, modifier = Modifier.fillMaxWidth()) {
                Text("Play all audio (${audioTracks.size})")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { backup.launch("RoohuX-playlists.json") }, enabled = playlists.isNotEmpty()) { Text("Backup") }
            TextButton(onClick = { restore.launch(arrayOf("application/json")) }) { Text("Restore") }
            TextButton(onClick = { newPlaylistName = ""; creatingPlaylist = true }) { Text("+ New") }
        }
        playlists.forEach { playlist ->
            val tracks = playlist.uris.mapNotNull { uri ->
                libraryItems.orEmpty().firstOrNull { it.uri == uri && !it.isVideo }?.let { it.uri to it.title }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                    Text("${tracks.size} available songs", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onPlayAudioQueue(tracks) }, enabled = tracks.isNotEmpty()) { Text("Play") }
                TextButton(onClick = { managingPlaylist = playlist.name }) { Text("Edit") }
                TextButton(onClick = { savePlaylists(playlists.filterNot { it.name == playlist.name }) }) { Text("Delete") }
            }
        }
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MediaThumbnail(item)
                            Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            (if (item.isVideo) "Video" else "Audio") + " · " + formatBytes(item.size) + if (item.downloadId == null) " · from device" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton({ onPlay(item.uri, item.title, item.isVideo) }) { Text("Play") }
                            if (!item.isVideo) TextButton({ addingSong = item }) { Text("Add to playlist") }
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

    managingPlaylist?.let { name ->
        val playlist = playlists.firstOrNull { it.name == name }
        if (playlist != null) AlertDialog(
            onDismissRequest = { managingPlaylist = null },
            title = { Text(playlist.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        newPlaylistName = playlist.name
                        editingPlaylist = playlist.name
                        managingPlaylist = null
                    }) { Text("Rename playlist") }
                    playlist.uris.forEachIndexed { index, uri ->
                        Text(libraryItems.orEmpty().firstOrNull { it.uri == uri }?.title ?: "Unavailable song")
                        Row {
                            TextButton(onClick = {
                                if (index > 0) {
                                    val changed = playlist.uris.toMutableList()
                                    java.util.Collections.swap(changed, index, index - 1)
                                    savePlaylists(playlists.map { if (it.name == name) it.copy(uris = changed) else it })
                                }
                            }, enabled = index > 0) { Text("Up") }
                            TextButton(onClick = {
                                if (index < playlist.uris.lastIndex) {
                                    val changed = playlist.uris.toMutableList()
                                    java.util.Collections.swap(changed, index, index + 1)
                                    savePlaylists(playlists.map { if (it.name == name) it.copy(uris = changed) else it })
                                }
                            }, enabled = index < playlist.uris.lastIndex) { Text("Down") }
                            TextButton(onClick = {
                                savePlaylists(playlists.map { if (it.name == name) it.copy(uris = it.uris.filterIndexed { i, _ -> i != index }) else it })
                            }) { Text("Remove") }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ managingPlaylist = null }) { Text("Done") } },
        )
    }
    editingPlaylist?.let { original ->
        AlertDialog(
            onDismissRequest = { editingPlaylist = null },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(newPlaylistName, { newPlaylistName = it }, label = { Text("New name") }) },
            confirmButton = { TextButton(onClick = {
                val name = newPlaylistName.trim()
                if (name.isNotEmpty() && playlists.none { it.name.equals(name, true) && it.name != original }) {
                    savePlaylists(playlists.map { if (it.name == original) it.copy(name = name) else it })
                    editingPlaylist = null
                }
            }) { Text("Save") } },
            dismissButton = { TextButton({ editingPlaylist = null }) { Text("Cancel") } },
        )
    }
    if (creatingPlaylist) {
        AlertDialog(
            onDismissRequest = { creatingPlaylist = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(newPlaylistName, { newPlaylistName = it }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = newPlaylistName.trim()
                    if (name.isNotEmpty() && playlists.none { it.name.equals(name, true) }) {
                        savePlaylists(playlists + SavedPlaylist(name, emptyList()))
                        creatingPlaylist = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ creatingPlaylist = false }) { Text("Cancel") } },
        )
    }
    addingSong?.let { song ->
        AlertDialog(
            onDismissRequest = { addingSong = null },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) Text("Create a playlist first.")
                    playlists.forEach { p ->
                        TextButton(onClick = {
                            savePlaylists(playlists.map {
                                if (it.name == p.name) it.copy(uris = (it.uris + song.uri).distinct()) else it
                            })
                            addingSong = null
                        }) { Text(p.name + if (song.uri in p.uris) " ✓" else "") }
                    }
                }
            },
            confirmButton = { TextButton({ addingSong = null }) { Text("Close") } },
        )
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

private data class SavedPlaylist(val name: String, val uris: List<String>)

private fun readPlaylists(raw: String?): List<SavedPlaylist> = try {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString("name").trim()
        if (name.isEmpty()) return@mapNotNull null
        val uris = item.optJSONArray("uris") ?: JSONArray()
        SavedPlaylist(name, (0 until uris.length()).mapNotNull { i -> uris.optString(i).takeIf { it.isNotBlank() } })
    }
} catch (_: Exception) { emptyList() }
