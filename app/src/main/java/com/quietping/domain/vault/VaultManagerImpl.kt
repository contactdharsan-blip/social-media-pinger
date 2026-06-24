package com.quietping.domain.vault

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.MessageVersion
import com.quietping.domain.repo.MessageRepository
import kotlinx.coroutines.flow.first

/**
 * Default [VaultManager], backed by [MessageRepository].
 *
 * Responsibilities (the domain rules of the Vault):
 *  - **Dedupe** identical re-deliveries of the same message. The same notification
 *    is frequently re-posted (e.g. when another message arrives in the chat); we
 *    must not store the same logical message twice. Identity is a stable key:
 *    `hash(app + conversationKey + sender + postedAt + bodyHash)` for an exact
 *    duplicate, and `hash(app + conversationKey + sender + postedAt)` for the
 *    "same message, body changed" (edit) case.
 *  - **Edit history** — when a message with the same edit-key reappears with a
 *    different body inside [EDIT_WINDOW_MS], it is recorded as a new
 *    [MessageVersion] and the message flips to [MessageStatus.EDITED]. Versions are
 *    append-only (v1 → v2 → …), giving free history.
 *  - **Deletion** — [markDeleted] flags the most recent cached message of a
 *    conversation (optionally matched by [body]) as [MessageStatus.DELETED] so the
 *    Vault surfaces it as "Recovered", while keeping its version history intact.
 *
 * Persistence detail: parsers emit messages with `conversationId = 0` because the
 * conversation row (key → id) is resolved by the data layer. When the caller has
 * already resolved a real [Message.conversationId] (> 0), this manager reads that
 * thread and makes the dedupe/version decision here; otherwise it delegates to
 * [MessageRepository.upsert], whose contract performs the same dedupe + version
 * append at the storage layer. Either path yields identical Vault semantics.
 */
class VaultManagerImpl(
    private val messageRepository: MessageRepository
) : VaultManager {

    override suspend fun ingest(message: Message) {
        // Unresolved conversation: the store owns resolution + dedupe/version.
        if (message.conversationId <= 0L) {
            messageRepository.upsert(message)
            return
        }

        val conversation = messageRepository.conversations().first()
            .firstOrNull { it.id == message.conversationId }
        // Without the conversation row we cannot derive a stable key; let the store
        // resolve + dedupe instead of guessing.
        if (conversation == null) {
            messageRepository.upsert(message)
            return
        }

        val incomingEditKey = StableKey.editKey(
            appPackage = conversation.appPackage,
            conversationKey = conversation.conversationKey,
            sender = message.sender,
            postedAt = message.postedAt
        )
        val incomingContentKey = StableKey.contentKey(
            appPackage = conversation.appPackage,
            conversationKey = conversation.conversationKey,
            sender = message.sender,
            postedAt = message.postedAt,
            body = message.currentBody
        )

        val existing = messageRepository.thread(message.conversationId).first()
        val match = existing.firstOrNull {
            StableKey.editKey(
                appPackage = conversation.appPackage,
                conversationKey = conversation.conversationKey,
                sender = it.sender,
                postedAt = it.postedAt
            ) == incomingEditKey
        }

        when {
            match == null -> {
                // Genuinely new message in a known conversation.
                messageRepository.upsert(message)
            }

            StableKey.contentKey(
                appPackage = conversation.appPackage,
                conversationKey = conversation.conversationKey,
                sender = match.sender,
                postedAt = match.postedAt,
                body = match.currentBody
            ) == incomingContentKey -> {
                // Exact duplicate re-delivery (same content key): nothing to do.
            }

            isWithinEditWindow(match, message) -> {
                // Same message, changed body ⇒ append a version, mark EDITED.
                val nextNo = (match.versions.maxOfOrNull { it.versionNo } ?: 1) + 1
                val appended = match.versions + MessageVersion(
                    id = 0L,
                    messageId = match.id,
                    versionNo = nextNo,
                    body = message.currentBody,
                    capturedAt = message.capturedAt
                )
                messageRepository.upsert(
                    match.copy(
                        status = MessageStatus.EDITED,
                        currentBody = message.currentBody,
                        capturedAt = message.capturedAt,
                        versions = appended
                    )
                )
            }

            else -> {
                // Same key but outside the edit window ⇒ treat as a new message.
                messageRepository.upsert(message)
            }
        }
    }

    override suspend fun markDeleted(
        conversationKey: String,
        appPackage: AppPackage,
        body: String?
    ) {
        // Find the conversation row for this key+app, then the target message.
        val conversation = messageRepository.conversations().first()
            .firstOrNull { it.appPackage == appPackage && it.conversationKey == conversationKey }
            ?: return

        val thread = messageRepository.thread(conversation.id).first()
        if (thread.isEmpty()) return

        val target = when {
            body != null -> thread.lastOrNull {
                it.status != MessageStatus.DELETED && bodyEquals(it.currentBody, body)
            }
            else -> thread.lastOrNull { it.status != MessageStatus.DELETED }
        } ?: return

        if (target.status == MessageStatus.DELETED) return
        messageRepository.upsert(target.copy(status = MessageStatus.DELETED))
    }

    private fun isWithinEditWindow(existing: Message, incoming: Message): Boolean =
        incoming.capturedAt - existing.capturedAt in 0..EDIT_WINDOW_MS

    private fun bodyEquals(a: String, b: String): Boolean =
        a.trim() == b.trim()

    private companion object {
        /** Two captures of the same edit-key within this window count as an edit. */
        const val EDIT_WINDOW_MS = 24L * 60L * 60L * 1000L
    }
}
