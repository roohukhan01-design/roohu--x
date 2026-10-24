package com.roohu.roohux.features.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roohu.roohux.core.design.GlassCard
import com.roohu.roohux.core.design.HoloOrb
import com.roohu.roohux.core.design.Spacing

private const val SUPPORT_EMAIL = "roohukhan01@gmail.com"

@Composable
fun AboutScreen(onBack: () -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${info.versionName}"
        }.getOrDefault("RoohuX")
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("About", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HoloOrb(Modifier.size(180.dp))
            Text("RoohuX", style = MaterialTheme.typography.displayMedium)
            Text("Browse. Save. Play.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(version, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("Developer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Roohu", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.s))
            Text("Support", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(SUPPORT_EMAIL, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton({
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Support email", SUPPORT_EMAIL))
                    onMessage("Email address copied.")
                }) { Text("Copy") }
                Button({
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).putExtra(Intent.EXTRA_SUBJECT, "RoohuX support")
                    try { context.startActivity(intent) } catch (e: Exception) { onMessage("No email app found. Address: $SUPPORT_EMAIL") }
                }) { Text("Email") }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("Privacy principles", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.s))
            listOf(
                "No account or sign-in.",
                "No analytics, ads or trackers.",
                "Downloads, history, bookmarks and playback progress stay on this device.",
                "Websites can't use your camera, microphone or location.",
                "RoohuX doesn't bypass DRM, paywalls or sign-in.",
            ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("Acknowledgements", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.s))
            listOf(
                "Poppins typeface: Indian Type Foundry, SIL Open Font License 1.1",
                "Jetpack Compose, Media3, Room, DataStore: Android Open Source Project, Apache License 2.0",
                "OkHttp: Square, Apache License 2.0",
                "Guava: Google, Apache License 2.0",
            ).forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Text("Made by Roohu", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
