package com.quietping.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated liquid-glass embellishments (DESIGN.md §5 motion + §7.1 glass), layered
 * on top of the base [Modifier.glass] frosting. Both honor the global motion gate
 * ([QuietPingThemeState.motionEnabled]): when motion is off they degrade to a static,
 * vestibular-motion-free fallback so the reduced-motion contract still holds.
 */

/**
 * A "travelling glow" border: a bright accent lobe that continuously orbits the
 * card's rounded-rect edge, leaving a soft animated halo on the perimeter.
 *
 * Implementation: a sweep gradient is drawn into an offscreen layer and rotated each
 * frame (rotating the gradient's frame makes the bright lobe orbit); the card interior
 * is then punched out with [BlendMode.Clear], leaving only a [width]-thick ring. The
 * outer rounded corners are clipped by the caller's `clip(shape)` higher in the chain.
 *
 * Reduced motion → a plain static accent edge, no animation.
 *
 * @param cornerRadius   corner radius of the surface this rims (match the card).
 * @param width    ring thickness.
 * @param colors   accent ramp for the lobe (head → mid → tail); defaults to emerald→teal.
 * @param durationMillis time for one full orbit.
 */
@Composable
fun Modifier.travelingGlowBorder(
    cornerRadius: Dp = GlassDefaults.CornerRadius,
    width: Dp = 1.5.dp,
    colors: List<Color> = listOf(Emerald400, Teal400, Emerald300),
    durationMillis: Int = 3200
): Modifier {
    if (!LocalQuietPingTheme.current.motionEnabled) {
        return this.border(width, colors.first().copy(alpha = 0.40f), RoundedCornerShape(cornerRadius))
    }

    val head = colors.getOrElse(0) { Emerald400 }
    val mid = colors.getOrElse(1) { head }
    val tail = colors.getOrElse(2) { mid }

    val transition = rememberInfiniteTransition(label = "glowBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowAngle"
    )

    return this.drawWithCache {
        val sw = width.toPx()
        val pivot = Offset(size.width / 2f, size.height / 2f)
        val outerR = cornerRadius.toPx()
        val innerR = (outerR - sw).coerceAtLeast(0f)

        // A single bright lobe (transparent → head → bright tip → tail → transparent),
        // so rotating the sweep frame reads as one light travelling round the edge.
        val brush = Brush.sweepGradient(
            0.00f to Color.Transparent,
            0.34f to head.copy(alpha = 0f),
            0.45f to head,
            0.50f to tail,
            0.55f to mid,
            0.66f to mid.copy(alpha = 0f),
            1.00f to Color.Transparent,
            center = pivot
        )

        onDrawWithContent {
            drawContent()
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                rotate(degrees = angle, pivot = pivot) {
                    drawCircle(brush = brush, radius = size.maxDimension, center = pivot)
                }
                // Punch the interior, leaving only the perimeter ring.
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(sw, sw),
                    size = Size(
                        width = (size.width - 2f * sw).coerceAtLeast(0f),
                        height = (size.height - 2f * sw).coerceAtLeast(0f)
                    ),
                    cornerRadius = CornerRadius(innerR, innerR),
                    blendMode = BlendMode.Clear
                )
                canvas.restore()
            }
        }
    }
}

/**
 * A slow diagonal "sheen": a faint translucent highlight band that sweeps across the
 * surface, giving glass a sense of reflected light. Drawn over the card content and
 * clipped by the caller's `clip(shape)`.
 *
 * Reduced motion → no-op (no sweep), keeping the surface perfectly still.
 *
 * @param color          highlight tint (defaults to white).
 * @param alpha          peak highlight opacity.
 * @param durationMillis time for one sweep across.
 */
@Composable
fun Modifier.sheen(
    color: Color = Color.White,
    alpha: Float = 0.10f,
    durationMillis: Int = 2600
): Modifier {
    if (!LocalQuietPingTheme.current.motionEnabled) return this

    val transition = rememberInfiniteTransition(label = "sheen")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheenProgress"
    )

    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.4f
        val x = -band + progress * (size.width + 2f * band)
        val brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + band, size.height)
        )
        drawRect(brush = brush)
    }
}
