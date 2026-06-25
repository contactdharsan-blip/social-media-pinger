package com.quietping.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.NeutralGray
import com.quietping.ui.theme.OnAccent
import com.quietping.ui.theme.TextTertiary

/**
 * The single Material [Switch] treatment for the whole app: track lights to the live
 * user [accent][com.quietping.ui.theme.QuietPingThemeState.accent], thumb reads on the
 * bright track, idle track is a muted gray. Previously this color block was copy-pasted
 * in five places (each subtly different / one hardcoded to emerald), which broke the
 * custom-accent contract — this is the one source of truth.
 *
 * Accessibility: when this switch lives inside a row/card that is itself `toggleable`
 * (the recommended pattern, e.g. [SettingToggleRow] / [AppToggleCard]), pass
 * `onCheckedChange = null` so the switch is a state *indicator* and the row owns the
 * single toggle target — avoiding a duplicate/disconnected accessibility focus.
 *
 * @param checked         current on/off state.
 * @param onCheckedChange new-state callback; pass `null` when an ancestor handles the toggle.
 * @param modifier        external layout modifier.
 * @param enabled         when false the switch is dimmed and non-interactive.
 */
@Composable
fun AccentSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val accent = LocalQuietPingTheme.current.accent
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = OnAccent,
            checkedTrackColor = accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = TextTertiary,
            uncheckedTrackColor = NeutralGray.copy(alpha = 0.30f),
            uncheckedBorderColor = Color.Transparent
        )
    )
}
