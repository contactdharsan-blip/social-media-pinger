package com.quietping.domain.parser

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.RawEvent

/**
 * Shared normalization helpers for notification-backed [MessageParser]s.
 *
 * Every chat-app notification ultimately exposes the same handful of extras —
 * `EXTRA_TITLE` (chat / group name), `EXTRA_TEXT` / `EXTRA_BIG_TEXT` (latest body),
 * `EXTRA_SUB_TEXT` (often the group name on group chats), and, when the app uses
 * `MessagingStyle`, a flat list of (sender, body) pairs. The per-app parsers
 * differ only in how those fields map onto sender / conversation / body and in
 * which content is noise. This base centralizes the boring parts so each concrete
 * parser stays focused on its app's quirks.
 *
 * Conversation identity: parsers produce a domain [Message] with a placeholder
 * `conversationId = 0`; the data layer resolves the real conversation row from the
 * notification [RawEvent.NotificationPosted.key] (a stable per-conversation key)
 * before persisting. We never invent ids here.
 */
internal abstract class NotificationParser(
    final override val appPackage: AppPackage
) : MessageParser {

    override fun canParse(e: RawEvent.NotificationPosted): Boolean =
        AppPackage.fromPackageId(e.packageName) == appPackage

    /**
     * Build a domain [Message] from a single (sender, body) unit. Returns null
     * when the body is empty or pure summary noise (fail safe — no false ping).
     *
     * @param status defaults ACTIVE; callers pass DELETED when the body is a
     *   revoked-message sentinel so the Vault surfaces it as "Recovered".
     */
    protected fun buildMessage(
        e: RawEvent.NotificationPosted,
        sender: String,
        body: String,
        status: MessageStatus = MessageStatus.ACTIVE
    ): Message? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (status == MessageStatus.ACTIVE && PatternCatalog.isSummaryNoise(trimmed)) return null
        return Message(
            id = 0L,
            conversationId = 0L,
            sender = sender.trim().ifEmpty { UNKNOWN_SENDER },
            postedAt = e.postedAt,
            capturedAt = System.currentTimeMillis(),
            source = CaptureSource.NOTIFICATION,
            status = status,
            currentBody = trimmed,
            versions = emptyList()
        )
    }

    /**
     * Many apps render a group message body as "Sender: text". Split off the
     * leading sender when [title] is a group name (so the real human sender is
     * preserved). Returns sender-to-body; falls back to [fallbackSender] + raw.
     */
    protected fun splitInlineSender(
        body: String,
        fallbackSender: String
    ): Pair<String, String> {
        val idx = body.indexOf(": ")
        if (idx in 1..40) {
            val candidate = body.substring(0, idx).trim()
            // A plausible name: short, no newline. Avoids splitting "http://x".
            if (candidate.isNotEmpty() && !candidate.contains('\n') && !candidate.contains("//")) {
                return candidate to body.substring(idx + 2).trim()
            }
        }
        return fallbackSender to body.trim()
    }

    /**
     * The best available "latest body" from the raw extras, preferring the richest
     * field. MessagingStyle (handled by subclasses) supersedes this.
     */
    protected fun bestBody(e: RawEvent.NotificationPosted): String? =
        e.bigText?.takeIf { it.isNotBlank() }
            ?: e.text?.takeIf { it.isNotBlank() }

    protected companion object {
        const val UNKNOWN_SENDER = "Unknown"
    }
}

/**
 * Parser for WhatsApp (`com.whatsapp`).
 *
 * WhatsApp posts one notification per chat. For 1:1 chats `EXTRA_TITLE` is the
 * contact and the body is the message text. For groups `EXTRA_TITLE` is the group
 * name and each message body is prefixed "Sender: ...". When `MessagingStyle` is
 * present we use its per-message (sender, body) pairs directly — that also yields
 * the multiple lines of a bundled notification. Revoked messages arrive as the
 * body "This message was deleted", which we capture as a DELETED-status [Message]
 * so the Vault can recover the *previously* cached text by conversation.
 */
internal class WhatsAppParser : NotificationParser(AppPackage.WHATSAPP) {

    override fun parse(e: RawEvent.NotificationPosted): List<Message> {
        val title = e.title?.trim().orEmpty()
        // subText is commonly the group name; presence is a strong group signal.
        val isGroup = !e.subText.isNullOrBlank() ||
            e.messages.any { it.first.isNotBlank() && it.first != title }

        val out = ArrayList<Message>()

        if (e.messages.isNotEmpty()) {
            for ((rawSender, rawBody) in e.messages) {
                val deleted = PatternCatalog.isDeletedSentinel(rawBody)
                val sender = rawSender.ifBlank { title.ifEmpty { UNKNOWN_SENDER } }
                val msg = buildMessage(
                    e = e,
                    sender = sender,
                    body = rawBody,
                    status = if (deleted) MessageStatus.DELETED else MessageStatus.ACTIVE
                )
                if (msg != null) out.add(msg)
            }
            return out
        }

        // Fallback: single body from text/bigText extras.
        val body = bestBody(e) ?: return emptyList()
        val deleted = PatternCatalog.isDeletedSentinel(body)
        val (sender, cleanBody) = if (isGroup) {
            splitInlineSender(body, fallbackSender = title.ifEmpty { UNKNOWN_SENDER })
        } else {
            title.ifEmpty { UNKNOWN_SENDER } to body
        }
        val msg = buildMessage(
            e = e,
            sender = sender,
            body = cleanBody,
            status = if (deleted) MessageStatus.DELETED else MessageStatus.ACTIVE
        )
        return if (msg != null) listOf(msg) else emptyList()
    }
}
