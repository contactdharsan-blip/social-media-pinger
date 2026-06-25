package com.quietping.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextTertiary

/**
 * The canonical labelled on/off row for settings screens (previously a per-screen
 * `ToggleRow` hand-rolled ~11 times, each a bare clickable `Row` whose label was not a
 * target and whose [androidx.compose.material3.Switch] state was disconnected from the
 * row click).
 *
 * Accessibility: the whole row is one [Modifier.toggleable] target with [Role.Switch]
 * and `mergeDescendants` so TalkBack reads "title, subtitle, switch, on/off" as a single
 * focusable control; the trailing [AccentSwitch] is a state *indicator*
 * (`onCheckedChange = null`) so there is no duplicate target.
 *
 * @param title           primary label.
 * @param checked         current on/off state.
 * @param onCheckedChange new-state callback.
 * @param modifier        external layout modifier.
 * @param subtitle        optional supporting line under the title.
 * @param leadingIcon     optional Material glyph shown in an accent disc.
 * @param enabled         when false the row is dimmed and non-interactive.
 */
@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            AccentIconDisc(icon = leadingIcon, contentDescription = null)
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
        AccentSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
