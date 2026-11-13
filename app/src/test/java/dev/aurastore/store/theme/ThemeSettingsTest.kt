package dev.aurastore.store.theme

import dev.aurastore.store.data.PaletteMode
import dev.aurastore.store.data.ThemeMode
import dev.aurastore.store.data.dynamicPaletteSupported
import dev.aurastore.store.data.firstRunPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the two appearance settings have to keep.
 *
 * A theme choice is a persisted enum, so it fails in the two ways persisted enums fail: a value an
 * older build wrote that no longer maps to anything, and a label that points at a string nobody
 * typed. Neither is caught by compilation, and both are invisible until the Settings screen renders
 * a blank row or the app resets a preference under someone's feet.
 *
 * The default is the case that actually happened here: the palette came from the wallpaper for
 * twenty-eight rounds because a default said so, so the defaults are asserted rather than trusted.
 * The two that follow it hold the theme to its own rules — one scheme per mode, and one colour for the
 * window the system paints before we can. Whether the strings these enums point at exist is not
 * checked here — `StringsResourceTest` already walks every string reference in the sources against the
 * table, and duplicating it from a second file would only add a dependency on generated build output.
 */
class ThemeSettingsTest {

    private val module = File(
        listOf(".", "..").first { File(it, "src/main/res/values/strings.xml").isFile },
    )

    /** The two enums share no supertype that carries the ids, so the pairs are collected per enum. */
    private fun appearanceIds(): List<Int> =
        ThemeMode.entries.flatMap { listOf(it.label, it.description) } +
            PaletteMode.entries.flatMap { listOf(it.label, it.description) }

    @Test
    fun `every theme choice has a label and a note of its own`() {
        val declared = appearanceIds()
        assertEquals("two entries must not share one string", declared.size, declared.distinct().size)
        assertTrue("an enum entry points at no resource", declared.none { it == 0 })
    }

    @Test
    fun `the theme enums are named for what is stored, not what is shown`() {
        // `Settings.load` reads these back with valueOf, so a rename is a data-loss event for anyone
        // who chose it before the upgrade. The names are the wire format.
        assertEquals(
            listOf("SYSTEM", "LIGHT", "DARK"), ThemeMode.entries.map { it.name },
        )
        assertEquals(
            listOf("HOUSE", "WALLPAPER", "CUSTOM"), PaletteMode.entries.map { it.name },
        )
    }

    @Test
    fun `a fresh install takes its palette from the device`() {
        // The thirty-first round moved the first-run answer off the house palette and onto the phone:
        // a Material You handset opens in its own colours, an older one in the house. On the unit-test
        // JVM there is no Android 12, so the answer this side of the divide is the one that can be
        // asserted here; §4ze photographs the other arm on the API-36 handset.
        assertEquals(PaletteMode.HOUSE, firstRunPalette())
        assertTrue("the unit-test JVM must not claim dynamic colour", !dynamicPaletteSupported())
    }

    @Test
    fun `wallpaper palette degrades to the house palette where the platform has none`() {
        // A JVM test sees Build.VERSION.SDK_INT as 0, which is exactly a pre-Android-12 device for
        // this function's purposes. On a real 12+ handset the instrumented suite covers the other arm.
        assertEquals(PaletteMode.HOUSE, effectivePalette(PaletteMode.WALLPAPER))
        assertEquals(PaletteMode.HOUSE, effectivePalette(PaletteMode.HOUSE))
        assertTrue("the unit-test JVM must not claim dynamic colour", !dynamicPaletteSupported())
    }

    /** The digits of an ARGB literal, whichever of the two notations it was written in. */
    private fun argb(raw: String): String = raw.trim().uppercase().let {
        if (it.startsWith("#")) it.drop(1) else if (it.startsWith("0X")) it.drop(2) else it
    }

    private fun colorToken(source: String, name: String): String {
        val line = source.lineSequence().first { it.startsWith("val $name =") }
        return argb(line.substringAfter('(').substringBefore(')'))
    }

    private fun xmlColor(path: String): String {
        val line = File(module, path).readLines()
            .first { it.contains("<color name=\"store_background\"") }
        return argb(line.substringAfter('>').substringBefore('<'))
    }

    @Test
    fun `a theme mode resolves against the phone in exactly one way`() {
        // The activity needs this answer before a composition exists and the composable needs it after.
        // Both call this one function, because a second copy of the rule is how the two start to differ.
        assertTrue(ThemeMode.SYSTEM.resolvesToDark(true))
        assertFalse(ThemeMode.SYSTEM.resolvesToDark(false))
        assertFalse(ThemeMode.LIGHT.resolvesToDark(true))
        assertTrue(ThemeMode.DARK.resolvesToDark(false))
    }

    @Test
    fun `the window the system paints is the scheme's own colour`() {
        // The starting window is drawn from XML before any Kotlin runs, so its background is a second
        // copy of Ink50 and NightBase. This is the check that keeps the copy honest.
        val tokens = File(module, "src/main/java/dev/aurastore/store/theme/Color.kt").readText()
        assertEquals(colorToken(tokens, "Ink50"), xmlColor("src/main/res/values/colors.xml"))
        assertEquals(colorToken(tokens, "NightBase"), xmlColor("src/main/res/values-night/colors.xml"))
    }

    @Test
    fun `the defaults are the house palette and the system theme`() {
        // The regression this guards is real: `dynamicColor = true` was the default until the
        // twenty-eighth round, which meant the designed scheme never rendered on a modern phone.
        val source = File(module, "src/main/java/dev/aurastore/store/theme/Theme.kt").readText()
        assertTrue(
            "AuraStoreTheme must default to ThemeMode.SYSTEM",
            source.contains("themeMode: ThemeMode = ThemeMode.SYSTEM"),
        )
        assertTrue(
            "AuraStoreTheme must default to PaletteMode.HOUSE — the wallpaper is not the brand",
            source.contains("palette: PaletteMode = PaletteMode.HOUSE"),
        )
    }
}
