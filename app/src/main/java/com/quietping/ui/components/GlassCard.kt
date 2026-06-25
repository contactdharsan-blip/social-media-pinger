package com.quietping.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.quietping.ui.theme.Emerald300
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.Motion
import com.quietping.ui.theme.Teal400
import com.quietping.ui.theme.glass
import com.quietping.ui.theme.pressElevation
import com.quietping.ui.theme.sheen
import com.quietping.ui.theme.travelingGlowBorder

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
 * @param glow          when true, rims the card with a travelling accent glow border
 *                      (motion-gated; static accent edge under reduced motion).
 * @param sheen         when true, sweeps a faint diagonal highlight across the surface
 *                      (motion-gated; no-op under reduced motion).
 * @param glowColors    accent ramp for the travelling glow lobe.
 * @param role          accessibility role for the click action (e.g. [Role.Switch] when
 *                      the card toggles, [Role.Button] when it navigates); null = unset.
 * @param onClickLabel  accessibility label describing what the tap does ("Open thread").
 * @param stateDescription announces a toggle/selection state to screen readers ("On").
 * @param content       the card body, laid out in a [Column].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = GlassDefaults.CornerRadius,
    intensity: Float = LocalQuietPingTheme.current.glassIntensity,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    glow: Boolean = false,
    sheen: Boolean = false,
    glowColors: List<Color> = listOf(Emerald400, Teal400, Emerald300),
    role: Role? = null,
    onClickLabel: String? = null,
    stateDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    val shape = RoundedCornerShape(cornerRadius)
    var base = modifier
        .pressElevation(interaction, shape, pressedScale = Motion.PressScaleCard)
        .clip(shape)
        .glass(intensity = intensity, cornerRadius = cornerRadius)

    if (sheen) base = base.sheen()
    if (glow) base = base.travelingGlowBorder(cornerRadius = cornerRadius, colors = glowColors)

    var clickable =
        if (onClick != null) {
            base.clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = onClickLabel,
                role = role,
                onClick = onClick
            )
        } else {
            base
        }

    if (stateDescription != null) {
        clickable = clickable.semantics { this.stateDescription = stateDescription }
    }

    Box(modifier = clickable) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
