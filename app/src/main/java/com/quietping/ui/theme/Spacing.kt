package com.quietping.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale for the Dark Liquid-Glass system (DESIGN.md §4). Screens previously
 * mixed ad-hoc values (e.g. a 20.dp Home gutter vs 16.dp everywhere else); use these
 * tokens so gutters, item gaps, and section breaks stay uniform across the app.
 */
object Spacing {
    /** Canonical screen edge gutter (horizontal content inset). */
    val ScreenH = 16.dp

    /** Canonical top/bottom screen inset. */
    val ScreenV = 16.dp

    /** Gap between adjacent items in a list/column. */
    val Item = 12.dp

    /** Break between distinct sections. */
    val Section = 24.dp

    /** Tight inline gap (icon↔label, chip rows). */
    val Tight = 8.dp
}
