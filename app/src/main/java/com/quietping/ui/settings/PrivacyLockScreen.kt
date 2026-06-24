package com.quietping.ui.settings

import android.content.Context
import androidx.biometric.BiometricManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.BgTertiary
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.OnAccent
import com.quietping.ui.theme.StatusError
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary

/**
 * Privacy & lock settings (PRD §11 / §9.1): biometric app-lock, the auto-purge
 * retention window, and a destructive "Purge now". A clear note explains that export
 * and cloud sync are impossible by design — the app has no INTERNET permission.
 *
 * Content-only: the host Scaffold lives in the nav graph.
 */
@Composable
fun PrivacyLockScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: PrivacyLockViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val biometricAvailable = remember { isBiometricAvailable(context) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    // Surface the purge result, then auto-dismiss it after it is shown.
    LaunchedEffect(state.purgeResult) {
        if (state.purgeResult is PurgeResult.Done) {
            kotlinx.coroutines.delay(2_500)
            viewModel.consumePurgeResult()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsScreenHeader(
                title = "Privacy & lock",
                subtitle = "The vault holds other people's messages. Lock it, limit how " +
                    "long it is kept, and clear it whenever you want."
            )
        }

        item {
            SettingsGlassCard {
                SectionLabel("App lock")
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Biometric lock",
                    subtitle = if (biometricAvailable)
                        "Require fingerprint or face to open QuietPing"
                    else
                        "No biometrics enrolled on this device",
                    trailing = {
                        GlassSwitch(
                            checked = state.privacy.biometricLock && biometricAvailable,
                            onCheckedChange = { enabled ->
                                if (biometricAvailable) viewModel.setBiometricLock(enabled)
                            }
                        )
                    }
                )
            }
        }

        item {
            SettingsGlassCard {
                SectionLabel("Retention")
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-purge after",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Captured messages older than this are deleted automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                RetentionChips(
                    selectedDays = state.privacy.retentionDays,
                    onSelect = viewModel::setRetentionDays
                )
            }
        }

        item {
            SettingsGlassCard {
                SectionLabel("Clear now")
                Spacer(Modifier.height(8.dp))
                PurgeRow(
                    result = state.purgeResult,
                    onClick = { showPurgeConfirm = true }
                )
            }
        }

        item { ExportDisabledNote() }
    }

    if (showPurgeConfirm) {
        PurgeConfirmDialog(
            onConfirm = {
                showPurgeConfirm = false
                viewModel.purgeNow()
            },
            onDismiss = { showPurgeConfirm = false }
        )
    }
}

/** Selectable retention-window chips (days). */
@Composable
private fun RetentionChips(selectedDays: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RETENTION_OPTIONS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { days ->
                    RetentionChip(
                        days = days,
                        selected = days == selectedDays,
                        onClick = { onSelect(days) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RetentionChip(
    days: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Emerald400 else BgTertiary.copy(alpha = 0.6f)
    val fg = if (selected) OnAccent else TextSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = retentionLabel(days),
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

private fun retentionLabel(days: Int): String = when {
    days % 365 == 0 -> "${days / 365}y"
    days % 30 == 0 -> "${days / 30}mo"
    else -> "${days}d"
}

/** The destructive "Purge now" row + state-aware label. */
@Composable
private fun PurgeRow(result: PurgeResult?, onClick: () -> Unit) {
    val running = result is PurgeResult.Running
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
            .background(StatusError.copy(alpha = 0.14f))
            .then(if (running) Modifier else Modifier.clickable(onClick = onClick))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = StatusError,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Purge now",
                    style = MaterialTheme.typography.bodyLarge,
                    color = StatusError,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (result) {
                        PurgeResult.Running -> "Clearing the vault…"
                        PurgeResult.Done -> "Vault cleared."
                        null -> "Delete every captured message immediately."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun PurgeConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = TextSecondary,
        icon = {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = StatusError
            )
        },
        title = { Text("Purge the vault?") },
        text = {
            Text(
                "This permanently deletes every captured message — including recovered " +
                    "and edited history. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Purge", color = StatusError, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** The on-device-only disclosure: export and cloud sync are impossible by design. */
@Composable
private fun ExportDisabledNote() {
    SettingsGlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = Emerald400,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Export & cloud are off — by design",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "QuietPing has no internet permission, so nothing can ever " +
                        "leave this device. There is intentionally no export or backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/** True when the device has usable biometric hardware with an enrolled credential. */
private fun isBiometricAvailable(context: Context): Boolean {
    val result = BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    return result == BiometricManager.BIOMETRIC_SUCCESS
}
