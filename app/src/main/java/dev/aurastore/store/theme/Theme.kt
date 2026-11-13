package dev.aurastore.store.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import dev.aurastore.store.data.PaletteMode
import dev.aurastore.store.data.ThemeMode
import dev.aurastore.store.data.dynamicPaletteSupported

/**
 * The storefront's scheme, chosen rather than inherited.
 *
 * Two axes, both persisted in [dev.aurastore.store.data.Settings]: [ThemeMode] decides which of the
 * light and dark schemes to draw, and [PaletteMode] decides where the colours come from — the house
 * amber in [Color.kt], the wallpaper-derived Material dynamic palette, or one seed colour of the
 * user's own run through [monetScheme].
 *
 * There are two defaults here and they answer different questions. This composable's own parameter is
 * [PaletteMode.HOUSE], because a caller that passes nothing — a preview, a test — should get the
 * designed scheme rather than whatever the device happens to like. A *fresh install* is asked of the
 * device instead, by [dev.aurastore.store.data.firstRunPalette]: Material You handsets open in their own
 * colours, everything else in the house palette. The twenty-eighth round's complaint stands either
 * way — `dynamicColor` used to be true unconditionally, so the designed scheme was never rendered and
 * nobody had chosen that — what changed in the thirty-first is that the default is now the phone's
 * answer *and* the first thing the user can overrule. Below Android 12 there is no dynamic palette to
 * ask for, so [PaletteMode.WALLPAPER] resolves to the house colours there and the Settings row says so
 * instead of pretending the toggle did nothing.
 */
private val LightColors = lightColorScheme(
    primary = AccentDeep,
    onPrimary = OnAccentLight,
    primaryContainer = AccentContainerLight,
    onPrimaryContainer = OnAccentContainerLight,
    secondary = Ink700,
    onSecondary = Color.White,
    secondaryContainer = Ink100,
    onSecondaryContainer = Ink900,
    tertiary = AccentDeep,
    onTertiary = OnAccentLight,
    background = Ink50,
    onBackground = Ink900,
    surface = PaperWhite,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink700,
    surfaceContainerLowest = PaperWhite,
    surfaceContainerLow = Ink50,
    surfaceContainer = Ink100,
    surfaceContainerHigh = Ink300,
    outline = Ink500,
    outlineVariant = Ink300,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFF8DDDA),
    onErrorContainer = OnErrorContainerLight,
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccentDark,
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = AccentBright,
    secondary = CreamMeta,
    onSecondary = NightBase,
    secondaryContainer = NightHigh,
    onSecondaryContainer = CreamText,
    tertiary = Accent,
    onTertiary = OnAccentDark,
    background = NightBase,
    onBackground = CreamText,
    surface = NightLow,
    onSurface = CreamText,
    surfaceVariant = NightHigh,
    onSurfaceVariant = CreamMeta,
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightHigh,
    surfaceContainerHigh = NightHighest,
    outline = NightOutline,
    outlineVariant = Color(0xFF3A322B),
    error = ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = OnErrorContainerDark,
    scrim = Color(0xB3000000),
)

/**
 * Which of the two schemes this mode draws on a phone that is itself in [systemIsDark].
 *
 * The one place that answers it, and a plain function so the answer can be tested without a screen:
 * [dev.aurastore.store.MainActivity] needs it before Compose exists, to colour the window the system
 * shows while the first frame is still being built.
 */
fun ThemeMode.resolvesToDark(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The phone's own light-or-dark, read the way [isSystemInDarkTheme] reads it — for the callers that run
 * before a composition exists, where that composable is not available yet.
 */
fun Context.systemIsDark(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * The palette actually in force, after the platform's own limits are applied.
 *
 * [dynamicPaletteSupported] lives beside [PaletteMode] in `data/Settings.kt`, because it is also what
 * a fresh install is defaulted from. This is the theme's view of that one rule, not a second copy.
 */
fun effectivePalette(palette: PaletteMode): PaletteMode =
    if (palette == PaletteMode.WALLPAPER && !dynamicPaletteSupported()) PaletteMode.HOUSE else palette

@Composable
fun AuraStoreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: PaletteMode = PaletteMode.HOUSE,
    seed: Int? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolvesToDark(isSystemInDarkTheme())
    // The SDK test is written here rather than reusing [effectivePalette], because lint's NewApi check
    // will not follow a helper's return value to conclude the call below is guarded — and it is right not
    // to: this function is the only place the dynamic calls are made, so this is the only place the
    // guard has to be visible.
    val colorScheme = if (palette == PaletteMode.WALLPAPER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (palette == PaletteMode.CUSTOM) {
        // A seed of the user's own, and the house amber until anyone drags the wheel.
        monetScheme(seed ?: Accent.toArgb(), darkTheme)
    } else if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StoreShapes,
        content = content,
    )
}
