package com.quietping.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.domain.model.VipContact
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.StatusError
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextTertiary

/**
 * Manage the starred contacts (handles) that fire a VIP rule for the current app.
 *
 * Lists current [vips] with a delete affordance, and offers a field to add a new
 * handle (Enter or the add button). The caller owns persistence via [onAdd] /
 * [onRemove]; this composable is purely presentational.
 *
 * @param vips     the VIP contacts to display (already scoped to the active app).
 * @param onAdd    invoked with a new handle to star.
 * @param onRemove invoked with the id of a contact to unstar.
 * @param modifier external layout modifier.
 */
@Composable
fun VipPicker(
    vips: List<VipContact>,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var field by remember { mutableStateOf("") }
    val accent = LocalQuietPingTheme.current.accent

    fun commit() {
        val candidate = field.trim()
        if (candidate.isNotEmpty()) onAdd(candidate)
        field = ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a name or handle") },
            singleLine = true,
            shape = RoundedCornerShape(GlassDefaults.CornerRadiusLg),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            trailingIcon = {
                IconButton(onClick = { commit() }, enabled = field.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = "Add VIP contact",
                        tint = accent
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = accent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        if (vips.isEmpty()) {
            Text(
                text = "No VIP contacts yet. Add the people whose messages should always ping.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        } else {
            vips.forEach { vip ->
                VipRow(vip = vip, onRemove = { onRemove(vip.id) })
            }
        }
    }
}

/** One VIP contact row: star disc, handle, delete. */
@Composable
private fun VipRow(
    vip: VipContact,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalQuietPingTheme.current.accent
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = vip.handle,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove ${vip.handle}",
                tint = StatusError
            )
        }
    }
}
