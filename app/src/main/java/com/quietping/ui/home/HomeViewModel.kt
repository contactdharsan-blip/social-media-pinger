package com.quietping.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.Rule
import com.quietping.domain.repo.MatchRepository
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.security.DecoyMode
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    // Bumped by retry() to re-subscribe to the dashboard streams after a load failure.
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> =
        retryTrigger
            .flatMapLatest {
                combine(
                    ruleRepository.rules(),
                    matchRepository.recent(RECENT_LIMIT)
                ) { rules, matches ->
                    HomeUiState(
                        apps = buildAppStatuses(rules),
                        // Decoy session (unlocked with the decoy PIN): suppress the real
                        // match feed so a coerced unlock reveals no sender names/snippets.
                        recentMatches = if (DecoyMode.isActive) emptyList() else buildFeed(matches, rules),
                        isLoading = false
                    )
                }.catch {
                    emit(HomeUiState(isLoading = false, errorMessage = "Couldn't load your dashboard."))
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HomeUiState(isLoading = true)
            )

    /** Re-subscribe to the dashboard streams after a load error. */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

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
