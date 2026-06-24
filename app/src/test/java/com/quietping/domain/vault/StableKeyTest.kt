package com.quietping.domain.vault

import com.google.common.truth.Truth.assertThat
import com.quietping.domain.model.AppPackage
import org.junit.Test

class StableKeyTest {

    @Test
    fun `edit key is invariant to body changes`() {
        val a = StableKey.editKey(AppPackage.WHATSAPP, "conv1", "Alice", 1000L)
        val b = StableKey.editKey(AppPackage.WHATSAPP, "conv1", "Alice", 1000L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `edit key differs by sender, conversation, app, and time`() {
        val base = StableKey.editKey(AppPackage.WHATSAPP, "conv1", "Alice", 1000L)
        assertThat(base).isNotEqualTo(StableKey.editKey(AppPackage.WHATSAPP, "conv1", "Bob", 1000L))
        assertThat(base).isNotEqualTo(StableKey.editKey(AppPackage.WHATSAPP, "conv2", "Alice", 1000L))
        assertThat(base).isNotEqualTo(StableKey.editKey(AppPackage.INSTAGRAM, "conv1", "Alice", 1000L))
        assertThat(base).isNotEqualTo(StableKey.editKey(AppPackage.WHATSAPP, "conv1", "Alice", 1001L))
    }

    @Test
    fun `sender match is case-insensitive in edit key`() {
        val a = StableKey.editKey(AppPackage.WHATSAPP, "c", "Alice", 1L)
        val b = StableKey.editKey(AppPackage.WHATSAPP, "c", "alice", 1L)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `content key changes with body`() {
        val a = StableKey.contentKey(AppPackage.WHATSAPP, "c", "Alice", 1L, "hi")
        val b = StableKey.contentKey(AppPackage.WHATSAPP, "c", "Alice", 1L, "hello")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `content key equals for identical inputs`() {
        val a = StableKey.contentKey(AppPackage.WHATSAPP, "c", "Alice", 1L, "hi")
        val b = StableKey.contentKey(AppPackage.WHATSAPP, "c", "Alice", 1L, "hi")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `fields cannot bleed across the separator`() {
        // Without a robust separator, ("ab","c") and ("a","bc") could collide.
        val left = StableKey.editKey(AppPackage.WHATSAPP, "ab", "c", 1L)
        val right = StableKey.editKey(AppPackage.WHATSAPP, "a", "bc", 1L)
        assertThat(left).isNotEqualTo(right)
    }

    @Test
    fun `key is hex sha-256 length`() {
        val k = StableKey.editKey(AppPackage.WHATSAPP, "c", "Alice", 1L)
        assertThat(k).matches("[0-9a-f]{64}")
    }
}
