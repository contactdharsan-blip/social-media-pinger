package com.quietping.ui.settings

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.BgTertiary
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.OnAccent
import com.quietping.ui.theme.StatusAlert
import com.quietping.ui.theme.animateSizeChange
import com.quietping.ui.theme.cascadeItem
import com.quietping.ui.theme.riseIn
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass

/**
 * Per-condition alert settings (PRD §8 / §9.1). For each [TriggerType] the user can
 * pick a sound preset (with a live preview), toggle vibration, and toggle the
 * Do-Not-Disturb override. Changes write through to the rules of that condition.
 *
 * Content-only: the Scaffold/top-bar/bottom-nav live in the nav graph.
 */
@Composable
fun AlertSettingsScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AlertSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val player = rememberSoundPreviewPlayer()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsScreenHeader(
                modifier = Modifier.riseIn(0),
                title = "Alert settings",
                subtitle = "A separate notification channel per condition — its own " +
                    "sound, vibration, and Do Not Disturb behavior."
            )
        }
        itemsIndexed(state.configs, key = { _, config -> config.triggerType.name }) { index, config ->
            AlertConditionCard(
                modifier = cascadeItem(index),
                config = config,
                onSelectPreset = { preset ->
                    viewModel.setSoundPreset(config.triggerType, preset)
                    player.preview(context, preset)
                },
                onToggleVibrate = { enabled ->
                    viewModel.setVibrate(config.triggerType, enabled)
                    if (enabled) vibratePreview(context)
                },
                onToggleDnd = { enabled ->
                    viewModel.setDndOverride(config.triggerType, enabled)
                },
                onPreview = { player.preview(context, config.soundPreset) }
            )
        }

        item(key = "digest") {
            DigestCard(
                digestEnabled = state.alerts.digestEnabled,
                digestHour = state.alerts.digestHour,
                onToggleDigest = viewModel::setDigestEnabled,
                onSelectHour = viewModel::setDigestHour
            )
        }

        item(key = "otp") {
            OtpCleanupCard(
                hours = state.alerts.otpAutoDeleteHours,
                onSelectHours = viewModel::setOtpAutoDeleteHours
            )
        }
    }
}

/** Daily digest toggle + delivery-hour picker. */
@Composable
private fun DigestCard(
    digestEnabled: Boolean,
    digestHour: Int,
    onToggleDigest: (Boolean) -> Unit,
    onSelectHour: (Int) -> Unit
) {
    SettingsGlassCard(modifier = Modifier.animateSizeChange()) {
        SectionLabel("Daily digest")
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            icon = Icons.Filled.NotificationsActive,
            title = "Summarize low-priority matches",
            subtitle = "One quiet summary a day instead of many pings",
            trailing = { GlassSwitch(checked = digestEnabled, onCheckedChange = onToggleDigest) }
        )
        AnimatedVisibility(visible = digestEnabled) {
            Column {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Deliver at",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                PrefChipRow(
                    options = DIGEST_HOURS.map { it to "%02d:00".format(it) },
                    selected = digestHour,
                    onSelect = onSelectHour
                )
            }
        }
    }
}

/** OTP auto-delete window picker. */
@Composable
private fun OtpCleanupCard(hours: Int, onSelectHours: (Int) -> Unit) {
    SettingsGlassCard(modifier = Modifier.animateSizeChange()) {
        SectionLabel("OTP auto-delete")
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Captured one-time passcodes (SMS) are deleted after this window. " +
                "Off keeps them under the normal retention limit.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Spacer(Modifier.height(12.dp))
        PrefChipRow(
            options = OTP_HOUR_OPTIONS.map { it to otpLabel(it) },
            selected = hours,
            onSelect = onSelectHours
        )
    }
}

/** A wrapping row of selectable value chips. */
@Composable
private fun PrefChipRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
                    .background(if (isSel) Emerald400 else BgTertiary.copy(alpha = 0.6f))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) OnAccent else TextSecondary,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

private val DIGEST_HOURS = listOf(7, 9, 12, 18, 21)
private val OTP_HOUR_OPTIONS = listOf(0, 6, 12, 24, 48)

private fun otpLabel(hours: Int): String = if (hours == 0) "Off" else "${hours}h"

