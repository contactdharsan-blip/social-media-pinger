package com.quietping.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietping.ui.theme.GlassDefaults

/**
 * A compact pill / chip used for statuses, tags, and counts (DESIGN.md §7.6).
 *
 * Tinted with [color] at low alpha for the fill, full [color] for the label, so it
 * blends into the dark canvas while staying legible. Optionally leads with [icon].
 *
 * @param text     the (usually short, uppercase-friendly) label.
 * @param color    the semantic tint (e.g. StatusSuccess / accent / StatusAlert).
 * @param modifier external layout modifier.
 * @param icon     optional leading Material icon.
 */
@Composable
fun PillBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.16f),
                shape = RoundedCornerShape(GlassDefaults.CornerRadiusFull)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
