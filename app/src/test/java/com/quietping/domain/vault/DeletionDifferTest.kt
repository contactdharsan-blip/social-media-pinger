package com.quietping.domain.vault

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeletionDifferTest {

    private val differ = DeletionDiffer()

    private fun msg(sender: String, body: String, at: Long) = SeenMessage(sender, body, at)

    @Test
    fun `empty previous yields nothing`() {
        val current = listOf(msg("Ann", "hi", 100))
        assertThat(differ.deletions(emptyList(), current)).isEmpty()
    }

    @Test
    fun `empty current yields nothing`() {
        val previous = listOf(msg("Ann", "hi", 100))
        assertThat(differ.deletions(previous, emptyList())).isEmpty()
    }

    @Test
    fun `middle message removed while older survives is flagged deleted`() {
        val previous = listOf(
            msg("Ann", "first", 100),
            msg("Ann", "SECRET", 200),
            msg("Ann", "third", 300),
        )
        // "SECRET" (200) gone; "first" (100, older) still shown ⇒ deliberate delete.
        val current = listOf(
            msg("Ann", "first", 100),
            msg("Ann", "third", 300),
        )
        val deleted = differ.deletions(previous, current)
        assertThat(deleted).containsExactly(msg("Ann", "SECRET", 200))
    }

    @Test
    fun `oldest message dropping off the top is treated as scroll-off not deletion`() {
        val previous = listOf(
            msg("Ann", "oldest", 100),
            msg("Ann", "mid", 200),
        )
        // "oldest" (100) gone but nothing older than it survives ⇒ scrolled off.
        val current = listOf(
            msg("Ann", "mid", 200),
            msg("Ann", "new", 300),
        )
        assertThat(differ.deletions(previous, current)).isEmpty()
    }

    @Test
    fun `changed body for same slot is an edit not a deletion`() {
        val previous = listOf(
            msg("Ann", "before edit", 100),
            msg("Ann", "anchor", 50),
        )
        val current = listOf(
            msg("Ann", "after edit", 100),
            msg("Ann", "anchor", 50),
        )
        // Same (sender, postedAt) ⇒ still present; body change is not our concern.
        assertThat(differ.deletions(previous, current)).isEmpty()
    }

    @Test
    fun `fully fresh newer batch is not mistaken for mass deletion`() {
        val previous = listOf(
            msg("Ann", "a", 100),
            msg("Ann", "b", 200),
        )
        // A different, entirely newer conversation snapshot: all newer ⇒ previous
        // entries are older than everything shown ⇒ none flagged.
        val current = listOf(
            msg("Bob", "x", 500),
            msg("Bob", "y", 600),
        )
        assertThat(differ.deletions(previous, current)).isEmpty()
    }

    @Test
    fun `multiple deliberate deletions flagged together`() {
        val previous = listOf(
            msg("Ann", "keep-old", 100),
            msg("Ann", "del1", 200),
            msg("Ann", "del2", 300),
            msg("Ann", "keep-new", 400),
        )
        val current = listOf(
            msg("Ann", "keep-old", 100),
            msg("Ann", "keep-new", 400),
        )
        assertThat(differ.deletions(previous, current))
            .containsExactly(msg("Ann", "del1", 200), msg("Ann", "del2", 300))
    }
}
