package com.quietping.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.domain.model.AlertStyle
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.RuleAction
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import com.quietping.ui.components.ChoiceChip
import com.quietping.ui.components.GlassButton
import com.quietping.ui.components.GlassButtonStyle
import com.quietping.ui.components.GlassCard
import com.quietping.ui.components.SectionHeader
import com.quietping.ui.components.SettingToggleRow
import com.quietping.ui.components.quietPingFieldColors
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.animateSizeChange
import com.quietping.ui.theme.TextTertiary

/**
 * Create or edit a [com.quietping.domain.model.Rule]. The user picks the app and
 * trigger type, supplies the type-specific matcher (keywords / a name / VIP
 * contacts), chooses a sound preset, and sets the DND-bypass and enabled switches.
 * Saving persists via the repository and pops back.
 *
 * Content only — the screen's Scaffold/top bar is owned by the nav graph; an inline
 * header provides back + title + save within the content.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: RuleEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vips by viewModel.vips.collectAsStateWithLifecycle()

    // One-shot: pop once the save completes.
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    val draft = state.draft

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header") {
            EditorHeader(
                title = if (state.isNew) "New rule" else "Edit rule",
                onBack = onBack
            )
        }

        if (state.isLoading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                        .semantics {
                            contentDescription = "Loading rule"
                            liveRegion = LiveRegionMode.Polite
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LocalQuietPingTheme.current.accent)
                }
            }
            return@LazyColumn
        }

        // --- App ---
        item(key = "app") {
            EditorSection(title = "App", subtitle = "Which app's messages to watch") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPackage.entries.forEach { app ->
                        ChoiceChip(
                            label = app.label(),
                            selected = draft.appPackage == app,
                            onClick = { viewModel.setApp(app) }
                        )
                    }
                }
            }
        }

        // --- Trigger type ---
        item(key = "trigger") {
            EditorSection(
                title = "Trigger",
                subtitle = draft.type.description()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TriggerType.entries.forEach { type ->
                        ChoiceChip(
                            label = type.label(),
                            selected = draft.type == type,
                            onClick = { viewModel.setType(type) }
                        )
                    }
                }
            }
        }

        // --- Type-specific matcher ---
        item(key = "matcher") {
          Column(modifier = Modifier.fillMaxWidth().animateSizeChange()) {
            when (draft.type) {
                TriggerType.KEYWORD -> EditorSection(
                    title = "Keywords",
                    subtitle = "Ping when any of these appears"
                ) {
                    KeywordEditor(
                        keywords = draft.pattern.splitKeywords(),
                        onAdd = { keyword ->
                            viewModel.setPattern(
                                (draft.pattern.splitKeywords() + keyword).joinKeywords()
                            )
                        },
                        onRemove = { keyword ->
                            viewModel.setPattern(
                                draft.pattern.splitKeywords()
                                    .filterNot { it.equals(keyword, ignoreCase = true) }
                                    .joinKeywords()
                            )
                        }
                    )
                }

                TriggerType.NAME_MENTION -> EditorSection(
                    title = "Your name",
                    subtitle = "The name or handle that means you"
                ) {
                    OutlinedTextField(
                        value = draft.pattern,
                        onValueChange = viewModel::setPattern,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your name") },
                        placeholder = { Text("e.g. Alex or @alex") },
                        singleLine = true,
                        shape = RoundedCornerShape(GlassDefaults.CornerRadiusLg),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = quietPingFieldColors()
                    )
                }

                TriggerType.VIP_CONTACT -> EditorSection(
                    title = "VIP contacts",
                    subtitle = "Always ping for these senders in ${draft.appPackage.label()}"
                ) {
                    VipPicker(
                        vips = vips,
                        onAdd = viewModel::addVip,
                        onRemove = viewModel::removeVip
                    )
                }

                TriggerType.POLL_CREATED,
                TriggerType.REPLY_MENTION,
                TriggerType.GROUP_EVENT -> EditorSection(
                    title = "How it matches",
                    subtitle = null
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = draft.type.description() +
                                " No keywords needed — QuietPing detects this automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
          }
        }

        // --- Sound preset ---
        item(key = "sound") {
            EditorSection(
                title = "Alert sound",
                subtitle = "Played when this rule fires"
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundPreset.entries.forEach { preset ->
                        ChoiceChip(
                            label = preset.displayName,
                            selected = draft.soundPreset == preset,
                            onClick = { viewModel.setSoundPreset(preset) }
                        )
                    }
                }
            }
        }

        // --- Escalation style ---
        item(key = "escalation") {
            EditorSection(
                title = "Escalation",
                subtitle = draft.alertStyle.description()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertStyle.entries.forEach { style ->
                        ChoiceChip(
                            label = style.label(),
                            selected = draft.alertStyle == style,
                            onClick = { viewModel.setAlertStyle(style) }
                        )
                    }
                }
            }
        }

        // --- Action: alert vs block ---
        item(key = "action") {
            EditorSection(
                title = "Action",
                subtitle = draft.action.description()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RuleAction.entries.forEach { action ->
                        ChoiceChip(
                            label = action.label(),
                            selected = draft.action == action,
                            onClick = { viewModel.setAction(action) }
                        )
                    }
                }
            }
        }

        // --- Active hours window ---
        item(key = "hours") {
            EditorSection(title = "Active hours", subtitle = "Only fire during a time window") {
                Column(
                    modifier = Modifier.fillMaxWidth().animateSizeChange(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        SettingToggleRow(
                            title = "Limit to a time window",
                            subtitle = "Outside these hours the rule stays silent",
                            checked = draft.windowEnabled,
                            onCheckedChange = viewModel::setWindowEnabled
                        )
                    }
                    if (draft.windowEnabled) {
                        Text(
                            text = "From",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        HourChips(
                            selectedMin = draft.windowStartMin,
                            onSelect = viewModel::setWindowStart
                        )
                        Text(
                            text = "Until",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        HourChips(
                            selectedMin = draft.windowEndMin,
                            onSelect = viewModel::setWindowEnd
                        )
                    }
                }
            }
        }

        // --- Switches ---
        item(key = "switches") {
            EditorSection(title = "Behavior", subtitle = null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    SettingToggleRow(
                        title = "Bypass Do Not Disturb",
                        subtitle = "Let this rule ping even when DND is on",
                        checked = draft.dndOverride,
                        onCheckedChange = viewModel::setDndOverride
                    )
                    SettingToggleRow(
                        title = "Enabled",
                        subtitle = "Turn the rule on or off without deleting it",
                        checked = draft.enabled,
                        onCheckedChange = viewModel::setEnabled
                    )
                }
            }
        }

        // --- Save ---
        item(key = "save") {
            GlassButton(
                text = if (state.isNew) "Create rule" else "Save changes",
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                style = GlassButtonStyle.Primary,
                enabled = state.canSave,
                leadingIcon = Icons.Filled.Check
            )
        }
    }
}

/** Inline header: back button + title (the nav graph owns the real top bar). */
@Composable
private fun EditorHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
    }
}

