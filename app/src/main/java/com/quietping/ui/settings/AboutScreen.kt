package com.quietping.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quietping.ui.components.AccentIconDisc
import com.quietping.ui.components.GlassButton
import com.quietping.ui.components.GlassButtonStyle
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.PillBadge
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.theme.Spacing
import com.quietping.ui.theme.StatusSuccess
import com.quietping.ui.theme.StatusWarning
import com.quietping.ui.theme.TabularFigures
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary

/**
 * About screen (PRD §9.1): app version, the live status of the core notification-access
 * permission (so it can be reviewed/re-granted post-onboarding), and the on-device
 * privacy statement that is the project's defining promise. Reachable from the Settings
 * hub. Full-screen route — the NavGraph supplies the status-bar inset.
 */
@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    // Live notification-listener grant state, re-checked whenever the screen resumes
    // (e.g. returning from the system settings the button below opens).
    var notificationAccess by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = isNotificationAccessEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ScreenH, vertical = Spacing.ScreenV),
        verticalArrangement = Arrangement.spacedBy(Spacing.Item)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        // Identity card.
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Item)
            ) {
                AccentIconDisc(icon = Icons.Outlined.Lock, contentDescription = null, size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("QuietPing", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    if (versionName.isNotEmpty()) {
                        Text(
                            text = "Version $versionName",
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularFigures),
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        SectionHeader(title = "Privacy")
        GlassCard {
            Text(
                text = "Everything stays on this device. QuietPing has no internet permission — " +
                    "no servers, no analytics, no cloud sync. Captured messages are encrypted at rest " +
                    "and auto-purged on your retention schedule. QuietPing is free and open source, so " +
                    "you don't have to take our word for it — the privacy promise is verifiable in the code.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        SectionHeader(title = "Permissions")
        GlassCard {
            Row(
                modifier = Modifier.padding(bottom = Spacing.Item),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Item)
            ) {
                AccentIconDisc(icon = Icons.Outlined.Notifications, contentDescription = null)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Notification access", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = "The primary capture source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                if (notificationAccess) {
                    PillBadge(text = "On", color = StatusSuccess, contentDescription = "Notification access on")
                } else {
                    PillBadge(text = "Off", color = StatusWarning, contentDescription = "Notification access off")
                }
            }
            GlassButton(
                text = "Manage in system settings",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                style = GlassButtonStyle.Secondary
            )
        }

        Spacer(Modifier.height(Spacing.Section))
    }
}

/** True when QuietPing's NotificationListenerService is enabled in system settings. */
private fun isNotificationAccessEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.split(":")?.any { it.substringBefore('/') == context.packageName } == true
}
