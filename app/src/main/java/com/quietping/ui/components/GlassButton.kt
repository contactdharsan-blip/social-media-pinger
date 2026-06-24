package com.quietping.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.glass

/** Visual style of a [GlassButton]. */
enum class GlassButtonStyle {
    /** Gradient accent fill + glow — the page's primary action (DESIGN.md §7.3). */
    Primary,

    /** Frosted glass fill + border — secondary actions. */
    Secondary,

    /** Borderless, text-only — tertiary / low-emphasis actions. */
    Ghost
}

/**
 * The button vocabulary for the Dark Liquid-Glass system (DESIGN.md §7.3).
 *
 * All variants share the springy press (scale 0.96 on press, honoring the global
 * motion gate) and a ≥48.dp touch target. [Primary] uses a gradient accent fill,
 * [Secondary] a frosted glass surface, [Ghost] is text-only.
 *
 * @param text     button label.
 * @param onClick  click handler (ignored when [enabled] is false).
 * @param modifier external layout modifier.
 * @param style    visual emphasis (see [GlassButtonStyle]).
 * @param enabled  when false the button is dimmed and non-interactive.
 * @param leadingIcon optional Material icon before the label.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val theme = LocalQuietPingTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && theme.motionEnabled && enabled) 0.96f else 1f,
        animationSpec = MotionTokens.floatSpring,
        label = "glassButtonPressScale"
    )

    val shape = RoundedCornerShape(GlassDefaults.CornerRadiusLg)
    val contentColor = when (style) {
        GlassButtonStyle.Primary -> MaterialTheme.colorScheme.onPrimary
        GlassButtonStyle.Secondary -> MaterialTheme.colorScheme.onSurface
        GlassButtonStyle.Ghost -> theme.accent
    }
    val alpha = if (enabled) 1f else 0.45f

    val styled = modifier
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            this.alpha = alpha
        }
        .clip(shape)
        .then(
            when (style) {
                GlassButtonStyle.Primary -> Modifier.background(
                    brush = Brush.linearGradient(
                        listOf(theme.accent, MaterialTheme.colorScheme.primaryContainer)
                    ),
                    shape = shape
                )

                GlassButtonStyle.Secondary -> Modifier
                    .glass(intensity = theme.glassIntensity, cornerRadius = GlassDefaults.CornerRadiusLg)

                GlassButtonStyle.Ghost -> Modifier.border(
                    BorderStroke(1.dp, Color.Transparent),
                    shape
                )
            }
        )
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
        .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
        .padding(horizontal = 20.dp, vertical = 12.dp)

    Box(modifier = styled, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
