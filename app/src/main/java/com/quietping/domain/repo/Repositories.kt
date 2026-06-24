package com.quietping.domain.repo

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.Message
import com.quietping.domain.model.Rule
import com.quietping.domain.model.VipContact
import kotlinx.coroutines.flow.Flow

/**
 * Reactive access to captured conversations and messages (the Vault store).
 */
interface MessageRepository {

    /** All conversations, most-recent first. */
    fun conversations(): Flow<List<Conversation>>

    /** The ordered message thread for [conversationId], including version history. */
    fun thread(conversationId: Long): Flow<List<Message>>

    /** Messages currently flagged DELETED ("Recovered"), across all conversations. */
    fun deleted(): Flow<List<Message>>

    /** Messages currently flagged EDITED, across all conversations. */
    fun edited(): Flow<List<Message>>

    /**
     * Insert or update [message] (dedupe + version append handled by the store).
     * @return the row id of the affected message.
     */
    suspend fun upsert(message: Message): Long

    /** Delete every message captured strictly before [epochMillis] (retention purge). */
    suspend fun purgeOlderThan(epochMillis: Long)
}

/**
 * Reactive access to user-defined alert rules.
 */
interface RuleRepository {

    /** All rules. */
    fun rules(): Flow<List<Rule>>

    /** Rules scoped to a single [app]. */
    fun rulesFor(app: AppPackage): Flow<List<Rule>>

    /**
     * Insert or update [rule].
     * @return the row id of the affected rule.
     */
    suspend fun upsert(rule: Rule): Long

    /** Remove the rule with [id]. */
    suspend fun delete(id: Long)
}

/**
 * Reactive access to starred VIP contacts.
 */
interface VipRepository {

    /** All VIP contacts. */
    fun vips(): Flow<List<VipContact>>

    /** Add [vip]. */
    suspend fun add(vip: VipContact)

    /** Remove the VIP contact with [id]. */
    suspend fun remove(id: Long)
}

/**
 * Reactive access to the alert audit log.
 */
interface MatchRepository {

    /** The [limit] most recent fired alerts, newest first. */
    fun recent(limit: Int): Flow<List<MatchLog>>

    /** Append [match] to the audit log. */
    suspend fun log(match: MatchLog)
}
