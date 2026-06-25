package com.quietping.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [Rule.activeAt] / [Rule.hasWindow] — the time-window gate (Bundle 2).
 */
class RuleWindowTest {

    private fun rule(start: Int, end: Int) = Rule(
        id = 1L,
        appPackage = AppPackage.WHATSAPP,
        type = TriggerType.KEYWORD,
        pattern = "x",
        soundPreset = SoundPreset.DROPLET,
        dndOverride = false,
        enabled = true,
        windowStartMin = start,
        windowEndMin = end
    )

    @Test
    fun `no window means always active`() {
        val r = rule(-1, -1)
        assertThat(r.hasWindow).isFalse()
        assertThat(r.activeAt(0)).isTrue()
        assertThat(r.activeAt(720)).isTrue()
        assertThat(r.activeAt(1439)).isTrue()
    }

    @Test
    fun `daytime window 9am to 5pm`() {
        val r = rule(9 * 60, 17 * 60) // 540..1020
        assertThat(r.hasWindow).isTrue()
        assertThat(r.activeAt(8 * 60)).isFalse()   // 08:00 before
        assertThat(r.activeAt(9 * 60)).isTrue()    // 09:00 inclusive start
        assertThat(r.activeAt(12 * 60)).isTrue()   // noon inside
        assertThat(r.activeAt(17 * 60)).isFalse()  // 17:00 exclusive end
        assertThat(r.activeAt(20 * 60)).isFalse()  // evening after
    }

    @Test
    fun `overnight window 10pm to 7am wraps midnight`() {
        val r = rule(22 * 60, 7 * 60) // 1320..420
        assertThat(r.activeAt(23 * 60)).isTrue()   // 23:00 active
        assertThat(r.activeAt(0)).isTrue()         // midnight active
        assertThat(r.activeAt(6 * 60)).isTrue()    // 06:00 active
        assertThat(r.activeAt(7 * 60)).isFalse()   // 07:00 exclusive end
        assertThat(r.activeAt(12 * 60)).isFalse()  // noon inactive
    }

    @Test
    fun `degenerate equal start and end is treated as no window`() {
        val r = rule(600, 600)
        assertThat(r.hasWindow).isFalse()
        assertThat(r.activeAt(600)).isTrue()
        assertThat(r.activeAt(0)).isTrue()
    }
}
