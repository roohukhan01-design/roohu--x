package com.roohu.roohux.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** True when the user enabled Reduce Motion or the system animation scale is 0. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val dark = isDarkSurface()
    val brush: Brush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF15161D), Color(0xFF0A0B0F), Color(0xFF060709)))
    } else SolidColor(MaterialTheme.colorScheme.background)
    Box(Modifier.fillMaxSize().background(brush)) { content() }
}

/** Translucent "glass" panel with a soft gradient border and a spring press effect when clickable. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val dark = isDarkSurface()
    val fill = if (dark) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.9f)
    val border: Brush = if (dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f)))
    } else SolidColor(Color.Black.copy(alpha = 0.08f))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun loopingValue(durationMillis: Int, reduce: Boolean, fixed: Float = 0.3f): State<Float> {
    if (reduce) return remember { mutableStateOf(fixed) }
    val transition = rememberInfiniteTransition(label = "loop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
}

/**
 * Lightweight 3D-style hologram: glowing core, three tilted orbital rings and drifting particles.
 * Pure Canvas (no 3D engine); the animation value is read in the draw phase only. Static when Reduce Motion is on.
 */
@Composable
fun HoloOrb(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val t = loopingValue(16000, reduce)
    val violet = Accent
    val cyan = AccentDeep
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) / 2f * 0.8f
        val phase = t.value
        drawCircle(Brush.radialGradient(listOf(violet.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r), radius = r, center = c)
        drawCircle(Brush.radialGradient(listOf(cyan.copy(alpha = 0.9f), violet.copy(alpha = 0.2f)), center = c, radius = r * 0.28f), radius = r * 0.28f, center = c)
        val tilts = floatArrayOf(0.30f, 0.55f, 0.80f)
        for (i in 0..2) {
            val angle = (phase * 360f * (if (i % 2 == 0) 1f else -1f)) + i * 60f
            rotate(angle, pivot = c) {
                scale(1f, tilts[i], pivot = c) {
                    drawCircle(
                        color = (if (i == 1) cyan else violet).copy(alpha = 0.75f),
                        radius = r * (0.95f - i * 0.12f),
                        center = c,
                        style = Stroke(width = 2.5f),
                    )
                }
            }
            val a = (phase * 2f * PI.toFloat() * (if (i % 2 == 0) 1f else -1.4f)) + i
            val rr = r * (0.95f - i * 0.12f)
            val dot = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr * tilts[i])
            rotate(angle, pivot = c) { drawCircle(Color.White.copy(alpha = 0.9f), radius = 4.5f, center = dot) }
        }
        for (i in 0 until 8) {
            val seed = i * 2.399963f
            val a = seed + phase * 2f * PI.toFloat() * 0.5f
            val rr = r * (0.45f + 0.5f * ((i * 37) % 100) / 100f)
            drawCircle(Color.White.copy(alpha = 0.25f + 0.35f * ((i * 53) % 100) / 100f), radius = 2f + (i % 3), center = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr * 0.7f))
        }
    }
}

/** Three softly pulsing dots for loading states. */
@Composable
fun PulsingDots(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val t = loopingValue(1200, reduce, 1f)
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val alpha = if (reduce) 0.8f else (0.25f + 0.75f * (0.5f + 0.5f * sin((t.value * 2f * PI.toFloat()) - i * 1.1f)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        }
    }
}
