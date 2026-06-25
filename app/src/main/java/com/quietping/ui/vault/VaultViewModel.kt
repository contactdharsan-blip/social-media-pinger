package com.quietping.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.Message
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.security.DecoyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [VaultScreen] (PRD §6D / §9.1): the encrypted Message Vault's conversation
 * list, with a top filter (All / Deleted / Edited) and free-text search.
 *
 * It reads three reactive streams from [MessageRepository] — [MessageRepository.conversations],
 * [MessageRepository.deleted], and [MessageRepository.edited] — and folds the latter two
 * (grouped by conversation) onto the conversation list to produce per-conversation
 * recovered/edited counts and a best-effort last-message preview, then applies the
 * active filter + search.
 *
 * Contract note: the repository exposes no "all messages" / "last message per
 * conversation" query, and subscribing to [MessageRepository.thread] for every
 * conversation would defeat the PRD's battery-light, event-driven goal (§6F).
 * The list therefore derives previews and counts from the deleted + edited pools it
 * already observes — which are exactly the Vault's reason to exist (§6D). A
 * conversation with only ACTIVE messages shows no preview here; its full thread is
 * always available on tap via [MessageRepository.thread].
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val filter = MutableStateFlow(VaultFilter.ALL)
    private val query = MutableStateFlow("")

    val uiState: StateFlow<VaultUiState> =
        combine(
            messageRepository.conversations(),
            messageRepository.deleted(),
            messageRepository.edited(),
            filter,
            query
        ) { conversations, deleted, edited, activeFilter, activeQuery ->
            buildState(conversations, deleted, edited, activeFilter, activeQuery)
        }.catch {
            emit(
                VaultUiState(
                    filter = filter.value,
                    query = query.value,
                    isLoading = false,
                    errorMessage = "Couldn't load the vault"
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VaultUiState()
        )

    /** Switch the top filter (All / Deleted / Edited). */
    fun setFilter(value: VaultFilter) {
        filter.value = value
    }

    /** Update the search text (matched against display name + last-message preview). */
    fun setQuery(value: String) {
        query.value = value
    }

    /** Convenience for the search field's clear affordance. */
    fun clearQuery() {
        query.value = ""
    }

    /**
     * Toggle whether a group conversation is watched for alerts. Muting a group keeps
     * archiving its messages but stops them reaching the RuleEngine (the per-group mute
     * gate in CapturePipeline). The change persists via [MessageRepository.setWatched]
     * and re-emits through the conversations Flow that drives this screen.
     */
    fun setWatched(conversationId: Long, watched: Boolean) {
        viewModelScope.launch {
            messageRepository.setWatched(conversationId, watched)
        }
    }

    private fun buildState(
        conversations: List<Conversation>,
        deleted: List<Message>,
        edited: List<Message>,
        activeFilter: VaultFilter,
        activeQuery: String
    ): VaultUiState {
        // Decoy session (unlocked with the decoy PIN): reveal an empty vault.
        if (DecoyMode.isActive) {
            return VaultUiState(filter = activeFilter, query = activeQuery, isLoading = false)
        }

        val deletedByConv = deleted.groupBy { it.conversationId }
        val editedByConv = edited.groupBy { it.conversationId }

        val summaries = conversations.map { conversation ->
            val convDeleted = deletedByConv[conversation.id].orEmpty()
            val convEdited = editedByConv[conversation.id].orEmpty()

            // Best-effort preview: newest message across the pools we can observe.
            val latest = (convDeleted + convEdited).maxByOrNull { it.capturedAt }

            ConversationSummary(
                conversation = conversation,
                lastMessageBody = latest?.currentBody.orEmpty(),
                lastMessageAt = latest?.capturedAt ?: 0L,
                messageCount = convDeleted.size + convEdited.size,
                deletedCount = convDeleted.size,
                editedCount = convEdited.size
            )
        }

        val filtered = summaries
            .filter { summary ->
                when (activeFilter) {
                    VaultFilter.ALL -> true
                    VaultFilter.DELETED -> summary.hasDeleted
                    VaultFilter.EDITED -> summary.hasEdited
                }
            }
            .filter { summary -> matchesQuery(summary, activeQuery) }
            // Most-recent activity first; conversations with no observed messages sink
            // but keep a stable name order among themselves.
            .sortedWith(
                compareByDescending<ConversationSummary> { it.lastMessageAt }
                    .thenBy { it.displayName.lowercase() }
            )

        return VaultUiState(
            conversations = filtered,
            filter = activeFilter,
            query = activeQuery,
            isLoading = false,
            totalCount = conversations.size
        )
    }

    private fun matchesQuery(summary: ConversationSummary, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return summary.displayName.contains(q, ignoreCase = true) ||
            summary.lastMessageBody.contains(q, ignoreCase = true)
    }
}
