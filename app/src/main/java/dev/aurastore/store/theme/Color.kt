package dev.aurastore.store.theme

import androidx.compose.ui.graphics.Color

/**
 * The house palette: one amber accent on a warm neutral ramp, in both schemes.
 *
 * The accent is the colour the app has been showing on a modern handset all along — Material's
 * dynamic colour took it from the wallpaper, so the designed green was never actually rendered and
 * every screenshot looked amber by accident. [Theme.kt] stops guessing, and this file makes that
 * amber a decision: one hue, warm greys only, no cool neutral anywhere in the ramp.
 *
 * Contrast is measured, not assumed (WCAG 2.x ratios against the surfaces each colour sits on):
 *
 * | pair                                            | ratio |
 * |-------------------------------------------------|-------|
 * | dark `Accent` #E0902C on `Surface` #15120F      | 7.28  |
 * | dark `onAccent` #1C1408 on `Accent`             | 7.11  |
 * | dark text #F2E9DE on `Surface`                  | 15.54 |
 * | dark meta #B9AB9B on `Surface`                  | 8.32  |
 * | light `Primary` #8A5310 with white text         | 6.31  |
 * | light `PrimaryContainer` #F3DCB8 with #2A1B08   | 12.50 |
 *
 * Light mode therefore fills its buttons with a brown-dark amber and writes them in white; the
 * brighter amber is reserved for dark mode, where white text would fail on it and near-black
 * succeeds. That asymmetry is deliberate and is the reason the two schemes do not share a primary.
 */

// Accent. One hue family: amber, from deep brown to pale sand.
val Accent = Color(0xFFE0902C)
val AccentBright = Color(0xFFF5C583)
val AccentDeep = Color(0xFF8A5310)
val AccentContainerLight = Color(0xFFF3DCB8)
val AccentContainerDark = Color(0xFF3A2610)
val OnAccentDark = Color(0xFF1C1408)
val OnAccentLight = Color(0xFFFFFBF5)
val OnAccentContainerLight = Color(0xFF2A1B08)

/** Warm neutrals. Every grey here carries the same brown undertone — no cool grey sneaks in. */
val Ink900 = Color(0xFF1F1A15)
val Ink700 = Color(0xFF5D5449)
val Ink500 = Color(0xFF8C8073)
val Ink300 = Color(0xFFD9CFC2)
val Ink100 = Color(0xFFF3EDE5)
val Ink50 = Color(0xFFFBF8F4)

val PaperWhite = Color(0xFFFFFFFF)
val InkOnPaper = Color(0xFF1F1A15)

/** The dark surface family, lifted off pure black so cards and sheets have somewhere to go. */
val NightBase = Color(0xFF15120F)
val NightLow = Color(0xFF1C1815)
val NightHigh = Color(0xFF26211C)
val NightHighest = Color(0xFF312A24)
val NightLowest = Color(0xFF100D0B)
val CreamText = Color(0xFFF2E9DE)
val CreamMeta = Color(0xFFB9AB9B)
val NightOutline = Color(0xFF4A4038)

/**
 * Ratings stars. Held a shade deeper than the accent so a 4.1 next to an *Install* pill reads as a
 * judgement rather than as another control.
 */
val RatingStar = Color(0xFFC97A16)

val ErrorLight = Color(0xFFB3261E)
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorContainerLight = Color(0xFF410E0B)
val OnErrorContainerDark = Color(0xFFF9DEDC)
