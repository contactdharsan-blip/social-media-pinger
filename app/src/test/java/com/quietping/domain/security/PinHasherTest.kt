package com.quietping.domain.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for [PinHasher] — salted decoy-PIN hashing + verification. */
class PinHasherTest {

    @Test
    fun `verify accepts the correct pin`() {
        val stored = PinHasher.hash("2468")
        assertThat(PinHasher.verify("2468", stored)).isTrue()
    }

    @Test
    fun `verify rejects a wrong pin`() {
        val stored = PinHasher.hash("2468")
        assertThat(PinHasher.verify("1357", stored)).isFalse()
    }

    @Test
    fun `the same pin hashes differently each time (salted)`() {
        val a = PinHasher.hash("0000")
        val b = PinHasher.hash("0000")
        assertThat(a).isNotEqualTo(b)
        // ...yet both verify.
        assertThat(PinHasher.verify("0000", a)).isTrue()
        assertThat(PinHasher.verify("0000", b)).isTrue()
    }

    @Test
    fun `verify is false for blank or malformed stored values`() {
        assertThat(PinHasher.verify("1234", "")).isFalse()
        assertThat(PinHasher.verify("1234", "not-a-valid-hash")).isFalse()
        assertThat(PinHasher.verify("1234", "deadbeef")).isFalse() // no separator
    }

    @Test
    fun `stored format never contains the raw pin`() {
        val stored = PinHasher.hash("9911")
        assertThat(stored).doesNotContain("9911")
        assertThat(stored).contains(":")
    }
}
