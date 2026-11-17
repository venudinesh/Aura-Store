package dev.aurastore.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher's two vector layers and the palette they are cut from.
 *
 * The icon is the same handwritten A the splash writes: two paths, a body and a bar, in a 108-unit
 * adaptive canvas. There is no raster behind it any more, so the claims worth holding are the ones
 * that used to drift silently: the foreground and the themed layer must draw the same pen, the whole
 * stroke must sit inside the adaptive safe circle, and the two colours must be the app's own tokens.
 *
 * A launcher can crop an icon to 66 of the 108 units, so the mark is measured with half its stroke
 * on every side. A future nudge of a control point that leaves the circle fails here instead of on
 * a phone with a round mask.
 */
class IconGeometryTest {

    private val module = File(listOf(".", "..").first { File(it, "src/main/res/values/strings.xml").isFile })
    private val foreground get() = File(module, "src/main/res/drawable/ic_launcher_foreground.xml").readText()
    private val monochrome get() = File(module, "src/main/res/drawable/ic_launcher_monochrome.xml").readText()
    private val background get() = File(module, "src/main/res/drawable/ic_launcher_background.xml").readText()
    private val launcher get() = File(module, "src/main/res/mipmap-anydpi/ic_launcher.xml").readText()
    private val launcherRound get() = File(module, "src/main/res/mipmap-anydpi/ic_launcher_round.xml").readText()
    private val tokens get() = File(module, "src/main/java/dev/aurastore/store/theme/Color.kt").readText()

    /** Every path in a VectorDrawable, in the order the file draws them. */
    private fun pathData(xml: String): List<String> =
        Regex("""android:pathData="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

    /** Every number in a `pathData`, which is how the XML spells its vertices and controls. */
    private fun pathNumbers(path: String): List<Float> =
        Regex("-?\\d+(?:\\.\\d+)?").findAll(path).map { it.value.toFloat() }.toList()

    private fun attribute(xml: String, name: String): String? =
        Regex("""android:$name="([^"]+)"""").find(xml)?.groupValues?.get(1)

    /** The ARGB digits of a `Color.kt` token, without its `0x`. */
    private fun tokenArgb(text: String, name: String): String {
        val line = text.lineSequence().first { it.startsWith("val $name =") }
        return line.substringAfter('(').substringBefore(')').uppercase().removePrefix("0X")
    }

    private fun argbIn(xml: String, argb: String): Boolean = xml.uppercase().contains("#$argb")

    @Test
    fun `the foreground and the themed layer draw the same pen`() {
        assertEquals(
            "the two launcher layers do not draw the same paths",
            pathData(foreground),
            pathData(monochrome),
        )
        assertEquals(2, pathData(foreground).size)
        val stroke = attribute(foreground, "strokeWidth")
        assertEquals("the pen is not one width", stroke, attribute(monochrome, "strokeWidth"))
        assertEquals("the pen is not the launcher weight", "4.6", stroke)
        assertEquals("round caps keep the handwritten ends", "round", attribute(foreground, "strokeLineCap"))
        assertEquals("round caps keep the handwritten ends", "round", attribute(monochrome, "strokeLineCap"))
    }

    @Test
    fun `the mark stays inside the adaptive safe circle`() {
        val stroke = attribute(foreground, "strokeWidth")!!.toFloat()
        // A path corner is a control point here as far as the safe circle is concerned: a quadratic
        // never leaves the hull of its endpoints and control, and the control is the wider reading.
        // Every pair of numbers is one point on that hull, so the bound is conservative by design.
        val points = pathData(foreground)
            .flatMap(::pathNumbers)
            .chunked(2)
            .map { it[0] to it[1] }
        assertTrue("the layer has no vertices to measure", points.isNotEmpty())
        val furthest = points.maxOf { (x, y) ->
            kotlin.math.sqrt((x - 54f) * (x - 54f) + (y - 54f) * (y - 54f)) + stroke / 2f
        }
        // 66dp of the 108dp canvas is what every launcher mask guarantees; the stroke's own width is
        // part of the mark, so it counts toward the radius.
        assertTrue(
            "the painted mark reaches $furthest from the centre, past the 33.1 the adaptive mask allows",
            furthest <= 33.1f,
        )
    }

    @Test
    fun `the layers are cut from the app's own tokens`() {
        // The field is NightBase, the same surface the splash writes on, and the pen is CreamText,
        // the same warm ink the rest of the store reads against it.
        val field = tokenArgb(tokens, "NightBase")
        val ink = tokenArgb(tokens, "CreamText")
        assertTrue("the field is not NightBase ($field)", argbIn(background, field))
        assertTrue("the pen is not CreamText ($ink)", argbIn(foreground, ink))
        assertTrue("the themed mark is not white", argbIn(monochrome, "FFFFFFFF"))
    }

    @Test
    fun `the launcher reads both layers and the themed one`() {
        for ((name, xml) in listOf("square" to launcher, "round" to launcherRound)) {
            assertTrue("the $name icon lost its background", xml.contains("@drawable/ic_launcher_background"))
            assertTrue("the $name icon lost its foreground", xml.contains("@drawable/ic_launcher_foreground"))
            assertTrue("the $name icon lost its monochrome layer", xml.contains("@drawable/ic_launcher_monochrome"))
        }
    }

    @Test
    fun `no raster layer shadows the vector`() {
        val res = File(module, "src/main/res")
        val rasters = res.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension in setOf("png", "webp") }
            .filter { it.nameWithoutExtension.startsWith("ic_launcher") }
            .toList()
        assertFalse("a launcher raster survives beside the vector: $rasters", rasters.isNotEmpty())
    }
}
