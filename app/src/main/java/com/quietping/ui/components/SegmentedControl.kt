package com.quietping.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.glass

/**
 * A glass segmented mode-switcher with a sliding "liquid" indicator (DESIGN.md §7.4).
 *
 * The frosted indicator pill animates between options on the signature spring
 * (suppressed when motion is disabled); only the indicator moves, not the labels.
 * Each tab is ≥48.dp tall. Generic over the option type [T].
 *
 * @param options       the selectable values, left-to-right.
 * @param selectedIndex index of the currently active option.
 * @param onSelect      invoked with the tapped option's index.
 * @param modifier      external layout modifier.
 * @param label         renders each option's display text.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String
) {
    if (options.isEmpty()) return
    val motionEnabled = LocalQuietPingTheme.current.motionEnabled
    val trackShape = RoundedCornerShape(GlassDefaults.CornerRadiusLg)
    val safeIndex = selectedIndex.coerceIn(0, options.lastIndex)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(trackShape)
            .glass(intensity = LocalQuietPingTheme.current.glassIntensity, cornerRadius = GlassDefaults.CornerRadiusLg)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * safeIndex,
            animationSpec = if (motionEnabled) MotionTokens.signatureSpring() else MotionTokens.gentleSpring(),
            label = "segmentIndicatorOffset"
        )

        // Sliding glass indicator behind the active tab.
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxSize()
                .graphicsLayer { translationX = indicatorOffset.toPx() }
                .clip(RoundedCornerShape(GlassDefaults.CornerRadiusMd))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val active = index == safeIndex
                val interaction = remember { MutableInteractionSource() }
                val weight by animateFloatAsState(
                    targetValue = if (active) 1f else 0.85f,
                    animationSpec = MotionTokens.floatSpring,
                    label = "segmentActiveFade"
                )
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(GlassDefaults.CornerRadiusMd))
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            TextSecondary.copy(alpha = weight)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
