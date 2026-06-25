package com.quietping.domain.alerts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [RepeatSenderTracker] — the repeated-sender break-through gate.
 * Clock is injected so timing is deterministic.
 */
class RepeatSenderTrackerTest {

    private class FakeClock(var t: Long = 0L) : () -> Long {
        override fun invoke(): Long = t
    }

    @Test
    fun `first fire from a sender never breaks through`() {
        val clock = FakeClock(1_000L)
        val tracker = RepeatSenderTracker(windowMs = 60_000L, now = clock)
        assertThat(tracker.shouldBreakThrough(1L, "Alice")).isFalse()
    }

    @Test
    fun `second fire within the window breaks through`() {
        val clock = FakeClock(1_000L)
        val tracker = RepeatSenderTracker(windowMs = 60_000L, now = clock)
        tracker.shouldBreakThrough(1L, "Alice")
        clock.t = 1_000L + 30_000L // 30s later, within 60s window
        assertThat(tracker.shouldBreakThrough(1L, "Alice")).isTrue()
    }

    @Test
    fun `second fire after the window does not break through`() {
        val clock = FakeClock(1_000L)
        val tracker = RepeatSenderTracker(windowMs = 60_000L, now = clock)
        tracker.shouldBreakThrough(1L, "Alice")
        clock.t = 1_000L + 120_000L // 2 min later, past 60s window
        assertThat(tracker.shouldBreakThrough(1L, "Alice")).isFalse()
    }

    @Test
    fun `different senders are tracked independently`() {
        val clock = FakeClock(1_000L)
        val tracker = RepeatSenderTracker(windowMs = 60_000L, now = clock)
        tracker.shouldBreakThrough(1L, "Alice")
        clock.t = 1_010L
        // Bob's first fire — Alice being recent must not leak into Bob.
        assertThat(tracker.shouldBreakThrough(1L, "Bob")).isFalse()
    }

    @Test
    fun `same sender in a different conversation is independent`() {
        val clock = FakeClock(1_000L)
        val tracker = RepeatSenderTracker(windowMs = 60_000L, now = clock)
        tracker.shouldBreakThrough(1L, "Alice")
        clock.t = 1_010L
        assertThat(tracker.shouldBreakThrough(2L, "Alice")).isFalse()
    }

    @Test
    fun `sender matching is case and whitespace insensitive`() {
        val clock = FakeClock(1_000L)
        val tracker = RepeatSenderTracker(windowMs = 60_000L, now = clock)
        tracker.shouldBreakThrough(1L, "Alice")
        clock.t = 1_010L
        assertThat(tracker.shouldBreakThrough(1L, "  alice ")).isTrue()
    }
}
