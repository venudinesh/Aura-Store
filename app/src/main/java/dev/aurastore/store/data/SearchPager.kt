package dev.aurastore.store.data

/**
 * The list one search term turns out to be.
 *
 * Neither source that answers a term knows about the other's matches: `v2/search` ranks the best few and
 * carries the full row, `wp/v2/posts?search=` has every match and can page them, and for a term like
 * `roblox` each finds posts the other does not. So the ranked list goes in first and the pages append the
 * ids that are not already on screen — by id, because the same post arrives from both, and a list that
 * showed it twice would look like two apps with one name.
 *
 * The pages are the only thing that grows this, and there is no way to be shown a page without asking for
 * it, which is what keeps a 19,000-post catalogue searchable without loading it.
 */
class SearchPager {

    private val rows = LinkedHashMap<Long, PostSummary>()
    private var reportedTotal: Int? = null

    /** How many pages have been taken in; the next one to ask for is `page + 1`. */
    var page: Int = 0
        private set

    /** Whether asking for another page could return anything. */
    var hasMore: Boolean = true
        private set

    val items: List<PostSummary> get() = rows.values.toList()

    /** The size of the answer, never smaller than what is already on screen. */
    val total: Int? get() = reportedTotal?.let { maxOf(it, rows.size) }

    /** The ranked matches, ahead of everything the pages add. */
    fun seed(best: List<PostSummary>) {
        for (row in best) rows[row.id] = row
    }

    /** False for a page that has already been taken in, which is what a doubled scroll trigger sends. */
    fun accept(result: Api.SearchPage): Boolean {
        if (result.page <= page) return false
        page = result.page
        for (row in result.items) rows.putIfAbsent(row.id, row)
        reportedTotal = result.total ?: reportedTotal
        hasMore = result.hasMore
        return true
    }
}
