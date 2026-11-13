package dev.aurastore.store.data.sources

import androidx.annotation.StringRes
import dev.aurastore.store.R
import dev.aurastore.store.data.SecureTransport
import dev.aurastore.store.data.codePoint
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/**
 * Additional places an APK can come from, alongside the catalogue.
 *
 * Every adapter here reads its site directly: its search endpoint, its app pages, and the exact
 * request its storage host needs. The catalogue's own API sits beside them, and no adapter is a
 * reader of somebody else's aggregate feed.
 *
 * Every adapter resolves to a **direct file URL** before the user taps Download, so the existing
 * engine (hash, pause/resume, install) works unchanged. Where a storage host checks the referer,
 * [dev.aurastore.store.data.Hosts] already knows the site it expects.
 */
/**
 * [label] and [site] stay literals: they are a brand and a URL, and neither is a sentence.
 * [note] says what the source *is*, so it is a resource.
 */
enum class SourceId(val label: String, val site: String, @StringRes val note: Int) {
    LITEAPKS("LiteAPKs", "https://liteapks.com/", R.string.source_note_liteapks),
    NINEMOD("9MOD", "https://9mod.com/", R.string.source_note_ninemod),
    FIVEPLAY("5play", "https://5play.org/en/", R.string.source_note_fiveplay),
    GETMODSAPK("GetModsAPK", "https://getmodsapk.com/", R.string.source_note_getmodsapk),
    AN1("AN1", "https://an1.com/", R.string.source_note_an1),
    APKVISION("ApkVision", "https://apkvision.org/", R.string.source_note_apkvision),
}

/**
 * One of a source's own categories.
 *
 * Every catalogue publishes its own way of grouping: the API has terms with ids, the WordPress sites
 * have a category taxonomy, the DLE sites have a games/apps split, and one source has no grouping at
 * all. The id is the source's own handle — a term id, a slug, a path — and the url is where the
 * source lists that category's apps, when it has such a place.
 */
data class SourceCategory(
    val source: SourceId,
    /** The source's own handle for the term. Never shown to a person; used to ask for its apps. */
    val id: String,
    val name: String,
    /** How many apps the source says the term holds, when it says. */
    val count: Int? = null,
    /** Where the source lists this category's apps. */
    val url: String? = null,
)

/**
 * One app as a source sees it. [key] is whatever that source needs to look the files up again
 * (a post id for the catalogue, a page URL for the sites), so nothing is re-searched.
 */
data class SourceApp(
    val source: SourceId,
    val key: String,
    val title: String,
    val version: String? = null,
    val size: String? = null,
    val subtitle: String? = null,
    val iconUrl: String? = null,
    val pageUrl: String? = null,
    /** Set when the row also exists in the catalogue, so the normal app page can open. */
    val cataloguePostId: Long? = null,
)

/** A downloadable artefact: already resolved to a file URL, with the label its page showed. */
data class SourceFile(
    val label: String,
    val url: String,
    val size: String? = null,
    val note: String? = null,
    /** Published digest, when the source provides one. */
    val sha256: String? = null,
)

/**
 * One catalogue. Both calls do blocking HTTP and HTML parsing, so a caller must dispatch them to
 * `Dispatchers.IO` — the UI keeps its main thread for composition.
 *
 * The three members below are what makes a source a first-class part of the storefront rather than a
 * corner of the search screen: the newest apps it publishes, the categories it groups them under, and
 * the apps in one of those categories. A source that publishes no grouping of its own answers an empty
 * list, and the screens above leave the row out rather than inventing one.
 */
interface Source {
    val id: SourceId
    suspend fun search(query: String): List<SourceApp>
    suspend fun files(app: SourceApp): List<SourceFile>

    /** The source's newest apps, in the order the source itself lists them. */
    suspend fun latest(): List<SourceApp> = emptyList()

    /** The source's own categories, in the source's own order. */
    suspend fun categories(): List<SourceCategory> = emptyList()

    /** The apps in one of [source]'s own categories. */
    suspend fun appsIn(category: SourceCategory): List<SourceApp> = emptyList()

    /**
     * A term, asked the way a full-text index reads it.
     *
     * Every site here matches words. *Spiderman* is one word to an index, while the titles it should
     * find are written *Spider-Man*, *Spider Man* or *Ultimate Spider-Man*; the same goes for
     * *candycrush* and *freefire*, which people type without the space the site's index has. So the
     * exact term is asked first and, when it comes back thin, [searchRelaxations] asks again with the
     * word split or its tail trimmed. Both are capped, so a common term costs one request and a rare
     * one costs at most four.
     *
     * The exact answer's failure is the source's to report: only the relaxed calls swallow theirs.
     */
    suspend fun searchRelaxed(query: String): List<SourceApp> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val variants = searchRelaxations(term)
        val rows = LinkedHashMap<String, SourceApp>()
        search(term).forEach { rows[it.key] = it }
        for (variant in variants) {
            if (rows.size >= RELAX_AFTER) break
            runCatching { search(variant) }.getOrDefault(emptyList()).forEach { rows.putIfAbsent(it.key, it) }
        }
        // The rows that carry the term in their own name are the ones the person asked for; a site's
        // loose matches keep their order behind them.
        val needles = listOf(term) + variants
        val (named, rest) = rows.values.partition { app -> needles.any { app.title.contains(it, true) } }
        return (named + rest).take(SOURCE_ROWS)
    }
}

