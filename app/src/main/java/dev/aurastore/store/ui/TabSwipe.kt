package dev.aurastore.store.ui

import kotlin.math.abs

/**
 * The rule that decides whether a horizontal swipe on a tab turns the page.
 *
 * Kept out of the Compose layer for the same reason [Screen.depth] and [moveOf] are: the decision is a
 * fraction and a velocity against the width of the phone, and a device is a slow place to check six
 * cases of it. The gesture owns the pixels; this owns the answer.
 */
object TabSwipe {
    /**
     * How far the page must travel before letting go turns it: 18 % of the width.
     *
     * A fifth of the screen at 1080 px is 194 px, which is past the point where the finger's own
     * movement has clearly started a page turn rather than nudged a list. Lower would make a slightly
     * diagonal scroll change tabs by accident.
     */
    const val COMMIT_FRACTION = 0.18f

    /**
     * A flick commits below the distance: 900 px a second.
     *
     * A gesture that covers little distance quickly still means "next"; without this the page would
     * spring back under a fast flick that ended early, which reads as a dropped gesture.
     */
    const val FLICK_VELOCITY = 900f

    /**
     * The tab to land on, or [tab] when the gesture does not commit.
     *
     * Dragging left (negative [dx]) asks for the tab to the right, because the page moves left, the way
     * a pager's next page comes in from the right. The result is clamped to the strip, so a swipe
     * outward at either end lands back on the tab already showing rather than past the edge.
     */
    fun target(tab: Int, lastIndex: Int, dx: Float, velocityX: Float, widthPx: Float): Int {
        if (widthPx <= 0f || lastIndex < 0) return tab
        val farEnough = abs(dx) > widthPx * COMMIT_FRACTION
        val flicked = abs(velocityX) > FLICK_VELOCITY
        if (!farEnough && !flicked) return tab
        val next = tab + if (dx < 0f) 1 else -1
        return next.coerceIn(0, lastIndex)
    }
}
