package com.quietping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.GlassFill
import com.quietping.ui.theme.LocalQuietPingTheme
import com.valentinilk.shimmer.shimmer

/**
 * A single shimmering skeleton block (DESIGN.md `shimmer`, §5.5) — a frosted
 * placeholder with a looping highlight sweep, provided by `compose-shimmer`. The
 * sweep is suppressed (static frost) when motion is disabled, satisfying the
 * reduced-motion contract.
 *
 * The library's default `ShimmerBounds.Window` synchronizes the sweep across every
 * block on screen, so a multi-row skeleton shimmers as one surface rather than each
 * row animating out of phase.
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
    val base = GlassFill.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            // Reduced motion → static frost (no sweep); motion on → library shimmer.
            .then(if (motionEnabled) Modifier.shimmer() else Modifier)
            .background(base, shape)
            // Decorative placeholder — keep it out of the accessibility tree.
            .clearAndSetSemantics { }
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The skeleton blocks are decorative; announce one "Loading" for the group.
            .semantics(mergeDescendants = true) {
                contentDescription = "Loading"
                liveRegion = LiveRegionMode.Polite
            }
    ) {
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
