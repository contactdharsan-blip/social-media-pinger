package com.quietping.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.GlassFill
import com.quietping.ui.theme.LocalQuietPingTheme

/**
 * A single shimmering skeleton block (DESIGN.md `shimmer`, §5.5) — a frosted
 * placeholder with a looping diagonal highlight sweep. The sweep is suppressed
 * (static frost) when motion is disabled, satisfying the reduced-motion contract.
 *
 * @param modifier     external layout modifier (set width via this).
 * @param height       the block height.
 * @param cornerRadius corner radius of the placeholder.
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = GlassDefaults.CornerRadiusMd
) {
    val motionEnabled = LocalQuietPingTheme.current.motionEnabled
    val shape = RoundedCornerShape(cornerRadius)

    val highlight = GlassFill.copy(alpha = 0.18f)
    val base = GlassFill.copy(alpha = 0.06f)

    val brush = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerProgress"
        )
        val shift = progress * 600f
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(shift - 300f, 0f),
            end = Offset(shift, 0f)
        )
    } else {
        Brush.linearGradient(listOf(base, base))
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(brush, shape)
    )
}

/**
 * A ready-made multi-line shimmer placeholder for list/card loading states.
 *
 * @param modifier external layout modifier.
 * @param lines    number of skeleton lines to render.
 */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
    lines: Int = 3
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(lines.coerceAtLeast(1)) { index ->
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.6f else 1f),
                height = 16.dp
            )
            if (index != lines - 1) {
                Box(modifier = Modifier.height(12.dp))
            }
        }
    }
}
