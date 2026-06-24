package com.quietping.domain.parser

import com.google.common.truth.Truth.assertThat
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.RawEvent
import org.junit.Test

class ParsersTest {

    private fun notif(
        pkg: String,
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        subText: String? = null,
        messages: List<Pair<String, String>> = emptyList()
    ) = RawEvent.NotificationPosted(
        packageName = pkg,
        key = "k1",
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        messages = messages,
        postedAt = 5_000L
    )

    // --- WhatsApp ---

    @Test
    fun `whatsapp canParse only its package`() {
        val p = WhatsAppParser()
        assertThat(p.canParse(notif("com.whatsapp"))).isTrue()
        assertThat(p.canParse(notif("com.instagram.android"))).isFalse()
    }

    @Test
    fun `whatsapp 1to1 maps title to sender and text to body`() {
        val p = WhatsAppParser()
        val out = p.parse(notif("com.whatsapp", title = "Alice", text = "see you at 6"))
        assertThat(out).hasSize(1)
        assertThat(out.first().sender).isEqualTo("Alice")
        assertThat(out.first().currentBody).isEqualTo("see you at 6")
        assertThat(out.first().source).isEqualTo(CaptureSource.NOTIFICATION)
    }

    @Test
    fun `whatsapp group splits inline sender from body`() {
        val p = WhatsAppParser()
        val out = p.parse(
            notif("com.whatsapp", title = "Trip Group", subText = "Trip Group", text = "Bob: bring snacks")
        )
        assertThat(out).hasSize(1)
        assertThat(out.first().sender).isEqualTo("Bob")
        assertThat(out.first().currentBody).isEqualTo("bring snacks")
    }

    @Test
    fun `whatsapp messaging style yields one message per pair`() {
        val p = WhatsAppParser()
        val out = p.parse(
            notif(
                "com.whatsapp",
                title = "Group",
                messages = listOf("Bob" to "hi", "Carol" to "hello")
            )
        )
        assertThat(out).hasSize(2)
        assertThat(out.map { it.sender }).containsExactly("Bob", "Carol")
    }

    @Test
    fun `whatsapp deleted sentinel marks message DELETED`() {
        val p = WhatsAppParser()
        val out = p.parse(notif("com.whatsapp", title = "Alice", text = "This message was deleted"))
        assertThat(out).hasSize(1)
        assertThat(out.first().status).isEqualTo(MessageStatus.DELETED)
    }

    @Test
    fun `whatsapp summary noise is dropped`() {
        val p = WhatsAppParser()
        val out = p.parse(notif("com.whatsapp", title = "WhatsApp", text = "5 new messages"))
        assertThat(out).isEmpty()
    }

    // --- Instagram ---

    @Test
    fun `instagram keeps activity title as body when no preview`() {
        val p = InstagramParser()
        val out = p.parse(notif("com.instagram.android", title = "alice mentioned you in a comment"))
        assertThat(out).hasSize(1)
        assertThat(out.first().sender).isEqualTo("alice")
        assertThat(out.first().currentBody).contains("mentioned you")
    }

    @Test
    fun `instagram dm maps title to sender`() {
        val p = InstagramParser()
        val out = p.parse(notif("com.instagram.android", title = "bob", text = "yo"))
        assertThat(out).hasSize(1)
        assertThat(out.first().sender).isEqualTo("bob")
        assertThat(out.first().currentBody).isEqualTo("yo")
    }

    // --- Messenger / Facebook ---

    @Test
    fun `messenger parses both orca and katana`() {
        val p = MessengerParser()
        assertThat(p.canParse(notif("com.facebook.orca"))).isTrue()
        assertThat(p.canParse(notif("com.facebook.katana"))).isTrue()
        assertThat(p.canParse(notif("com.whatsapp"))).isFalse()
    }

    @Test
    fun `messenger 1to1 maps title and text`() {
        val p = MessengerParser()
        val out = p.parse(notif("com.facebook.orca", title = "Eve", text = "call me"))
        assertThat(out).hasSize(1)
        assertThat(out.first().sender).isEqualTo("Eve")
        assertThat(out.first().currentBody).isEqualTo("call me")
    }

    // --- SMS ---

    @Test
    fun `sms parser tags source SMS via notification`() {
        val p = SmsParser()
        val out = p.parse(notif("com.android.messaging", title = "+15551234", text = "your code is 123"))
        assertThat(out).hasSize(1)
        assertThat(out.first().source).isEqualTo(CaptureSource.SMS)
        assertThat(out.first().sender).isEqualTo("+15551234")
    }

    @Test
    fun `sms fromSmsRow maps inbound row`() {
        val p = SmsParser()
        val msg = p.fromSmsRow(address = "+15550000", body = "hello", dateMillis = 9L, incoming = true)
        assertThat(msg).isNotNull()
        assertThat(msg!!.sender).isEqualTo("+15550000")
        assertThat(msg.currentBody).isEqualTo("hello")
        assertThat(msg.postedAt).isEqualTo(9L)
        assertThat(msg.source).isEqualTo(CaptureSource.SMS)
    }

    @Test
    fun `sms fromSmsRow marks outbound as self`() {
        val p = SmsParser()
        val msg = p.fromSmsRow(address = "+15550000", body = "reply", dateMillis = 9L, incoming = false)
        assertThat(msg!!.sender).isEqualTo("You")
    }

    @Test
    fun `sms fromSmsRow drops blank body`() {
        val p = SmsParser()
        assertThat(p.fromSmsRow(address = "x", body = "   ", dateMillis = 1L, incoming = true)).isNull()
    }

    @Test
    fun `app package resolution covers all source packages`() {
        assertThat(AppPackage.fromPackageId("com.whatsapp")).isEqualTo(AppPackage.WHATSAPP)
        assertThat(AppPackage.fromPackageId("com.facebook.katana")).isEqualTo(AppPackage.FACEBOOK)
        assertThat(AppPackage.fromPackageId("unknown.app")).isNull()
    }
}
