package com.quietping.domain.vault

/**
 * One message as seen inside a MessagingStyle notification snapshot: the
 * (sender, postedAt) pair is its stable identity; [body] is carried so a
 * detected deletion can be matched against the cached copy in the Vault.
 */
data class SeenMessage(
    val sender: String,
    val body: String,
    val postedAt: Long
)

/**
 * Detects *which specific* message disappeared between two snapshots of a chat's
 * MessagingStyle notification (the `EXTRA_MESSAGES` array), distinguishing a real
 * "delete for everyone" from a message merely scrolling off the top of the
 * notification as newer ones arrive.
 *
 * Why this matters: the bare `onNotificationRemoved` callback can only say "the
 * whole notification went away" — it can't tell a deletion from a swipe, and it
 * can't name the message. WhatsApp/Instagram re-post the same chat notification
 * carrying the last N messages; comparing consecutive arrays pinpoints the gap.
 *
 * The discriminating rule (the heart of the heuristic):
 *  - A message present in [previous] but absent from [current] is a *candidate*.
 *  - It is only flagged deleted when [current] still shows a message **older**
 *    than the candidate. If something older survived, the candidate was not
 *    pushed off the bottom by the array's fixed capacity — it was removed on
 *    purpose. If the candidate is older than everything now shown, it most
 *    likely just scrolled out of the window → not flagged (fail safe, no false
 *    "recovered").
 *
 * Identity is `(sender, postedAt)` and deliberately ignores body, so a changed
 * body for the same slot is an **edit** (handled by [VaultManager] version
 * history), never a deletion.
 *
 * Pure and allocation-light; unit-tested in isolation from Android.
 */
class DeletionDiffer {

    /**
     * Return the [previous] entries that appear to have been deliberately deleted,
     * given the [current] snapshot. Empty when either side is empty (no basis to
     * reason) or when the only missing entries are older than everything still
     * shown (treated as scroll-off, not deletion).
     */
    fun deletions(
        previous: List<SeenMessage>,
        current: List<SeenMessage>
    ): List<SeenMessage> {
        if (previous.isEmpty() || current.isEmpty()) return emptyList()

        val currentIds: Set<Pair<String, Long>> =
            current.mapTo(HashSet(current.size)) { it.sender to it.postedAt }
        val oldestStillShown = current.minOf { it.postedAt }

        return previous.filter { p ->
            val stillPresent = (p.sender to p.postedAt) in currentIds
            // Gone, and something older survived ⇒ it was removed, not scrolled off.
            !stillPresent && p.postedAt > oldestStillShown
        }
    }
}
