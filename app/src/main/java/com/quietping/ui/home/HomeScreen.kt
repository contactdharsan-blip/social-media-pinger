package com.quietping.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.TriggerType
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.NeutralGray
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.cascadeItem
import com.quietping.ui.theme.glass
import com.quietping.ui.theme.motionEnter
import com.quietping.ui.theme.motionExit
import com.quietping.ui.theme.riseIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Home dashboard (PRD §9.1): per-app toggle cards for the watched apps plus a
 * live "Recent matches" feed. Tapping a match opens the Message Vault.
 *
 * Content-only composable — the Scaffold + bottom navigation live in the nav
 * graph. The whole screen scrolls as one LazyColumn so the feed can grow.
 */
@Composable
fun HomeScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") { HomeHeader(modifier = Modifier.riseIn(0)) }

            item(key = "apps-section") {
                SectionLabel(text = "Watched apps", modifier = Modifier.riseIn(1))
            }

            itemsIndexed(uiState.apps, key = { _, status -> status.appPackage.name }) { index, status ->
                AppToggleCard(
                    status = status,
                    onToggle = { enabled ->
                        viewModel.setAppEnabled(status.appPackage, enabled)
                    },
                    modifier = cascadeItem(index)
                )
            }

            item(key = "feed-section") {
                Spacer(Modifier.height(8.dp))
                SectionLabel(text = "Recent matches", modifier = Modifier.riseIn(2))
            }

            if (uiState.isFeedEmpty) {
                item(key = "feed-empty") { FeedEmptyState() }
            } else {
                itemsIndexed(uiState.recentMatches, key = { _, match -> match.matchId }) { index, match ->
                    MatchRow(
                        item = match,
                        // The MatchLog contract exposes a messageId but not the
                        // owning conversationId, and the injected repositories do
                        // not resolve one -> open the Vault conversation list.
                        onClick = { onNavigate(Dest.Vault) },
                        modifier = cascadeItem(index)
                    )
                }
            }

            item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Greeting / brand header. */
@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QuietPing",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Silent by default. Alerts only when it matters.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Uppercase section divider label (DESIGN.md typo-label treatment). */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/** A per-app card with an icon badge, rule summary, and an on/off switch. */
@Composable
private fun AppToggleCard(
    status: AppStatus,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadius))
            .glass(cornerRadius = GlassDefaults.CornerRadius)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            icon = status.appPackage.icon(),
            tint = if (status.enabled) Emerald400 else NeutralGray
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = appSubtitle(status),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        // Default Material3 colors already track colorScheme.primary (= the active
        // emerald accent), so the switch matches the theme without binding to the
        // SwitchColors factory's individual (version-sensitive) parameter names.
        Switch(
            checked = status.enabled,
            onCheckedChange = onToggle
        )
    }
}

private fun appSubtitle(status: AppStatus): String = when {
    !status.enabled -> "Not watching"
    status.ruleCount == 1 -> "1 active rule"
    else -> "${status.ruleCount} active rules"
}

/** A single fired-alert row in the feed. */
@Composable
private fun MatchRow(
    item: MatchFeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(GlassDefaults.CornerRadius),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(cornerRadius = GlassDefaults.CornerRadius)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = item.trigger.icon(),
                tint = Emerald400
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.trigger.label(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = matchSubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = relativeTime(item.firedAt),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

private fun matchSubtitle(item: MatchFeedItem): String {
    val app = item.appPackage?.displayLabel()
    val pattern = item.pattern.takeIf { it.isNotBlank() }
    return when {
        app != null && pattern != null -> "$app  ·  $pattern"
        app != null -> app
        pattern != null -> pattern
        else -> "Alert fired"
    }
}

/** Empty-state card for the feed (DESIGN.md feedback contract). */
@Composable
private fun FeedEmptyState() {
    AnimatedVisibility(
        visible = true,
        enter = motionEnter(),
        exit = motionExit()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassDefaults.CornerRadius))
                .glass(cornerRadius = GlassDefaults.CornerRadius)
                .padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .glass(cornerRadius = GlassDefaults.CornerRadiusFull),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsOff,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "All quiet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No alerts have fired yet. When one of your rules matches, it shows up here.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.fillMaxWidth(0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** A rounded, frosted icon badge used on cards and rows. */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .glass(cornerRadius = GlassDefaults.CornerRadiusMd),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// --- Icon / label mappings (Material icons only; never emoji) -----------------

private fun AppPackage.icon(): ImageVector = when (this) {
    AppPackage.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    AppPackage.INSTAGRAM -> Icons.Filled.CameraAlt
    AppPackage.MESSENGER -> Icons.AutoMirrored.Filled.Message
    AppPackage.FACEBOOK -> Icons.Filled.Group
    AppPackage.SMS -> Icons.AutoMirrored.Filled.Message
}

private fun TriggerType?.icon(): ImageVector = when (this) {
    TriggerType.NAME_MENTION -> Icons.Filled.AlternateEmail
    TriggerType.POLL_CREATED -> Icons.Filled.Poll
    TriggerType.KEYWORD -> Icons.Filled.TextFields
    TriggerType.REPLY_MENTION -> Icons.Filled.AlternateEmail
    TriggerType.VIP_CONTACT -> Icons.Filled.Star
    TriggerType.GROUP_EVENT -> Icons.Filled.Group
    null -> Icons.Filled.Bolt
}

private fun TriggerType?.label(): String = when (this) {
    TriggerType.NAME_MENTION -> "Name mentioned"
    TriggerType.POLL_CREATED -> "Poll created"
    TriggerType.KEYWORD -> "Keyword matched"
    TriggerType.REPLY_MENTION -> "Reply / mention"
    TriggerType.VIP_CONTACT -> "VIP contact"
    TriggerType.GROUP_EVENT -> "Group event"
    null -> "Alert"
}

// --- Time formatting ----------------------------------------------------------

private val absoluteTimeFormat = SimpleDateFormat("MMM d", Locale.getDefault())

/** Compact relative time ("now", "5m", "3h"), falling back to an absolute date. */
private fun relativeTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - epochMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> absoluteTimeFormat.format(Date(epochMillis))
    }
}
