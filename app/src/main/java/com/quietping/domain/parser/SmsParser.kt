package com.quietping.domain.parser

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.RawEvent

/**
 * Parser for the Messages app (`com.android.messaging`) — both its notifications
 * and, primarily, rows read from the SMS/MMS content provider.
 *
 * SMS is the one source with a real local store: `READ_SMS` + `ContentResolver`
 * exposes the full message history, and a `ContentObserver`-driven diff against our
 * cache reliably detects deletions. The capture layer reads provider rows and
 * hands their fields here via [fromSmsRow] to get a normalized domain [Message]
 * (source = SMS). The [MessageParser] notification path is a secondary fallback
 * for previews when the provider read is unavailable.
 *
 * Conversation identity for SMS is the address/thread; like the other parsers we
 * emit `conversationId = 0` and let the data layer resolve/insert the row using a
 * stable conversation key (the normalized address or threadId).
 */
internal class SmsParser : NotificationParser(AppPackage.SMS) {

    /**
     * Notification fallback for the Messages app. SMS notifications put the sender
     * in `EXTRA_TITLE` and the text in `EXTRA_TEXT`; MessagingStyle is rare but
     * handled when present. Marked as SMS source even via the notification so the
     * Vault attributes it correctly.
     */
    override fun parse(e: RawEvent.NotificationPosted): List<Message> {
        if (e.messages.isNotEmpty()) {
            val out = ArrayList<Message>(e.messages.size)
            for ((rawSender, rawBody) in e.messages) {
                val sender = rawSender.ifBlank { e.title?.trim().orEmpty() }
                asSmsMessage(
                    sender = sender,
                    body = rawBody,
                    postedAt = e.postedAt
                )?.let(out::add)
            }
            if (out.isNotEmpty()) return out
        }

        val body = bestBody(e) ?: return emptyList()
        val sender = e.title?.trim().orEmpty().ifEmpty { UNKNOWN_SENDER }
        val msg = asSmsMessage(sender = sender, body = body, postedAt = e.postedAt)
        return if (msg != null) listOf(msg) else emptyList()
    }

    /**
     * Map a single SMS/MMS provider row to a domain [Message].
     *
     * @param address normalized sender/recipient address (Telephony `ADDRESS`).
     * @param body    message text (Telephony `BODY`).
     * @param dateMillis  the provider `DATE` (epoch millis the message was sent/received).
     * @param incoming whether this row is inbound (`TYPE == MESSAGE_TYPE_INBOX`);
     *   outbound rows keep the user's own address as sender for thread context.
     * @return a normalized [Message], or null when [body] is blank.
     */
    fun fromSmsRow(
        address: String,
        body: String,
        dateMillis: Long,
        incoming: Boolean
    ): Message? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val sender = if (incoming) {
            address.trim().ifEmpty { UNKNOWN_SENDER }
        } else {
            SELF_SENDER
        }
        return Message(
            id = 0L,
            conversationId = 0L,
            sender = sender,
            postedAt = dateMillis,
            capturedAt = System.currentTimeMillis(),
            source = CaptureSource.SMS,
            status = MessageStatus.ACTIVE,
            currentBody = trimmed,
            versions = emptyList()
        )
    }

    private fun asSmsMessage(sender: String, body: String, postedAt: Long): Message? {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || PatternCatalog.isSummaryNoise(trimmed)) return null
        return Message(
            id = 0L,
            conversationId = 0L,
            sender = sender.ifEmpty { UNKNOWN_SENDER },
            postedAt = postedAt,
            capturedAt = System.currentTimeMillis(),
            source = CaptureSource.SMS,
            status = MessageStatus.ACTIVE,
            currentBody = trimmed,
            versions = emptyList()
        )
    }

    private companion object {
        const val SELF_SENDER = "You"
    }
}
