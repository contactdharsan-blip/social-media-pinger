package com.quietping.domain.parser

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Message
import com.quietping.domain.model.RawEvent

/**
 * Per-app strategy that normalizes a posted notification into zero or more domain
 * [Message]s. Each implementation owns exactly one [appPackage] and is responsible
 * for that app's notification quirks (MessagingStyle, sentinels, locale formats).
 */
interface MessageParser {

    /** The app this parser handles. */
    val appPackage: AppPackage

    /**
     * Cheap pre-check: true if this parser recognizes [e] (typically by matching
     * [RawEvent.NotificationPosted.packageName] against [appPackage]).
     */
    fun canParse(e: RawEvent.NotificationPosted): Boolean

    /**
     * Extract domain messages from [e]. May return an empty list when nothing
     * actionable can be reliably parsed (fail safe — never guess).
     */
    fun parse(e: RawEvent.NotificationPosted): List<Message>
}
