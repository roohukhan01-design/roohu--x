package com.roohu.roohux.features.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roohu.roohux.core.database.DownloadEntity
import com.roohu.roohux.core.database.DownloadStatus
import com.roohu.roohux.core.design.Accent
import com.roohu.roohux.core.design.GlassCard
import com.roohu.roohux.core.design.HoloOrb
import com.roohu.roohux.core.design.Spacing
import com.roohu.roohux.core.design.formatBytes
import com.roohu.roohux.features.browser.BrowserManager
import com.roohu.roohux.features.browser.BrowserPanel
import com.roohu.roohux.features.downloads.ui.DownloadsViewModel
import com.roohu.roohux.features.player.PlayedItem
import com.roohu.roohux.features.player.ResumePolicy
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: DownloadsViewModel,
    browser: BrowserManager,
    recents: List<PlayedItem>,
    onPlayRecent: (PlayedItem) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list by vm.downloads.collectAsStateWithLifecycle()
    val history by browser.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }

    fun startDownload() {
        error = vm.add(input)
        if (error == null) input = ""
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startDownload() }

    fun onDownloadClicked() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 && !askedForNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk && input.isNotBlank()) {
            askedForNotifications = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startDownload()
    }

    fun openBrowser() {
        if (input.isNotBlank()) { browser.go(input); input = "" }
        error = null
        onNavigate("browser")
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val active = list.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val finished = list.filter { it.status == DownloadStatus.COMPLETED }.take(3)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Box(
            Modifier.fillMaxWidth().height(210.dp).clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(listOf(Color(0xFF252039), Color(0xFF151923), Color(0xFF101116)))),
        ) {
            HoloOrb(Modifier.align(Alignment.CenterEnd).size(184.dp))
            Box(Modifier.align(Alignment.TopStart).padding(start = Spacing.l, top = Spacing.l)
                .clip(MaterialTheme.shapes.small).background(Accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("YOUR PERSONAL MEDIA SPACE", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(greeting, style = MaterialTheme.typography.labelLarge, color = Color(0xFFB5B6C8))
                Text("Everything you love.\nOne place.", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF5F3FF))
                Text("Browse · Save · Play", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB7BBC9))
            }
            Text("RX", Modifier.align(Alignment.TopEnd).padding(Spacing.l), style = MaterialTheme.typography.titleLarge, color = Accent)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("Find something good", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                placeholder = { Text("Search or paste a media link") },
                leadingIcon = { Text("⌕", style = MaterialTheme.typography.headlineMedium, color = Accent) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { openBrowser() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Button(onClick = { openBrowser() }, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.large) {
                    Text("Explore  ↗")
                }
                OutlinedButton(onClick = { onDownloadClicked() }, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.large) {
                    Text("Save link  ↓")
                }
            }
        }

        if (recents.isNotEmpty()) {
            Section("Continue") {
                recents.take(3).forEach { item -> ContinueRow(item) { onPlayRecent(item) } }
            }
        }

        Section("Quick actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuickAction("Browser", Modifier.weight(1f)) { onNavigate("browser") }
                QuickAction("Library", Modifier.weight(1f)) { onNavigate("library") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuickAction("Downloads", Modifier.weight(1f)) { onNavigate("downloads") }
                QuickAction("Bookmarks", Modifier.weight(1f)) { browser.pendingPanel = BrowserPanel.BOOKMARKS; onNavigate("browser") }
            }
        }

        if (active.isNotEmpty()) {
            Section("Downloading") {
                GlassCard(Modifier.fillMaxWidth(), onClick = { onNavigate("downloads") }) {
                    active.take(3).forEach { ActiveRow(it) }
                }
            }
        }
        if (finished.isNotEmpty()) {
            Section("Recent downloads") {
                GlassCard(Modifier.fillMaxWidth(), onClick = { onNavigate("downloads") }) {
                    finished.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.fileName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(formatBytes(d.downloadedBytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            Section("Recent sites", action = "Clear" to { browser.clearHistory() }) {
                GlassCard(Modifier.fillMaxWidth()) {
                    history.take(4).forEach { h ->
                        Text(
                            h.title,
                            Modifier.fillMaxWidth().clickable { browser.go(h.url); onNavigate("browser") }.padding(vertical = Spacing.s),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            action?.let { (label, run) -> TextButton(run) { Text(label) } }
        }
        content()
    }
}

@Composable
private fun ContinueRow(item: PlayedItem, onClick: () -> Unit) {
    val start = ResumePolicy.startPosition(item.positionMs, item.durationMs)
    val fraction = if (item.durationMs > 0) (start.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(
            (if (item.isVideo) "Video" else "Audio") + if (start > 0) " · resume at ${start / 60000}:${"%02d".format((start / 1000) % 60)}" else " · play again",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fraction > 0f) {
            Spacer(Modifier.height(Spacing.s))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QuickAction(title: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ActiveRow(d: DownloadEntity) {
    val target = if (d.totalBytes > 0) (d.downloadedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, label = "progress")
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(d.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        if (d.totalBytes > 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
