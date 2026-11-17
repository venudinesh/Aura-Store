package dev.aurastore.store.ui.backdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The snowfall, checked as arithmetic rather than as pixels.
 *
 * A particle field is only pleasant if it stays on the page, keeps a readable alpha, and gives the
 * same answer twice. All three are cheaper to prove off the handset, and a flake that drifts half a
 * screen past the edge or an alpha that leaves `0..1` is invisible until someone stares at a phone.
 */
class BackdropMathTest {

    private val width = 1080f
    private val height = 2400f
    private val dp = 3f

    @Test
    fun `a phone-sized page carries a snowfall`() {
        val count = snowflakeCount(width, height, dp)
        assertTrue("a page must have weather, not a stray dot", count >= 40)
        assertTrue("a page must not turn into static", count <= 170)
    }

    @Test
    fun `a thumbnail carries fewer flakes than a page`() {
        val tile = snowflakeCount(420f, 222f, dp)
        val page = snowflakeCount(width, height, dp)
        assertTrue("a tile is a small scene, not a compressed blizzard", tile < page)
        assertTrue(tile >= 12)
    }

    @Test
    fun `nothing is drawn for an empty region`() {
        assertEquals(0, snowflakeCount(0f, height, dp))
        assertEquals(0, snowflakeCount(width, 0f, dp))
        assertEquals(0, snowflakeCount(width, height, 0f))
    }

    @Test
    fun `flakes stay on and around the page`() {
        val count = snowflakeCount(width, height, dp)
        // Two minutes at half-second steps covers several wraps of the slowest and fastest flake.
        for (index in 0 until count) {
            for (step in 0..240) {
                val flake = snowflakeAt(index, step * 0.5f, width, height, dp)
                assertTrue("flake $index has no x at step $step", flake.x.isFinite())
                assertTrue("flake $index has no y at step $step", flake.y.isFinite())
                assertTrue(
                    "flake $index drifted far off the side: ${flake.x}",
                    flake.x > -width * 0.15f && flake.x < width * 1.15f,
                )
                assertTrue(
                    "flake $index left its fall: ${flake.y}",
                    flake.y >= -flake.radius && flake.y <= height + flake.radius,
                )
                assertTrue("flake $index has alpha ${flake.alpha}", flake.alpha in 0f..1f)
                assertTrue("flake $index has no size", flake.radius > 0f)
            }
        }
    }

    @Test
    fun `the same moment draws the same flake`() {
        val first = snowflakeAt(17, 42.25f, width, height, dp)
        val again = snowflakeAt(17, 42.25f, width, height, dp)
        assertEquals(first, again)
    }

    @Test
    fun `the hash covers the unit interval`() {
        var lowest = 1f
        var highest = 0f
        for (seed in -500..500) {
            val value = hash01(seed)
            assertTrue("hash01($seed) = $value", value in 0f..1f)
            lowest = minOf(lowest, value)
            highest = maxOf(highest, value)
        }
        assertTrue("the hash must not sit in one corner", lowest < 0.1f && highest > 0.9f)
    }
}
