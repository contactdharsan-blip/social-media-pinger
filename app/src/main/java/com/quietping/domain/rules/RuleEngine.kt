package com.quietping.domain.rules

import com.quietping.domain.model.MatchResult
import com.quietping.domain.model.Message
import com.quietping.domain.model.Rule

/**
 * Evaluates a captured [Message] against a set of [Rule]s and returns every
 * condition that fired. Implementations should run in O(n) over the message text
 * (e.g. Aho-Corasick for keywords + compiled regex) and only consider enabled
 * rules whose appPackage matches the message's conversation.
 */
interface RuleEngine {

    /**
     * @return one [MatchResult] per rule that matched [message]; empty if none.
     */
    fun evaluate(message: Message, rules: List<Rule>): List<MatchResult>
}
