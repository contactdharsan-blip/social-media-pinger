package com.quietping.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Liquid-glass surface treatment from DESIGN.md §7.1.
 *
 * Applies, scaled by [intensity] (0f..1f):
 *  - a semi-transparent white fill (frosting),
 *  - a 1px light border (the lit glass edge),
 *  - rounded corners at [cornerRadius] (default 24.dp ≈ 1.5rem, the --radius-2xl card radius).
 *
 * True backdrop blur is only available via [androidx.compose.ui.draw.blur] on the
 * content *behind* a layer, which a single Modifier cannot reach; on Android the
 * frosted look is therefore approximated with the translucent fill + border. Call
 * sites that want a real blur should apply `Modifier.blur(...)` to the background
 * layer on API 31+. This modifier degrades gracefully on all supported APIs (26+).
 *
 * @param intensity    0f = nearly invisible, 1f = full DESIGN.md glass alpha.
 * @param cornerRadius corner radius for the rounded glass panel.
 */
fun Modifier.glass(
    intensity: Float = 1f,
    cornerRadius: Dp = GlassDefaults.CornerRadius
): Modifier {
    val i = intensity.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(cornerRadius)
    // Base glass fill alpha 0.07 and border alpha 0.14 (DESIGN.md §2.2), scaled.
    val fill = GlassFill.copy(alpha = GlassFill.alpha * i)
    val borderColor = GlassBorder.copy(alpha = GlassBorder.alpha * i)
    return this
        .clip(shape)
        .background(color = fill, shape = shape)
        .border(width = 1.dp, color = borderColor, shape = shape)
}

/** Shared glass tokens. */
object GlassDefaults {
    /** --radius-2xl ≈ 1.5rem. */
    val CornerRadius: Dp = 24.dp

    /** --radius-lg (inputs/buttons). */
    val CornerRadiusLg: Dp = 16.dp

    /** --radius-md. */
    val CornerRadiusMd: Dp = 12.dp

    /** --radius-full (pills). */
    val CornerRadiusFull: Dp = 9999.dp
}

/**
 * Spring/motion tokens for the Dark Liquid-Glass system (DESIGN.md §5).
 *
 * The signature spring overshoots, matching `cubic-bezier(0.34,1.56,0.64,1)` /
 * Motion's `{ damping: 25, stiffness: 300 }`. Use these instead of hand-written
 * specs so motion stays consistent and can be globally suppressed when the user
 * disables motion (see [ThemeSettings.motionEnabled]) or reduced-motion is on.
 */
object MotionTokens {

    /** Signature overshoot spring — buttons, card entry, hovers. */
    fun <T> signatureSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium
    )

    /** Heavier spring for modal/dialog panels (DESIGN.md modalContent, damping 28). */
    fun <T> modalSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Gentle, low-stiffness spring for large reveals / expand-collapse. */
    fun <T> gentleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** IntOffset-typed signature spring for slide/enter transitions. */
    fun offsetSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium
    )

    /** Float-typed signature spring (alpha/scale convenience). */
    val floatSpring: AnimationSpec<Float> = signatureSpring()
}
