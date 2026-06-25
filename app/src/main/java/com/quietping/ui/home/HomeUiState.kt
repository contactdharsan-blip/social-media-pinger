package com.quietping.ui.home

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.TriggerType

/**
 * Per-app summary row on the Home dashboard. [enabled] reflects whether the app
 * has at least one active rule (the user's "watch this app" switch); [ruleCount]
 * is the number of enabled rules driving alerts for it. The display name + glyph are
 * derived from [appPackage] by the shared `AppToggleCard`.
 */
data class AppStatus(
    val appPackage: AppPackage,
    val enabled: Boolean,
    val ruleCount: Int
)

/**
 * A single entry in the live "Recent matches" feed. Built by joining a fired
 * [com.quietping.domain.model.MatchLog] against the rule that produced it so the
 * UI can show what fired and where, plus carry [messageId] for navigation.
 *
 * @param matchId    the MatchLog id (stable list key).
 * @param messageId  the captured message that matched (for opening in the Vault).
 * @param appPackage the source app of the firing rule (null if the rule is gone).
 * @param trigger    the trigger type of the firing rule (null if the rule is gone).
 * @param pattern    the rule's pattern/handle/keyword for a human-readable label.
 * @param firedAt    epoch millis the alert fired.
 */
data class MatchFeedItem(
    val matchId: Long,
    val messageId: Long,
    val appPackage: AppPackage?,
    val trigger: TriggerType?,
    val pattern: String,
    val firedAt: Long
)

/**
 * UI state for the Home dashboard (PRD §9.1): per-app status cards plus the live
 * match feed. Models the four async states explicitly (DESIGN.md feedback
 * contract) via [isLoading] + emptiness of [recentMatches] + [errorMessage].
 *
 * @param apps          per-app toggle/summary cards in display order.
 * @param recentMatches newest-first feed of fired alerts.
 * @param isLoading     true until the first repository emission arrives.
 * @param errorMessage  non-null when the backing Flow failed to load (DB read error).
 */
data class HomeUiState(
    val apps: List<AppStatus> = emptyList(),
    val recentMatches: List<MatchFeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    /** True when loading has finished, no error occurred, and no alerts have fired yet. */
    val isFeedEmpty: Boolean get() = !isLoading && errorMessage == null && recentMatches.isEmpty()
}
