package dev.aurastore.store.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The swipe rule, checked at the edges rather than on a phone.
 *
 * The handset can show that a left swipe reaches Apps; only these cases can show that a fast flick
 * with almost no distance still commits, that a slow nudge does not, and that the strip clamps at both
 * ends instead of sliding past them.
 */
class TabSwipeTest {

    private val width = 1080f
    private val last = 4

    @Test
    fun `a slow nudge stays put`() {
        // 150 px is 14 % of the width, under the 18 % commit distance, with no speed behind it.
        assertEquals(2, TabSwipe.target(tab = 2, lastIndex = last, dx = -150f, velocityX = -200f, widthPx = width))
    }

    @Test
    fun `a drag past the fraction commits`() {
        assertEquals(3, TabSwipe.target(tab = 2, lastIndex = last, dx = -220f, velocityX = 0f, widthPx = width))
        assertEquals(1, TabSwipe.target(tab = 2, lastIndex = last, dx = 220f, velocityX = 0f, widthPx = width))
    }

    @Test
    fun `a fast flick commits below the distance`() {
        assertEquals(3, TabSwipe.target(tab = 2, lastIndex = last, dx = -40f, velocityX = -1400f, widthPx = width))
        assertEquals(1, TabSwipe.target(tab = 2, lastIndex = last, dx = 40f, velocityX = 1400f, widthPx = width))
    }

    @Test
    fun `the strip clamps at both ends`() {
        assertEquals(0, TabSwipe.target(tab = 0, lastIndex = last, dx = 900f, velocityX = 3000f, widthPx = width))
        assertEquals(last, TabSwipe.target(tab = last, lastIndex = last, dx = -900f, velocityX = -3000f, widthPx = width))
    }

    @Test
    fun `a flick outward at the last tab does not wrap`() {
        assertEquals(4, TabSwipe.target(tab = 4, lastIndex = last, dx = -500f, velocityX = -2000f, widthPx = width))
    }

    @Test
    fun `an unmeasured width cannot commit`() {
        // The first frame of the bar has no width yet; a gesture there must not turn the page.
        assertEquals(1, TabSwipe.target(tab = 1, lastIndex = last, dx = -900f, velocityX = -3000f, widthPx = 0f))
    }
}
