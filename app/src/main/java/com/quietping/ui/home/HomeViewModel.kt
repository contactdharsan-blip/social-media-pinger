package com.quietping.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.Rule
import com.quietping.domain.repo.MatchRepository
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Home dashboard. Joins the user's rules with the live alert
 * feed to render per-app status cards and a newest-first match feed (PRD §9.1).
 *
 * "Enabling" an app is expressed through its rules: an app is considered ON when
 * it has at least one enabled rule. Toggling an app therefore flips the `enabled`
 * flag on every rule for that app via [RuleRepository.upsert] — staying entirely
 * within the domain contract (no app-level switch table exists).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val matchRepository: MatchRepository,
    @Suppress("unused") private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Apps surfaced as toggle cards on Home (chat apps + SMS), in display order. */
    private val dashboardApps: List<AppPackage> = listOf(
        AppPackage.WHATSAPP,
        AppPackage.INSTAGRAM,
        AppPackage.MESSENGER,
        AppPackage.SMS
    )

    val uiState: StateFlow<HomeUiState> =
        combine(
            ruleRepository.rules(),
            matchRepository.recent(RECENT_LIMIT)
        ) { rules, matches ->
            HomeUiState(
                apps = buildAppStatuses(rules),
                recentMatches = buildFeed(matches, rules),
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(isLoading = true)
        )

    // Latest rules snapshot, kept current by the collector below; used so the
    // toggle can flip concrete rules without re-collecting the Flow inline.
    @Volatile
    private var latestRules: List<Rule> = emptyList()

    init {
        viewModelScope.launch {
            ruleRepository.rules().collect { latestRules = it }
        }
    }

    /**
     * Toggle whether [app] is watched. Flips `enabled` on every rule for that app
     * via [RuleRepository.upsert]; the reactive rules() Flow then re-emits and the
     * UI updates itself. If [enabled] is true but the app has no rules yet this is
     * a no-op (the user adds rules from the Rules screen) — the card stays off.
     */
    fun setAppEnabled(app: AppPackage, enabled: Boolean) {
        viewModelScope.launch {
            latestRules
                .filter { it.appPackage == app && it.enabled != enabled }
                .forEach { rule -> ruleRepository.upsert(rule.copy(enabled = enabled)) }
        }
    }

    private fun buildAppStatuses(rules: List<Rule>): List<AppStatus> =
        dashboardApps.map { app ->
            val appRules = rules.filter { it.appPackage == app }
            val enabledCount = appRules.count { it.enabled }
            AppStatus(
                appPackage = app,
                displayName = app.displayLabel(),
                enabled = enabledCount > 0,
                ruleCount = enabledCount
            )
        }

    private fun buildFeed(matches: List<MatchLog>, rules: List<Rule>): List<MatchFeedItem> {
        val rulesById = rules.associateBy { it.id }
        return matches
            .sortedByDescending { it.firedAt }
            .map { log ->
                val rule = rulesById[log.ruleId]
                MatchFeedItem(
                    matchId = log.id,
                    messageId = log.messageId,
                    appPackage = rule?.appPackage,
                    trigger = rule?.type,
                    pattern = rule?.pattern.orEmpty(),
                    firedAt = log.firedAt
                )
            }
    }

    companion object {
        private const val RECENT_LIMIT = 30
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** A human-friendly label for an [AppPackage] (Home/feed display). */
internal fun AppPackage.displayLabel(): String = when (this) {
    AppPackage.WHATSAPP -> "WhatsApp"
    AppPackage.INSTAGRAM -> "Instagram"
    AppPackage.MESSENGER -> "Messenger"
    AppPackage.FACEBOOK -> "Facebook"
    AppPackage.SMS -> "Messages"
}
