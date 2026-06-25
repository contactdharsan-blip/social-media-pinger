package com.quietping.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.TextPrimary

/**
 * The canonical [OutlinedTextField] color treatment for QuietPing's text inputs:
 * accent-tinted focus border + cursor, [TextPrimary] text, subtle outline when
 * unfocused. Shared by the rule editor, keyword editor, and VIP picker so every
 * field reads identically.
 */
@Composable
fun quietPingFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalQuietPingTheme.current.accent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = LocalQuietPingTheme.current.accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
