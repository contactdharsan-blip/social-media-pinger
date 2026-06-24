package com.quietping.domain.parser

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.RawEvent

/**
 * Parser for Facebook Messenger (`com.facebook.orca`) and the Facebook app
 * (`com.facebook.katana`), which delivers Messenger-style chat notifications too.
 *
 * Unlike [MessageParser]s that own a single package, this parser recognizes
 * *both* Facebook packages (the contract's [AppPackage.MESSENGER] and
 * [AppPackage.FACEBOOK] are effectively the same notification surface). The
 * declared [appPackage] is MESSENGER; [canParse] additionally accepts FACEBOOK,
 * and parsed messages are tagged with whichever package actually produced them so
 * downstream rules scoped to FACEBOOK still match.
 *
 * Message shape mirrors WhatsApp: MessagingStyle (sender, body) pairs when present;
 * otherwise group bodies are "Sender: text" under a group `EXTRA_TITLE`. Messenger
 * announces removals as an "unsent a message" line, captured as DELETED.
 */
internal class MessengerParser : NotificationParser(AppPackage.MESSENGER) {

    override fun canParse(e: RawEvent.NotificationPosted): Boolean =
        when (AppPackage.fromPackageId(e.packageName)) {
            AppPackage.MESSENGER, AppPackage.FACEBOOK -> true
            else -> false
        }

    override fun parse(e: RawEvent.NotificationPosted): List<Message> {
        // The true source app (MESSENGER vs FACEBOOK) is recovered downstream from
        // RawEvent.packageName when the data layer resolves the Conversation, so it
        // is not carried on the Message here (the model has no appPackage field).
        val title = e.title?.trim().orEmpty()
        val isGroup = !e.subText.isNullOrBlank() ||
            e.messages.any { it.first.isNotBlank() && it.first != title }

        if (e.messages.isNotEmpty()) {
            val out = ArrayList<Message>(e.messages.size)
            for ((rawSender, rawBody) in e.messages) {
                val deleted = PatternCatalog.isDeletedSentinel(rawBody)
                val sender = rawSender.ifBlank { title.ifEmpty { UNKNOWN_SENDER } }
                buildMessage(
                    e = e,
                    sender = sender,
                    body = rawBody,
                    status = if (deleted) MessageStatus.DELETED else MessageStatus.ACTIVE
                )?.let(out::add)
            }
            if (out.isNotEmpty()) return out
        }

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
