package com.quietping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme

/**
 * The recurring "icon inside an accent-tinted disc" motif (settings rows, toggle
 * cards, list leading slots). Standardizes the size/alpha/shape that were previously
 * hand-rolled ~9 different ways, and tints to the live user accent by default.
 *
 * The glyph is decorative by default ([contentDescription] = null) — the adjacent
 * label carries the meaning. Pass a description only when the disc stands alone.
 *
 * @param icon              the Material glyph to show.
 * @param contentDescription a11y label, or null when the icon is decorative.
 * @param modifier          external layout modifier.
 * @param size              disc diameter (glyph is sized at half the disc).
 * @param tint              glyph color (defaults to the live accent).
 * @param background        disc fill (defaults to a low-alpha wash of [tint]).
 * @param shape             disc shape (defaults to the medium rounded-square).
 */
@Composable
fun AccentIconDisc(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = LocalQuietPingTheme.current.accent,
    background: Color = tint.copy(alpha = 0.12f),
    shape: Shape = RoundedCornerShape(GlassDefaults.CornerRadiusMd)
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
