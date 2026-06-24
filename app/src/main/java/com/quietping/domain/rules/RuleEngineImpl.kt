package com.quietping.domain.rules

import com.quietping.domain.model.MatchResult
import com.quietping.domain.model.Message
import com.quietping.domain.model.Rule
import com.quietping.domain.model.TriggerType
import com.quietping.domain.parser.PatternCatalog
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [RuleEngine].
 *
 * For each enabled rule it tests the [Message] according to the rule's
 * [TriggerType]:
 *  - **NAME_MENTION** — the rule pattern holds the user's display name(s)/handle(s)
 *    (comma- or newline-separated). A match fires if any name appears in the body
 *    (word-boundary, case-insensitive via Aho-Corasick) **or** the body contains an
 *    explicit `@handle` mention, **or** the app's own "mentioned you/replied"
 *    sentinel is present.
 *  - **KEYWORD** — any of the rule's terms/phrases occurs in the body
 *    (Aho-Corasick, O(n), word-boundary aware).
 *  - **REPLY_MENTION** — the body matches a reply/mention sentinel for the message's
 *    app, or contains an `@handle` from the rule pattern.
 *  - **POLL_CREATED** / **GROUP_EVENT** — the body matches the app's poll /
 *    group-event sentinels.
 *  - **VIP_CONTACT** — the message [Message.sender] matches the rule pattern
 *    (a starred handle), regardless of content.
 *
 * The caller is expected to pass the rules already scoped to the message's app
 * (e.g. via `RuleRepository.rulesFor(app)`); the engine additionally skips any
 * disabled rule. Sentinel-based triggers (poll / reply / group-event) read the
 * app straight off the [Rule.appPackage] being tested, so the engine needs no
 * extra context to resolve the message's app.
 *
 * Compiled keyword automatons are cached by pattern string so repeated evaluation
 * of the same rule across many messages does not rebuild the automaton.
 */
class RuleEngineImpl : RuleEngine {

    private val automatonCache = ConcurrentHashMap<String, AhoCorasick>()

    override fun evaluate(message: Message, rules: List<Rule>): List<MatchResult> {
        if (rules.isEmpty()) return emptyList()
        val body = message.currentBody
        val results = ArrayList<MatchResult>()
        for (rule in rules) {
            if (!rule.enabled) continue
            if (matches(rule, message, body)) {
                results.add(MatchResult(rule = rule, message = message))
            }
        }
        return results
    }

    private fun matches(rule: Rule, message: Message, body: String): Boolean =
        when (rule.type) {
            TriggerType.KEYWORD ->
                automatonFor(rule.pattern).containsMatch(body)

            TriggerType.NAME_MENTION -> {
                val names = splitPatterns(rule.pattern)
                automatonFor(rule.pattern).containsMatch(body) ||
                    PatternCatalog.mentionsHandle(body, names) ||
                    (rule.appPackage.let { PatternCatalog.isReplyOrMention(it, body) })
            }

            TriggerType.REPLY_MENTION -> {
                val handles = splitPatterns(rule.pattern)
                PatternCatalog.isReplyOrMention(rule.appPackage, body) ||
                    PatternCatalog.mentionsHandle(body, handles)
            }

            TriggerType.POLL_CREATED ->
                PatternCatalog.isPoll(rule.appPackage, body)

            TriggerType.GROUP_EVENT ->
                PatternCatalog.isGroupEvent(rule.appPackage, body)

            TriggerType.VIP_CONTACT ->
                matchesVip(rule.pattern, message.sender)
        }

    /**
     * VIP matching: the rule pattern is a starred handle/contact name. Match if it
     * equals the sender (case-insensitive) or the sender contains it as a
     * whole-token substring (handles "Alice" vs "Alice Smith"). Blank ⇒ no match.
     */
    private fun matchesVip(pattern: String, sender: String): Boolean {
        val target = sender.trim()
        if (target.isEmpty()) return false
        for (raw in splitPatterns(pattern)) {
            val handle = raw.removePrefix("@").trim()
            if (handle.isEmpty()) continue
            if (handle.equals(target, ignoreCase = true)) return true
            // whole-word containment so "Bob" matches "Bob Jones" but not "Bobby"
            val quoted = Regex.escape(handle)
            val re = Regex("""(^|[^A-Za-z0-9_])$quoted($|[^A-Za-z0-9_])""", RegexOption.IGNORE_CASE)
            if (re.containsMatchIn(target)) return true
        }
        return false
    }

    private fun automatonFor(pattern: String): AhoCorasick =
        automatonCache.getOrPut(pattern) { AhoCorasick.build(splitPatterns(pattern)) }

    private fun splitPatterns(pattern: String): List<String> =
        pattern.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
