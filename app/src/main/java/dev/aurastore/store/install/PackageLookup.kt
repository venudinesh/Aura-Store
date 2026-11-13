package dev.aurastore.store.install

import android.content.Context
import androidx.core.content.edit
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.LibraryEntry
import dev.aurastore.store.data.PostSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * What the catalogue says about one package, and when we asked.
 *
 * A null [postId] is an answer, not a gap: the search ran and no listing carries this package. That
 * is worth caching — otherwise a phone with its own apps asks the same pointless question on every
 * visit.
 *
 * [originalVersion] is the catalogue's `lastest_version`: the build of the *original* app the listing
 * was written for, which a post is not obliged to ship a file of. It is a screen for which posts are
 * worth opening, not the number a row shows — [AutoUpdater.available] reads that from the post's own
 * files through [UpdateScanner].
 */
data class Lookup(val postId: Long?, val originalVersion: String?, val checkedAt: Long)

/**
 * Which post an app that did not come from this store belongs to.
 *
 * There is no "does com.foo have an update" endpoint, but every listing with a Play counterpart
 * publishes that counterpart's URL, and the `?id=` in it is the package name. So a search for the
 * app's own label answers two questions in one request: whether the catalogue carries this package
 * at all, and which version of the original app the listing was written for.
 *
 * Two rules keep the cost honest, because a normal phone holds dozens of apps: an answer is kept for
 * [TTL_MS], and a pass asks for at most [PER_PASS] packages — the most recently updated ones, which
 * is the order an app list actually changes in. Callers that want more of the list get it over the
 * next day, not in one request storm.
 */
object PackageLookup {

    private const val PREFS = "aurastore_package_lookup"
    private const val KEY_POST = "post:"
    private const val KEY_VERSION = "version:"
    private const val KEY_AT = "at:"

    /** How long a package→post answer stays usable: a day. A listing's identity moves far slower than its builds. */
    const val TTL_MS = 24L * 60 * 60 * 1000

    /** Lookups per pass. */
    const val PER_PASS = 8

    /** Requests in flight at once — this goes to the same endpoint the search screen uses. */
    private const val PARALLEL = 4

    /** True when there is no usable answer for this package yet. */
    fun isStale(lookup: Lookup?, now: Long, ttl: Long = TTL_MS): Boolean =
        lookup == null || !UpdateScanner.isFresh(lookup.checkedAt, now, ttl)

    /**
     * The apps this pass will ask about, in the order the caller cares about, at most [PER_PASS] of
     * them. [apps] is expected with the most recently updated app first; the bound is what keeps a
     * phone holding sixty apps from opening sixty requests every time the tab opens.
     */
    fun needing(
        apps: List<LibraryEntry>,
        cache: Map<String, Lookup>,
        now: Long,
        force: Boolean = false,
        limit: Int = PER_PASS,
    ): List<LibraryEntry> = apps.filter { force || isStale(cache[it.packageName], now) }.take(limit)

    /**
     * The terms worth asking the catalogue for, in order.
     *
     * A phone labels an app the way its store page does — `Truecaller: Caller ID & Block`,
     * `Amazon Prime Video (Beta)` — while the catalogue titles it by its name. The exact label is asked
     * first, because that is the string the user is looking at; when it finds nothing the head before a
     * subtitle, a dash or a bracket is asked once more, which is the request a person would make by hand.
     * The retry is one request per miss, not a walk: the route that filters cannot page (measured in
     * round 24), so the expensive door stays closed until there is a narrower one.
     */
    internal fun searchTerms(label: String): List<String> {
        val full = label.trim()
        if (full.isEmpty()) return emptyList()
        val head = full.substringBefore(':')
            .substringBefore(" - ")
            .substringBefore(" — ")
            .substringBefore(" | ")
            .substringBefore('(')
            .trim()
        return if (head.length >= 2 && !head.equals(full, ignoreCase = true)) listOf(full, head) else listOf(full)
    }

    /**
     * The listing that *is* [packageName].
     *
     * Exact package equality is the whole filter: a search for "Spotify" returns Spotify, Spotify
     * Lite and several downloaders, and only the one whose Play listing names this package is the
     * same app. When one package has both a plain and a modded listing, the newer original version
     * wins, then the newer post.
     */
    fun pick(posts: List<PostSummary>, packageName: String, now: Long = System.currentTimeMillis()): Lookup? {
        val best = posts.filter { it.id > 0 && it.packageName == packageName }
            .reduceOrNull { a, b ->
                val newer = Updates.compare(a.latestVersion, b.latestVersion)
                if (newer > 0 || (newer == 0 && a.id > b.id)) a else b
            }
            ?: return null
        return Lookup(best.id, best.latestVersion, now)
    }

