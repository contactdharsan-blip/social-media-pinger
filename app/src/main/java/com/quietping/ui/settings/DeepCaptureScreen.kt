package com.quietping.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.components.SettingToggleRow
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.StatusAlert
import com.quietping.ui.theme.StatusError
import com.quietping.ui.theme.TextTertiary

/**
 * Deep capture (root) settings (FACE2_XPOSED_RD.md). On a rooted device running LSPosed,
 * the user can opt specific chat apps into in-process hooking that recovers
 * deleted/edited messages the notification listener can't see. The capability is gated:
 * if the device isn't rooted or LSPosed isn't active, the screen shows a plain explainer
 * instead of toggles — never a switch that would silently do nothing.
 *
 * Content-only: the host Scaffold (and status-bar inset) live in the nav graph.
 */
@Composable
fun DeepCaptureScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: DeepCaptureViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsScreenHeader(
                title = "Deep capture",
                subtitle = "On a rooted device with LSPosed, hook chat apps directly to " +
                    "recover messages deleted or edited before the notification was read. " +
                    "Everything stays on this device."
            )
        }

        if (state.errorMessage != null) {
            item { DeepCaptureErrorCard(message = state.errorMessage!!) }
        } else if (!state.available && !state.loading) {
            item { DeepCaptureUnavailableCard(reason = state.reason) }
        } else if (state.available) {
            item {
                GlassCard {
                    SectionHeader(title = "Hooked apps")
                    Spacer(Modifier.height(8.dp))
                    state.targets.forEachIndexed { index, target ->
                        if (index > 0) ThinDivider()
                        SettingToggleRow(
                            leadingIcon = Icons.Outlined.Visibility,
                            title = target.label,
                            subtitle = target.packageName,
                            checked = state.enabled[target.packageName] == true,
                            onCheckedChange = { enabled ->
                                viewModel.setEnabled(target.packageName, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Surfaces a failure to probe root/LSPosed availability, with a retry. */
@Composable
private fun DeepCaptureErrorCard(message: String) {
    GlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = StatusError,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/** Explains why deep capture can't be offered, in place of the toggles. */
@Composable
private fun DeepCaptureUnavailableCard(reason: DeepCaptureUnavailableReason?) {
    val message = when (reason) {
        DeepCaptureUnavailableReason.LSPOSED_INACTIVE ->
            "This device is rooted, but the LSPosed framework isn't active. Install and " +
                "activate LSPosed, then enable the QuietPing module to use deep capture."
        else ->
            "Deep capture needs a rooted device running the LSPosed framework. This " +
                "device isn't rooted, so QuietPing falls back to standard notification " +
                "capture — which still works for everything except deleted-before-read " +
                "messages."
    }
    GlassCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = StatusAlert,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Deep capture unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}
