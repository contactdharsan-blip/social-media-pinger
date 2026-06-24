package com.quietping.domain.parser

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.RawEvent

/**
 * Parser for Instagram (`com.instagram.android`).
 *
 * Instagram DM notifications are terse: `EXTRA_TITLE` is usually the sender (or a
 * "username sent you a message" style line) and `EXTRA_TEXT` the (short, sometimes
 * absent) preview. Instagram also surfaces *typed* notifications whose own copy is
 * the signal — "mentioned you in a comment", "replied to your story", poll/notes
 * activity — where the body itself is what we key triggers off rather than a chat
 * preview. We keep both: the body is preserved verbatim so the RuleEngine can run
 * mention/poll detection over it, and the sender is taken from the title.
 *
 * Many IG notifications are pure rollups ("3 new messages"); those are dropped by
 * the shared summary-noise filter so they never enter the Vault.
 */
internal class InstagramParser : NotificationParser(AppPackage.INSTAGRAM) {

    override fun parse(e: RawEvent.NotificationPosted): List<Message> {
        if (e.messages.isNotEmpty()) {
            val out = ArrayList<Message>(e.messages.size)
            for ((rawSender, rawBody) in e.messages) {
                val sender = rawSender.ifBlank { e.title?.trim().orEmpty() }
                buildMessage(e, sender = sender, body = rawBody)?.let(out::add)
            }
            if (out.isNotEmpty()) return out
        }

        val title = e.title?.trim().orEmpty()
        val body = bestBody(e)

        // Typed activity (mention/reply/poll) where the title carries the verb and
        // there is little/no separate preview: keep the title as the body so the
        // sentinel detectors can see "mentioned you", "added a poll", etc.
        if (body.isNullOrBlank()) {
            if (title.isEmpty()) return emptyList()
            val deleted = PatternCatalog.isDeletedSentinel(title)
            val msg = buildMessage(
                e = e,
                sender = senderFromActivityTitle(title),
                body = title,
                status = if (deleted) MessageStatus.DELETED else MessageStatus.ACTIVE
            )
            return if (msg != null) listOf(msg) else emptyList()
        }

        val deleted = PatternCatalog.isDeletedSentinel(body)
        val msg = buildMessage(
            e = e,
            sender = title.ifEmpty { UNKNOWN_SENDER },
            body = body,
            status = if (deleted) MessageStatus.DELETED else MessageStatus.ACTIVE
        )
        return if (msg != null) listOf(msg) else emptyList()
    }

    /**
     * Pull the leading handle out of activity titles like
     * "alice mentioned you in a comment" so VIP/sender matching still works when
     * the verb is embedded in the title. Falls back to the whole title.
     */
    private fun senderFromActivityTitle(title: String): String {
        val firstToken = title.substringBefore(' ').trim()
        return firstToken.ifEmpty { title }
    }
}
