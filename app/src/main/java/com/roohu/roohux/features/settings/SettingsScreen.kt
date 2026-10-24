package com.roohu.roohux.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roohu.roohux.core.design.GlassCard
import com.roohu.roohux.core.settings.SearchEngine
import com.roohu.roohux.core.settings.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onAbout: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Section("Appearance") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(s.theme == m, { vm.setTheme(m) }, { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            SwitchRow("Reduce motion", "Turns off the animated hologram, page transitions and looping effects.", s.reduceMotion, vm::setReduceMotion)
        }
        Section("Browser") {
            Text("Search engine", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchEngine.entries.forEach { e -> FilterChip(s.searchEngine == e, { vm.setSearchEngine(e) }, { Text(e.label) }) }
            }
            Text("Websites can't use camera, microphone or location in this version (requests are denied).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Downloads") {
            SwitchRow("Wi-Fi only", "Downloads fail with a clear message on mobile data.", s.wifiOnly, vm::setWifiOnly)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Simultaneous downloads", Modifier.weight(1f))
                OutlinedButton({ vm.setMaxSimultaneous(s.maxSimultaneous - 1) }, enabled = s.maxSimultaneous > 1) { Text("−") }
                Text("${s.maxSimultaneous}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                OutlinedButton({ vm.setMaxSimultaneous(s.maxSimultaneous + 1) }, enabled = s.maxSimultaneous < 5) { Text("+") }
            }
            Text("Location: Downloads/RoohuX (fixed in this version).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Section("Player") {
            SwitchRow("Autoplay", "Start playing as soon as you open a video or audio file.", s.autoplay, vm::setAutoplay)
            OutlinedButton(vm::clearPlaybackHistory) { Text("Clear playback history") }
        }
        Section("Privacy") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(vm::clearHistory) { Text("Clear history") }
                OutlinedButton(vm::clearCookies) { Text("Clear cookies") }
                OutlinedButton(vm::clearCache) { Text("Clear cache") }
            }
        }
        Section("About") {
            Text("RoohuX by Roohu", style = MaterialTheme.typography.titleMedium)
            Text(
                "Version, support contact, privacy principles and licences.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onAbout) { Text("About RoohuX") }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
