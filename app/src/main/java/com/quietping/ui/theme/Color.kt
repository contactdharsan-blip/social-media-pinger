package com.quietping.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dark Liquid-Glass palette translated from DESIGN.md (§2) to Compose [Color]s.
 * Near-black canvas, emerald -> teal accent ramp, frosted glass surfaces.
 */

// --- Accent ramp: emerald (primary) ---
val Emerald50 = Color(0xFFECFDF5)
val Emerald100 = Color(0xFFD1FAE5)
val Emerald200 = Color(0xFFA7F3D0)
val Emerald300 = Color(0xFF6EE7B7)
val Emerald400 = Color(0xFF34D399) // primary accent
val Emerald500 = Color(0xFF10B981)
val Emerald600 = Color(0xFF059669) // primary-dark
val Emerald700 = Color(0xFF047857)
val Emerald800 = Color(0xFF065F46)
val Emerald900 = Color(0xFF064E3B)

// --- Accent ramp: teal (secondary) ---
val Teal50 = Color(0xFFF0FDFA)
val Teal100 = Color(0xFFCCFBF1)
val Teal200 = Color(0xFF99F6E4)
val Teal300 = Color(0xFF5EEAD4)
val Teal400 = Color(0xFF2DD4BF)
val Teal500 = Color(0xFF14B8A6) // secondary accent
val Teal600 = Color(0xFF0D9488)
val Teal700 = Color(0xFF0F766E)
val Teal800 = Color(0xFF115E59)
val Teal900 = Color(0xFF134E4A)

// --- Canvas & raised surfaces (§2.1) ---
val BgPrimary = Color(0xFF030712)   // near-black app background
val BgSecondary = Color(0xFF111827) // raised surface
val BgTertiary = Color(0xFF1F2937)  // highest surface
val CardBg = Color(0xCC111827)      // rgba(17,24,39,0.8) opaque card fallback

// --- Text (§2.3) ---
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFCBD5E1)
val TextTertiary = Color(0xFF94A3B8)

// --- Semantic status (§2.5) ---
val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusError = Color(0xFFEF4444)
val StatusAlert = Color(0xFFF87171)
val NeutralGray = Color(0xFF6B7280)

// --- Glass + borders/dividers (§2.2, §2.6) ---
val GlassFill = Color(0x12FFFFFF)    // rgba(255,255,255,0.07)
val GlassBorder = Color(0x24FFFFFF)  // rgba(255,255,255,0.14)
val BorderSubtle = Color(0x1AFFFFFF) // rgba(255,255,255,0.10)
val Divider = Color(0x0FFFFFFF)      // rgba(255,255,255,0.06)
val CardBorder = Color(0x14FFFFFF)   // rgba(255,255,255,0.08)

// --- On-accent foreground (dark text reads well on the bright emerald) ---
val OnAccent = Color(0xFF03130D)
