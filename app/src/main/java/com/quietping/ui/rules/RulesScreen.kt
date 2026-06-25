package com.quietping.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Rule
import com.quietping.domain.model.TriggerType
import com.quietping.ui.components.EmptyState
import com.quietping.ui.components.GlassButton
import com.quietping.ui.components.GlassButtonStyle
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.PillBadge
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.cascadeItem
import com.quietping.ui.theme.riseIn
import com.quietping.ui.theme.OnAccent
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextTertiary

/**
 * Rules list, grouped by app. Each rule shows its trigger, pattern preview, sound
 * preset, and an inline enable switch; tapping a rule opens the editor. The header
 * action and empty-state CTA both create a new rule.
 *
 * Content only — the screen's Scaffold/bottom-nav is owned by the nav graph.
 */
@Composable
fun RulesScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: RulesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            SectionHeader(
                modifier = Modifier.riseIn(0),
                title = "Rules",
                subtitle = if (state.totalCount > 0) {
                    "${state.enabledCount} of ${state.totalCount} active"
                } else {
                    "Ping only when your conditions fire"
                },
                trailing = {
                    GlassButton(
                        text = "New",
                        onClick = { onNavigate(Dest.RuleEditor) },
                        style = GlassButtonStyle.Primary,
                        leadingIcon = Icons.Filled.Add
                    )
                }
            )
        }

        if (state.isEmpty) {
            item(key = "empty") {
                EmptyState(
                    modifier = Modifier.riseIn(1),
                    icon = Icons.Filled.RuleFolder,
                    title = "No rules yet",
                    message = "Create a rule to get pinged for the messages that matter — a name mention, a keyword, a VIP, and more.",
                    action = {
                        GlassButton(
                            text = "Create your first rule",
                            onClick = { onNavigate(Dest.RuleEditor) },
                            style = GlassButtonStyle.Primary,
                            leadingIcon = Icons.Filled.Add
                        )
                    }
                )
            }
        } else {
            state.groups.forEach { group ->
                item(key = "group_${group.app.name}") {
                    AppGroupHeader(app = group.app, count = group.rules.size)
                }
                itemsIndexed(
                    items = group.rules,
                    key = { _, rule -> "rule_${rule.id}" }
                ) { index, rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { enabled -> viewModel.setEnabled(rule, enabled) },
                        onClick = { onNavigate(Dest.RuleEditor) },
                        modifier = cascadeItem(index)
                    )
                }
            }
        }
    }
}

/** Per-app group label with rule count. */
@Composable
private fun AppGroupHeader(
    app: AppPackage,
    count: Int,
    modifier: Modifier = Modifier
) {
    val accent = LocalQuietPingTheme.current.accent
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = app.icon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = app.label(),
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
        PillBadge(text = count.toString(), color = accent)
    }
}

/** A single rule as a tappable glass card with an inline enable switch. */
@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = rule.type.label(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = rule.patternSummary()
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillBadge(
                        text = rule.soundPreset.displayName,
                        color = MaterialTheme.colorScheme.secondary,
                        icon = Icons.Filled.NotificationsActive
                    )
                    if (rule.dndOverride) {
                        PillBadge(
                            text = "DND bypass",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccent,
                    checkedTrackColor = Emerald400,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

// --- Shared rules-package presentation helpers ---------------------------------

/** Human label for an app (Vault/Rules screens). */
internal fun AppPackage.label(): String = when (this) {
    AppPackage.WHATSAPP -> "WhatsApp"
    AppPackage.INSTAGRAM -> "Instagram"
    AppPackage.MESSENGER -> "Messenger"
    AppPackage.FACEBOOK -> "Facebook"
    AppPackage.SMS -> "Messages"
}

/** A stroke Material icon standing in for each app (no app logos bundled). */
internal fun AppPackage.icon(): ImageVector = when (this) {
    AppPackage.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    AppPackage.INSTAGRAM -> Icons.Filled.PhotoCamera
    AppPackage.MESSENGER -> Icons.AutoMirrored.Filled.Chat
    AppPackage.FACEBOOK -> Icons.Filled.Public
    AppPackage.SMS -> Icons.AutoMirrored.Filled.Message
}

/** Human label for a trigger type. */
internal fun TriggerType.label(): String = when (this) {
    TriggerType.NAME_MENTION -> "Name mention"
    TriggerType.POLL_CREATED -> "Poll created"
    TriggerType.KEYWORD -> "Keyword"
    TriggerType.REPLY_MENTION -> "Reply / @mention"
    TriggerType.VIP_CONTACT -> "VIP contact"
    TriggerType.GROUP_EVENT -> "Group event"
}

/** Short one-line explanation of a trigger type, for the editor. */
internal fun TriggerType.description(): String = when (this) {
    TriggerType.NAME_MENTION -> "Pings when your name or handle appears in a message."
    TriggerType.POLL_CREATED -> "Pings when a new poll is created in a chat."
    TriggerType.KEYWORD -> "Pings when any of your keywords appears."
    TriggerType.REPLY_MENTION -> "Pings when someone replies to or @mentions you."
    TriggerType.VIP_CONTACT -> "Pings for any message from a starred contact."
    TriggerType.GROUP_EVENT -> "Pings on group events: added, admin change, or call."
}

/** A compact preview of what a rule matches on, or null when there's nothing to show. */
internal fun Rule.patternSummary(): String? = when (type) {
    TriggerType.KEYWORD -> pattern.splitKeywords()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
    TriggerType.NAME_MENTION -> pattern.takeIf { it.isNotBlank() }
    TriggerType.VIP_CONTACT -> "Starred contacts"
    else -> null
}

/** Parse the comma-separated [Rule.pattern] used by keyword rules into trimmed terms. */
internal fun String.splitKeywords(): List<String> =
    split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Join keyword terms back into the canonical comma-separated [Rule.pattern]. */
internal fun List<String>.joinKeywords(): String = joinToString(", ")
