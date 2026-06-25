package com.quietping.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/**
 * Reduced-motion-aware animation helpers shared by every screen (DESIGN.md §5).
 *
 * Each helper reads [QuietPingThemeState.motionEnabled] from [LocalQuietPingTheme]
 * itself, so call sites stay a bare `animatedItem()` / `motionEnter()`
 * with no gating boilerplate — the motion gate lives in exactly one place.
 *
 * When motion is off (system reduced-motion or the user's appearance toggle), the
 * helpers degrade to a short cross-fade with **no translation or scaling**: the UI
 * change stays legible but carries zero vestibular motion. Springs come from
 * [MotionTokens] so list/screen motion matches buttons, cards, and nav.
 */
object Motion {
    /** Fade timing used for the reduced-motion fallback. */
    val ReducedFade = tween<Float>(durationMillis = 120)

    /** Standard fade for content reveals when motion is on. */
    val ContentFadeIn = tween<Float>(durationMillis = 220)

    /** Standard fade for content removals when motion is on. */
    val ContentFadeOut = tween<Float>(durationMillis = 160)

    // --- Staggered list cascade (cascadeIn / cascadeItem) ---

    /** Rows past this index share the final delay, so long lists never stall offscreen rows. */
    const val MaxCascade = 6

    /** Per-row entrance delay in the first-paint cascade. */
    val CascadeStaggerMs = 40L

    /** Upward distance a cascading row rises from as it fades in. */
    val CascadeRise = 12.dp

    // --- Content rise-in (riseIn) ---

    /** Per-step delay when static sections assemble sequentially. */
    val RiseStaggerMs = 55L

    /** Upward distance a rising section travels as it fades in. */
    val RiseDistance = 16.dp

    // --- Premium press elevation (pressElevation) ---

    /** Pressed scale for cards. */
    val PressScaleCard = 0.97f

    /** Pressed scale for list rows (shallower than cards). */
    val PressScaleRow = 0.98f

    /** Pressed scale for buttons (the deepest press — most tactile). */
    val PressScaleButton = 0.96f

    /** Shadow elevation a surface lifts to while pressed. */
    val PressLift = 6.dp

    /** Resting shadow elevation (flat) before a press. */
    val RestElevation = 0.dp
}

/**
 * Lazy-list item animation (appearance, reordering, removal). Apply to the item's
 * root `Modifier` inside `items { }`. No-op when motion is disabled so inserts and
 * removals are instant.
 */
@Composable
fun LazyItemScope.animatedItem(): Modifier =
    if (LocalQuietPingTheme.current.motionEnabled) {
        Modifier.animateItem(
            fadeInSpec = Motion.ContentFadeIn,
            placementSpec = MotionTokens.offsetSpring(),
            fadeOutSpec = Motion.ContentFadeOut
        )
    } else {
        Modifier
    }

/**
 * Animate this composable's size when its content changes (expand/collapse,
 * growing chip rows, validation messages). Instant when motion is disabled.
 */
@Composable
fun Modifier.animateSizeChange(): Modifier =
    if (LocalQuietPingTheme.current.motionEnabled) {
        this.animateContentSize(animationSpec = MotionTokens.gentleSpring<IntSize>())
    } else {
        this
    }

/**
 * Enter transition for [androidx.compose.animation.AnimatedVisibility] reveals
 * (empty states, conditional sections, banners): a fade plus a small upward slide.
 * Reduced motion → fade only.
 */
@Composable
fun motionEnter(): EnterTransition =
    if (LocalQuietPingTheme.current.motionEnabled) {
        fadeIn(MotionTokens.signatureSpring()) +
            slideInVertically(MotionTokens.offsetSpring()) { full -> full / 8 }
    } else {
        fadeIn(Motion.ReducedFade)
    }

