package com.quietping.domain.alerts

import com.quietping.domain.model.MatchResult

/**
 * Turns a [MatchResult] into a user-visible alert. Owns one NotificationChannel
 * per condition type (independent sound, vibration, importance, DND-bypass) and
 * fires an IMPORTANCE_HIGH heads-up notification when a rule matches.
 */
interface AlertDispatcher {

    /**
     * Idempotently create/refresh all notification channels for the supported
     * condition types and sound presets. Safe to call on every app/service start.
     */
    fun ensureChannels()

    /**
     * Deliver the alert for [match] on the channel implied by its rule, applying
     * the rule's sound preset and DND override.
     */
    fun fire(match: MatchResult)
}
