package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strip's permutation rules, without a device.
 *
 * A stored order is the one preference that can make a screen unreachable: a dropped index hides a tab
 * and a duplicated one draws two. Every path into [NavOrder] is a string from a file or a drag
 * distance, so the repair has to be in the model rather than trusted to the caller.
 */
class NavOrderTest {

    @Test
    fun aFreshInstallUsesTheScreensOwnOrder() {
        assertEquals(listOf(0, 1, 2, 3, 4), NavOrder.DEFAULT)
        assertEquals(NavOrder.DEFAULT, NavOrder.sanitize(null as String?))
        assertEquals(NavOrder.DEFAULT, NavOrder.sanitize(""))
    }

    @Test
    fun aStoredOrderSurvivesARoundTrip() {
        val order = listOf(3, 0, 4, 1, 2)
        assertEquals(order, NavOrder.sanitize(NavOrder.join(order)))
    }

    @Test
    fun unknownIndicesAreDroppedAndMissingOnesAppended() {
        // A pref from another build, or one hand-edited, is a promise nobody can keep.
        assertEquals(listOf(2, 0, 1, 3, 4), NavOrder.sanitize("2,9,-1,2,0"))
        assertEquals(listOf(4, 0, 1, 2, 3), NavOrder.sanitize("4"))
        assertEquals(NavOrder.DEFAULT, NavOrder.sanitize("banana,,,"))
    }

    @Test
    fun anEmptyStoredOrderCannotHideATab() {
        val repaired = NavOrder.sanitize(listOf<Int>())
        assertEquals(NavOrder.COUNT, repaired.size)
        assertEquals((0 until NavOrder.COUNT).toSet(), repaired.toSet())
    }

    @Test
    fun movingAnItemKeepsEveryoneElseInOrder() {
        val order = listOf(0, 1, 2, 3, 4)
        assertEquals(listOf(3, 0, 1, 2, 4), NavOrder.move(order, 3, 0))
        assertEquals(listOf(0, 1, 4, 2, 3), NavOrder.move(order, 4, 2))
        assertEquals(order, NavOrder.move(order, 2, 2))
    }

    @Test
    fun aMovePastEitherEndIsANoOp() {
        assertEquals(NavOrder.DEFAULT, NavOrder.move(NavOrder.DEFAULT, 0, 9))
        assertEquals(NavOrder.DEFAULT, NavOrder.move(NavOrder.DEFAULT, -1, 2))
    }

    @Test
    fun positionIsWhereThePillTravels() {
        val order = listOf(3, 0, 4, 1, 2)
        assertEquals(0, NavOrder.positionOf(order, 3))
        assertEquals(4, NavOrder.positionOf(order, 2))
        assertEquals(-1, NavOrder.positionOf(order, 7))
        // Even a corrupt list answers with a valid position for every real screen.
        val repaired = NavOrder.sanitize("3")
        assertEquals(0, NavOrder.positionOf(repaired, 3))
        assertEquals(1, NavOrder.positionOf(repaired, 0))
    }
}
