package com.quietping.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.glass

/**
 * The primary liquid-glass surface (DESIGN.md §7.1) — a frosted, rounded panel.
 *
 * Use it as the structural container for almost every grouping on a screen. When
 * [onClick] is non-null the card becomes interactive with a spring "press" scale
 * (honoring the global motion gate) and a subtle ripple.
 *
 * @param modifier      external layout modifier.
 * @param onClick       optional click handler; when set the card is tappable.
 * @param cornerRadius  glass corner radius (default the --radius-2xl card radius).
 * @param intensity     glass alpha multiplier (defaults to the user's glass intensity).
 * @param contentPadding inner padding around [content].
 * @param content       the card body, laid out in a [Column].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = GlassDefaults.CornerRadius,
    intensity: Float = LocalQuietPingTheme.current.glassIntensity,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val motionEnabled = LocalQuietPingTheme.current.motionEnabled
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && motionEnabled && onClick != null) 0.97f else 1f,
        animationSpec = MotionTokens.floatSpring,
        label = "glassCardPressScale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val base = modifier
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .clip(shape)
        .glass(intensity = intensity, cornerRadius = cornerRadius)

    val clickable =
        if (onClick != null) {
            base.clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick
            )
        } else {
            base
        }

    Box(modifier = clickable) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
