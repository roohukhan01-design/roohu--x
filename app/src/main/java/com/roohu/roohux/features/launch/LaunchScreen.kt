package com.roohu.roohux.features.launch

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roohu.roohux.AppContainer
import com.roohu.roohux.core.database.DownloadStatus
import com.roohu.roohux.core.design.Accent
import com.roohu.roohux.core.design.Ink
import com.roohu.roohux.core.design.LocalReduceMotion
import com.roohu.roohux.core.design.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BRAND_MOMENT_MS = 900L

/**
 * In-app launch screen shown right after the system splash (same background and mark, so there is no flash).
 * The progress line reflects REAL start-up work: saved settings, then the library database. After that it fades out;
 * the only extra wait is the short brand animation (BRAND_MOMENT_MS total). With Reduce Motion it dismisses immediately.
 */
@Composable
fun LaunchScreen(container: AppContainer, onFinished: () -> Unit) {
    val reduce = LocalReduceMotion.current
    var steps by remember { mutableIntStateOf(0) }
    val ring = remember { Animatable(if (reduce) 1f else 0f) }
    val mark = remember { Animatable(if (reduce) 1f else 0f) }
    val word = remember { Animatable(if (reduce) 1f else 0f) }
    val glow = remember { Animatable(if (reduce) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduce) {
            launch { ring.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
            launch { glow.animateTo(1f, tween(700)) }
            launch { delay(230); mark.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { delay(330); word.animateTo(1f, tween(380)) }
        }
    }
    LaunchedEffect(Unit) {
        val started = SystemClock.uptimeMillis()
        container.settings.settings.first()
        steps = 1
        withContext(Dispatchers.IO) { container.database.downloadDao().byStatus(DownloadStatus.QUEUED) } // opens the database
        steps = 2
        if (!reduce) {
            val left = BRAND_MOMENT_MS - (SystemClock.uptimeMillis() - started)
            if (left > 0) delay(left)
        }
        onFinished()
    }

    val progress by animateFloatAsState(steps / 2f, tween(220), label = "init")
    val status = when (steps) { 0 -> "Loading settings"; 1 -> "Opening library"; else -> "Ready" }

    Box(
        Modifier.fillMaxSize().background(Ink)
            .pointerInput(Unit) { // block touches while the screen is up
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(168.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.4f
                drawCircle(
                    Brush.radialGradient(listOf(Accent.copy(alpha = 0.28f * glow.value), Color.Transparent), center = c, radius = size.minDimension / 2f),
                    radius = size.minDimension / 2f, center = c,
                )
                drawCircle(Color.White.copy(alpha = 0.06f), radius = r, center = c, style = Stroke(width = 4.dp.toPx()))
                drawArc(
                    color = Accent, startAngle = -90f, sweepAngle = 360f * ring.value, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
                val s = 0.55f + 0.45f * mark.value
                val path = Path().apply {
                    moveTo(c.x - r * 0.22f * s, c.y - r * 0.42f * s)
                    lineTo(c.x - r * 0.22f * s, c.y + r * 0.42f * s)
                    lineTo(c.x + r * 0.46f * s, c.y)
                    close()
                }
                drawPath(path, Accent.copy(alpha = mark.value.coerceIn(0f, 1f)))
            }
            Spacer(Modifier.height(Spacing.l))
            Text("RoohuX", Modifier.alpha(word.value), style = MaterialTheme.typography.displayMedium, color = Color(0xFFECEDF2))
            Text("Browse. Save. Play.", Modifier.alpha(word.value), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9AA3B5))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(120.dp).height(2.dp),
                color = Accent, trackColor = Color.White.copy(alpha = 0.1f),
            )
            Spacer(Modifier.height(Spacing.s))
            Text(status, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA3B5))
        }
    }
}
