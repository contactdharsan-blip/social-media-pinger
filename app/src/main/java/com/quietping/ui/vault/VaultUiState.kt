package com.quietping.ui.vault

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Conversation

/**
 * The top filter on the Vault conversation list (PRD §6D / §9.1). Mirrors the
 * three lenses the user can apply to the archive.
 */
enum class VaultFilter(val label: String) {
    /** Every captured conversation. */
    ALL("All"),

    /** Only conversations that contain at least one DELETED ("Recovered") message. */
    DELETED("Deleted"),

    /** Only conversations that contain at least one EDITED message. */
    EDITED("Edited")
}

/**
 * A row in the Vault conversation list: the [conversation] plus the lightweight,
 * pre-computed summary the list needs (last-message preview + per-status counts),
 * so the list never has to load full threads.
 *
 * @param conversation     the underlying conversation.
 * @param lastMessageBody  preview text of the most recently captured message ("" if none).
 * @param lastMessageAt    capture time of that latest message (0 if none) — drives ordering.
 * @param messageCount     total captured messages in the conversation.
 * @param deletedCount     number of DELETED ("Recovered") messages.
 * @param editedCount      number of EDITED messages.
 */
data class ConversationSummary(
    val conversation: Conversation,
    val lastMessageBody: String,
    val lastMessageAt: Long,
    val messageCount: Int,
    val deletedCount: Int,
    val editedCount: Int
) {
    val id: Long get() = conversation.id
    val appPackage: AppPackage get() = conversation.appPackage
    val displayName: String get() = conversation.displayName
    val isGroup: Boolean get() = conversation.isGroup

    /** Whether this group is watched for alerts (only meaningful when [isGroup]). */
    val watched: Boolean get() = conversation.watched

    /** True when this conversation has any recovered (deleted) content. */
    val hasDeleted: Boolean get() = deletedCount > 0

    /** True when this conversation has any edited content. */
    val hasEdited: Boolean get() = editedCount > 0
}

/**
 * Immutable UI state for [VaultScreen].
 *
 * [conversations] is already filtered by [filter] and [query]; the screen renders it
 * verbatim. [isLoading] is true only until the first repository emission, gating the
 * shimmer; [isEmpty] distinguishes "nothing captured yet" from "nothing matches the
 * current filter/search" so the empty state can be worded correctly.
 *
 * @param conversations the visible, filtered + searched conversation summaries.
 * @param filter        the active top filter.
 * @param query         the active search text (matches display name + last message).
 * @param isLoading     true until the first emission arrives.
 * @param totalCount    total conversations captured (pre-filter), for empty-state copy.
 */
data class VaultUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val filter: VaultFilter = VaultFilter.ALL,
    val query: String = "",
    val isLoading: Boolean = true,
    val totalCount: Int = 0
) {
    /** No rows to show right now (post-filter). */
    val isEmpty: Boolean get() = !isLoading && conversations.isEmpty()

    /** The empty list is the result of an active filter/search, not an empty vault. */
    val isFilteredEmpty: Boolean
        get() = isEmpty && (totalCount > 0 || query.isNotBlank())
}
