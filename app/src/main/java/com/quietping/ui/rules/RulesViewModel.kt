package com.quietping.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Rule
import com.quietping.domain.repo.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [RulesScreen]: streams every [Rule] from the [RuleRepository], groups them
 * by [AppPackage] (in [AppPackage.entries] order so the list is stable), and lets
 * the user flip a rule's enabled flag or delete it.
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository
) : ViewModel() {

    val uiState: StateFlow<RulesUiState> =
        ruleRepository.rules()
            .map { rules -> rules.toUiState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RulesUiState()
            )

    /** Toggle [rule]'s enabled flag and persist it. */
    fun setEnabled(rule: Rule, enabled: Boolean) {
        if (rule.enabled == enabled) return
        viewModelScope.launch {
            ruleRepository.upsert(rule.copy(enabled = enabled))
        }
    }

    /** Permanently remove the rule with [ruleId]. */
    fun delete(ruleId: Long) {
        viewModelScope.launch {
            ruleRepository.delete(ruleId)
        }
    }
}

/** Group a flat rule list by app in declaration order, dropping empty groups. */
private fun List<Rule>.toUiState(): RulesUiState {
    val byApp = groupBy { it.appPackage }
    val groups = AppPackage.entries
        .mapNotNull { app ->
            val appRules = byApp[app].orEmpty()
            if (appRules.isEmpty()) null else RuleGroup(app = app, rules = appRules)
        }
    return RulesUiState(
        groups = groups,
        isLoading = false,
        enabledCount = count { it.enabled },
        totalCount = size
    )
}
