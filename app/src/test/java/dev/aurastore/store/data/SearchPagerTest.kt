package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list one term turns out to be, built from a ranked list and however many pages arrive after it.
 *
 * These are the rules a reader would notice broken: the same app twice in one search, a count smaller than
 * the rows on screen, a scroll that never ends, and a second copy of a page adding a second copy of every
 * row on it. None of it needs a network or a handset — the pages are built here in the shapes
 * [Api.SearchPage] can hold.
 */
class SearchPagerTest {

    private fun row(id: Long, from: String = "paged", publisher: String? = null) = PostSummary(
        id = id,
        title = "App $id ($from)",
        imageUrl = null,
        bannerUrl = null,
        publisher = publisher,
        genre = null,
        size = null,
        type = null,
        modInfo = null,
        latestVersion = null,
        packageName = null,
        ratingAvg = 0.0,
        ratingCount = 0,
        views = 0L,
        updatedAt = null,
    )

    private fun page(page: Int, vararg rows: PostSummary, total: Int? = null, hasMore: Boolean = false) =
        Api.SearchPage(rows.toList(), total, page, hasMore)

    @Test
    fun `the ranked matches come first and keep what they know`() {
        val pager = SearchPager()
        pager.seed(listOf(row(1, "instant", publisher = "Roblox Corporation"), row(2, "instant")))
        assertTrue(pager.accept(page(1, row(2, "paged"), row(3, "paged"), row(4, "paged"))))

        assertEquals(listOf(1L, 2L, 3L, 4L), pager.items.map { it.id })
        assertEquals(
            "the instant row is the one that names a publisher, so it is the one that stays",
            "Roblox Corporation",
            pager.items[0].publisher,
        )
        assertEquals("App 3 (paged)", pager.items[2].title)
    }

    @Test
    fun `a page taken in twice adds nothing`() {
        val pager = SearchPager()
        assertTrue(pager.accept(page(1, row(7), row(8), hasMore = true)))
        assertFalse("a doubled scroll trigger must not repeat the page", pager.accept(page(1, row(7), row(8))))
        assertEquals(listOf(7L, 8L), pager.items.map { it.id })
        assertEquals(1, pager.page)
    }

    @Test
    fun `pages are taken in order and the cursor moves with them`() {
        val pager = SearchPager()
        pager.accept(page(1, row(1), hasMore = true))
        pager.accept(page(2, row(2), hasMore = true))
        assertFalse("a late answer for a page already read cannot rewind the cursor", pager.accept(page(1, row(1))))
        assertEquals(2, pager.page)
        assertTrue(pager.accept(page(3, row(3))))
        assertFalse(pager.hasMore)
    }

    @Test
    fun `the count is never smaller than the rows on screen`() {
        val pager = SearchPager()
        // The instant list can hold a match the paged source does not, so the reported 4 is not the whole
        // answer once 7 rows are showing; claiming otherwise would be a number the reader can count past.
        pager.seed(listOf(row(1, "instant"), row(2, "instant"), row(3, "instant")))
        pager.accept(page(1, row(3), row(4), row(5), row(6), row(7), total = 4))
        assertEquals(7, pager.items.size)
        assertEquals(7, pager.total)
    }

    @Test
    fun `a total is kept from the page that offered one`() {
        val pager = SearchPager()
        assertNull("nothing has said how many there are yet", pager.total)
        pager.accept(page(1, row(1), total = null, hasMore = true))
        assertNull(pager.total)
        pager.accept(page(2, row(2), total = 21, hasMore = true))
        assertEquals(21, pager.total)
        pager.accept(page(3, row(3), total = null))
        assertEquals("a page that forgot the header does not lose the count", 21, pager.total)
    }

    @Test
    fun `the list stays open until a page says it is the last`() {
        val pager = SearchPager()
        assertTrue("before anything has been asked", pager.hasMore)
        pager.accept(page(1, row(1), total = 60, hasMore = true))
        assertTrue(pager.hasMore)
        assertEquals(60, pager.total)
        pager.accept(page(2, row(2)))
        assertFalse(pager.hasMore)
    }
}
