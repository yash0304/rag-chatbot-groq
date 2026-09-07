package com.mindquest.app.ui

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Marginalia-console warm palette (from the 3D model's materials).
// Core role names are kept from the old dark theme so every screen re-skins automatically:
//  - Abyss = app background, Realm = card surface, Rune = accent, Parchment = primary text.
val Rune = Color(0xFFC67139) // terracotta / brass accent (was gold)
val Abyss = Color(0xFFFAF4E8) // paper — app background (was near-black)
val Realm = Color(0xFFEFE3CB) // cream shell — card surface (was dark)
val Parchment = Color(0xFF2B2826) // ink charcoal — primary text (was light)

val Brass = Color(0xFFB1651B)
val Sage = Color(0xFF7A8A5E)

// ---------- semantic roles ----------
// These replace the leftover dark-theme colours (Tailwind slate/emerald/rose and the stock
// blue-grey Color.Gray) that were picked against a near-black background and read as washed
// out or plain alien on cream. Each is contrast-checked against BOTH Abyss (page) and Realm
// (card), the two grounds they actually sit on — every one clears 4.5:1 on both.

/** Secondary text. Warm grey-brown; replaces `Color.Gray`. 5.5:1 on paper, 4.7:1 on card. */
val Muted = Color(0xFF6E6255)

/** Success, completed, healthy. Deeper than Sage so it stays legible as small text. */
val Verdant = Color(0xFF52632F)

/** Errors, warnings, destructive actions. Warm brick rather than the old salmon. */
val Ember = Color(0xFFA33D25)

/** Hairlines and knowledge-graph edges — ink at low opacity, never a solid grey. */
val Hairline = Color(0x332B2826)

/** Empty heatmap cell / chart trough. A shadow on the paper, not a hole in it. */
val Trough = Color(0xFFE2D5BC)

// World-map nodes: one warm family, separated by hue rather than brightness so the map still
// reads when the terracotta domains dominate it.
val NodeDomain = Rune
val NodeDocument = Color(0xFF4E7A8A) // muted teal-blue
val NodeTag = Color(0xFF9A8B78) // stone

// Collectible rarity — warm equivalents of the old slate / sky / violet ladder.
val RarityCommon = Color(0xFF9A8B78)
val RarityRare = Color(0xFF4E7A8A)
val RarityEpic = Color(0xFF7A5A8A)
val RarityLegendary = Rune

/**
 * The full Material scheme. Every role is pinned — leaving them to the M3 defaults was the
 * real source of the mismatch: Card, Divider, OutlinedTextField and the drawer read
 * `surfaceVariant`, `outline` and the `surfaceContainer*` ramp, none of which were set, so
 * they fell back to Material's stock lilac-grey on top of our cream.
 */
val MindQuestLightColors = lightColorScheme(
    primary = Rune,
    onPrimary = Abyss,
    primaryContainer = Color(0xFFF3DEC2),
    onPrimaryContainer = Color(0xFF4A2A12),
    secondary = Brass,
    onSecondary = Abyss,
    secondaryContainer = Color(0xFFF0DFC6),
    onSecondaryContainer = Color(0xFF43290E),
    tertiary = Sage,
    onTertiary = Abyss,
    tertiaryContainer = Color(0xFFDFE6D0),
    onTertiaryContainer = Color(0xFF2C3618),
    background = Abyss,
    onBackground = Parchment,
    surface = Realm,
    onSurface = Parchment,
    surfaceVariant = Color(0xFFE7DAC0),
    onSurfaceVariant = Muted,
    surfaceTint = Rune,
    inverseSurface = Parchment,
    inverseOnSurface = Abyss,
    error = Ember,
    onError = Abyss,
    errorContainer = Color(0xFFF6DCD3),
    onErrorContainer = Color(0xFF5A1D0F),
    outline = Color(0xFFA1917C),
    outlineVariant = Color(0xFFD6C7AC),
    scrim = Color(0xCC2B2826),
    surfaceBright = Color(0xFFFDF8EF),
    surfaceDim = Color(0xFFE8DCC5),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF7EEDD),
    surfaceContainer = Realm,
    surfaceContainerHigh = Color(0xFFE9DCC2),
    surfaceContainerHighest = Color(0xFFE2D4B8),
)
