package com.quietping.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.domain.model.AppPackage
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.NeutralGray
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextTertiary

/** Human-readable label for an [AppPackage], shown on toggle cards and rule lists. */
fun AppPackage.displayLabel(): String = when (this) {
    AppPackage.WHATSAPP -> "WhatsApp"
    AppPackage.INSTAGRAM -> "Instagram"
    AppPackage.MESSENGER -> "Messenger"
    AppPackage.FACEBOOK -> "Facebook"
    AppPackage.SMS -> "Messages"
}

/** Representative Material icon for an [AppPackage] (no brand assets / emoji). */
fun AppPackage.glyph(): ImageVector = when (this) {
    AppPackage.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    AppPackage.INSTAGRAM -> Icons.Filled.CameraAlt
    AppPackage.MESSENGER -> Icons.Filled.Forum
    AppPackage.FACEBOOK -> Icons.AutoMirrored.Filled.Message
    AppPackage.SMS -> Icons.Filled.Sms
}

/**
 * A per-app on/off card for the Home dashboard (PRD §9.1). Shows the app glyph in a
 * disc that lights up to the accent when [enabled], the app name, an optional
 * [subtitle] (e.g. "12 matches today"), and a trailing [Switch]. The whole card is
 * tappable to toggle, and the icon-disc tint springs between active/idle.
 *
 * @param appPackage which target app this card controls.
 * @param enabled    current on/off state.
 * @param onToggle   invoked with the requested new state.
 * @param modifier   external layout modifier.
 * @param subtitle   optional status line under the name.
 */
@Composable
fun AppToggleCard(
    appPackage: AppPackage,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val accent = LocalQuietPingTheme.current.accent
    val discColor by animateColorAsState(
        targetValue = if (enabled) accent.copy(alpha = 0.18f) else NeutralGray.copy(alpha = 0.12f),
        animationSpec = MotionTokens.signatureSpring(),
        label = "appToggleDisc"
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) accent else TextTertiary,
        animationSpec = MotionTokens.signatureSpring(),
        label = "appToggleIcon"
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { onToggle(!enabled) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        role = Role.Switch,
        stateDescription = if (enabled) "On" else "Off"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(discColor, RoundedCornerShape(GlassDefaults.CornerRadiusMd)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = appPackage.glyph(),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = appPackage.displayLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle ?: if (enabled) "Watching" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) accent else TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // State indicator only — the whole card is the single toggle target
            // (GlassCard role = Switch), so the switch itself takes no click.
            AccentSwitch(checked = enabled, onCheckedChange = null)
        }
    }
}
