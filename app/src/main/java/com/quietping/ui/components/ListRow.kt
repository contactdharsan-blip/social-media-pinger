package com.quietping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.Motion
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.pressElevation

/**
 * A standard list row: optional leading icon (in an accent-tinted disc), a
 * title + optional supporting text, and an optional trailing slot (chevron, switch,
 * badge). Tappable when [onClick] is provided; min height 56.dp for comfortable
 * touch targets.
 *
 * @param title       primary text.
 * @param modifier    external layout modifier.
 * @param subtitle    optional secondary text below the title.
 * @param leadingIcon optional Material icon shown in a tinted disc.
 * @param onClick     optional row click handler.
 * @param trailing    optional end-aligned content.
 * @param role        accessibility role for the click (e.g. [Role.Button] for nav rows).
 * @param onClickLabel accessibility label for the tap action ("Open privacy settings").
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    role: Role? = null,
    onClickLabel: String? = null
) {
    val accent = LocalQuietPingTheme.current.accent
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(GlassDefaults.CornerRadiusMd)

    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .pressElevation(interaction, shape, pressedScale = Motion.PressScaleRow)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClickLabel = onClickLabel,
                    role = role,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        .padding(horizontal = 4.dp, vertical = 8.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
