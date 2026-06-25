package com.quietping.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.ui.components.quietPingFieldColors
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.animateSizeChange
import com.quietping.ui.theme.TextTertiary

/**
 * Chip-style editor for a list of keywords/phrases. Existing entries render as
 * removable accent-tinted chips; a trailing field adds a new one (Enter or the +
 * button). Empty/whitespace and case-insensitive duplicates are ignored.
 *
 * @param keywords the current keyword list.
 * @param onAdd    invoked with a new (already-trimmed) keyword to append.
 * @param onRemove invoked with a keyword to remove.
 * @param modifier external layout modifier.
 * @param label    field placeholder ("Add keyword").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordEditor(
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Add keyword"
) {
    var field by remember { mutableStateOf("") }
    val accent = LocalQuietPingTheme.current.accent

    fun commit() {
        val candidate = field.trim()
        if (candidate.isNotEmpty() &&
            keywords.none { it.equals(candidate, ignoreCase = true) }
        ) {
            onAdd(candidate)
        }
        field = ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (keywords.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateSizeChange(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    KeywordChip(text = keyword, onRemove = { onRemove(keyword) })
                }
            }
        }

        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            placeholder = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(GlassDefaults.CornerRadiusLg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            trailingIcon = {
                IconButton(onClick = { commit() }, enabled = field.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add keyword",
                        tint = accent
                    )
                }
            },
            colors = quietPingFieldColors()
        )
    }
}

/** A single removable keyword chip. */
@Composable
private fun KeywordChip(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalQuietPingTheme.current.accent
    Row(
        modifier = modifier
            .background(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(GlassDefaults.CornerRadiusFull)
            )
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove $text",
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