    fun readCache(context: Context): Map<String, Lookup> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = mutableMapOf<String, Lookup>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith(KEY_POST) || value !is Long) continue
            val pkg = key.removePrefix(KEY_POST)
            out[pkg] = Lookup(
                postId = value.takeIf { it > 0 },
                originalVersion = prefs.getString(KEY_VERSION + pkg, null)?.ifBlank { null },
                checkedAt = prefs.getLong(KEY_AT + pkg, 0L),
            )
        }
        return out
    }

    fun writeCache(context: Context, packageName: String, lookup: Lookup) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_POST + packageName, lookup.postId ?: 0L)
            putString(KEY_VERSION + packageName, lookup.originalVersion.orEmpty())
            putLong(KEY_AT + packageName, lookup.checkedAt)
        }
    }

    /**
     * The map a caller reads: what this pass fetched, and for every other app whatever the cache
     * already knew.
     *
     * Handing back only the fetched rows is the mistake this closes. A second visit inside the TTL has
     * nothing left to fetch, so it would answer "none of these apps is in the catalogue", and a matched
     * update would appear once and then vanish for a day.
     */
    fun answers(
        apps: List<LibraryEntry>,
        cache: Map<String, Lookup>,
        fetched: Map<String, Lookup?>,
    ): Map<String, Lookup> = apps.mapNotNull { entry ->
        (fetched[entry.packageName] ?: cache[entry.packageName])?.let { entry.packageName to it }
    }.toMap()

    /**
     * How far the budgeted sweep has actually got.
     *
     * An app searched and missed and an app never asked about produce the same screen — no row — and
     * the difference is the one a user needs. [answered] counts any cached entry, including a miss;
     * [matched] counts the ones that resolved to a listing, so [missed] is the ones the catalogue
     * provably does not carry and [pending] the ones still waiting behind the per-pass budget.
     */
    data class Coverage(val total: Int, val answered: Int, val matched: Int) {
        val pending: Int get() = (total - answered).coerceAtLeast(0)
        val missed: Int get() = (answered - matched).coerceAtLeast(0)
    }

    fun coverage(apps: List<LibraryEntry>, cache: Map<String, Lookup>): Coverage = Coverage(
        total = apps.size,
        answered = apps.count { cache.containsKey(it.packageName) },
        matched = apps.count { cache[it.packageName]?.postId != null },
    )

    /**
     * Answers for [apps], cached first and asked for second.
     *
     * [apps] arrives in the order its caller cares about; [needing] decides what this pass asks for.
     * A request that failed keeps the previous answer rather than recording a miss, so one dropped
     * connection cannot hide an app for a day.
     */
    suspend fun lookups(
        context: Context,
        apps: List<LibraryEntry>,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ): Map<String, Lookup> = coroutineScope {
        val cache = withContext(Dispatchers.IO) { readCache(context) }
        val stale = needing(apps, cache, now, force)
        val gate = Semaphore(PARALLEL)
        val fetched: Map<String, Lookup?> = stale.map { entry ->
            async {
                val first = gate.withPermit { runCatching { Api.search(entry.label) } }
                var posts = first.getOrNull()
                var failed = first.isFailure
                // A longer label is the common miss; one retry on its head is the cheap half of a
                // narrower sweep. A failed retry keeps the previous answer rather than recording a miss.
                if (posts?.let { pick(it, entry.packageName, now) } == null) {
                    searchTerms(entry.label).getOrNull(1)?.let { head ->
                        val second = gate.withPermit { runCatching { Api.search(head) } }
                        if (second.isSuccess) posts = second.getOrNull() else failed = true
                    }
                }
                val found = posts?.let { pick(it, entry.packageName, now) }
                val answer = when {
                    // A listing that names this package is worth keeping, whether or not it had one before.
                    found != null -> {
                        withContext(Dispatchers.IO) { writeCache(context, entry.packageName, found) }
                        found
                    }
                    // Nothing came back. A failed request is not a missing listing: recording a miss here
                    // would hide an app for a day because of one dropped connection.
                    failed -> cache[entry.packageName]
                    else -> {
                        val miss = Lookup(null, null, now)
                        withContext(Dispatchers.IO) { writeCache(context, entry.packageName, miss) }
                        miss
                    }
                }
                entry.packageName to answer
            }
        }.awaitAll().toMap()
        answers(apps, cache, fetched)
    }
}
