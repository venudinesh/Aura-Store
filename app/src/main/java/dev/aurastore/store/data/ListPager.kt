package dev.aurastore.store.data

/**
 * The rows one listing has on screen, and how far into it the reader has got.
 *
 * [SearchPager] with the two things a search has and a listing has not taken out: there is no second source
 * to merge, and `v2/posts` publishes no total, so nothing here knows how long the list is. What is left is
 * the half that matters *more* on a listing, because the total is not there to catch a mistake — a page that
 * arrives out of order cannot rewind the cursor, and a page whose ids are all already on screen ends the
 * walk. That last rule is the only way to notice a server that stopped honouring `page` while still
 * answering `200`, which is exactly how the search route behaves; without it a scroll becomes a loop that
 * pays 12 kB a turn for rows the screen already has.
 *
 * A walk can also end for a reason that is not the list's: the route answers any page above
 * [Api.LIST_MAX_PAGES] with an empty list whatever the page size, so the flat lists stop at
 * `50 × LIST_PAGE_SIZE` rows however long the ordering is. [stoppedAtCeiling] is how the screen behind this
 * one tells that cutoff from a real end, and the difference is a sentence the reader can act on.
 */
class ListPager {

    private val rows = LinkedHashMap<Long, PostSummary>()

    /** How many pages have been taken in; the next one to ask for is `page + 1`. */
    var page: Int = 0
        private set

    /** Whether asking for another page could return anything. */
    var hasMore: Boolean = true
        private set

    /** Whether the walk ended past the page the route is willing to answer, rather than at the list's end. */
    var stoppedAtCeiling: Boolean = false
        private set

    val items: List<PostSummary> get() = rows.values.toList()

    /**
     * False for a page already taken in, and for one that arrives after the walk has ended — which is what a
     * doubled scroll trigger and a slow page from a list the user has since left both look like.
     */
    fun accept(page: Api.PostPage): Boolean {
        if (!hasMore || page.page <= this.page) return false
        val known = rows.size
        this.page = page.page
        for (row in page.items) rows.putIfAbsent(row.id, row)
        hasMore = page.hasMore && rows.size > known
        // Only a page the route would not answer closes the walk this way: a short page *inside* the window
        // is the ordering running out, which is a different sentence.
        if (!hasMore && page.page > Api.LIST_MAX_PAGES) stoppedAtCeiling = true
        return true
    }

    internal companion object {
        /** A pager that already has these rows on screen, at the end of the page they came from. */
        fun of(items: List<PostSummary>, page: Int, hasMore: Boolean): ListPager = ListPager().apply {
            items.forEach { row -> this.rows[row.id] = row }
            this.page = page
            this.hasMore = hasMore
        }
    }
}
