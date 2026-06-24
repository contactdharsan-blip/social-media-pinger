package com.quietping.domain.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AhoCorasickTest {

    @Test
    fun `matches a single keyword case insensitively`() {
        val ac = AhoCorasick.build(listOf("project"))
        assertThat(ac.containsMatch("The PROJECT is due")).isTrue()
        assertThat(ac.containsMatch("the project is due")).isTrue()
    }

    @Test
    fun `respects word boundaries`() {
        val ac = AhoCorasick.build(listOf("ann"))
        assertThat(ac.containsMatch("ann said hi")).isTrue()
        assertThat(ac.containsMatch("the annual report")).isFalse()
        assertThat(ac.containsMatch("call ann.")).isTrue()
    }

    @Test
    fun `matches any of several keywords in one pass`() {
        val ac = AhoCorasick.build(listOf("alpha", "beta", "gamma"))
        assertThat(ac.containsMatch("the beta build")).isTrue()
        assertThat(ac.containsMatch("only gamma here")).isTrue()
        assertThat(ac.containsMatch("nothing relevant")).isFalse()
    }

    @Test
    fun `matches multi-word phrases verbatim`() {
        val ac = AhoCorasick.build(listOf("release candidate"))
        assertThat(ac.containsMatch("the release candidate ships friday")).isTrue()
        assertThat(ac.containsMatch("release the candidate")).isFalse()
    }

    @Test
    fun `empty automaton matches nothing`() {
        val ac = AhoCorasick.build(emptyList())
        assertThat(ac.containsMatch("anything at all")).isFalse()
    }

    @Test
    fun `blank patterns are ignored`() {
        val ac = AhoCorasick.build(listOf("  ", "", "hit"))
        assertThat(ac.containsMatch("a hit here")).isTrue()
        assertThat(ac.containsMatch("no match")).isFalse()
    }

    @Test
    fun `handles overlapping suffix patterns via failure links`() {
        // "she" and "he" overlap; scanning "ushers" must find "he" inside "ushers"
        // only at a word boundary, so it should NOT match here.
        val ac = AhoCorasick.build(listOf("he", "she"))
        assertThat(ac.containsMatch("he left")).isTrue()
        assertThat(ac.containsMatch("she left")).isTrue()
        assertThat(ac.containsMatch("ushers")).isFalse()
    }

    @Test
    fun `empty text never matches`() {
        val ac = AhoCorasick.build(listOf("x"))
        assertThat(ac.containsMatch("")).isFalse()
    }

    @Test
    fun `matches keyword adjacent to punctuation`() {
        val ac = AhoCorasick.build(listOf("deploy"))
        assertThat(ac.containsMatch("ready to deploy!")).isTrue()
        assertThat(ac.containsMatch("(deploy)")).isTrue()
        assertThat(ac.containsMatch("redeploys")).isFalse()
    }
}
