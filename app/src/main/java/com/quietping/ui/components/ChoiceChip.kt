package com.quietping.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.BorderSubtle
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.GlassFill
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.TextSecondary

/**
 * A single-select "choice" pill for option rows (sound preset, retention window, rule
 * matcher, etc.). Selected → accent fill + accent border + accent label; unselected →
 * frosted glass + subtle border + muted label. The selection color crossfades on the
 * signature spring (a vestibular-neutral color fade, so no reduced-motion gate needed).
 *
 * Accessibility: uses [Modifier.selectable] with [Role.RadioButton] so the selected
 * state is *announced* (not conveyed by color alone — the prior hand-rolled chips were
 * `clickable` with no role/state), and a ≥48.dp touch target.
 *
 * @param label       the chip text.
 * @param selected    whether this option is the active one.
 * @param onClick     invoked when tapped.
 * @param modifier    external layout modifier.
 * @param leadingIcon optional Material glyph before the label.
 * @param enabled     when false the chip is non-interactive.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val accent = LocalQuietPingTheme.current.accent
    val shape = RoundedCornerShape(GlassDefaults.CornerRadiusFull)
    val bg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.18f) else GlassFill,
        animationSpec = MotionTokens.signatureSpring(),
        label = "choiceChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.60f) else BorderSubtle,
        animationSpec = MotionTokens.signatureSpring(),
        label = "choiceChipBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else TextSecondary,
        animationSpec = MotionTokens.signatureSpring(),
        label = "choiceChipContent"
    )

    Row(
        modifier = modifier
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
