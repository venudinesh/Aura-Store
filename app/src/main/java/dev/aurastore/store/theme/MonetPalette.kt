package dev.aurastore.store.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.aurastore.store.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A Monet-style scheme from one seed colour.
 *
 * Material You builds its palettes in HCT, where the tone axis is CIE L* and the other two axes are a
 * CAM16 hue and chroma. This builds them in **CIE LCh(ab)** instead: the same tone axis — so the
 * round trip `tone(colourAt(t)) == t` is exact and testable — and the same fixed tone numbers that
 * make a Material scheme readable, but a Lab hue and chroma rather than CAM16's. The difference shows
 * as a slightly different distribution of similar hues (Lab compresses blues and expands yellows);
 * it does not touch the contrast, which comes from the tone numbers. A full CAM16 port was not worth
 * the ~600 lines and the third-party dependency for a palette the user can retune in one drag.
 *
 * Every tone is clipped into sRGB by pulling chroma down until the colour exists, so a seed the
 * display cannot reach yields the closest thing it can rather than a clamped artefact.
 */
data class Lch(val tone: Double, val chroma: Double, val hue: Double)

private const val DEG = PI / 180.0
private const val EPS = 0.008856451679035631   // (6/29)^3, the CIE linear/final split
private const val KAPPA = 7.787037037037037    // (29/3)^2 / 3
private const val XN = 0.95047
private const val YN = 1.0
private const val ZN = 1.08883

private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

