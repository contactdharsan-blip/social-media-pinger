package com.quietping.ui.vault

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.domain.model.AppPackage
import com.quietping.ui.components.EmptyState
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.LoadingShimmer
import com.quietping.ui.components.PillBadge
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.components.SegmentedControl
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.StatusAlert
import com.quietping.ui.theme.StatusWarning
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass

/**
 * Message Vault — conversation list (PRD §6D / §9.1). Content only: the hosting
 * Scaffold + bottom nav live in `QuietPingNavGraph`.
 *
 * Top: a [SegmentedControl] filter (All / Deleted / Edited) and a frosted search
 * field. Body: a list of conversation [GlassCard]s, each showing the source app, the
 * conversation name, a best-effort last-message preview, and [PillBadge] counts for
 * recovered (deleted) and edited content. Tapping a row opens its thread.
 *
 * @param onNavigate generic destination navigation (mandated screen contract).
 * @param onBack     back affordance (unused as a bottom-nav root; kept for the contract).
 * @param onOpenThread opens a conversation thread by id. The nav graph wires this as
 *                     `{ id -> navController.navigate(Dest.VaultThread.createRoute(id)) }`
 *                     because [Dest.VaultThread] is a parameterized route the generic
 *                     `(Dest) -> Unit` callback cannot carry an id through.
 * @param viewModel  the Hilt-provided [VaultViewModel].
 */
@Composable
fun VaultScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    onOpenThread: (Long) -> Unit = {},
    viewModel: VaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    VaultContent(
        uiState = uiState,
        onFilterChange = viewModel::setFilter,
        onQueryChange = viewModel::setQuery,
        onClearQuery = viewModel::clearQuery,
        onOpenThread = onOpenThread
    )
}

@Composable
private fun VaultContent(
    uiState: VaultUiState,
    onFilterChange: (VaultFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenThread: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header: title + filter + search, in a frosted column that floats over the list.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = "Message Vault",
                subtitle = "Recovered and edited messages, on-device only"
            )

            val filters = VaultFilter.entries
            SegmentedControl(
                options = filters,
                selectedIndex = filters.indexOf(uiState.filter),
                onSelect = { index -> onFilterChange(filters[index]) },
                label = { it.label }
            )

            VaultSearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery
            )
        }

        when {
            uiState.isLoading -> VaultLoading()
            uiState.isEmpty -> VaultEmpty(uiState)
            else -> ConversationList(
                conversations = uiState.conversations,
                onOpenThread = onOpenThread
            )
        }
    }
}

/** A frosted-glass search input (DESIGN.md §7.5) with a leading magnifier + clear. */
@Composable
private fun VaultSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val accent = LocalQuietPingTheme.current.accent
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
            .glass(cornerRadius = GlassDefaults.CornerRadiusLg),
        placeholder = {
            Text(
                text = "Search conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = TextTertiary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = TextTertiary
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun ConversationList(
    conversations: List<ConversationSummary>,
    onOpenThread: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = conversations, key = { it.id }) { summary ->
            ConversationRow(
                summary = summary,
                onClick = { onOpenThread(summary.id) }
            )
        }
    }
}

/** A single conversation card: app disc + name + preview + recovered/edited badges. */
@Composable
private fun ConversationRow(
    summary: ConversationSummary,
    onClick: () -> Unit
) {
    val accent = LocalQuietPingTheme.current.accent
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App / group disc.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (summary.isGroup) Icons.Filled.Groups else summary.appPackage.vaultIcon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = summary.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = summary.appPackage.shortName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1
                    )
                }
                Text(
                    text = summary.lastMessageBody.ifBlank { "No recovered preview" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary.lastMessageBody.isBlank()) TextTertiary else TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary.hasDeleted || summary.hasEdited) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (summary.hasDeleted) {
                            PillBadge(
                                text = "${summary.deletedCount} recovered",
                                color = StatusAlert,
                                icon = Icons.Filled.DeleteOutline
                            )
                        }
                        if (summary.hasEdited) {
                            PillBadge(
                                text = "${summary.editedCount} edited",
                                color = StatusWarning,
                                icon = Icons.Filled.EditNote
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(5) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                LoadingShimmer(lines = 2)
            }
        }
    }
}

@Composable
private fun VaultEmpty(uiState: VaultUiState) {
    val (title, message, icon) = when {
        uiState.query.isNotBlank() -> Triple(
            "No matches",
            "No conversations match \"${uiState.query.trim()}\".",
            Icons.Filled.Search
        )

        uiState.filter == VaultFilter.DELETED -> Triple(
            "No recovered messages",
            "When someone deletes a message QuietPing has seen, it shows up here.",
            Icons.Filled.DeleteOutline
        )

        uiState.filter == VaultFilter.EDITED -> Triple(
            "No edited messages",
            "Edits to messages QuietPing has captured will appear here with full history.",
            Icons.Filled.EditNote
        )

        else -> Triple(
            "Vault is empty",
            "Captured conversations will appear here. Keep notification access on so " +
                "QuietPing can archive what others edit or delete.",
            Icons.Filled.Inbox
        )
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            icon = icon,
            title = title,
            message = message,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

// --- App metadata (package-internal; shared with VaultThreadScreen) -----------

/** A stroke Material icon standing in for each source app (no brand emoji, §9 / DESIGN.md). */
internal fun AppPackage.vaultIcon(): ImageVector = when (this) {
    AppPackage.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    AppPackage.INSTAGRAM -> Icons.Filled.PhotoCamera
    AppPackage.MESSENGER -> Icons.Filled.Forum
    AppPackage.FACEBOOK -> Icons.Filled.ThumbUp
    AppPackage.SMS -> Icons.Filled.Sms
}

/** Short human label for the source app, shown as a row tag. */
internal fun AppPackage.shortName(): String = when (this) {
    AppPackage.WHATSAPP -> "WhatsApp"
    AppPackage.INSTAGRAM -> "Instagram"
    AppPackage.MESSENGER -> "Messenger"
    AppPackage.FACEBOOK -> "Facebook"
    AppPackage.SMS -> "Messages"
}
