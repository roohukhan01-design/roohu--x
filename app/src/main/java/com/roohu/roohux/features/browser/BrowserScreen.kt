package com.roohu.roohux.features.browser

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.MutableContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BrowserScreen(manager: BrowserManager, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { manager.ensureTab() }
    DisposableEffect(Unit) {
        onDispose { manager.tabs.forEach { it.webView.onPause() } } // stop page timers/media while this screen is away
    }
    val tab = manager.active
    if (tab != null) BrowserContent(manager, tab, onNavigate, modifier) else Box(modifier.fillMaxSize())
}

@Composable
private fun BrowserContent(manager: BrowserManager, tab: BrowserTab, onNavigate: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var panel by remember { mutableStateOf<BrowserPanel?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    val bookmarks by manager.bookmarks.collectAsStateWithLifecycle()
    val bookmarked = tab.url.isNotBlank() && bookmarks.any { it.url == tab.url }

    LaunchedEffect(manager.pendingPanel) { manager.pendingPanel?.let { panel = it; manager.pendingPanel = null } }
    BackHandler(enabled = tab.canGoBack && !tab.blank) { tab.webView.goBack() }

    Column(modifier.fillMaxSize()) {
        AddressBar(manager, tab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab.blank) NewTabPage(manager, tab) else key(tab.id) { WebHost(manager, tab) }
            tab.error?.let { ErrorOverlay(tab, it) { tab.error = null; manager.reloadOrStop(tab) } }
            tab.httpStatus?.let { code ->
                Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text("The server reported an error ($code).", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (tab.media.any { it.kind == MediaKind.DIRECT_VIDEO || it.kind == MediaKind.DIRECT_AUDIO } && !tab.chipDismissed && tab.error == null) {
                MediaChip(tab, Modifier.align(Alignment.TopEnd).padding(8.dp)) { panel = BrowserPanel.MEDIA }
            }
        }
        if (finding) FindBar(tab) { finding = false; tab.webView.clearMatches() }
        BottomBar(
            tab = tab,
            tabCount = manager.tabs.size,
            onReload = { manager.reloadOrStop(tab) },
            onTabs = { panel = BrowserPanel.TABS },
            menuOpen = menuOpen,
            onMenu = { menuOpen = it },
        ) {
            @Composable fun item(label: String, action: () -> Unit) = DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
            item("New tab") { manager.newTab() }
            if (!tab.blank) item("Find media / Download") { panel = BrowserPanel.MEDIA }
            if (!tab.blank) {
                item(if (bookmarked) "Remove bookmark" else "Add bookmark") { manager.toggleBookmark(tab) }
                item("Find in page") { finding = true }
                item(if (tab.desktop) "Desktop site: on (tap for mobile)" else "Desktop site: off (tap to enable)") { manager.setDesktop(tab, !tab.desktop) }
                item("Share page") { sharePage(context, tab.url) }
                item("Copy link") { copyLink(context, tab.url); manager.say("Link copied.") }
                item("Media & downloads") { panel = BrowserPanel.MEDIA }
                item("Open in another app") { openElsewhere(context, tab.url, manager) }
            }
            HorizontalDivider()
            item("Bookmarks") { panel = BrowserPanel.BOOKMARKS }
            item("History") { panel = BrowserPanel.HISTORY }
            HorizontalDivider()
            item("Dashboard") { onNavigate("home") }
            item("Downloads") { onNavigate("downloads") }
            item("Library") { onNavigate("library") }
            item("Settings") { onNavigate("settings") }
        }
    }

    when (panel) {
        BrowserPanel.TABS -> TabsSheet(manager) { panel = null }
        BrowserPanel.BOOKMARKS -> BookmarksHistorySheet(manager, startOnHistory = false) { panel = null }
        BrowserPanel.HISTORY -> BookmarksHistorySheet(manager, startOnHistory = true) { panel = null }
        BrowserPanel.MEDIA -> MediaSheet(manager, tab) { panel = null }
        null -> Unit
    }
}

@Composable
private fun AddressBar(manager: BrowserManager, tab: BrowserTab) {
    val focus = LocalFocusManager.current
    var text by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).onFocusChanged { focused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Search or enter address") },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); manager.load(tab, text) }),
            leadingIcon = if (!tab.blank && tab.url.isNotEmpty()) {
                {
                    val secure = tab.url.startsWith("https://")
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = if (secure) "Secure connection" else "Not secure",
                        tint = if (secure) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
            } else null,
            trailingIcon = {
                if (focused && text.isNotEmpty()) IconButton({ text = "" }) { Icon(Icons.Default.Close, "Clear address") }
            },
        )
        if (tab.loading) LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth())
        else Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BottomBar(
    tab: BrowserTab,
    tabCount: Int,
    onReload: () -> Unit,
    onTabs: () -> Unit,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ tab.webView.goBack() }, enabled = tab.canGoBack && !tab.blank) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            IconButton({ tab.webView.goForward() }, enabled = tab.canGoForward && !tab.blank) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward") }
            IconButton(onReload, enabled = !tab.blank) {
                if (tab.loading) Icon(Icons.Default.Close, "Stop loading") else Icon(Icons.Default.Refresh, "Reload")
            }
            IconButton(onTabs) {
                Box(Modifier.size(26.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    Text("$tabCount", style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                IconButton({ onMenu(true) }) { Icon(Icons.Default.MoreVert, "Menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }, content = menuContent)
            }
        }
    }
}

@Composable
private fun WebHost(manager: BrowserManager, tab: BrowserTab) {
    val appContext = LocalContext.current.applicationContext
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        modifier = Modifier.fillMaxSize(),
        onRelease = { frame ->
            frame.removeAllViews()
            (tab.webView.context as? MutableContextWrapper)?.baseContext = appContext
        },
        update = { frame ->
            val wv = tab.webView
            (wv.context as? MutableContextWrapper)?.baseContext = frame.context // activity context for dialogs etc.
            if (wv.parent !== frame) {
                (wv.parent as? ViewGroup)?.removeView(wv)
                frame.removeAllViews()
                frame.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            wv.onResume()
        },
    )
}

@Composable
private fun NewTabPage(manager: BrowserManager, tab: BrowserTab) {
    val bookmarks by manager.bookmarks.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("RoohuX Browser", style = MaterialTheme.typography.headlineSmall)
        Text("Type a web address or a search above. Direct file links start downloads automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bookmarks.isNotEmpty()) {
            Text("Bookmarks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            bookmarks.take(8).forEach { b ->
                Text(b.title, Modifier.fillMaxWidth().clickable { manager.load(tab, b.url) }.padding(vertical = 8.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ErrorOverlay(tab: BrowserTab, message: String, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Can't open this page", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(tab.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun MediaChip(tab: BrowserTab, modifier: Modifier, onOpen: () -> Unit) {
    val downloadable = tab.media.any { it.kind == MediaKind.DIRECT_VIDEO || it.kind == MediaKind.DIRECT_AUDIO }
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), shadowElevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Download available",
                Modifier.clickable(onClick = onOpen).padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton({ tab.chipDismissed = true }, Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Dismiss", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun FindBar(tab: BrowserTab, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) tab.webView.clearMatches() else tab.webView.findAllAsync(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Find in page") },
            )
            Text(if (query.isBlank() || tab.findTotal == 0) "0" else "${tab.findCurrent + 1}/${tab.findTotal}", Modifier.padding(horizontal = 8.dp))
            TextButton({ tab.webView.findNext(false) }) { Text("Prev") }
            TextButton({ tab.webView.findNext(true) }) { Text("Next") }
            IconButton(onClose) { Icon(Icons.Default.Close, "Close find") }
        }
    }
}

private fun sharePage(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, "Share page").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RoohuX link", url))
}

private fun openElsewhere(context: Context, url: String, manager: BrowserManager) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        manager.say("No other app can open this link.")
    }
}
