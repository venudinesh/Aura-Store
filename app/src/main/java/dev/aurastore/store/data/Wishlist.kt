package dev.aurastore.store.data

import android.content.Context
import androidx.core.content.edit
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One app kept for later.
 *
 * A wish is written down as data rather than as a route, because the thing has to survive a process
 * death and the catalogue's list is not around to rebuild it. A catalogue entry keeps its post id and
 * the few fields a row draws; a source entry keeps the whole [SourceApp] it was opened from, since a
 * site's key is the page URL and nothing else can look it up again.
 */
data class WishEntry(
    val key: String,
    val kind: Kind,
    val title: String,
    val iconUrl: String? = null,
    /** The publisher for a catalogue post, the app's own subtitle for a source. */
    val subtitle: String? = null,
    val version: String? = null,
    val size: String? = null,
    val postId: Long? = null,
    val source: SourceId? = null,
    val sourceKey: String? = null,
    val pageUrl: String? = null,
    val cataloguePostId: Long? = null,
    val addedAt: Long = 0L,
) {

    enum class Kind { POST, SOURCE }

    /** The second line a row draws, in the order a storefront reads it. */
    fun metaLine(): String = buildList {
        if (kind == Kind.SOURCE) source?.let { add(it.label) }
        subtitle?.takeIf { it.isNotBlank() }?.let(::add)
        version?.takeIf { it.isNotBlank() }?.let(::add)
        this@WishEntry.size?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")

    /** This wish as the page a source opens, or null when it did not come from one. */
    fun toSourceApp(): SourceApp? {
        val id = source ?: return null
        val lookup = sourceKey?.takeIf { it.isNotBlank() } ?: return null
        return SourceApp(
            source = id,
            key = lookup,
            title = title,
            version = version,
            size = size,
            subtitle = subtitle,
            iconUrl = iconUrl,
            pageUrl = pageUrl,
            cataloguePostId = cataloguePostId,
        )
    }
}

/**
 * The wishlist, in one file.
 *
 * The list is a `StateFlow` for the same reason the settings are: the heart in a page's top bar and
 * the row on the Wishlist tab are two readers of one fact, and a toggle has to reach both without a
 * refresh. Every write replaces the whole stored array; the list is a handful of rows, and one key
 * rewritten atomically is simpler to reason about than a diff.
 */
object Wishlist {

    private const val PREFS = "aurastore_wishlist"
    private const val KEY_ENTRIES = "entries"
    private const val VERSION = 1

    private val _entries = MutableStateFlow<List<WishEntry>>(emptyList())
    val entries: StateFlow<List<WishEntry>> = _entries.asStateFlow()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _entries.value = WishCodec.decode(prefs.getString(KEY_ENTRIES, null))
    }

    fun postKey(postId: Long): String = "post:$postId"

    fun sourceKey(app: SourceApp): String = "source:${app.source.name}:${app.key}"

    fun isWished(key: String): Boolean = _entries.value.any { it.key == key }

    fun post(
        postId: Long,
        title: String,
        iconUrl: String?,
        subtitle: String?,
        size: String?,
        now: Long = System.currentTimeMillis(),
    ): WishEntry = WishEntry(
        key = postKey(postId),
        kind = WishEntry.Kind.POST,
        title = title,
        iconUrl = iconUrl,
        subtitle = subtitle,
        size = size,
        postId = postId,
        addedAt = now,
    )

    fun source(app: SourceApp, now: Long = System.currentTimeMillis()): WishEntry = WishEntry(
        key = sourceKey(app),
        kind = WishEntry.Kind.SOURCE,
        title = app.title,
        iconUrl = app.iconUrl,
        subtitle = app.subtitle,
        version = app.version,
        size = app.size,
        source = app.source,
        sourceKey = app.key,
        pageUrl = app.pageUrl,
        cataloguePostId = app.cataloguePostId,
        addedAt = now,
    )

    /** Adds the entry, or drops the one already saved under its key. Answers whether it was added. */
    fun toggle(context: Context, entry: WishEntry): Boolean {
        load(context)
        val saved = _entries.value.any { it.key == entry.key }
        commit(context, toggled(_entries.value, entry))
        return !saved
    }

    /** The list rule, pure so a test can pin "newest first" without a preferences file. */
    internal fun toggled(entries: List<WishEntry>, entry: WishEntry): List<WishEntry> =
        if (entries.any { it.key == entry.key }) {
            entries.filterNot { it.key == entry.key }
        } else {
            listOf(entry) + entries
        }

    fun remove(context: Context, key: String) {
        load(context)
        commit(context, _entries.value.filterNot { it.key == key })
    }

    private fun commit(context: Context, next: List<WishEntry>) {
        _entries.value = next
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_ENTRIES, WishCodec.encode(next)) }
    }

    /** The wire form, pure so a JVM test can round-trip it without a handset. */
    internal object WishCodec {

        fun encode(entries: List<WishEntry>): String {
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("v", VERSION)
                        .put("key", entry.key)
                        .put("kind", entry.kind.name)
                        .put("title", entry.title)
                        .putOpt("iconUrl", entry.iconUrl)
                        .putOpt("subtitle", entry.subtitle)
                        .putOpt("version", entry.version)
                        .putOpt("size", entry.size)
                        .putOpt("postId", entry.postId)
                        .putOpt("source", entry.source?.name)
                        .putOpt("sourceKey", entry.sourceKey)
                        .putOpt("pageUrl", entry.pageUrl)
                        .putOpt("cataloguePostId", entry.cataloguePostId)
                        .put("addedAt", entry.addedAt),
                )
            }
            return array.toString()
        }

        /** Whatever is readable; a bad row is dropped, never allowed to take the list with it. */
        fun decode(raw: String?): List<WishEntry> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            val out = ArrayList<WishEntry>(array.length())
            for (index in 0 until array.length()) {
                val o = array.optJSONObject(index) ?: continue
                val kind = runCatching {
                    WishEntry.Kind.valueOf(o.optString("kind"))
                }.getOrNull() ?: continue
                val key = o.stringOrNull("key") ?: continue
                val title = o.stringOrNull("title") ?: continue
                // A file edited by hand can repeat a key; the flow is a set of wishes, not a log.
                if (out.any { it.key == key }) continue
                out += WishEntry(
                    key = key,
                    kind = kind,
                    title = title,
                    iconUrl = o.stringOrNull("iconUrl"),
                    subtitle = o.stringOrNull("subtitle"),
                    version = o.stringOrNull("version"),
                    size = o.stringOrNull("size"),
                    postId = o.longOrNull("postId"),
                    source = o.stringOrNull("source")?.let { name ->
                        runCatching { SourceId.valueOf(name) }.getOrNull()
                    },
                    sourceKey = o.stringOrNull("sourceKey"),
                    pageUrl = o.stringOrNull("pageUrl"),
                    cataloguePostId = o.longOrNull("cataloguePostId"),
                    addedAt = o.optLong("addedAt", 0L),
                )
            }
            return out
        }

        private fun JSONObject.stringOrNull(name: String): String? =
            if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

        private fun JSONObject.longOrNull(name: String): Long? =
            if (!has(name) || isNull(name)) null else optLong(name).takeIf { it != 0L }
    }
}
