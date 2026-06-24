package com.quietping.domain.parser

import com.google.common.truth.Truth.assertThat
import com.quietping.domain.model.AppPackage
import org.junit.Test

class PatternCatalogTest {

    @Test
    fun `detects whatsapp poll`() {
        assertThat(PatternCatalog.isPoll(AppPackage.WHATSAPP, "Poll: where to eat?")).isTrue()
        assertThat(PatternCatalog.isPoll(AppPackage.WHATSAPP, "created a poll")).isTrue()
        assertThat(PatternCatalog.isPoll(AppPackage.WHATSAPP, "just chatting")).isFalse()
    }

    @Test
    fun `detects mention and reply phrasing`() {
        assertThat(PatternCatalog.isReplyOrMention(AppPackage.INSTAGRAM, "alice mentioned you")).isTrue()
        assertThat(PatternCatalog.isReplyOrMention(AppPackage.WHATSAPP, "bob replied to you")).isTrue()
        assertThat(PatternCatalog.isReplyOrMention(AppPackage.WHATSAPP, "ordinary text")).isFalse()
    }

    @Test
    fun `detects group events`() {
        assertThat(PatternCatalog.isGroupEvent(AppPackage.WHATSAPP, "Carl added you")).isTrue()
        assertThat(PatternCatalog.isGroupEvent(AppPackage.WHATSAPP, "missed voice call")).isTrue()
        assertThat(PatternCatalog.isGroupEvent(AppPackage.WHATSAPP, "hello there")).isFalse()
    }

    @Test
    fun `detects deleted sentinels`() {
        assertThat(PatternCatalog.isDeletedSentinel("This message was deleted")).isTrue()
        assertThat(PatternCatalog.isDeletedSentinel("Bob unsent a message")).isTrue()
        assertThat(PatternCatalog.isDeletedSentinel("normal message")).isFalse()
    }

    @Test
    fun `detects summary noise`() {
        assertThat(PatternCatalog.isSummaryNoise("5 new messages")).isTrue()
        assertThat(PatternCatalog.isSummaryNoise("   ")).isTrue()
        assertThat(PatternCatalog.isSummaryNoise("Hey, lunch?")).isFalse()
    }

    @Test
    fun `mentionsHandle is word-boundary aware`() {
        assertThat(PatternCatalog.mentionsHandle("ping @dharsan now", listOf("dharsan"))).isTrue()
        assertThat(PatternCatalog.mentionsHandle("the annual report", listOf("ann"))).isFalse()
        assertThat(PatternCatalog.mentionsHandle("anything", emptyList())).isFalse()
    }
}
