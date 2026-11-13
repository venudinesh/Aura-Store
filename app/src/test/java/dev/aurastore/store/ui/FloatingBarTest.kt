package dev.aurastore.store.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vertical stack: the navigation bar, the gap it floats on, and the download panel above it.
 *
 * The panel and the bar used to be placed by two formulas in two files. They now share one column in
 * the bar's own window, and the page reserves room with one function — [FloatingBar.reserve]. These
 * cases pin that function, including the three settings a device pass does not visit: a bar at the
 * bottom of the screen, a bar at 100 % bottom offset, and a 2× text size.
 */
class FloatingBarTest {

    /** The sheet at the default text size: 10 + 32 + 16 + 10 dp. */
    private val shippedBar = 68.dp

    @Test
    fun `the page reserves one bar when no panel is up`() {
        for (offset in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val inset = FloatingBar.inset(enabled = true, FloatingBar.height, offset)
            val reserve = FloatingBar.reserve(
                fontScale = 1f,
                barShown = true,
                floating = true,
                offset = offset,
                dockHeight = null,
            )
            assertEquals("offset $offset", inset, reserve)
        }
    }

    @Test
    fun `the panel adds its own height and one gap to the reserve`() {
        val panel = 94.dp
        for (offset in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (scale in listOf(1f, 1.3f, 1.6f, 2f)) {
                val bar = FloatingBar.inset(enabled = true, FloatingBar.heightFor(scale), offset)
                val reserve = FloatingBar.reserve(
                    fontScale = scale,
                    barShown = true,
                    floating = true,
                    offset = offset,
                    dockHeight = panel,
                )
                assertEquals("offset $offset at ${scale}×", bar + panel + FloatingBar.dockGap, reserve)
            }
        }
    }

    @Test
    fun `a larger text size grows the bar by its label, up to the cap`() {
        val panel = 94.dp
        fun reserve(scale: Float) = FloatingBar.reserve(
            fontScale = scale,
            barShown = true,
            floating = true,
            offset = 0f,
            dockHeight = panel,
        )
        val one = reserve(1f)
        val two = reserve(2f)
        val barGrowth = (FloatingBar.heightFor(2f) - FloatingBar.height).value
        // The sheet grows by exactly what its label gains, so the page's reserve must grow by that
        // same amount — a shorter reserve would put the larger bar's top edge inside the panel.
        // Compared with a hair of tolerance: the two sums subtract in a different order, and a
        // fractional growth (the label is capped at 1.4×) lands a few ulps apart.
        assertEquals(barGrowth, (two - one).value, 0.01f)
        // The label stops at 1.4×, so the sheet grows by 0.4 of its 16 dp line and no further.
        assertEquals(16.dp.value * (FloatingBar.maxLabelScale - 1f), barGrowth, 0.01f)
        assertTrue(two > one)
    }

    @Test
    fun `a hidden bar still reserves the panel and its gap`() {
        val panel = 94.dp
        assertEquals(
            panel + FloatingBar.dockGap,
            FloatingBar.reserve(
                fontScale = 1.4f,
                barShown = false,
                floating = true,
                offset = 1f,
                dockHeight = panel,
            ),
        )
        assertEquals(
                0.dp,
                FloatingBar.reserve(
                    fontScale = 1.4f,
                    barShown = false,
                    floating = true,
                    offset = 1f,
                    dockHeight = null,
                ),
            )
    }

    @Test
    fun `a classic bar sits on the edge and the panel still clears it`() {
        val height = FloatingBar.heightFor(1.4f)
        val panel = 94.dp
        assertEquals(height, FloatingBar.inset(enabled = false, height, offset = 1f))
        assertEquals(
            height + panel + FloatingBar.dockGap,
            FloatingBar.reserve(
                fontScale = 1.4f,
                barShown = true,
                floating = false,
                offset = 1f,
                dockHeight = panel,
            ),
        )
    }

    @Test
    fun `the shipped defaults are one shipped bar and one gap`() {
        assertEquals(shippedBar, FloatingBar.height)
        assertEquals(
            shippedBar,
            FloatingBar.reserve(
                fontScale = 1f,
                barShown = true,
                floating = true,
                offset = 0f,
                dockHeight = null,
            ),
        )
        assertEquals(
            shippedBar + 100.dp + FloatingBar.dockGap,
            FloatingBar.reserve(
                fontScale = 1f,
                barShown = true,
                floating = true,
                offset = 0f,
                dockHeight = 100.dp,
            ),
        )
        assertTrue("the panel must not touch the bar", FloatingBar.dockGap > 0.dp)
    }

    @Test
    fun `the pill is as wide as its labels need and never wider than the page`() {
        // A short label still gets a cell at least as wide as the selection pill plus its hair, and
        // the sheet keeps 6 dp of air at each end. Five such cells are 292 dp, not the 340 dp the old
        // 64 dp floor made — that floor is what turned the pill into a slab on a 360 dp page.
        assertEquals(56.dp * 5 + 12.dp, FloatingBar.pillWidth(labelWidth = 20.dp))
        assertTrue("the pill must stay narrower than a 360 dp page", FloatingBar.pillWidth(labelWidth = 20.dp) < 360.dp)
        // A long one — "Browse" at a 2× text size — widens its cell, and with it the pill.
        assertTrue(FloatingBar.pillWidth(labelWidth = 120.dp) > FloatingBar.pillWidth(40.dp))
        // The cap: the shipped 100 % never bites, a 60 % page share does, and neither escapes the page.
        // Sweeping above 100 % up to 150 % expands the pill, capped at the content width.
        assertEquals(340.dp, FloatingBar.sheetWidth(340.dp, 1f, 411.dp))
        assertEquals(411.dp * 0.6f, FloatingBar.sheetWidth(340.dp, 0.6f, 411.dp))
        assertEquals(411.dp, FloatingBar.sheetWidth(600.dp, 1f, 411.dp))
        assertEquals(411.dp, FloatingBar.sheetWidth(340.dp, 1.5f, 411.dp))
        assertEquals(510.dp, FloatingBar.sheetWidth(340.dp, 1.5f, 800.dp))
        assertEquals(510.dp, FloatingBar.sheetWidth(340.dp, 2f, 800.dp))
    }

    @Test
    fun `the tab glyph fits its pill and thickens as it fills`() {
        // 22 dp in the 32 dp pill leaves the five dp of air a navigation icon is specified with.
        assertEquals(22.dp, BarIcon.glyph)
        assertTrue("the glyph must not reach the pill's edge", BarIcon.glyph < 32.dp)
        assertEquals(1.8.dp, BarIcon.strokeFor(0f))
        assertEquals(2.8.dp, BarIcon.strokeFor(1f))
        assertTrue("half-filled sits between the two", BarIcon.strokeFor(0.5f) > BarIcon.strokeFor(0f))
        assertEquals("out of range stays at the solid end", 2.8.dp, BarIcon.strokeFor(4f))
        assertEquals(1.8.dp, BarIcon.strokeFor(-2f))
    }

    @Test
    fun `a joined panel adds its height and no gap`() {
        val panel = 94.dp
        val joined = FloatingBar.reserve(
            fontScale = 1f,
            barShown = true,
            floating = true,
            offset = 0f,
            dockHeight = panel,
            dockJoined = true,
        )
        assertEquals(FloatingBar.height + panel, joined)
        // Apart, the same panel needs the gap: that is the only difference between the two states.
        assertEquals(FloatingBar.dockGap, joined - (FloatingBar.height + panel) + FloatingBar.dockGap)
    }
}
