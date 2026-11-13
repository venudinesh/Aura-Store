package dev.aurastore.store.install

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * What one catalogue post offered the last time it was checked, and when that happened.
 *
 * [versionLabel] can be null: the check ran and found nothing installable, which is an answer worth
 * caching too — otherwise a post with no files is re-requested on every visit.
 */
data class Checked(val versionLabel: String?, val checkedAt: Long)

/**
 * The update check, with a cache in front of it.
 *
 * The catalogue has no batch endpoint — there is no "which of these 30 packages have updates" call —
 * so the only way to answer is one `GET posts/{id}` per installed app. Un-cached, that meant opening
 * the You tab fired a request per app every single time, which is slow, burns the user's data, and is
 * the kind of traffic a shared endpoint eventually refuses. This keeps each verdict for
 * [TTL_MS] and refreshes only what has gone stale, with the requests bounded so one visit cannot
 * occupy a dozen sockets.
 *
 * The decision functions here take the clock as a parameter and touch no Android types, so the TTL
 * rule is unit-testable ([dev.aurastore.store.install.UpdatesTest] covers it).
 */
object UpdateScanner {

    /** How long a verdict stays usable: six hours, the same rhythm Play's own check runs on. */
    const val TTL_MS = 6L * 60 * 60 * 1000

    /** How many posts may be in flight at once. */
    private const val PARALLEL = 4

    private const val PREFS = "aurastore_updates"
    private const val KEY_LABEL = "label:"
    private const val KEY_AT = "at:"

    fun isFresh(checkedAt: Long, now: Long, ttl: Long = TTL_MS): Boolean =
        checkedAt in 1..now && now - checkedAt < ttl

    /**
     * Which of [postIds] still need a request. A post that was never checked, or whose check has aged
     * out, is fetched; a fresh verdict is used as it stands.
     */
    fun needingFetch(
        cache: Map<Long, Checked>,
        postIds: List<Long>,
        now: Long,
        ttl: Long = TTL_MS,
        force: Boolean = false,
    ): List<Long> =
        if (force) postIds.distinct()
        else postIds.filter { id -> cache[id]?.let { isFresh(it.checkedAt, now, ttl) } != true }.distinct()

    fun readCache(context: Context): Map<Long, Checked> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = mutableMapOf<Long, Checked>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith(KEY_LABEL) || value !is String) continue
            val id = key.removePrefix(KEY_LABEL).toLongOrNull() ?: continue
            out[id] = Checked(value.ifBlank { null }, prefs.getLong(KEY_AT + id, 0L))
        }
        return out
    }

    fun writeCache(context: Context, postId: Long, versionLabel: String?, now: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LABEL + postId, versionLabel.orEmpty())
            putLong(KEY_AT + postId, now)
        }
    }

    /** Forget one verdict, so the next scan re-reads that post (an install does this for itself). */
    fun forget(context: Context, postId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_LABEL + postId)
            remove(KEY_AT + postId)
        }
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }

    /**
     * How many installed apps the last check found an update for. Held here rather than in the
     * screen's state so the You tab's badge, the background worker and the You list all read the same
     * number — the whole point of the badge is that it is visible when the list is not.
     */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    fun reportPending(count: Int) {
        _pending.value = count
    }

    /**
     * The newest version label of every post in [postIds], reading through the cache.
     *
     * A post whose request fails keeps whatever verdict was cached before — a network hiccup must not
     * turn a known update into "no update", and must not look like the catalogue stopped carrying it.
     */
    suspend fun labels(
        context: Context,
        postIds: List<Long>,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ): Map<Long, String?> = coroutineScope {
        val cache = withContext(Dispatchers.IO) { readCache(context) }
        val stale = needingFetch(cache, postIds, now, force = force)
        val gate = Semaphore(PARALLEL)
        val fetched = stale.map { id ->
            async {
                val outcome = gate.withPermit {
                    runCatching { withContext(Dispatchers.IO) { QuickInstall.pick(id) } }
                }
                // A post that genuinely carries no installable file is a verdict worth caching; a
                // request that failed is not, because caching it would hide a real update for six
                // hours on a hiccup and read as "up to date".
                if (!outcome.isFailure) {
                    withContext(Dispatchers.IO) { writeCache(context, id, outcome.getOrNull()?.versionLabel, now) }
                }
                id to outcome.getOrNull()?.versionLabel
            }
        }.awaitAll().toMap()
        postIds.associateWith { id -> fetched[id] ?: cache[id]?.versionLabel }
    }
}
