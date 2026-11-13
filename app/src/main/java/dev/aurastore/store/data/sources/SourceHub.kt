package dev.aurastore.store.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * What one source contributes to the storefront: its newest apps and its own categories.
 *
 * A source that cannot be read right now is still a section — an empty one — because the other six
 * have nothing to do with it and a row that says so is better than a screen that lost a shelf.
 */
data class SourceSection(
    val source: SourceId,
    val apps: List<SourceApp>,
    val categories: List<SourceCategory>,
    /** Whether the source refused to answer. The section is empty and this says why it is. */
    val failed: Boolean = false,
)

/**
 * One source's answer to "where else is this app?".
 *
 * The files are not resolved here: a site's download link is minted per file and often per session,
 * so asking every source for its files before the user has chosen one would spend a dozen requests
 * on a question nobody asked yet.
 */
data class SourceMatch(
    val source: SourceId,
    val app: SourceApp,
)

/**
 * One source's full answer to a term: every row it returned, in its own order.
 *
 * A search is a search across the shop, so a source that has twenty matches for a term should show
 * twenty of them rather than the single row that happens to be the best guess.
 */
data class SourceAnswer(
    val source: SourceId,
    val apps: List<SourceApp>,
)

/**
 * The one place the sources are asked, so the storefront and the search read the same answers.
 *
 * Everything here is a cache over the sources themselves: the sections are asked for once and kept
 * for half an hour, and a term is asked of every source at once. The sources themselves stay
 * blocking and HTML-shaped; this is where that becomes a list a screen can draw.
 */
object SourceHub {

    /** How long a section stays usable. Long enough to be a cache, short enough to notice a new app. */
    private const val TTL_MS = 30L * 60 * 1000

    private data class Cached(val at: Long, val section: SourceSection)

    private val cache = ConcurrentHashMap<SourceId, Cached>()

    /**
     * Every enabled source's section, asked for in parallel.
     *
     * A source that answers from the cache is not asked again; one that cannot be read is remembered
     * as empty rather than retried on every scroll.
     */
    suspend fun sections(enabled: Set<String>): List<SourceSection> = coroutineScope {
        Sources.enabled(enabled).map { source -> async { sectionFor(source) } }.awaitAll()
    }

    /** One source's section, from the cache when the cache has it. */
    suspend fun sectionFor(source: Source): SourceSection {
        val now = System.currentTimeMillis()
        val cached = cache[source.id]
        if (cached != null && now - cached.at < TTL_MS) return cached.section
        val section = withContext(Dispatchers.IO) {
            val apps = runCatching { source.latest() }.getOrDefault(emptyList())
            val categories = runCatching { source.categories() }.getOrDefault(emptyList())
            SourceSection(
                source = source.id,
                apps = apps,
                categories = categories,
                failed = apps.isEmpty() && categories.isEmpty(),
            )
        }
        cache[source.id] = Cached(now, section)
        return section
    }

    /** The apps of one category, read through the source that published the category. */
    suspend fun appsIn(source: SourceId, category: SourceCategory): List<SourceApp> {
        val adapter = Sources.all.firstOrNull { it.id == source } ?: return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { adapter.appsIn(category) }.getOrDefault(emptyList())
        }
    }

    /**
     * Every enabled source's full answer to a term, asked at once.
     *
     * Each answer keeps all of its rows, so the search screen can show what a site actually has
     * instead of one guess per site. A source that cannot answer is left out rather than taking the
     * other six with it, and the order is [Sources.all]'s, which is the order the store reads in.
     */
    suspend fun search(term: String, enabled: Set<String>): List<SourceAnswer> = coroutineScope {
        val needle = term.trim()
        if (needle.isEmpty()) return@coroutineScope emptyList()
        Sources.enabled(enabled).map { source ->
            async {
                // A source's search is blocking HTTP and HTML parsing, so it goes to IO like every
                // other read of a site. Without this the whole answer dies on the main thread.
                source to withContext(Dispatchers.IO) {
                    runCatching { source.searchRelaxed(needle) }.getOrDefault(emptyList())
                }
            }
        }
            .awaitAll()
            .mapNotNull { (source, apps) ->
                if (apps.isEmpty()) null else SourceAnswer(source = source.id, apps = apps)
            }
    }

    /**
     * Every enabled source's answer to a term, asked at once.
     *
     * The catalogue is included: it is a source like the others, and a term that the catalogue has
     * and the others do not must still find its row.
     */
    suspend fun find(term: String, enabled: Set<String>): List<SourceMatch> = coroutineScope {
        val needle = term.trim()
        if (needle.isEmpty()) return@coroutineScope emptyList()
        Sources.enabled(enabled).map { source -> async { matchFor(source, needle) } }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * One source's answer to a term.
     *
     * A site's search is loose: it answers with a page of things near the term. The row that carries
     * the term in its own name is the app that was asked for; when the source has no such row, its
     * first answer is offered rather than nothing, because the user can see which source it came from.
     */
    suspend fun matchFor(source: Source, term: String): SourceMatch? = withContext(Dispatchers.IO) {
        val apps = runCatching { source.search(term) }.getOrDefault(emptyList())
        if (apps.isEmpty()) return@withContext null
        val app = apps.firstOrNull { candidate -> titleMatches(candidate.title, term) }
            ?: apps.firstOrNull { candidate -> candidate.subtitle?.let { titleMatches(it, term) } == true }
            ?: apps.firstOrNull()
            ?: return@withContext null
        SourceMatch(source = source.id, app = app)
    }

    /**
     * Whether a row's name is the term, or carries it.
     *
     * The word match is loose on purpose: the catalogue writes "Spotify Music" where a person types
     * "spotify", and a modded build writes "Spotify Premium". A row that carries the term is the app
     * that was asked for.
     */
    fun titleMatches(title: String?, term: String): Boolean {
        val needle = term.trim().lowercase()
        if (needle.isBlank()) return false
        val haystack = title?.lowercase().orEmpty()
        if (haystack.isBlank()) return false
        return haystack.contains(needle)
    }

    /** Forgets every cached section, so the next read asks the sources again. */
    fun invalidate() {
        cache.clear()
    }
}
