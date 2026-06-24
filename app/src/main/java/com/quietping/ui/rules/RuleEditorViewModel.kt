package com.quietping.ui.rules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Rule
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import com.quietping.domain.model.VipContact
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.repo.VipRepository
import com.quietping.ui.nav.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editable draft of a [Rule]. Mirrors the persisted shape minus the id, so the
 * editor can build a brand-new rule or mutate a loaded one uniformly.
 */
data class RuleDraft(
    val appPackage: AppPackage = AppPackage.WHATSAPP,
    val type: TriggerType = TriggerType.NAME_MENTION,
    val pattern: String = "",
    val soundPreset: SoundPreset = SoundPreset.DROPLET,
    val dndOverride: Boolean = false,
    val enabled: Boolean = true
)

/**
 * UI state for [RuleEditorScreen].
 *
 * @param draft      the in-progress rule values.
 * @param isNew      true when creating (no existing id); false when editing.
 * @param isLoading  true while an existing rule is being fetched.
 * @param canSave    whether the current draft is valid to persist.
 * @param saved      flips true once a save completes (one-shot navigation signal).
 */
data class RuleEditorUiState(
    val draft: RuleDraft = RuleDraft(),
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false
)

/**
 * Drives [RuleEditorScreen]. Reads the optional [Dest.RuleEditor.ARG_RULE_ID] from
 * [SavedStateHandle]; a value other than [Dest.RuleEditor.NEW_RULE_ID] loads that
 * rule for editing. Persists via [RuleRepository.upsert] and manages the VIP roster
 * (used by the [TriggerType.VIP_CONTACT] picker) via [VipRepository].
 */
@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val vipRepository: VipRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Tolerate the nav arg arriving as either a Long (NavType.LongType) or a String
    // (the default for optional query args), so editing works regardless of how the
    // nav graph types ARG_RULE_ID.
    private val ruleId: Long =
        savedStateHandle.get<Long>(Dest.RuleEditor.ARG_RULE_ID)
            ?: savedStateHandle.get<String>(Dest.RuleEditor.ARG_RULE_ID)?.toLongOrNull()
            ?: Dest.RuleEditor.NEW_RULE_ID

    private val isNew: Boolean = ruleId == Dest.RuleEditor.NEW_RULE_ID

    /** The id of the rule being edited (null when creating a new one). */
    private val editingId: Long? = if (isNew) null else ruleId

    private val _uiState = MutableStateFlow(
        RuleEditorUiState(isNew = isNew, isLoading = !isNew)
    )
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    /**
     * VIP contacts scoped to the draft's currently-selected app. Recomputes when
     * either the roster changes or the user switches the draft's app, so the picker
     * always shows the right list.
     */
    val vips: StateFlow<List<VipContact>> =
        combine(vipRepository.vips(), _uiState) { all, state ->
            all.filter { it.appPackage == state.draft.appPackage }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        if (!isNew) loadExisting(ruleId)
        recomputeCanSave()
    }

    private fun loadExisting(id: Long) {
        viewModelScope.launch {
            val existing = ruleRepository.rules().first().firstOrNull { it.id == id }
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        draft = RuleDraft(
                            appPackage = existing.appPackage,
                            type = existing.type,
                            pattern = existing.pattern,
                            soundPreset = existing.soundPreset,
                            dndOverride = existing.dndOverride,
                            enabled = existing.enabled
                        ),
                        isLoading = false
                    )
                }
            } else {
                // Rule vanished (e.g. deleted from the list) — fall back to a new draft.
                _uiState.update { it.copy(isLoading = false) }
            }
            recomputeCanSave()
        }
    }

    fun setApp(app: AppPackage) = updateDraft { it.copy(appPackage = app) }

    fun setType(type: TriggerType) = updateDraft { it.copy(type = type) }

    fun setPattern(pattern: String) = updateDraft { it.copy(pattern = pattern) }

    fun setSoundPreset(preset: SoundPreset) = updateDraft { it.copy(soundPreset = preset) }

    fun setDndOverride(enabled: Boolean) = updateDraft { it.copy(dndOverride = enabled) }

    fun setEnabled(enabled: Boolean) = updateDraft { it.copy(enabled = enabled) }

    /** Add a VIP [handle] for the draft's current app (used by the VIP picker). */
    fun addVip(handle: String) {
        val trimmed = handle.trim()
        if (trimmed.isEmpty()) return
        val app = _uiState.value.draft.appPackage
        viewModelScope.launch {
            // Avoid duplicates for the same app/handle (case-insensitive).
            val exists = vipRepository.vips().first().any {
                it.appPackage == app && it.handle.equals(trimmed, ignoreCase = true)
            }
            if (!exists) {
                vipRepository.add(VipContact(id = 0L, handle = trimmed, appPackage = app))
            }
        }
    }

    /** Remove the VIP contact with [id]. */
    fun removeVip(id: Long) {
        viewModelScope.launch { vipRepository.remove(id) }
    }

    /** Validate, persist, and flag [RuleEditorUiState.saved] so the screen can pop. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val draft = state.draft
        viewModelScope.launch {
            ruleRepository.upsert(
                Rule(
                    id = editingId ?: 0L,
                    appPackage = draft.appPackage,
                    type = draft.type,
                    pattern = draft.pattern.trim(),
                    soundPreset = draft.soundPreset,
                    dndOverride = draft.dndOverride,
                    enabled = draft.enabled
                )
            )
            _uiState.update { it.copy(saved = true) }
        }
    }

    private inline fun updateDraft(transform: (RuleDraft) -> RuleDraft) {
        _uiState.update { it.copy(draft = transform(it.draft)) }
        recomputeCanSave()
    }

    private fun recomputeCanSave() {
        _uiState.update { it.copy(canSave = it.draft.isValid()) }
    }
}

/**
 * A draft is valid to save when types that depend on free text actually have it.
 * [TriggerType.KEYWORD] and [TriggerType.NAME_MENTION] require a non-blank pattern;
 * the remaining types (poll/reply/VIP/group) match structurally and need no text.
 */
private fun RuleDraft.isValid(): Boolean = when (type) {
    TriggerType.KEYWORD, TriggerType.NAME_MENTION -> pattern.isNotBlank()
    else -> true
}
