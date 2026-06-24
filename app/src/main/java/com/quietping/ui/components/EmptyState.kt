package com.quietping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.TextPrimary
import com.quietping.ui.theme.TextTertiary

/**
 * The empty-state for any list/feed that has no content yet (DESIGN.md feedback
 * contract §7.0). A softly-glowing accent icon over a short title + explanation,
 * with an optional call-to-action slot.
 *
 * @param icon     a Material icon summarizing the absent content.
 * @param title    short headline ("No matches yet").
 * @param modifier external layout modifier.
 * @param message  optional supporting sentence.
 * @param action   optional CTA (e.g. a [GlassButton]).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    val accent = LocalQuietPingTheme.current.accent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(accent.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Box(modifier = Modifier.padding(top = 4.dp)) {
                action()
            }
        }
    }
}