/** Matching exit for [motionEnter]. Reduced motion → quick fade. */
@Composable
fun motionExit(): ExitTransition =
    if (LocalQuietPingTheme.current.motionEnabled) {
        fadeOut(Motion.ContentFadeOut)
    } else {
        fadeOut(Motion.ReducedFade)
    }

/**
 * Enter transition for emphasis reveals (an icon disc, a hero element): fade plus a
 * gentle scale-up from 92%. Reduced motion → fade only (no scale).
 */
@Composable
fun motionScaleIn(): EnterTransition =
    if (LocalQuietPingTheme.current.motionEnabled) {
        fadeIn(MotionTokens.signatureSpring()) +
            scaleIn(MotionTokens.signatureSpring(), initialScale = 0.92f)
    } else {
        fadeIn(Motion.ReducedFade)
    }

/**
 * First-paint staggered entrance for a lazy-list row: the row fades and rises into
 * place, delayed by its [index] so the visible rows cascade in. This complements
 * [animatedItem] (which only animates later list *mutations*) — without it, screens
 * pop in all at once on first composition.
 *
 * Only the first [Motion.MaxCascade] rows stagger; later rows share that delay so a
 * long list never holds back offscreen content. Instant (returns the receiver
 * unchanged) when motion is disabled.
 */
@Composable
fun Modifier.cascadeIn(index: Int): Modifier {
    if (!LocalQuietPingTheme.current.motionEnabled) return this
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(Motion.MaxCascade) * Motion.CascadeStaggerMs)
        anim.animateTo(1f, MotionTokens.signatureSpring())
    }
    return this.graphicsLayer {
        val p = anim.value
        alpha = p
        translationY = (1f - p) * Motion.CascadeRise.toPx()
    }
}

/**
 * Convenience for `items { }`: chains the first-paint [cascadeIn] entrance with the
 * mutation-aware [animatedItem]. Apply to the row's root `Modifier`. Both halves
 * read the motion gate internally, so this is a no-op when motion is disabled.
 */
@Composable
fun LazyItemScope.cascadeItem(index: Int): Modifier =
    Modifier
        .cascadeIn(index)
        .then(animatedItem())

/**
 * First-paint rise-in for a static, non-list screen section (a header, a settings
 * group). Sequentially-numbered sections assemble one after another via [stepIndex],
 * using the calmer [MotionTokens.modalSpring] so the page feels composed rather than
 * springy. Instant (returns the receiver unchanged) when motion is disabled.
 */
@Composable
fun Modifier.riseIn(stepIndex: Int = 0): Modifier {
    if (!LocalQuietPingTheme.current.motionEnabled) return this
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(stepIndex * Motion.RiseStaggerMs)
        anim.animateTo(1f, MotionTokens.modalSpring())
    }
    return this.graphicsLayer {
        val p = anim.value
        alpha = p
        translationY = (1f - p) * Motion.RiseDistance.toPx()
    }
}

/**
 * Premium press feedback for a tappable surface: while pressed it scales down to
 * [pressedScale] and lifts an accent-tinted shadow to [lift], adding depth the flat
 * press-scale lacked. Drive it with the same [interactionSource] you pass to
 * `clickable`, and pass the surface [shape] so the shadow matches its corners.
 *
 * No shadow and no scale (returns the receiver unchanged) when motion is disabled.
 */
@Composable
fun Modifier.pressElevation(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    pressedScale: Float = Motion.PressScaleCard,
    lift: Dp = Motion.PressLift
): Modifier {
    if (!LocalQuietPingTheme.current.motionEnabled) return this
    val accent = LocalQuietPingTheme.current.accent
    val pressed by interactionSource.collectIsPressedAsState()
    val p by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MotionTokens.floatSpring,
        label = "pressElevation"
    )
    return this
        .shadow(
            elevation = lerp(Motion.RestElevation, lift, p),
            shape = shape,
            spotColor = accent,
            ambientColor = accent
        )
        .graphicsLayer {
            val s = lerp(1f, pressedScale, p)
            scaleX = s
            scaleY = s
        }
}
