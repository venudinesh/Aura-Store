package dev.aurastore.store.data

import dev.aurastore.store.ui.feed.Rail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a listing can be read, and the catalogue's own shape that number has to clear.
 *
 * [Api.LIST_PAGE_SIZE] looks like a free choice and is not one: the route's window is on the page number, so
 * the rows a walk can reach are `pages × rows`, and the page size is what decides whether the last row of a
 * category is reachable or whether the reader is told, mid-list, that the catalogue stopped answering. These
 * two cases are the tripwire for someone lowering the page size for a first-paint win and quietly making a
 * footer sentence false again.
 *
 * The row counts are measured, not inferred: on 28 Oct the deepest leaf term, `Simulation`, answered 50 rows
 * on page 23 of a 100-row ask and nothing on page 24 — 2 250 rows, exactly the `count` the category route
 * publishes for it. `Tools` (1 257), `Personalization` (1 210) and `Photography` (433) each ended on a short
 * page at their own `count` too, so a leaf's listing is its count and the largest count is the depth a walk
 * has to reach.
 */
class ListingReachTest {

    @Test
    fun `the reach is the window's pages at the listing's own page size`() {
        assertEquals(50, Api.LIST_MAX_PAGES)
        assertEquals(Api.LIST_MAX_PAGES * Api.LIST_PAGE_SIZE, Api.LIST_REACH)
    }

    @Test
    fun `a flat walk reaches the deepest term in the catalogue`() {
        val deepestLeafTerm = 2_250
        assertTrue(
            "a walk of ${Api.LIST_REACH} rows cannot read a term of $deepestLeafTerm to its end, so " +
                "`list_end` would be claiming an ending it never reached",
            Api.LIST_REACH >= deepestLeafTerm,
        )
    }

    @Test
    fun `a rail's wall is its own module's page size, not the listing default`() {
        // A rail pages at the `limit` its module asked for, so the number its tail sentence prints is that
        // limit times the window — and a rail with no limit in its query is a rail paging at the default.
        val twelve = Rail(title = "Popular", posts = emptyList(), query = mapOf("limit" to "12"))
        assertEquals(Api.LIST_MAX_PAGES * 12, twelve.reach)
        val noLimit = Rail(title = "Popular", posts = emptyList(), query = emptyMap())
        assertEquals(Api.LIST_REACH, noLimit.reach)
    }
}
