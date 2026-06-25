package com.quietping.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.MessageVersion
import com.quietping.ui.components.EmptyState
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.LoadingShimmer
import com.quietping.ui.components.PillBadge
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.StatusAlert
import com.quietping.ui.theme.StatusWarning
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.animatedItem
import com.quietping.ui.theme.glass
import com.quietping.ui.theme.sheen
import com.quietping.ui.theme.travelingGlowBorder
import java.text.DateFormat
import java.util.Date

/**
 * Message Vault — a single conversation thread (PRD §6D). Content only; the Scaffold
 * lives in the nav graph.
 *
 * Each message is rendered by status:
 *  - **ACTIVE** — a normal frosted bubble.
 *  - **EDITED** — an "edited" [PillBadge] and a tap-to-expand block showing the full
 *    [MessageVersion] history (v1 -> vN) with a word-level diff highlight on each step.
 *  - **DELETED** — styled as "Recovered": a soft-alert frame with the body struck
 *    through and a restore-from-trash glyph, surfacing the cached original (§6D).
 *
 * Reduced motion (the global motion gate) is honored: the edited-history expander
 * snaps instead of springing when motion is off.
 *
 * @param onNavigate generic destination navigation (mandated screen contract).
 * @param onBack     pops back to the conversation list.
 * @param viewModel  the Hilt-provided [VaultThreadViewModel] (reads the id from SavedStateHandle).
 */
@Composable
fun VaultThreadScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: VaultThreadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ThreadHeader(uiState = uiState, onBack = onBack)

        when {
            uiState.isLoading -> ThreadLoading()
            uiState.isEmpty -> ThreadEmpty()
            else -> ThreadList(messages = uiState.messages)
        }
    }
}

/** Top bar: back affordance, conversation title + app, recovered/edited summary. */
@Composable
private fun ThreadHeader(uiState: VaultThreadUiState, onBack: () -> Unit) {
    val conversation = uiState.conversation
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (conversation != null) {
                Text(
                    text = conversation.appPackage.shortName() +
                        if (conversation.isGroup) " · Group" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
        if (uiState.deletedCount > 0) {
            PillBadge(
                text = uiState.deletedCount.toString(),
                color = StatusAlert,
                icon = Icons.Filled.DeleteOutline
            )
        }
        if (uiState.editedCount > 0) {
            Spacer(Modifier.width(6.dp))
            PillBadge(
                text = uiState.editedCount.toString(),
                color = StatusWarning,
                icon = Icons.Filled.EditNote
            )
        }
    }
}

@Composable
private fun ThreadList(messages: List<Message>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = messages, key = { it.id }) { message ->
            val itemModifier = animatedItem()
            when (message.status) {
                MessageStatus.DELETED -> DeletedMessageCard(message, itemModifier)
                MessageStatus.EDITED -> EditedMessageCard(message, itemModifier)
                MessageStatus.ACTIVE -> ActiveMessageCard(message, itemModifier)
            }
        }
    }
}

/** A normal captured message. */
@Composable
private fun ActiveMessageCard(message: Message, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        MessageMeta(sender = message.sender, timestamp = message.postedAt)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message.currentBody,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

/**
 * An edited message: shows the current body, an "edited" badge, and a tap-to-expand
 * full version history with a per-step word diff. Honors the motion gate.
 */
@Composable
private fun EditedMessageCard(message: Message, modifier: Modifier = Modifier) {
    val motionEnabled = LocalQuietPingTheme.current.motionEnabled
    var expanded by remember(message.id) { mutableStateOf(false) }
    val hasHistory = message.versions.size > 1

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionEnabled) MotionTokens.floatSpring else snap<Float>(),
        label = "editedChevron"
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        onClick = if (hasHistory) ({ expanded = !expanded }) else null,
        contentPadding = PaddingValues(14.dp),
        glow = true,
        sheen = true,
        glowColors = listOf(StatusWarning)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MessageMeta(
                sender = message.sender,
                timestamp = message.postedAt,
                modifier = Modifier.weight(1f)
            )
            PillBadge(
                text = "Edited",
                color = StatusWarning,
                icon = Icons.Filled.EditNote
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = message.currentBody,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (hasHistory) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (expanded) {
                        "Hide history"
                    } else {
                        "Show ${message.versions.size} versions"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusWarning
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (motionEnabled) {
                    expandVertically(MotionTokens.gentleSpring()) + fadeIn()
                } else {
                    expandVertically(snap<IntSize>())
                },
                exit = if (motionEnabled) {
                    shrinkVertically(MotionTokens.gentleSpring()) + fadeOut()
                } else {
                    shrinkVertically(snap<IntSize>())
                }
            ) {
                VersionHistory(versions = message.versions)
            }
        }
    }
}