/** A glass card for a single condition with its alert controls. */
@Composable
private fun AlertConditionCard(
    config: AlertConfig,
    onSelectPreset: (SoundPreset) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsGlassCard(modifier = modifier.animateSizeChange()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(GlassDefaults.CornerRadiusMd))
                    .background(Emerald400.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.triggerType.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = config.triggerType.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            if (config.isPending) {
                StatusPill(text = "No rules yet", color = StatusAlert)
            } else {
                StatusPill(
                    text = if (config.ruleCount == 1) "1 rule" else "${config.ruleCount} rules",
                    color = Emerald400
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Sound preset")
        Spacer(Modifier.height(8.dp))
        SoundPresetGrid(
            selected = config.soundPreset,
            onSelect = onSelectPreset
        )

        Spacer(Modifier.height(14.dp))
        ToggleRow(
            icon = Icons.Outlined.PlayArrow,
            title = "Preview tone",
            subtitle = if (config.soundPreset == SoundPreset.SILENT)
                "Silent+ is vibrate-only" else config.soundPreset.displayName,
            trailing = {
                PreviewButton(
                    enabled = config.soundPreset != SoundPreset.SILENT,
                    onClick = onPreview
                )
            }
        )

        ThinDivider()
        ToggleRow(
            icon = Icons.Outlined.Vibration,
            title = "Vibrate",
            subtitle = "Play the paired vibration pattern",
            trailing = {
                GlassSwitch(checked = config.vibrate, onCheckedChange = onToggleVibrate)
            }
        )

        ThinDivider()
        ToggleRow(
            icon = Icons.Outlined.DoNotDisturbOn,
            title = "Bypass Do Not Disturb",
            subtitle = "Alert even while DND is on",
            trailing = {
                GlassSwitch(checked = config.dndOverride, onCheckedChange = onToggleDnd)
            }
        )

        AnimatedVisibility(
            visible = config.isPending,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No rule uses this condition yet. Your choice applies " +
                        "automatically once you add one in Rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/** A wrapping grid of selectable sound-preset chips. */
@Composable
private fun SoundPresetGrid(
    selected: SoundPreset,
    onSelect: (SoundPreset) -> Unit
) {
    // Two-column flow keeps each chip a comfortable touch target on mobile.
    val presets = SoundPreset.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(2).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPresets.forEach { preset ->
                    PresetChip(
                        preset = preset,
                        selected = preset == selected,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: SoundPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Emerald400 else BgTertiary.copy(alpha = 0.6f),
        animationSpec = MotionTokens.signatureSpring(),
        label = "presetChipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) OnAccent else TextSecondary,
        animationSpec = MotionTokens.signatureSpring(),
        label = "presetChipFg"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun PreviewButton(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Emerald400 else TextTertiary
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
            .background(tint.copy(alpha = 0.16f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = "Preview tone",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- Sound + haptic preview helpers ---

/** A small composable-scoped owner for the one-shot preview [MediaPlayer]. */
private class SoundPreviewPlayer {
    private var current: MediaPlayer? = null

    fun preview(context: Context, preset: SoundPreset) {
        if (preset == SoundPreset.SILENT) {
            vibratePreview(context)
            return
        }
        release()
        val resId = context.resources.getIdentifier(
            preset.rawName, "raw", context.packageName
        )
        if (resId == 0) return
        current = MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener {
                it.release()
                if (current === it) current = null
            }
            start()
        }
    }

    fun release() {
        current?.runCatching { stop() }
        current?.release()
        current = null
    }
}

@Composable
private fun rememberSoundPreviewPlayer(): SoundPreviewPlayer {
    val player = remember { SoundPreviewPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}

/** Fire a short confirmation haptic for the vibrate toggle/preview. */
private fun vibratePreview(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(60)
    }
}

// --- Shared small UI primitives (kept local to the settings screens) ---

@Composable
internal fun SettingsScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
    }
}

@Composable
internal fun SettingsGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(cornerRadius = GlassDefaults.CornerRadius)
            .padding(16.dp),
        content = content
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary
    )
}

@Composable
internal fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun ThinDivider() {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(com.quietping.ui.theme.Divider)
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = OnAccent,
            checkedTrackColor = Emerald400,
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = BgTertiary,
            uncheckedBorderColor = Color.Transparent
        )
    )
}
