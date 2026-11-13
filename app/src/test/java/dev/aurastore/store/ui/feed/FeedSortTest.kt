package dev.aurastore.store.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Browse tab's ordering chips, pinned to the order the store opens on.
 *
 * A storefront that opens on its most-viewed list hides what just changed behind its own history. The
 * catalogue's `modified` ordering is the one that moves, so it is the first chip and the default.
 */
class FeedSortTest {

    @Test
    fun `new and updated is the first ordering and the default`() {
        assertEquals("modified", SORTS.first().orderBy)
        assertEquals("modified", FeedState().sort)
    }

    @Test
    fun `popular and trending follow it rather than lead`() {
        assertEquals(
            listOf("modified", "post_views", "post_views_week"),
            SORTS.map { it.orderBy },
        )
    }
}
