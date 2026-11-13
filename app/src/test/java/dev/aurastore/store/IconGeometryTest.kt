package dev.aurastore.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launcher's vectors, the script that redraws its rasters, and the palette they are cut from.
 *
 * The thirtieth round found the raster generator still drawing the previous mark: the README credits
 * that script with keeping the vectors and the rasters from drifting, and it had drifted silently,
 * because nothing re-ran it. A comment saying "keep these in step" is the same instruction that just
 * failed, so the pairing is asserted here instead — the four numbers the safe-zone claim rests on, and
 * the two colours the contrast arithmetic chose.
 *
 * The script lives at `tools/render_logo.py` rather than beside the repository: a test that reads a
 * file outside the checkout cannot run in CI, and a generator nobody can run is how the drift started.
 */
class IconGeometryTest {

    private val module = File(listOf(".", "..").first { File(it, "src/main/res/values/strings.xml").isFile })
    private val root = File(module, "..")

    private val monochrome get() = File(module, "src/main/res/drawable/ic_launcher_monochrome.xml").readText()
    private val background get() = File(module, "src/main/res/drawable/ic_launcher_background.xml").readText()
    private val generator get() = File(root, "tools/render_logo.py").readText()
    private val tokens get() = File(module, "src/main/java/dev/aurastore/store/theme/Color.kt").readText()

    /** Every number in a VectorDrawable `pathData`, which is how the XML spells its vertices. */
    private fun pathNumbers(path: String): List<Double> =
        Regex("-?\\d+(?:\\.\\d+)?").findAll(path.substringAfter("pathData=\"").substringBefore("\""))
            .map { it.value.toDouble() }.toList()

    private fun pythonPoints(text: String): List<Pair<Double, Double>> =
        Regex("""\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)""")
            .findAll(text.substringAfter("MARK_POINTS = [").substringBefore("]"))
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }.toList()

    /** The ARGB digits of a `Color.kt` token, without its `0x`. */
    private fun tokenArgb(text: String, name: String): String {
        val line = text.lineSequence().first { it.startsWith("val $name =") }
        return line.substringAfter('(').substringBefore(')').uppercase().removePrefix("0X")
    }

    private fun argbIn(xml: String, argb: String): Boolean = xml.uppercase().contains("#$argb")

    @Test
    fun `the generator draws the polygon the vectors draw`() {
        val points = pythonPoints(generator)
        assertTrue("MARK_POINTS vanished from the generator", points.isNotEmpty())
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val numbers = pathNumbers(monochrome)
        // The XML spells a vertex as `H80` / `V70`, so it carries each coordinate once per move
        // rather than as a pair — the extremes are the claim worth holding, and they are the ones the
        // safe-zone arithmetic and the docs' "52 × 39" sentence are computed from.
        assertEquals(listOf(28.0, 80.0), listOf(xs.min(), xs.max()))
        assertEquals(listOf(31.0, 70.0), listOf(ys.min(), ys.max()))
        assertTrue("the vectors do not reach the generator's extremes", numbers.containsAll(xs + ys))
    }

    @Test
    fun `the mark stays inside the adaptive safe circle`() {
        val points = pythonPoints(generator)
        val dx = (points.maxOf { it.first } - points.minOf { it.first }) / 2
        val dy = (points.maxOf { it.second } - points.minOf { it.second }) / 2
        val halfDiagonal = kotlin.math.sqrt(dx * dx + dy * dy)
        // 66dp of the 108dp canvas is what every launcher mask guarantees, and the mark's furthest
        // point lands at 32.5 — a rounding past that guidance, and 3.5dp inside the 72dp circle a
        // launcher actually masks.
        assertTrue(
            "the mark's half-diagonal is $halfDiagonal, past the 33.1 the docs allow",
            halfDiagonal <= 33.1,
        )
    }

    @Test
    fun `the launcher layers are cut from the app's own tokens`() {
        // The disc is the house amber and the wordmark is OnAccentDark, so the icon cannot quietly
        // disagree with the storefront's filled buttons. The field behind them is the same value in
        // both places for the same reason: the raster and the vector are drawn from one palette.
        val accent = tokenArgb(tokens, "Accent").removePrefix("FF").chunked(2).joinToString(", ") { "0x$it" }
        val onAccentDark = tokenArgb(tokens, "OnAccentDark").removePrefix("FF").chunked(2)
            .joinToString(", ") { "0x$it" }
        assertTrue("the disc is not Accent ($accent)", generator.contains("FIELD = ($accent)"))
        assertTrue("the wordmark is not OnAccentDark ($onAccentDark)", generator.contains("INK = ($onAccentDark)"))
        assertTrue("the monochrome mark is not white", argbIn(monochrome, "FFFFFFFF"))
        assertTrue(
            "the field behind the badge is not the generator's #1E1E1E",
            argbIn(background, "FF1E1E1E"),
        )
    }
}
