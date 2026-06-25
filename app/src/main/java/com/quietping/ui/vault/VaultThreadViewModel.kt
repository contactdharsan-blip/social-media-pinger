package com.quietping.ui.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.Message
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.security.DecoyMode
import com.quietping.ui.nav.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Immutable UI state for [VaultThreadScreen].
 *
 * @param conversation the owning conversation (null until resolved / if missing).
 * @param messages     the ordered thread (oldest -> newest), each carrying its full
 *                     [Message.versions] history for the edited-message expander.
 * @param isLoading    true until the first thread emission arrives.
 * @param errorMessage non-null when the thread failed to load (the screen shows an error state).
 */
data class VaultThreadUiState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    /** Title for the thread app bar; falls back gracefully before the conversation resolves. */
    val title: String get() = conversation?.displayName ?: "Conversation"

    /** True when the thread resolved but holds no messages. */
    val isEmpty: Boolean get() = !isLoading && messages.isEmpty()

    /** Count of recovered (deleted) messages in the thread. */
    val deletedCount: Int
        get() = messages.count { it.status == com.quietping.domain.model.MessageStatus.DELETED }

    /** Count of edited messages in the thread. */
    val editedCount: Int
        get() = messages.count { it.status == com.quietping.domain.model.MessageStatus.EDITED }
}

/**
 * Drives [VaultThreadScreen] (PRD §6D): a single conversation thread from the Vault.
 *
 * The [conversationId] is read from [SavedStateHandle] (the nav arg
 * [Dest.VaultThread.ARG_CONVERSATION_ID]). The thread itself streams from
 * [MessageRepository.thread]; the conversation header is resolved by matching the id
 * against [MessageRepository.conversations]. Both are reactive, so edits/deletions
 * captured while the screen is open update live.
 */
@HiltViewModel
class VaultThreadViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** The conversation this thread shows; -1 if the arg was absent (defensive). */
    val conversationId: Long =
        savedStateHandle.get<Long>(Dest.VaultThread.ARG_CONVERSATION_ID) ?: -1L

    val uiState: StateFlow<VaultThreadUiState> =
        combine(
            messageRepository.thread(conversationId),
            messageRepository.conversations()
        ) { messages, conversations ->
            // Decoy session (unlocked with the decoy PIN): reveal a blank, empty thread.
            if (DecoyMode.isActive) {
                VaultThreadUiState(isLoading = false)
            } else {
                VaultThreadUiState(
                    conversation = conversations.firstOrNull { it.id == conversationId },
                    messages = messages,
                    isLoading = false
                )
            }
        }.catch {
            emit(VaultThreadUiState(isLoading = false, errorMessage = "Couldn't load this conversation"))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VaultThreadUiState()
        )
}