/** The ordered v1..vN history with a word-diff highlight relative to the prior version. */
@Composable
private fun VersionHistory(versions: List<MessageVersion>) {
    val ordered = versions.sortedBy { it.versionNo }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ordered.forEachIndexed { index, version ->
            val previous = ordered.getOrNull(index - 1)
            VersionRow(
                version = version,
                previousBody = previous?.body,
                isLatest = index == ordered.lastIndex
            )
        }
    }
}

@Composable
private fun VersionRow(
    version: MessageVersion,
    previousBody: String?,
    isLatest: Boolean
) {
    val accent = LocalQuietPingTheme.current.accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusMd))
            .glass(intensity = LocalQuietPingTheme.current.glassIntensity, cornerRadius = GlassDefaults.CornerRadiusMd)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "v${version.versionNo}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLatest) accent else TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLatest) {
                    Text(
                        text = "current",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTimestamp(version.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            Text(
                text = diffHighlight(previousBody, version.body, accent),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

/**
 * A "Recovered" deleted message: soft-alert frame, restore glyph, and the cached
 * original body shown struck through (PRD §6D — surfaced before it was deleted).
 */
@Composable
private fun DeletedMessageCard(message: Message, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassDefaults.CornerRadius))
            .glass(intensity = LocalQuietPingTheme.current.glassIntensity, cornerRadius = GlassDefaults.CornerRadius)
            .background(StatusAlert.copy(alpha = 0.06f), RoundedCornerShape(GlassDefaults.CornerRadius))
            .sheen()
            .travelingGlowBorder(
                cornerRadius = GlassDefaults.CornerRadius,
                colors = listOf(StatusAlert)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageMeta(
                    sender = message.sender,
                    timestamp = message.postedAt,
                    modifier = Modifier.weight(1f)
                )
                PillBadge(
                    text = "Recovered",
                    color = StatusAlert,
                    icon = Icons.Filled.RestoreFromTrash
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = message.currentBody,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textDecoration = TextDecoration.LineThrough
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This message was deleted — recovered from QuietPing's cache.",
                style = MaterialTheme.typography.labelSmall,
                color = StatusAlert
            )
        }
    }
}

/** Sender + timestamp line shared by every message card. */
@Composable
private fun MessageMeta(
    sender: String,
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = sender.ifBlank { "Unknown" },
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = formatTimestamp(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun ThreadLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                LoadingShimmer(lines = 3)
            }
        }
    }
}

@Composable
private fun ThreadEmpty() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            icon = Icons.Filled.Inbox,
            title = "No messages",
            message = "This conversation has no captured messages yet.",
            modifier = Modifier.padding(top = 32.dp)
        )
    }
}

// --- Diff + formatting helpers ------------------------------------------------

/**
 * Build a word-level diff [AnnotatedString]: words in [current] that are not present
 * in [previous] are emphasized with an accent tint + bold, so each edit step makes
 * the change obvious. With no [previous] (the first version), the text is plain.
 *
 * Deliberately simple (token set membership, not an LCS): fast, allocation-light, and
 * legible for short chat messages — the only thing the notification path recovers (§6D).
 */
private fun diffHighlight(previous: String?, current: String, accent: Color): AnnotatedString {
    if (previous == null) return AnnotatedString(current)
    val previousWords = previous.split(WHITESPACE).filter { it.isNotBlank() }.toHashSet()
    val tokens = TOKEN_SPLIT.findAll(current).map { it.value }.toList()
    return buildAnnotatedString {
        tokens.forEach { token ->
            val isWord = token.isNotBlank()
            if (isWord && token !in previousWords) {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append(token)
                }
            } else {
                append(token)
            }
        }
    }
}

/** Splits on whitespace for the previous-version word set. */
private val WHITESPACE = Regex("\\s+")

/** Tokenizes into runs of non-space and runs of space, so spacing is preserved verbatim. */
private val TOKEN_SPLIT = Regex("\\S+|\\s+")

/** Locale-aware short date+time for a captured/posted epoch-millis value. */
private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return formatter.format(Date(epochMillis))
}
