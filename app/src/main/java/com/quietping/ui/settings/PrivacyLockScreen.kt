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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quietping.ui.components.ChoiceChip
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.components.SettingToggleRow
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.StatusError
import com.quietping.ui.theme.animateSizeChange
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
    var showDecoyDialog by remember { mutableStateOf(false) }

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
            GlassCard {
                SectionHeader(title = "App lock")
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    leadingIcon = Icons.Outlined.Fingerprint,
                    title = "Biometric lock",
                    subtitle = if (biometricAvailable)
                        "Require fingerprint or face to open QuietPing"
                    else
                        "No biometrics enrolled on this device",
                    checked = state.privacy.biometricLock && biometricAvailable,
                    onCheckedChange = { enabled ->
                        if (biometricAvailable) viewModel.setBiometricLock(enabled)
                    },
                    enabled = biometricAvailable
                )
                Spacer(Modifier.height(8.dp))
                DecoyPinRow(
                    configured = state.privacy.decoyPinEnabled,
                    onClick = { showDecoyDialog = true }
                )
            }
        }

        item {
            GlassCard {
                SectionHeader(title = "Screen privacy")
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    leadingIcon = Icons.Outlined.Visibility,
                    title = "Block screenshots",
                    subtitle = "Stop screenshots, screen recording, and recents previews",
                    checked = state.privacy.screenshotBlock,
                    onCheckedChange = viewModel::setScreenshotBlock
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    leadingIcon = Icons.Outlined.NotificationsOff,
                    title = "Hide alert content",
                    subtitle = "Show a generic alert instead of the message text",
                    checked = state.privacy.hideNotificationContent,
                    onCheckedChange = viewModel::setHideNotificationContent
                )
                Spacer(Modifier.height(8.dp))
                SettingToggleRow(
                    leadingIcon = Icons.Outlined.Warning,
                    title = "Break-in log",
                    subtitle = "Record failed unlock attempts",
                    checked = state.privacy.breakInLogEnabled,
                    onCheckedChange = viewModel::setBreakInLogEnabled
                )
            }
        }

        if (state.privacy.breakInLogEnabled) {
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(title = "Break-in attempts", modifier = Modifier.weight(1f))
                        if (state.breakIns.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearBreakIns) {
                                Text("Clear", color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    when {
                        state.errorMessage != null -> Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusError
                        )
                        state.breakIns.isEmpty() -> Text(
                            text = "No failed unlock attempts recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                        else -> state.breakIns.take(8).forEach { event ->
                            Text(
                                text = "• ${breakInLabel(event.reason)} — ${formatTime(event.attemptedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionHeader(title = "Advanced capture")
                Spacer(Modifier.height(8.dp))
                DeepCaptureRow(onClick = { onNavigate(Dest.DeepCapture) })
            }
        }

        item {
            GlassCard {
                SectionHeader(title = "Retention")
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
            GlassCard {
                SectionHeader(title = "Clear now")
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

    if (showDecoyDialog) {
        DecoyPinDialog(
            configured = state.privacy.decoyPinEnabled,
            onSet = { pin -> viewModel.setDecoyPin(pin); showDecoyDialog = false },
            onClear = { viewModel.clearDecoyPin(); showDecoyDialog = false },
            onDismiss = { showDecoyDialog = false }
        )
    }
}

/** A row that opens the decoy-PIN dialog; shows whether one is configured. */
@Composable
private fun DecoyPinRow(configured: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Password,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Decoy PIN",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (configured)
                    "Set — entering it opens an empty vault"
                else
                    "Open an empty vault under coercion",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DecoyPinDialog(
    configured: Boolean,
    onSet: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalQuietPingTheme.current.accent
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = TextSecondary,
        icon = { Icon(Icons.Outlined.Password, contentDescription = null, tint = accent) },
        title = { Text(if (configured) "Change decoy PIN" else "Set decoy PIN") },
        text = {
            Column {
                Text(
                    "Entering this PIN at the lock screen opens an empty vault — useful if " +
                        "someone forces you to unlock the app. Use a different PIN from your real one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) pin = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New PIN (4–12 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (pin.length >= 4) onSet(pin) },
                enabled = pin.length >= 4
            ) {
                Text("Save", color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            if (configured) {
                TextButton(onClick = onClear) { Text("Remove", color = StatusError) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

private fun breakInLabel(reason: String): String = when (reason) {
    "biometric_failed" -> "Failed biometric"
    "pin_failed" -> "Wrong PIN"
    else -> "Failed unlock"
}

/** Short absolute time label for a break-in row (local time, no date library churn). */
private fun formatTime(epochMillis: Long): String {
    val dt = java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
    return "%04d-%02d-%02d %02d:%02d".format(
        dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute
    )
}

/** Selectable retention-window chips (days). */
@Composable
private fun RetentionChips(selectedDays: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RETENTION_OPTIONS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { days ->
                    ChoiceChip(
                        label = retentionLabel(days),
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

private fun retentionLabel(days: Int): String = when {
    days % 365 == 0 -> "${days / 365}y"
    days % 30 == 0 -> "${days / 30}mo"
    else -> "${days}d"
}

/** A row that opens the Deep capture (root/LSPosed) sub-screen. */
@Composable
private fun DeepCaptureRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Deep capture (root)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Hook chat apps on a rooted LSPosed device to recover " +
                    "deleted-before-read messages.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The destructive "Purge now" row + state-aware label. */
@Composable
private fun PurgeRow(result: PurgeResult?, onClick: () -> Unit) {
    val running = result is PurgeResult.Running
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateSizeChange()
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
    val accent = LocalQuietPingTheme.current.accent
    GlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = accent,
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