private fun fromLinear(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

private fun labF(t: Double) = if (t > EPS) cbrt(t) else KAPPA * t + 16.0 / 116

private fun labFInv(t: Double) = if (t * t * t > EPS) t * t * t else (t - 16.0 / 116) / KAPPA

/** Packs three linear-light channels into an opaque ARGB int, clamping the rounding edge. */
private fun pack(r: Double, g: Double, b: Double): Int {
    fun byte(c: Double) = ((fromLinear(c.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255))
    return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}

private fun inGamut(r: Double, g: Double, b: Double) =
    r in -0.0001..1.0001 && g in -0.0001..1.0001 && b in -0.0001..1.0001

/**
 * The sRGB colour at a tone/chroma/hue, with chroma pulled in until it exists on the display.
 *
 * The chroma ladder is a 6% shrink per step: enough resolution to stop at the first colour that fits,
 * few enough steps that a whole palette is a handful of microseconds.
 */
fun argbOf(lch: Lch): Int {
    var chroma = lch.chroma
    val hr = lch.hue * DEG
    val cosH = cos(hr)
    val sinH = sin(hr)
    while (chroma > 0.05) {
        val a = chroma * cosH
        val b = chroma * sinH
        val fy = (lch.tone + 16.0) / 116
        val x = XN * labFInv(fy + a / 500)
        val y = YN * labFInv(fy)
        val z = ZN * labFInv(fy - b / 200)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        if (inGamut(r, g, bl)) return pack(r, g, bl)
        chroma *= 0.94
    }
    val fy = (lch.tone + 16.0) / 116
    val x = XN * labFInv(fy)
    val y = YN * labFInv(fy)
    val z = ZN * labFInv(fy)
    return pack(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
    )
}

/** The LCh(ab) coordinates of an ARGB colour. [Lch.tone] is CIE L*, 0..100. */
fun Int.toLch(): Lch {
    val r = toLinear(((this shr 16) and 0xFF) / 255.0)
    val g = toLinear(((this shr 8) and 0xFF) / 255.0)
    val b = toLinear((this and 0xFF) / 255.0)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN
    val fx = labF(x)
    val fy = labF(y / YN)
    val fz = labF(z)
    val a = 500.0 * (fx - fy)
    val bb = 200.0 * (fy - fz)
    val hue = (atan2(bb, a) / DEG + 360.0) % 360.0
    return Lch(116.0 * fy - 16.0, hypot(a, bb), hue)
}

/** One hue/chroma pair walked across the tone scale, which is what a Material palette is. */
class TonalPalette(val hue: Double, val chroma: Double) {
    fun argb(tone: Int): Int = argbOf(Lch(tone.toDouble(), chroma, hue))

    fun color(tone: Int): Color = Color(argb(tone))
}

/** The seed's hue and chroma, with the roles' chroma set the way the baseline scheme sets them. */
private fun palettesFrom(seed: Int): Palettes {
    val lch = seed.toLch()
    return Palettes(
        primary = TonalPalette(lch.hue, lch.chroma.coerceIn(16.0, 48.0)),
        secondary = TonalPalette(lch.hue, 16.0),
        tertiary = TonalPalette((lch.hue + 60.0) % 360.0, 28.0),
        neutral = TonalPalette(lch.hue, 4.0),
        neutralVariant = TonalPalette(lch.hue, 10.0),
        error = TonalPalette(29.0, 40.0),
    )
}

private class Palettes(
    val primary: TonalPalette,
    val secondary: TonalPalette,
    val tertiary: TonalPalette,
    val neutral: TonalPalette,
    val neutralVariant: TonalPalette,
    val error: TonalPalette,
)

/**
 * The scheme a seed produces, in light or dark.
 *
 * The tone numbers are Material's: 40 for a filled element on a 100 surface in light, 80 on a 20
 * surface in dark, containers two steps behind their labels. Those pairs are what make a generated
 * scheme legible without checking it, and [MonetContrastTest] checks them anyway across every hue.
 */
fun monetScheme(seed: Int, dark: Boolean): ColorScheme {
    val p = palettesFrom(seed)
    return if (dark) {
        darkColorScheme(
            primary = p.primary.color(80),
            onPrimary = p.primary.color(20),
            primaryContainer = p.primary.color(30),
            onPrimaryContainer = p.primary.color(90),
            secondary = p.secondary.color(80),
            onSecondary = p.secondary.color(20),
            secondaryContainer = p.secondary.color(30),
            onSecondaryContainer = p.secondary.color(90),
            tertiary = p.tertiary.color(80),
            onTertiary = p.tertiary.color(20),
            tertiaryContainer = p.tertiary.color(30),
            onTertiaryContainer = p.tertiary.color(90),
            error = p.error.color(80),
            onError = p.error.color(20),
            errorContainer = p.error.color(30),
            onErrorContainer = p.error.color(90),
            background = p.neutral.color(6),
            onBackground = p.neutral.color(90),
            surface = p.neutral.color(6),
            onSurface = p.neutral.color(90),
            surfaceVariant = p.neutralVariant.color(30),
            onSurfaceVariant = p.neutralVariant.color(80),
            surfaceContainerLowest = p.neutral.color(4),
            surfaceContainerLow = p.neutral.color(9),
            surfaceContainer = p.neutral.color(11),
            surfaceContainerHigh = p.neutral.color(16),
            surfaceContainerHighest = p.neutral.color(24),
            outline = p.neutralVariant.color(60),
            outlineVariant = p.neutralVariant.color(30),
            inverseSurface = p.neutral.color(90),
            inverseOnSurface = p.neutral.color(20),
            inversePrimary = p.primary.color(40),
            scrim = Color(0xB3000000),
        )
    } else {
        lightColorScheme(
            primary = p.primary.color(40),
            onPrimary = p.neutral.color(100),
            primaryContainer = p.primary.color(90),
            onPrimaryContainer = p.primary.color(10),
            secondary = p.secondary.color(40),
            onSecondary = p.neutral.color(100),
            secondaryContainer = p.secondary.color(90),
            onSecondaryContainer = p.secondary.color(10),
            tertiary = p.tertiary.color(40),
            onTertiary = p.neutral.color(100),
            tertiaryContainer = p.tertiary.color(90),
            onTertiaryContainer = p.tertiary.color(10),
            error = p.error.color(40),
            onError = p.neutral.color(100),
            errorContainer = p.error.color(90),
            onErrorContainer = p.error.color(10),
            background = p.neutral.color(99),
            onBackground = p.neutral.color(10),
            surface = p.neutral.color(99),
            onSurface = p.neutral.color(10),
            surfaceVariant = p.neutralVariant.color(90),
            onSurfaceVariant = p.neutralVariant.color(30),
            surfaceContainerLowest = p.neutral.color(100),
            surfaceContainerLow = p.neutral.color(98),
            surfaceContainer = p.neutral.color(96),
            surfaceContainerHigh = p.neutral.color(94),
            surfaceContainerHighest = p.neutral.color(92),
            outline = p.neutralVariant.color(50),
            outlineVariant = p.neutralVariant.color(80),
            inverseSurface = p.neutral.color(20),
            inverseOnSurface = p.neutral.color(95),
            inversePrimary = p.primary.color(80),
            scrim = Color(0x99000000),
        )
    }
}

/** Hue in degrees, saturation and value in 0..1 — the coordinates the wheel drags. */
data class Hsv(val hue: Double, val saturation: Double, val value: Double)

fun Int.toHsv(): Hsv {
    val r = ((this shr 16) and 0xFF) / 255.0
    val g = ((this shr 8) and 0xFF) / 255.0
    val b = (this and 0xFF) / 255.0
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta < 1e-9 -> 0.0
        max == r -> 60.0 * (((g - b) / delta) % 6.0)
        max == g -> 60.0 * ((b - r) / delta + 2.0)
        else -> 60.0 * ((r - g) / delta + 4.0)
    }
    return Hsv(
        hue = (hue + 360.0) % 360.0,
        saturation = if (max < 1e-9) 0.0 else delta / max,
        value = max,
    )
}

fun hsvToArgb(hsv: Hsv): Int {
    fun byte(v: Double) = (v.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)
    val c = hsv.value * hsv.saturation
    val hp = ((hsv.hue % 360.0) + 360.0) % 360.0 / 60.0
    val x = c * (1 - kotlin.math.abs(hp % 2.0 - 1.0))
    val m = hsv.value - c
    val (r, g, b) = when (hp.toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0.0)
        1 -> Triple(x, c, 0.0)
        2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c)
        4 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    // HSV channels are already sRGB-encoded, so this packs bytes rather than going through `pack`.
    return (0xFF shl 24) or (byte(r + m) shl 16) or (byte(g + m) shl 8) or byte(b + m)
}

/**
 * Prebuilt seeds, all soft: low chroma, mid-to-light tone, so a generated palette reads as a tinted
 * storefront rather than as a highlighter. Offered because the wheel can reach these but nobody finds
 * a 12-chroma blue by dragging for it.
 */
data class SoftPalette(@StringRes val label: Int, val seed: Int)

object SoftPalettes {
    val all = listOf(
        SoftPalette(R.string.soft_sand, 0xFFC4B29A.toInt()),
        SoftPalette(R.string.soft_honey, 0xFFD6BE93.toInt()),
        SoftPalette(R.string.soft_clay, 0xFFC09A86.toInt()),
        SoftPalette(R.string.soft_blush, 0xFFD6AFA8.toInt()),
        SoftPalette(R.string.soft_lilac, 0xFFB7A8CE.toInt()),
        SoftPalette(R.string.soft_iris, 0xFFA3AED6.toInt()),
        SoftPalette(R.string.soft_sky, 0xFFA5C1D1.toInt()),
        SoftPalette(R.string.soft_sage, 0xFFADBFA2.toInt()),
    )

    /** The seed the wheel opens on when nothing has been chosen yet: the house amber. */
    val fallback = Accent.toArgb()
}
