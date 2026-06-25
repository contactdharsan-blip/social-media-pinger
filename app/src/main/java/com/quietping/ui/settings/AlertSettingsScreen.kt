package com.quietping.ui.settings

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import com.quietping.ui.components.ChoiceChip
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.ListRow
import com.quietping.ui.components.PillBadge
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.components.SettingToggleRow
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.StatusAlert
import com.quietping.ui.theme.animateSizeChange
import com.quietping.ui.theme.cascadeItem
import com.quietping.ui.theme.riseIn
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary

/**
 * Per-condition alert settings (PRD §8 / §9.1). For each [TriggerType] the user can
 * pick a sound preset (with a live preview), toggle vibration, and toggle the
 * Do-Not-Disturb override. Changes write through to the rules of that condition.
 *
 * This screen is also the bottom-nav "Settings" hub: a trailing "More" section links
 * out to Appearance, Privacy & lock, and About.
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
        state.errorMessage?.let { msg ->
            item(key = "error") {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

        item(key = "more") {
            MoreSettingsCard(onNavigate = onNavigate)
        }
    }
}

/** The "More" hub: links to the other settings screens with no inline entry point. */
@Composable
private fun MoreSettingsCard(onNavigate: (Dest) -> Unit) {
    GlassCard {
        SectionHeader(title = "More")
        Spacer(Modifier.height(8.dp))
        ListRow(
            title = "Appearance",
            subtitle = "Accent color, glass, motion, and app icon",
            leadingIcon = Icons.Outlined.Palette,
            onClick = { onNavigate(Dest.Appearance) },
            role = Role.Button,
            onClickLabel = "Open appearance settings",
            trailing = { NavChevron() }
        )
        ListRow(
            title = "Privacy & lock",
            subtitle = "App lock, retention, and clearing the vault",
            leadingIcon = Icons.Outlined.Lock,
            onClick = { onNavigate(Dest.PrivacyLock) },
            role = Role.Button,
            onClickLabel = "Open privacy and lock settings",
            trailing = { NavChevron() }
        )
        ListRow(
            title = "About",
            subtitle = "Version, on-device privacy, and credits",
            leadingIcon = Icons.Outlined.Info,
            onClick = { onNavigate(Dest.About) },
            role = Role.Button,
            onClickLabel = "Open about",
            trailing = { NavChevron() }
        )
    }
}

/** The end-aligned chevron shared by the hub's navigation rows. */
@Composable
private fun NavChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = TextTertiary,
        modifier = Modifier.size(20.dp)
    )
}

/** Daily digest toggle + delivery-hour picker. */
@Composable
private fun DigestCard(
    digestEnabled: Boolean,
    digestHour: Int,
    onToggleDigest: (Boolean) -> Unit,
    onSelectHour: (Int) -> Unit
) {
    GlassCard(modifier = Modifier.animateSizeChange()) {
        SectionHeader(title = "Daily digest")
        Spacer(Modifier.height(8.dp))
        SettingToggleRow(
            leadingIcon = Icons.Filled.NotificationsActive,
            title = "Summarize low-priority matches",
            subtitle = "One quiet summary a day instead of many pings",
            checked = digestEnabled,
            onCheckedChange = onToggleDigest
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
    GlassCard(modifier = Modifier.animateSizeChange()) {
        SectionHeader(title = "OTP auto-delete")
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
            ChoiceChip(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f)
            )
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
    val accent = LocalQuietPingTheme.current.accent
    GlassCard(modifier = modifier.animateSizeChange()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(GlassDefaults.CornerRadiusMd))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = accent,
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
                PillBadge(text = "No rules yet", color = StatusAlert)
            } else {
                PillBadge(
                    text = if (config.ruleCount == 1) "1 rule" else "${config.ruleCount} rules",
                    color = accent
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionHeader(title = "Sound preset")
        Spacer(Modifier.height(8.dp))
        SoundPresetGrid(
            selected = config.soundPreset,
            onSelect = onSelectPreset
        )

        Spacer(Modifier.height(14.dp))
        ListRow(
            leadingIcon = Icons.Outlined.PlayArrow,
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
        SettingToggleRow(
            leadingIcon = Icons.Outlined.Vibration,
            title = "Vibrate",
            subtitle = "Play the paired vibration pattern",
            checked = config.vibrate,
            onCheckedChange = onToggleVibrate
        )

        ThinDivider()
        SettingToggleRow(
            leadingIcon = Icons.Outlined.DoNotDisturbOn,
            title = "Bypass Do Not Disturb",
            subtitle = "Alert even while DND is on",
            checked = config.dndOverride,
            onCheckedChange = onToggleDnd
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
                    ChoiceChip(
                        label = preset.displayName,
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
private fun PreviewButton(enabled: Boolean, onClick: () -> Unit) {
    val accent = LocalQuietPingTheme.current.accent
    val tint = if (enabled) accent else TextTertiary
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