/** Enough rows that a source has clearly understood the term, so nothing is asked again. */
private const val RELAX_AFTER = 8

/** The most rows one source contributes to one list. */
private const val SOURCE_ROWS = 40

/**
 * The other ways to write a term a site's index did not recognise.
 *
 * A run-together word is tried as two words at its middle (*candycrush* → *candy crush*), then with
 * its tail trimmed to the part that is likely the app's own name (*spiderman* → *spider*). A title
 * with words in it loses its last word, which is where the catalogue and the sites disagree most
 * (*Spider-Man Unlimited* → *Spider-Man*). Every variant is at least six characters, so a trim can
 * never fall back to a word as common as *free*.
 */
internal fun searchRelaxations(term: String): List<String> {
    val clean = term.trim()
    val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    val variants = LinkedHashSet<String>()
    if (words.size > 1) {
        val shorter = words.dropLast(1).joinToString(" ")
        if (shorter.length >= 5) variants += shorter
    } else if (clean.length >= 8) {
        val middle = clean.length / 2
        if (middle >= 4) variants += clean.substring(0, middle) + " " + clean.substring(middle)
        for (share in doubleArrayOf(0.65, 0.45)) {
            val length = floor(clean.length * share).toInt()
                .coerceAtLeast(6)
                .coerceAtMost(clean.length - 1)
            variants += clean.substring(0, length)
        }
    }
    variants -= clean
    return variants.toList()
}

object Sources {

    /** Order the pickers show; the catalogue first because it is the one this app is built around. */
    val all: List<Source> = listOf(
        LiteapksSource,
        NineModSource,
        FivePlaySource,
        GetModsApkSource,
        An1Source,
        ApkVisionSource,
    )

    fun byId(id: SourceId): Source = all.first { it.id == id }

    /**
     * The catalogues the user left switched on. An empty set really does mean "none": the default
     * "everything on" lives in [dev.aurastore.store.data.Settings], which is where the absence of a
     * stored preference is known.
     */
    fun enabled(enabled: Set<String>): List<Source> = all.filter { it.id.name in enabled }
}

/** Shared HTTP for the site adapters. Small on purpose: these are plain HTML pages and JSON. */
internal object Web {

    const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.6367.113 Mobile Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addNetworkInterceptor(SecureTransport.HttpsOnly)
            .build()
    }

    fun get(url: String, referer: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} on $url")
            return response.body.string()
        }
    }

    /** Form POST, used by 9MOD's WordPress AJAX endpoint. */
    fun post(url: String, body: String, referer: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .apply { if (referer != null) header("Referer", referer) }
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} on $url")
            return response.body.string()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Makes a relative link absolute against [base]; leaves absolute links alone. */
    fun absolute(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val root = base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/')
        return if (href.startsWith("/")) root + href else root + "/" + href
    }

    /** Text of an HTML fragment with tags removed and entities decoded, for link labels. */
    fun text(html: String): String = html
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { decodeEntities(it) }
}

/**
 * Decodes HTML entities once. Shares the model layer's numeric-entity decode so a title scraped from a
 * page reads the same as one from the API — including an emoji written as a code point above the BMP.
 */
internal fun decodeEntities(value: String): String = value
    .replace(Regex("&#(\\d{1,7});")) { codePoint(it.groupValues[1], 10, it.value) }
    .replace(Regex("&#[xX]([0-9a-fA-F]{1,6});")) { codePoint(it.groupValues[1], 16, it.value) }
    .replace("&quot;", "\"")
    .replace("&#039;", "'")
    .replace("&apos;", "'")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .trim()

/** Turns `%20`-style escapes in a scraped string back into the characters they stand for. */
internal fun percentDecode(value: String): String =
    Regex("%([0-9A-Fa-f]{2})").replace(value) { m ->
        runCatching { m.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(m.value)
    }

/** "8255-spotify-music-mod-apk" -> "Spotify Music" — the fallback when a card has no clean label. */
internal fun titleFromSlug(slug: String): String = slug
    .replace(Regex("^[0-9]+-"), "")
    .replace(Regex("-(mod|apk|premium|pro|free|download|apks)$"), "")
    .replace('-', ' ')
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

/** "role-playing" -> "Role Playing" — the same words, only a category's name. */
internal fun labelFromSlug(slug: String): String = slug
    .replace('-', ' ')
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
