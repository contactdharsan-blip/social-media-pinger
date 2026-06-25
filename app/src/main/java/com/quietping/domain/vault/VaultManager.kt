package com.quietping.domain.vault

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Message

/**
 * Owns the encrypted Message Vault. Dedupes incoming messages by stable key,
 * appends a new version when an existing message's body changes (edit history),
 * and flags messages as deleted when the source revokes/removes them.
 */
interface VaultManager {

    /**
     * Persist [message]: insert if new, or append a [com.quietping.domain.model.MessageVersion]
     * and mark EDITED if the same logical message reappears with a changed body.
     *
     * @return the row id of the stored message (the existing row's id on a
     *   dedupe/edit, or the newly inserted row's id), so callers can attach the
     *   real id to the message before rule matching / alert dispatch.
     */
    suspend fun ingest(message: Message): Long

    /**
     * Mark the cached message identified by [conversationKey] + [appPackage] (and
     * optionally [body], when the source provides the deleted text) as DELETED so
     * it surfaces as "Recovered" in the Vault.
     */
    suspend fun markDeleted(conversationKey: String, appPackage: AppPackage, body: String?)
}
