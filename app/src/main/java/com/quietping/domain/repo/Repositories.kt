package com.quietping.domain.repo

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.BreakInEvent
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.MediaItem
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

    /**
     * Find or create the conversation identified by ([appPackage], [conversationKey]),
     * refreshing its [displayName] / [isGroup] flag, and return its row id. Idempotent.
     *
     * Callers must resolve a conversation row before ingesting its first [Message],
     * otherwise the message's foreign key has no parent to reference.
     */
    suspend fun resolveConversationId(
        appPackage: AppPackage,
        conversationKey: String,
        displayName: String,
        isGroup: Boolean
    ): Long

    /** Delete every message captured strictly before [epochMillis] (retention purge). */
    suspend fun purgeOlderThan(epochMillis: Long)

    /**
     * Delete SMS messages that look like one-time passcodes captured before
     * [epochMillis] (OTP auto-delete). Returns the number removed.
     */
    suspend fun purgeOtpOlderThan(epochMillis: Long): Int

    /**
     * Read-only lookup of an existing conversation's row id by its stable identity,
     * or null when none exists. Unlike [resolveConversationId] this never creates or
     * mutates a row — used on notification-removal to cancel re-ping loops.
     */
    suspend fun conversationIdFor(appPackage: AppPackage, conversationKey: String): Long?

    /**
     * Read-only lookup of a conversation by row id, or null when none exists. Used by
     * the capture pipeline to read the group [Conversation.watched] flag before rule
     * evaluation (the per-group mute gate).
     */
    suspend fun conversationById(id: Long): Conversation?

    /**
     * Set the per-group alert [watched] flag for the conversation [id]. A muted group
     * (`watched = false`) is still archived but its messages skip the RuleEngine.
     */
    suspend fun setWatched(id: Long, watched: Boolean)
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
 * Read access to the on-device media vault (images / video / voice notes captured
 * from messaging apps). A flat file gallery — there is no media↔message link.
 */
interface MediaRepository {

    /** All captured media files, most-recent first. Re-emits when [refresh] is called. */
    fun media(): Flow<List<MediaItem>>

    /** Re-scan the vault directory (e.g. on screen open) so [media] re-emits. */
    suspend fun refresh()
}

/**
 * Reactive access to the privacy break-in log (failed unlock attempts).
 */
interface BreakInRepository {

    /** The [limit] most recent failed-unlock attempts, newest first. */
    fun recent(limit: Int): Flow<List<BreakInEvent>>

    /** Record a failed unlock attempt with a coarse [reason]. */
    suspend fun record(reason: String)

    /** Clear the entire break-in log. */
    suspend fun clear()
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
