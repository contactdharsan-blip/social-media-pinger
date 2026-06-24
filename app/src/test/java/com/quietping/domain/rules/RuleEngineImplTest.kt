package com.quietping.domain.rules

import com.google.common.truth.Truth.assertThat
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.Rule
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import org.junit.Test

class RuleEngineImplTest {

    private val engine = RuleEngineImpl()

    private fun message(
        body: String,
        sender: String = "Alice",
        source: CaptureSource = CaptureSource.NOTIFICATION
    ) = Message(
        id = 1L,
        conversationId = 10L,
        sender = sender,
        postedAt = 1_000L,
        capturedAt = 1_000L,
        source = source,
        status = MessageStatus.ACTIVE,
        currentBody = body
    )

    private fun rule(
        type: TriggerType,
        pattern: String,
        app: AppPackage = AppPackage.WHATSAPP,
        enabled: Boolean = true
    ) = Rule(
        id = 1L,
        appPackage = app,
        type = type,
        pattern = pattern,
        soundPreset = SoundPreset.DROPLET,
        dndOverride = false,
        enabled = enabled
    )

    @Test
    fun `keyword rule matches body term`() {
        val rules = listOf(rule(TriggerType.KEYWORD, "invoice, payment"))
        val matches = engine.evaluate(message("Did you send the invoice yet?"), rules)
        assertThat(matches).hasSize(1)
        assertThat(matches.first().rule.type).isEqualTo(TriggerType.KEYWORD)
    }

    @Test
    fun `keyword rule does not match across word boundary`() {
        val rules = listOf(rule(TriggerType.KEYWORD, "cat"))
        assertThat(engine.evaluate(message("concatenate the list"), rules)).isEmpty()
    }

    @Test
    fun `disabled rules are skipped`() {
        val rules = listOf(rule(TriggerType.KEYWORD, "urgent", enabled = false))
        assertThat(engine.evaluate(message("this is urgent"), rules)).isEmpty()
    }

    @Test
    fun `name mention matches plain name in body`() {
        val rules = listOf(rule(TriggerType.NAME_MENTION, "Dharsan"))
        assertThat(engine.evaluate(message("hey Dharsan can you check"), rules)).hasSize(1)
    }

    @Test
    fun `name mention matches at-handle`() {
        val rules = listOf(rule(TriggerType.NAME_MENTION, "dharsan"))
        assertThat(engine.evaluate(message("ping @dharsan please"), rules)).hasSize(1)
    }

    @Test
    fun `name mention matches app native mentioned-you sentinel`() {
        val rules = listOf(rule(TriggerType.NAME_MENTION, "someoneelse"))
        // Body has the app's own phrasing even though the configured name is absent.
        assertThat(engine.evaluate(message("Bob mentioned you in Group"), rules)).hasSize(1)
    }

    @Test
    fun `poll rule matches whatsapp poll sentinel`() {
        val rules = listOf(rule(TriggerType.POLL_CREATED, ""))
        assertThat(engine.evaluate(message("Poll: Lunch at 1pm?"), rules)).hasSize(1)
    }

    @Test
    fun `poll rule does not match ordinary text`() {
        val rules = listOf(rule(TriggerType.POLL_CREATED, ""))
        assertThat(engine.evaluate(message("just a normal message"), rules)).isEmpty()
    }

    @Test
    fun `reply mention matches replied sentinel`() {
        val rules = listOf(rule(TriggerType.REPLY_MENTION, ""))
        assertThat(engine.evaluate(message("Carol replied to you"), rules)).hasSize(1)
    }

    @Test
    fun `group event matches added-you sentinel`() {
        val rules = listOf(rule(TriggerType.GROUP_EVENT, ""))
        assertThat(engine.evaluate(message("Dave added you to Weekend Trip"), rules)).hasSize(1)
    }

    @Test
    fun `vip contact matches sender regardless of content`() {
        val rules = listOf(rule(TriggerType.VIP_CONTACT, "Alice"))
        assertThat(engine.evaluate(message(body = "anything", sender = "Alice"), rules)).hasSize(1)
    }

    @Test
    fun `vip contact whole-word match does not fire on partial`() {
        val rules = listOf(rule(TriggerType.VIP_CONTACT, "Bob"))
        assertThat(engine.evaluate(message(body = "hi", sender = "Bobby"), rules)).isEmpty()
        assertThat(engine.evaluate(message(body = "hi", sender = "Bob Jones"), rules)).hasSize(1)
    }

    @Test
    fun `multiple rules can fire on one message`() {
        val rules = listOf(
            rule(TriggerType.KEYWORD, "poll"),
            rule(TriggerType.POLL_CREATED, "")
        )
        val matches = engine.evaluate(message("Poll: dinner?"), rules)
        assertThat(matches).hasSize(2)
    }

    @Test
    fun `empty rule list yields no matches`() {
        assertThat(engine.evaluate(message("hi"), emptyList())).isEmpty()
    }
}
