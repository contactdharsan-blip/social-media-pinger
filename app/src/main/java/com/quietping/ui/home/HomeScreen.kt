package com.quietping.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.domain.model.TriggerType
import com.quietping.ui.components.AccentIconDisc
import com.quietping.ui.components.AppToggleCard
import com.quietping.ui.components.EmptyState
import com.quietping.ui.components.GlassButton
import com.quietping.ui.components.GlassButtonStyle
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.LoadingShimmer
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.components.displayLabel
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.Spacing
import com.quietping.ui.theme.TabularFigures
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.cascadeItem
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
            contentPadding = PaddingValues(horizontal = Spacing.ScreenH, vertical = Spacing.ScreenV),
            verticalArrangement = Arrangement.spacedBy(Spacing.Item)
        ) {
            item(key = "header") { HomeHeader(modifier = Modifier.riseIn(0)) }

            when {
                uiState.errorMessage != null -> item(key = "error") {
                    EmptyState(
                        modifier = Modifier.riseIn(1),
                        icon = Icons.Filled.CloudOff,
                        title = "Something went wrong",
                        message = uiState.errorMessage,
                        action = {
                            GlassButton(
                                text = "Retry",
                                onClick = viewModel::retry,
                                style = GlassButtonStyle.Primary
                            )
                        }
                    )
                }

                uiState.isLoading -> {
                    item(key = "apps-section-skeleton") {
                        SectionHeader(title = "Watched apps", modifier = Modifier.riseIn(1))
                    }
                    items(4, key = { "app-skeleton-$it" }) { index ->
                        GlassCard(modifier = cascadeItem(index).fillMaxWidth()) {
                            LoadingShimmer(lines = 2)
                        }
                    }
                    item(key = "feed-section-skeleton") {
                        Spacer(Modifier.height(Spacing.Tight))
                        SectionHeader(title = "Recent matches", modifier = Modifier.riseIn(2))
                    }
                    item(key = "feed-skeleton") {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            LoadingShimmer(lines = 2)
                        }
                    }
                }

                else -> {
                    item(key = "apps-section") {
                        SectionHeader(title = "Watched apps", modifier = Modifier.riseIn(1))
                    }

                    itemsIndexed(
                        uiState.apps,
                        key = { _, status -> status.appPackage.name }
                    ) { index, status ->
                        AppToggleCard(
                            appPackage = status.appPackage,
                            enabled = status.enabled,
                            onToggle = { enabled -> viewModel.setAppEnabled(status.appPackage, enabled) },
                            modifier = cascadeItem(index),
                            subtitle = appSubtitle(status)
                        )
                    }

                    item(key = "feed-section") {
                        Spacer(Modifier.height(Spacing.Tight))
                        SectionHeader(title = "Recent matches", modifier = Modifier.riseIn(2))
                    }

                    if (uiState.isFeedEmpty) {
                        item(key = "feed-empty") {
                            EmptyState(
                                icon = Icons.Filled.NotificationsOff,
                                title = "All quiet",
                                message = "No alerts have fired yet. When one of your rules " +
                                    "matches, it shows up here."
                            )
                        }
                    } else {
                        itemsIndexed(
                            uiState.recentMatches,
                            key = { _, match -> match.matchId }
                        ) { index, match ->
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
                }
            }

            item(key = "bottom-spacer") { Spacer(Modifier.height(Spacing.Section)) }
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
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        role = Role.Button,
        onClickLabel = "Open in Message Vault"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentIconDisc(
                icon = item.trigger.icon(),
                contentDescription = null,
                size = 44.dp
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = TabularFigures
                ),
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

// --- Icon / label mappings (Material icons only; never emoji) -----------------

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
