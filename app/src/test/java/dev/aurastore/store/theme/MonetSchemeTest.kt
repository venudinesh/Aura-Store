package dev.aurastore.store.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a generated scheme has to keep, checked across the whole hue circle.
 *
 * A hand-picked palette is reviewed by eye; a palette computed from whatever someone drags the wheel
 * to has no such check, so the properties that make Material's scheme readable are asserted instead:
 * a tone means what it says, and every text/background pair the storefront draws clears WCAG AA at
 * every hue, not just the eight that were looked at.
 */
class MonetSchemeTest {

    /** WCAG relative luminance, from a Compose colour's already-encoded sRGB channels. */
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Every hue at a mid chroma, plus the extremes the wheel can reach. */
    private fun seeds(): List<Int> = buildList {
        for (hue in 0 until 360 step 15) add(hsvToArgb(Hsv(hue.toDouble(), 0.55, 0.85)))
        add(hsvToArgb(Hsv(0.0, 1.0, 1.0)))          // pure red, the wheel's corner
        add(hsvToArgb(Hsv(210.0, 1.0, 1.0)))        // pure blue
        add(hsvToArgb(Hsv(120.0, 1.0, 1.0)))        // pure green
        add(hsvToArgb(Hsv(60.0, 0.9, 0.35)))        // dark, near-black seeds
        add(0xFF808080.toInt())                     // and a seed with no hue at all
    }

    @Test
    fun `a tone asked for is the tone that comes back`() {
        for (hue in 0 until 360 step 30) {
            for (tone in intArrayOf(4, 10, 20, 30, 40, 60, 80, 90, 95, 99)) {
                val back = argbOf(Lch(tone.toDouble(), 30.0, hue.toDouble())).toLch().tone
                assertEquals("tone $tone at hue $hue", tone.toDouble(), back, 0.6)
            }
        }
    }

    @Test
    fun `every generated scheme keeps its text above the AA floor`() {
        for (seed in seeds()) {
            for (dark in booleanArrayOf(false, true)) {
                val s = monetScheme(seed, dark)
                val pairs = listOf(
                    "primary" to (s.primary to s.onPrimary),
                    "container" to (s.primaryContainer to s.onPrimaryContainer),
                    "background" to (s.background to s.onBackground),
                    "surface" to (s.surface to s.onSurface),
                    "surface variant" to (s.surfaceVariant to s.onSurfaceVariant),
                    "secondary" to (s.secondaryContainer to s.onSecondaryContainer),
                )
                for ((name, pair) in pairs) {
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue(
                        "${if (dark) "dark" else "light"} $name at ${seed.hex()} only reaches %.2f".format(ratio),
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun `a seed the display cannot show comes back inside it at the same tone`() {
        // LCh chroma 180 does not exist in sRGB at any tone; the clip has to find the nearest real
        // colour rather than clamp channels and shift the lightness out from under the tone scale.
        val impossible = Lch(50.0, 180.0, 90.0)
        val argb = argbOf(impossible)
        val back = argb.toLch()
        assertEquals("tone survives the clip", 50.0, back.tone, 0.6)
        assertTrue("chroma was not pulled in: ${back.chroma}", back.chroma < impossible.chroma)
        val rgb = Color(argb)
        assertTrue(
            "channel left 0..1",
            rgb.red in 0f..1f && rgb.green in 0f..1f && rgb.blue in 0f..1f,
        )
    }

    @Test
    fun `the wheel's coordinates round trip through the seed it writes`() {
        for (hue in 0 until 360 step 20) {
            for (sat in doubleArrayOf(0.0, 0.35, 0.7, 1.0)) {
                for (value in doubleArrayOf(0.25, 0.6, 1.0)) {
                    val back = hsvToArgb(Hsv(hue.toDouble(), sat, value)).toHsv()
                    assertEquals("value at hue $hue", value, back.value, 0.01)
                    assertEquals("saturation at hue $hue", sat, back.saturation, 0.01)
                    // Achromatic colours have no defined hue, so only the chromatic ones are checked.
                    if (sat > 0.05) {
                        val delta = kotlin.math.abs(back.hue - hue)
                        // A seed is eight bits per channel, so a dark, low-chroma colour recovers its
                        // angle to under a degree at best — measured worst is 0.87° at value 0.25.
                        assertTrue("hue drifted to ${back.hue} from $hue", minOf(delta, 360 - delta) < 1.0)
                    }
                }
            }
        }
    }

    @Test
    fun `the prebuilt seeds really are soft`() {
        // "Soft" is the brief, so it is a property rather than a vibe: mid-to-light tone, restrained
        // chroma, and no two of them the same colour.
        val tones = SoftPalettes.all.map { it.seed.toLch().tone }
        val chromas = SoftPalettes.all.map { it.seed.toLch().chroma }
        assertTrue("tones out of the soft band: $tones", tones.all { it in 55.0..85.0 })
        assertTrue("chromas too loud: $chromas", chromas.all { it in 8.0..40.0 })
        assertEquals("two presets are the same colour", tones.size, tones.distinct().size)
    }

    @Test
    fun `the custom default is the house amber`() {
        // The theme falls back to this when the mode is CUSTOM and nobody has dragged the wheel yet,
        // so it has to be the colour the rest of the app is wearing.
        assertEquals(Accent.toArgb(), SoftPalettes.fallback)
    }

    private fun Int.hex() = "0x" + toString(16).uppercase()
}
