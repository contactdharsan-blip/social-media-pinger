package com.quietping.capture

/**
 * Pure SMS/MMS group-thread classification, kept free of any Android dependency so it
 * is unit-testable without Robolectric (the [SmsObserver] companion can't be loaded
 * under a plain JVM test — it evaluates `Telephony` URIs on class-init).
 *
 * Android stores a thread's canonical recipient ids as a single space-separated string
 * (`recipient_ids` in the simple conversations view). A thread addressed to more than
 * one recipient is a group (group MMS); one or zero recipients is a 1:1 thread.
 */
object SmsGrouping {

    /**
     * Number of recipients encoded in a thread's space-separated [recipientIds].
     * Null/blank safe; collapses repeated whitespace so " 12  34 " counts as 2.
     */
    fun recipientCount(recipientIds: String?): Int =
        recipientIds?.trim()
            ?.split(' ')
            ?.count { it.isNotBlank() }
            ?: 0

    /** True when [recipientIds] names more than one recipient (a group thread). */
    fun isGroupThread(recipientIds: String?): Boolean = recipientCount(recipientIds) > 1
}
