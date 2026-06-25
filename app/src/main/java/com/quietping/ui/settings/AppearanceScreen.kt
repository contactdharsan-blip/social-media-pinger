package com.quietping.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quietping.R
import com.quietping.domain.settings.ThemeSettings
import com.quietping.icon.IconSwitcherImpl
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.BgTertiary
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.OnAccent
import com.quietping.ui.theme.QuietPingTheme
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass
import com.quietping.ui.theme.motionExit
import com.quietping.ui.theme.motionScaleIn
import com.quietping.ui.theme.parseHexColor

/**
 * Appearance settings (PRD 6E / §9.1): a live-preview card plus accent color
 * presets, a glass-intensity slider, a motion toggle, a dark/light switch, and the
 * app-icon switcher grid. All theme edits persist immediately and re-render the
 * preview using the pending [ThemeSettings].
 *
 * Content-only: the host Scaffold lives in the nav graph.
 */
@Composable
fun AppearanceScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsScreenHeader(
                title = "Appearance",
                subtitle = "Re-skin QuietPing. Changes apply live and stay on-device."
            )
        }

        item { ThemePreviewCard(pending = state.pending) }

        item {
            SettingsGlassCard {
                SectionLabel("Accent color")
                Spacer(Modifier.height(12.dp))
                AccentGrid(
                    selected = state.selectedAccent,
                    onSelect = viewModel::setAccent
                )
            }
        }

        item {
            SettingsGlassCard {
                SectionLabel("Surface & motion")
                Spacer(Modifier.height(8.dp))
                GlassIntensityControl(
                    value = state.pending.glassIntensity,
                    onChange = viewModel::setGlassIntensity
                )
                ThinDivider()
                ToggleRow(
                    icon = Icons.Outlined.Bolt,
                    title = "Motion",
                    subtitle = "Spring animations (honors system reduce-motion)",
                    trailing = {
                        GlassSwitch(
                            checked = state.pending.motionEnabled,
                            onCheckedChange = viewModel::setMotionEnabled
                        )
                    }
                )
                ThinDivider()
                ToggleRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark canvas",
                    subtitle = "Near-black background (recommended)",
                    trailing = {
                        GlassSwitch(
                            checked = state.pending.darkMode,
                            onCheckedChange = viewModel::setDarkMode
                        )
                    }
                )
            }
        }

        item {
            SettingsGlassCard {
                SectionLabel("App icon")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Switching the icon briefly refreshes your launcher. " +
                        "Stealth disguises the app as a generic utility.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Spacer(Modifier.height(12.dp))
                IconGrid(
                    aliases = state.iconAliases,
                    active = state.activeIconAlias,
                    onSelect = viewModel::selectIcon
                )
            }
        }
    }
}

/**
 * The live-preview card. It wraps a miniature mock of the app's surfaces in a nested
 * [QuietPingTheme] driven by the *pending* settings, so the accent / glass / motion
 * choices are visible before the user leaves the screen.
 */
@Composable
private fun ThemePreviewCard(pending: ThemeSettings) {
    SettingsGlassCard {
        SectionLabel("Live preview")
        Spacer(Modifier.height(12.dp))
        QuietPingTheme(themeSettings = pending) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // A mock match-feed row inside a glass surface.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(intensity = pending.glassIntensity, cornerRadius = GlassDefaults.CornerRadiusMd)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Name mentioned",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "\"...can you review this, @you?\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // A mock primary button so the accent gradient is visible.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Primary action",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** A wrapping grid of accent color swatches. */
@Composable
private fun AccentGrid(
    selected: AccentPreset?,
    onSelect: (AccentPreset) -> Unit
) {
    val presets = AccentPreset.entries
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { preset ->
                    AccentSwatch(
                        preset = preset,
                        selected = preset == selected,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AccentSwatch(
    preset: AccentPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = parseHexColor(preset.hex, fallback = Emerald400)
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = MotionTokens.signatureSpring(),
        label = "accentBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Transparent,
        animationSpec = MotionTokens.signatureSpring(),
        label = "accentBorderColor"
    )
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = motionScaleIn(),
                exit = motionExit()
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = OnAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else TextTertiary
        )
    }
}

/** The glass-intensity slider with a leading blur icon and a live percentage. */
@Composable
private fun GlassIntensityControl(value: Float, onChange: (Float) -> Unit) {
    val animated by animateFloatAsState(targetValue = value, label = "glassIntensity")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.BlurOn,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Glass intensity",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = Emerald400
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Emerald400,
                activeTrackColor = Emerald400,
                inactiveTrackColor = BgTertiary
            )
        )
    }
}

/** The app-icon switcher grid. */
@Composable
private fun IconGrid(
    aliases: List<String>,
    active: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        aliases.forEach { alias ->
            IconChoice(
                alias = alias,
                selected = alias == active,
                onClick = { onSelect(alias) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun IconChoice(
    alias: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) Emerald400 else com.quietping.ui.theme.CardBorder,
        animationSpec = MotionTokens.signatureSpring(),
        label = "iconBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = MotionTokens.signatureSpring(),
        label = "iconBorderWidth"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
                .background(BgTertiary)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(GlassDefaults.CornerRadiusLg)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconDrawableFor(alias)),
                contentDescription = alias,
                tint = Color.Unspecified,
                modifier = Modifier.size(40.dp)
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = motionScaleIn(),
                exit = motionExit(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Emerald400),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = OnAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = alias,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else TextTertiary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Map an icon alias to a representative drawable for the grid preview. */
private fun iconDrawableFor(alias: String): Int = when (alias) {
    IconSwitcherImpl.ALIAS_MONO -> R.drawable.ic_launcher_mono
    IconSwitcherImpl.ALIAS_STEALTH -> R.drawable.ic_launcher_stealth
    else -> R.drawable.ic_launcher_foreground
}
