package com.roohu.roohux.features.downloads.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.database.DownloadStatus
import com.roohu.roohux.core.design.GlassCard
import com.roohu.roohux.core.design.Spacing
import com.roohu.roohux.core.design.formatBytes
import com.roohu.roohux.core.design.formatEta
import com.roohu.roohux.core.design.formatSpeed
import com.roohu.roohux.core.storage.FileInfo

@Composable
fun DownloadsScreen(vm: DownloadsViewModel, modifier: Modifier = Modifier) {
    val list by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    if (list.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No downloads yet.\nPaste a direct file link on the Home tab, or download from the browser.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                val saved = list.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.downloadedBytes }
                Text(
                    "${list.count { it.status == DownloadStatus.COMPLETED }} saved · ${formatBytes(saved)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(list, key = { it.id }) { d -> DownloadCard(d, vm, context) { pendingDelete = d } }
        }
    }

    pendingDelete?.let { d ->
        val completed = d.status == DownloadStatus.COMPLETED
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = {
                Text(
                    if (completed) "Delete the file from Downloads/RoohuX too, or only remove it from this list? Tap outside to cancel."
                    else "This removes it from the list and discards any partial data.",
                )
            },
            confirmButton = {
                TextButton({ vm.delete(d.id, deleteFile = true); pendingDelete = null }) { Text(if (completed) "Delete file" else "Delete") }
            },
            dismissButton = {
                if (completed) TextButton({ vm.delete(d.id, deleteFile = false); pendingDelete = null }) { Text("Remove from list") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadCard(d: DownloadEntity, vm: DownloadsViewModel, context: Context, onDelete: () -> Unit) {
    var expanded by rememberSaveable(d.id) { mutableStateOf(false) }
    val category = FileInfo.category(d.mimeType, d.fileName)
    val format = FileInfo.formatLabel(d.fileName, d.mimeType)

    GlassCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (category) { "Video" -> "VID"; "Audio" -> "AUD"; "Image" -> "IMG"; else -> "FILE" },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(d.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    category.uppercase() + "/" + format,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        StatusBlock(d)

        TextButton({ expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (expanded) "File details  ▲" else "File details  ▼")
        }
        AnimatedVisibility(expanded) { DetailsGrid(d, format) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when (d.status) {
                DownloadStatus.DOWNLOADING -> {
                    if (d.resumable == 1) FilledTonalButton({ vm.pause(d.id) }) { Text("Pause") }
                    OutlinedButton({ vm.cancel(d.id) }) { Text("Cancel") }
                }
                DownloadStatus.QUEUED -> OutlinedButton({ vm.cancel(d.id) }) { Text("Cancel") }
                DownloadStatus.PAUSED -> {
                    FilledTonalButton({ vm.resume(d.id) }) { Text("Resume") }
                    OutlinedButton({ vm.cancel(d.id) }) { Text("Cancel") }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                    FilledTonalButton({ vm.resume(d.id) }) { Text("Retry") }
                    OutlinedButton(onDelete) { Text("Delete") }
                }
                DownloadStatus.COMPLETED -> {
                    FilledTonalButton({ DownloadActions.open(context, d) }) { Text("Open") }
                    OutlinedButton({ DownloadActions.share(context, d) }) { Text("Share") }
                    OutlinedButton({ DownloadActions.locate(context) }) { Text("Locate") }
                    TextButton(onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun DetailsGrid(d: DownloadEntity, format: String) {
    val hint = FileInfo.resolutionHint(d.fileName)
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = Spacing.s))
        DetailRow("FILE SIZE", if (d.totalBytes > 0) formatBytes(d.totalBytes) else "Unknown")
        DetailRow("FORMAT", format)
        DetailRow("CONTENT TYPE", d.mimeType ?: "Unknown")
        DetailRow("SOURCE WEBSITE", FileInfo.sourceHost(d.url, d.referer) ?: "Unknown")
        if (hint != null) DetailRow("RESOLUTION", "$hint*")
        DetailRow("DOWNLOADED", formatBytes(d.downloadedBytes))
        if (d.status != DownloadStatus.COMPLETED && d.totalBytes > 0) {
            DetailRow("REMAINING", formatBytes((d.totalBytes - d.downloadedBytes).coerceAtLeast(0)))
        }
        DetailRow("RESUME SUPPORT", when (d.resumable) { 1 -> "Supported"; 0 -> "Not supported"; else -> "Not known yet" })
        if (hint != null) {
            Text("*Filename hint only", Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.s))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.45f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.55f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusBlock(d: DownloadEntity) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    when (d.status) {
        DownloadStatus.QUEUED -> Text("Queued", color = sub)
        DownloadStatus.DOWNLOADING -> {
            if (d.totalBytes > 0) {
                val fraction = (d.downloadedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f)
                val animated by animateFloatAsState(fraction, label = "progress")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    LinearProgressIndicator(progress = { animated }, modifier = Modifier.weight(1f))
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            val parts = buildList {
                add("${formatBytes(d.downloadedBytes)} / ${formatBytes(d.totalBytes)}")
                if (d.speedBps > 0) add(formatSpeed(d.speedBps))
                if (d.speedBps > 0 && d.totalBytes > 0) add(formatEta((d.totalBytes - d.downloadedBytes) / d.speedBps))
            }
            Text(parts.joinToString(" · "), color = sub, style = MaterialTheme.typography.bodySmall)
            if (d.resumable == 0) Text("This server doesn't support pausing or resuming.", color = sub, style = MaterialTheme.typography.bodySmall)
        }
        DownloadStatus.PAUSED -> Text("Paused · ${formatBytes(d.downloadedBytes)} / ${formatBytes(d.totalBytes)}", color = sub)
        DownloadStatus.COMPLETED -> Text("${formatBytes(d.downloadedBytes)} · Saved to Downloads/RoohuX", color = sub)
        DownloadStatus.CANCELED -> Text("Canceled", color = sub)
        DownloadStatus.FAILED -> {
            Text(d.error?.userMessage ?: "Download failed.", color = MaterialTheme.colorScheme.error)
            d.errorDetail?.let { Text("Details: $it", color = sub, style = MaterialTheme.typography.bodySmall) }
            if (d.resumable == 1 && d.downloadedBytes > 0) Text("Retry will continue from ${formatBytes(d.downloadedBytes)}.", color = sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