/** A titled block: [SectionHeader] over its [content]. */
@Composable
private fun EditorSection(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(title = title, subtitle = subtitle)
        content()
    }
}


/** A curated set of hour chips for picking a window bound (minute-of-day). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourChips(
    selectedMin: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WINDOW_HOUR_OPTIONS.forEach { hour ->
            val min = hour * 60
            ChoiceChip(
                label = "%02d:00".format(hour),
                selected = selectedMin == min,
                onClick = { onSelect(min) }
            )
        }
    }
}

/** Common boundary hours offered by the active-hours picker. */
private val WINDOW_HOUR_OPTIONS = listOf(0, 6, 7, 8, 9, 12, 17, 18, 20, 21, 22, 23)

private fun AlertStyle.label(): String = when (this) {
    AlertStyle.STANDARD -> "Standard"
    AlertStyle.PERSISTENT -> "Persistent"
    AlertStyle.CRITICAL -> "Critical"
}

private fun AlertStyle.description(): String = when (this) {
    AlertStyle.STANDARD -> "One heads-up alert."
    AlertStyle.PERSISTENT -> "Keep re-pinging until you read it."
    AlertStyle.CRITICAL -> "Full-screen alarm that pierces Do Not Disturb."
}

private fun RuleAction.label(): String = when (this) {
    RuleAction.ALERT -> "Alert me"
    RuleAction.SUPPRESS -> "Block (no alert)"
}

private fun RuleAction.description(): String = when (this) {
    RuleAction.ALERT -> "Fire an alert when this rule matches."
    RuleAction.SUPPRESS -> "Silently archive matches — never alert (keyword block)."
}
