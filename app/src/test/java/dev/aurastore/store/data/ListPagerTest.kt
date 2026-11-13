package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walk one listing takes, page by page.
 *
 * A listing has no total to reason from — `v2/posts` sends no `X-WP-Total` — so every rule here is a rule
 * about what the rows themselves say: a page shorter than asked for is the last one, a page of rows the
 * screen already has is the server repeating itself, and a page numbered behind the cursor is an answer
 * that arrived too late. All of it is testable without a network, in the shapes [Api.PostPage] holds.
 */
class ListPagerTest {

    private fun row(id: Long) = PostSummary(
        id = id,
        title = "App $id",
        imageUrl = null,
        bannerUrl = null,
        publisher = null,
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

    /** A page in the shape the route would answer with: [pageSize] asks for the rows the walk is on. */
    private fun page(page: Int, vararg ids: Long, pageSize: Int = 20, hasMore: Boolean = ids.size >= pageSize) =
        Api.PostPage(ids.map(::row), page, hasMore)

    @Test
    fun `a page as long as asked for keeps the walk open`() {
        val full = Api.PostPage.of((1L..20L).map(::row), page = 1, pageSize = 20)
        assertTrue("twenty rows out of an ask for twenty means there is more", full.hasMore)
        assertEquals(1, full.page)

        val short = Api.PostPage.of((1L..7L).map(::row), page = 1, pageSize = 20)
        assertFalse("seven is all the ordering has", short.hasMore)

        val none = Api.PostPage.of(emptyList(), page = 4, pageSize = 20)
        assertFalse(none.hasMore)
        assertEquals("page 4 asked for, page 4 reported", 4, none.page)
    }

    @Test
    fun `a page numbered behind the cursor cannot rewind the walk`() {
        val pager = ListPager()
        pager.accept(page(1, 1, 2, pageSize = 2, hasMore = true))
        assertFalse(pager.accept(page(1, 1, 2, pageSize = 2)))
        assertEquals(1, pager.page)
        assertEquals(listOf(1L, 2L), pager.items.map { it.id })
    }

    @Test
    fun `rows the screen has not seen append in page order`() {
        val pager = ListPager()
        pager.accept(page(1, 1, 2, 3, pageSize = 3, hasMore = true))
        pager.accept(page(2, 4, 5, 6, pageSize = 3, hasMore = true))
        pager.accept(page(3, 7, pageSize = 3))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), pager.items.map { it.id })
        assertEquals(3, pager.page)
        assertFalse(pager.hasMore)
    }

    @Test
    fun `a row the last page already carried is not shown twice`() {
        val pager = ListPager()
        pager.accept(page(1, 1, 2, pageSize = 2, hasMore = true))
        // Overlapping pages are what an ordering that moved between two asks looks like.
        assertTrue(pager.accept(page(2, 2, 3, pageSize = 2, hasMore = true)))
        assertEquals(listOf(1L, 2L, 3L), pager.items.map { it.id })
    }

    @Test
    fun `a page of rows already on screen ends the walk`() {
        val pager = ListPager()
        pager.accept(page(1, 1, 2, pageSize = 2, hasMore = true))
        // The route ignored `page` and sent the first page again: without this rule the reader's every
        // swipe pays for two more rows it can already see, forever.
        assertTrue("the walk does move on", pager.accept(page(2, 1, 2, pageSize = 2, hasMore = true)))
        assertFalse("but a page that adds nothing is the end of it", pager.hasMore)
        assertEquals(listOf(1L, 2L), pager.items.map { it.id })
    }

    @Test
    fun `an empty page closes a walk that was still open`() {
        val pager = ListPager()
        pager.accept(page(1, 1, 2, pageSize = 2, hasMore = true))
        assertTrue(pager.accept(page(2, pageSize = 2, hasMore = true)))
        assertFalse("nothing arrived, so there is nothing left to ask for", pager.hasMore)
        assertEquals(2, pager.page)
    }

    @Test
    fun `a walk already part-way is picked up where it stands`() {
        // What a rail that re-renders after its screen left needs: the rows it shows and the page they
        // came from, with no memory of the request that fetched them.
        val pager = ListPager.of(listOf(row(1), row(2)), page = 1, hasMore = true)
        assertEquals(listOf(1L, 2L), pager.items.map { it.id })
        assertTrue(pager.accept(page(2, 3, 4, pageSize = 2, hasMore = true)))
        assertEquals(listOf(1L, 2L, 3L, 4L), pager.items.map { it.id })
        assertEquals(2, pager.page)
    }

    @Test
    fun `a walk that has ended stays ended`() {
        val pager = ListPager()
        pager.accept(page(1, 1, pageSize = 20))
        assertFalse(pager.hasMore)
        assertFalse(pager.accept(page(2, 2, 3, pageSize = 20, hasMore = true)))
        assertEquals(listOf(1L), pager.items.map { it.id })
    }

    @Test
    fun `a short page inside the window is the ordering running out`() {
        val pager = ListPager.of((1L..120L).map(::row), page = 6, hasMore = true)
        assertTrue(pager.accept(page(7, 121, 122, 123, 124, 125, 126, 127, 128, pageSize = 20)))
        assertFalse(pager.hasMore)
        assertFalse("the list ended, so the screen may say so", pager.stoppedAtCeiling)
    }

    @Test
    fun `a walk closed by the page the route will not answer is not an end`() {
        // `v2/posts` answers any page above 50 with an empty list whatever the page size, so a walk that is
        // still growing at page 50 has hit that window, not the bottom of the ordering. Both cases arrive as
        // "nothing came back", and only one of them entitles the footer to say the list is finished.
        val pager = ListPager.of((1L..Api.LIST_REACH.toLong()).map(::row), page = Api.LIST_MAX_PAGES, hasMore = true)
        assertTrue(pager.accept(page(Api.LIST_MAX_PAGES + 1, pageSize = Api.LIST_PAGE_SIZE)))
        assertFalse(pager.hasMore)
        assertTrue("${Api.LIST_REACH} rows is the ceiling, not the length", pager.stoppedAtCeiling)
        assertEquals(Api.LIST_REACH, pager.items.size)
    }

    @Test
    fun `the ceiling is not raised by a page that merely repeats what the screen has`() {
        // The other way a walk closes on a page that was asked for in full: the route ignored `page` and sent
        // rows already on screen. That is the list saying it has nothing new, well inside the window, and must
        // not borrow the ceiling's sentence.
        val pager = ListPager.of((1L..20L).map(::row), page = 1, hasMore = true)
        val again = (1L..20L).toList().toLongArray()
        assertTrue(pager.accept(page(2, *again, pageSize = 20, hasMore = true)))
        assertFalse(pager.hasMore)
        assertFalse(pager.stoppedAtCeiling)
    }
}
