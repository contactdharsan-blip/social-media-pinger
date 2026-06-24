package com.quietping.ui.rules

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Rule

/**
 * A set of [Rule]s belonging to a single [AppPackage], for the grouped Rules list.
 *
 * @param app   the owning app (header label / icon).
 * @param rules the app's rules, in stable order.
 */
data class RuleGroup(
    val app: AppPackage,
    val rules: List<Rule>
)

/**
 * Immutable UI state for [RulesScreen].
 *
 * @param groups       rules grouped per app (empty groups omitted).
 * @param isLoading    true until the first emission from the repository arrives.
 * @param enabledCount number of currently-enabled rules across all apps.
 * @param totalCount   total number of rules across all apps.
 */
data class RulesUiState(
    val groups: List<RuleGroup> = emptyList(),
    val isLoading: Boolean = true,
    val enabledCount: Int = 0,
    val totalCount: Int = 0
) {
    /** True when there are no rules at all (drives the empty state). */
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}
