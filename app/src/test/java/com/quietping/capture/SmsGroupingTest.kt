package com.quietping.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [SmsGrouping] — the pure SMS/MMS group-thread classifier that feeds
 * the per-group mute gate. A thread is a group iff it has more than one recipient,
 * read from the provider's space-separated `recipient_ids`. These must hold without
 * any Android dependency (the logic is deliberately split out of [SmsObserver]).
 */
class SmsGroupingTest {

    @Test
    fun `single recipient is not a group`() {
        assertThat(SmsGrouping.recipientCount("12")).isEqualTo(1)
        assertThat(SmsGrouping.isGroupThread("12")).isFalse()
    }

    @Test
    fun `multiple recipients is a group`() {
        assertThat(SmsGrouping.recipientCount("12 34 56")).isEqualTo(3)
        assertThat(SmsGrouping.isGroupThread("12 34 56")).isTrue()
    }

    @Test
    fun `exactly two recipients is a group`() {
        assertThat(SmsGrouping.isGroupThread("12 34")).isTrue()
    }

    @Test
    fun `extra and surrounding whitespace is collapsed`() {
        assertThat(SmsGrouping.recipientCount("  12   34  ")).isEqualTo(2)
        assertThat(SmsGrouping.isGroupThread("  12   34  ")).isTrue()
    }

    @Test
    fun `null and blank fail safe to non-group`() {
        assertThat(SmsGrouping.recipientCount(null)).isEqualTo(0)
        assertThat(SmsGrouping.recipientCount("")).isEqualTo(0)
        assertThat(SmsGrouping.recipientCount("   ")).isEqualTo(0)
        assertThat(SmsGrouping.isGroupThread(null)).isFalse()
        assertThat(SmsGrouping.isGroupThread("")).isFalse()
    }
}
