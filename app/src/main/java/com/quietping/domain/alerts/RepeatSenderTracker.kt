package com.quietping.domain.alerts

/**
 * Repeated-sender break-through (OS "repeat callers" / BuzzKill frequency parity).
 *
 * When the same sender in the same conversation fires an alert twice within a short
 * window, the second alert is escalated to a hard, full-screen [com.quietping.domain.model.AlertStyle.CRITICAL]
 * even if the rule itself only asked for a standard ping — the assumption being that
 * a person messaging you repeatedly in quick succession is trying to reach you.
 *
 * Pure and clock-injected so it is deterministic under test. Not thread-safe by
 * itself; the dispatcher confines all calls to its own coroutine scope.
 *
 * @param windowMs   max gap between two fires from the same sender to count as a burst.
 * @param now        clock (epoch millis), injectable for tests.
 */
class RepeatSenderTracker(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lastFiredAt = HashMap<String, Long>()

    /**
     * Record a fire for ([conversationId], [sender]) and report whether it should
     * break through (i.e. a prior fire from the same sender happened within [windowMs]).
     */
    fun shouldBreakThrough(conversationId: Long, sender: String): Boolean {
        val key = conversationId.toString() + ":" + sender.trim().lowercase()
        val t = now()
        val prev = lastFiredAt.put(key, t)
        return prev != null && (t - prev) <= windowMs
    }

    companion object {
        /** Two messages within two minutes from one sender counts as a burst. */
        const val DEFAULT_WINDOW_MS = 2 * 60 * 1000L
    }
}
