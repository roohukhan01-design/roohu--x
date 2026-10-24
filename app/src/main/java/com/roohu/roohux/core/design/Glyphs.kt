package com.roohu.roohux.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Drawn play/pause glyph (the core icon set has no pause icon, and this stays crisp at any size). */
@Composable
fun PlayPauseGlyph(playing: Boolean, tint: Color, modifier: Modifier = Modifier, glyphSize: Dp = 24.dp) {
    Canvas(modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        if (playing) {
            val barW = w * 0.2f
            drawRoundRect(tint, Offset(w * 0.24f, h * 0.2f), Size(barW, h * 0.6f), CornerRadius(barW / 3f))
            drawRoundRect(tint, Offset(w * 0.56f, h * 0.2f), Size(barW, h * 0.6f), CornerRadius(barW / 3f))
        } else {
            val path = Path().apply {
                moveTo(w * 0.30f, h * 0.18f)
                lineTo(w * 0.30f, h * 0.82f)
                lineTo(w * 0.82f, h * 0.50f)
                close()
            }
            drawPath(path, tint)
        }
    }
}
