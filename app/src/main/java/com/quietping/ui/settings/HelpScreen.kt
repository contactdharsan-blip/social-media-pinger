package com.quietping.ui.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.theme.Spacing
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary

/**
 * Help screen (PRD §9.1): a static, scrollable explainer of what QuietPing is, its
 * features, the two capture modes, the core user flows, and a glossary. Reachable from
 * the Settings "More" hub. Full-screen route — the NavGraph supplies the status-bar inset.
 *
 * Content-only and stateless (no ViewModel): the text is the product, not data.
 */
@Composable
fun HelpScreen(onBack: () -> Unit = {}) {
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
                text = "Help",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        // What & why.
        GlassCard {
            Text(
                text = "What is QuietPing?",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Body(
                "Chat apps are all-or-nothing: every message buzzes, or you mute a " +
                    "conversation and miss the one that mattered. QuietPing watches the " +
                    "notifications (and SMS) of your chat apps and re-notifies you only when a " +
                    "condition you defined fires — your name mentioned, a VIP sender, a keyword, " +
                    "a poll. Everything else stays silent."
            )
            Spacer(Modifier.height(8.dp))
            Body(
                "It also keeps a private, encrypted Vault so you can read messages that were " +
                    "edited or deleted. Everything runs on this device — there is no internet " +
                    "permission, no server, no analytics, no cloud."
            )
        }

        // The core loop.
        SectionHeader(title = "How it works")
        GlassCard {
            Step(1, "Capture", "A notification arrives (or an SMS lands). QuietPing reads it " +
                "the instant it appears — no polling, no battery drain.")
            Step(2, "Match", "Your enabled rules are checked against the message text and sender.")
            Step(3, "Alert", "If a rule matches, QuietPing re-notifies you on that rule's channel " +
                "— with its own sound, vibration, and Do Not Disturb behavior.")
            Step(4, "Archive", "The message is saved to the encrypted Vault, so later edits or a " +
                "deletion can be recovered.", last = true)
        }

        // Two faces.
        SectionHeader(title = "Two capture modes")
        GlassCard {
            Labeled(
                "Face 1 — universal",
                "Works on any phone, no root. Reads chat-app notifications and the SMS store. " +
                    "This is the main product and covers everything above."
            )
            Spacer(Modifier.height(12.dp))
            Labeled(
                "Face 2 — deep capture (rooted only)",
                "An optional add-on for rooted devices with LSPosed. It hooks the chat apps in " +
                    "their own process to recover \"delete-for-everyone\" messages that a " +
                    "notification alone can't. It ships inert — it does nothing unless you are " +
                    "rooted, have LSPosed, and explicitly enable and scope it."
            )
        }

        // Feature tour.
        SectionHeader(title = "Features")
        GlassCard {
            Feature("Conditional alerts", "Rules match by keyword, VIP sender, mention, reply, " +
                "or poll — and can alert or suppress.")
            Feature("Quiet hours", "A rule can fire only inside a time window you set.")
            Feature("Alert escalation", "Standard, persistent (re-pings until read), or critical " +
                "(full-screen + alarm, bypasses DND). Repeat senders auto-escalate.")
            Feature("Daily digest", "One quiet summary a day of everything that stayed silent.")
            Feature("Message Vault", "Edited messages keep full version history; deleted messages " +
                "are recovered. Lockable behind biometrics.")
            Feature("Media gallery", "Captured images and files in one encrypted grid.")
            Feature("Privacy tools", "Screenshot block, content-hidden notifications, a break-in " +
                "log, and a decoy PIN that opens an empty vault.")
            Feature("SMS productivity", "One-time passcodes are detected and auto-deleted; bills " +
                "are flagged.", last = true)
        }

        // Flows.
        SectionHeader(title = "Key flows")
        GlassCard {
            Labeled(
                "Set up a filter",
                "Rules → add a rule → pick the app and a condition (keyword, VIP, mention…) → " +
                    "choose alert or suppress and an optional quiet-hours window → save. Tune its " +
                    "sound and escalation under Settings."
            )
            Spacer(Modifier.height(12.dp))
            Labeled(
                "Read recovered messages",
                "Vault → pick a conversation → the thread shows active, edited (tap to see the " +
                    "version diff), and deleted (\"recovered\") messages. Tapping an alert jumps " +
                    "straight to its thread."
            )
        }

        // Glossary.
        SectionHeader(title = "Glossary")
        GlassCard {
            Term("Rule", "A condition you define (keyword, VIP, mention, poll…) plus what to do " +
                "when it matches — alert or suppress.")
            Term("Trigger", "The kind of condition a rule watches for, e.g. a name mention or a " +
                "VIP sender. Each trigger has its own alert channel.")
            Term("Pattern", "The text fingerprint QuietPing matches against, kept in a versioned " +
                "catalog because notification wording varies by app and language.")
            Term("Vault", "The on-device encrypted store of captured messages, including edited " +
                "history and recovered deletions.")
            Term("Capture", "Reading a message the moment its notification or SMS appears.")
            Term("Decoy PIN", "An alternate unlock code that opens an empty vault — a coercion " +
                "escape hatch, separate from your real biometric lock.", last = true)
        }

        Spacer(Modifier.height(Spacing.Section))
    }
}

/** A body paragraph in the secondary text color. */
@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
}

/** A numbered step in the "how it works" loop. */
@Composable
private fun Step(number: Int, title: String, detail: String, last: Boolean = false) {
    Text(
        text = "$number.  $title",
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary
    )
    Spacer(Modifier.height(4.dp))
    Body(detail)
    if (!last) Spacer(Modifier.height(14.dp))
}

/** A bold label over a description paragraph. */
@Composable
private fun Labeled(label: String, detail: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary
    )
    Spacer(Modifier.height(4.dp))
    Body(detail)
}

/** A single feature line: emphasized name then a one-line description. */
@Composable
private fun Feature(name: String, detail: String, last: Boolean = false) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary
    )
    Spacer(Modifier.height(2.dp))
    Text(text = detail, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
    if (!last) Spacer(Modifier.height(12.dp))
}

/** A glossary entry: the term, then its definition. */
@Composable
private fun Term(term: String, definition: String, last: Boolean = false) {
    Text(
        text = term,
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary
    )
    Spacer(Modifier.height(2.dp))
    Text(text = definition, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    if (!last) Spacer(Modifier.height(12.dp))
}
