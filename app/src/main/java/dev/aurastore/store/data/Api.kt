package dev.aurastore.store.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin client for the catalogue API.
 *
 * Note on the ABI query: the server filters download files by ABI when a post ships
 * per-ABI artefacts, and returns the universal file otherwise.
 */
object Api {

    const val BASE = "https://liteapks.com/wp-json/v2/"

    /** Rows per ask: enough to fill a screen, small enough that the first page is one cheap round trip. */
    const val SEARCH_PAGE_SIZE = 20

    /** Everything a result line renders, out of what WordPress puts on a post. */
    private const val SEARCH_FIELDS = "id,title,thumbnail,size,mod,version,rating_avg,views,modified,class_list"

    /**
     * Rows per ask from the listing route.
     *
     * This route cannot be made cheaper per row: a post comes back with its `content`, `versions`, `tabs`
     * and `gallery` — on the view-ordered set the top charts page through that measured ~9 kB a row on
     * 28 Oct (5 rows = 68 kB, 20 = 235 kB, 50 = 444 kB, 100 = 807 kB, 1 000 = 6.4 MB), and the `_fields`
     * parameter that trims the standard namespace to 160 B a row does not exist here: asking `v2/posts`
     * for it answers `200` with `[]`. So the only saving available is to ask for fewer rows and ask again
     * when the reader needs them — and the only cost of asking for more is the first page.
     *
     * It is 50 because of [LIST_MAX_PAGES] and the catalogue's own shape: a walk reaches
     * [LIST_REACH] rows and no further, and every leaf term in the tree is shorter than that. Measured on
     * 28 Oct, the deepest one is `Simulation` at exactly 2 250 rows, and a leaf's listing is *exactly* its
     * published `count`: a short page is the ordering running out, and at 100 rows a page `Tools` (count
     * 1 257) gives 57 rows on page 13, `Personalization` (1 210) gives 10, `Simulation` (2 250) gives 50 on
     * page 23 and nothing on page 24, and at 20 a page `Photography` (433) gives 13 on page 22 and nothing
     * on page 23. At 20 rows a page the same walk stopped at 1 000 and called that the end of a 1 257-row
     * term; at 50 every category the picker offers can be read to its last row, which is what `list_end`
     * claims. Parents cannot — `Games` lists 13 285 rows, the whole subtree, against a `count` of 5 — and
     * neither can the unfiltered charts; those are what [LIST_REACH] is there for the footer to name.
     */
    const val LIST_PAGE_SIZE = 50

    /**
     * The highest page number `v2/posts` answers, measured on the live catalogue on 28 Oct.
     *
     * Page 51 comes back as an empty list at every page size tried (`10`, `20`, `30`, `50`, `100`, `200`),
     * while page 50 answers in full at all of them — 50 rows at `limit=50`, 200 at `limit=200`, and the
     * page-50 tail of one ordering is the same row however the pages are cut. So the cutoff is the page
     * number, not the offset, and a walk of [LIST_PAGE_SIZE]-sized pages can reach [LIST_REACH] rows and no
     * further. Nothing in the answer says which of the two ends the reader has arrived at, which is what
     * `ListPager.stoppedAtCeiling` is for.
     */
    const val LIST_MAX_PAGES = 50

    /** How far a listing walk goes before [LIST_MAX_PAGES] closes it, in rows. */
    const val LIST_REACH = LIST_MAX_PAGES * LIST_PAGE_SIZE

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addNetworkInterceptor(SecureTransport.HttpsOnly)
            .build()
    }

    /**
     * Every ABI this device can run, most preferred first (`arm64-v8a`, `armeabi-v7a`, ...).
     *
     * The first entry is what the catalogue is asked for; the full list is what a published file has
     * to match before it is offered, since a phone may run more than one.
     */
    val deviceAbis: List<String>
        get() = Build.SUPPORTED_ABIS?.toList()?.filter { it.isNotBlank() }.orEmpty()
            .ifEmpty { listOf("arm64-v8a") }

    /** Device ABI as understood by the catalogue (`arm64-v8a`, `armeabi-v7a`, `x86_64`, ...). */
    val deviceAbi: String
        get() = deviceAbis.first()

    private fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(call(BASE, path, query).text)

    /** A body, plus the two headers the standard WordPress namespace puts the size of a list in. */
    internal class Raw(val text: String, val total: Int?, val totalPages: Int?)

    private fun call(base: String, path: String, query: Map<String, String>): Raw {
        val url = (base + path.trimStart('/')).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> if (v.isNotBlank()) addQueryParameter(k, v) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "okhttp/3.12.13") // the endpoint's WAF rejects unusual agents
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            return Raw(
                body,
                response.header("x-wp-total")?.toIntOrNull(),
                response.header("x-wp-totalpages")?.toIntOrNull(),
            )
        }
    }

    internal fun payload(json: JSONObject): JSONObject {
        val status = json.optInt("status", 0)
        if (status != 0 && status != 200) {
            throw IOException(json.optString("message", "API error $status"))
        }
        return json.optJSONObject("data") ?: JSONObject()
    }

    /**
     * A single listing, or a clear refusal. `posts/{unknown}` answers `200` with `status: 200` and
     * `data: null`, so nothing in the status distinguishes a listing the site does not have from one
     * that parsed to nothing — and an empty detail page reads like a broken app.
     */
    internal fun payloadPost(json: JSONObject, id: Long): JSONObject {
        val data = payload(json)
        if (data.length() == 0) throw IOException("Post $id is not in the catalogue.")
        return data
    }

    private fun payloadArray(json: JSONObject): org.json.JSONArray {
        payload(json) // validates status
        return json.optJSONArray("data") ?: org.json.JSONArray()
    }

    suspend fun home(): Home = withContext(Dispatchers.IO) { Home.parse(payload(get("home"))) }

    suspend fun categories(): List<Category> = withContext(Dispatchers.IO) {
        Category.parseList(payload(get("categories")))
    }

    suspend fun posts(query: Map<String, String>): List<PostSummary> = withContext(Dispatchers.IO) {
        val data = payload(get("posts", query))
        val arr = data.optJSONArray("posts") ?: org.json.JSONArray()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { PostSummary.parse(it) } }
    }

    /**
     * One page of a listing, and whether a next one is worth asking for.
     *
     * `v2/posts` really does page, which is more than the search route manages: measured on the live
     * catalogue on 28 Oct, page 1 and page 2 of `tax_ids=3&order_by=post_views` share no id, and the two
     * together are exactly the first ten rows of the same query asked at `limit=20` — so a page is a slice
     * of one ordering, not a reshuffle of it.
     *
     * What the route never says is how long that ordering is. `X-WP-Total` and `X-WP-TotalPages` belong to
     * the standard namespace, and this one answers with neither, so a short page is the only end a listing
     * can give of itself. It can also give an end it does not mean: the window is on the **page number** and
     * closes at [LIST_MAX_PAGES] whatever the page size — measured on 28 Oct, `page=51` answers an empty
     * list at `limit` 10, 20, 30, 50, 100 and 200 alike, while `page=50` answers in full at every one of
     * them, reaching 2 500 rows deep at `limit=50` and 10 000 at `limit=200`. So [LIST_REACH] is the far end
     * of a walk, and a listing longer than that — a parent category, or the charts with no category on them
     * — is one the reader can only reach the beginning of.
     */
    data class PostPage(val items: List<PostSummary>, val page: Int, val hasMore: Boolean) {

        internal companion object {
            fun of(items: List<PostSummary>, page: Int, pageSize: Int): PostPage =
                PostPage(items, page.coerceAtLeast(1), items.size >= pageSize)
        }
    }

    /**
     * The next page of [posts].
     *
     * The page size falls back to whatever the query already asks for, because a module's own `limit` is
     * how big the designer wanted one screen of that rail to look; overwriting it with a listing default
     * would make a 12-row rail serve 50 and change what the row means while leaving its title alone.
     *
     * The bound is this app's own policy, not the route's: `v2/posts` answered a 1 000-row page (6.4 MB of
     * JSON, 11 s) without complaining. [LIST_PAGE_SIZE] is the largest page anything here asks for, because
     * a page that size is what makes every leaf term walkable to its end.
     */
    suspend fun postPage(
        query: Map<String, String>,
        page: Int,
        pageSize: Int = query["limit"]?.toIntOrNull() ?: LIST_PAGE_SIZE,
    ): PostPage = withContext(Dispatchers.IO) {
        val n = page.coerceAtLeast(1)
        val size = pageSize.coerceIn(1, LIST_PAGE_SIZE)
        PostPage.of(
            posts(query + mapOf("limit" to size.toString(), "page" to n.toString())),
            n,
            size,
        )
    }

    suspend fun post(id: Long, abi: String = deviceAbi): PostDetail = withContext(Dispatchers.IO) {
        PostDetail.parse(payloadPost(get("posts/$id", mapOf("abi" to abi)), id))
    }

    /**
     * Instant search. The live backend reads `s` (the official client's newer build sends `q`), so
     * both are sent: whichever the deployment understands wins, and neither is harmful.
     */
    suspend fun search(query: String): List<PostSummary> = withContext(Dispatchers.IO) {
        val arr = payloadArray(get("search", mapOf("s" to query, "q" to query)))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { PostSummary.parse(it) } }
    }

    /**
     * One page of every post that matches a term.
     *
     * @param total what the deployment says matches in all, or null when it did not say.
     * @param hasMore whether a page after this one is worth asking for.
     */
    data class SearchPage(val items: List<PostSummary>, val total: Int?, val page: Int, val hasMore: Boolean)

    /**
     * The searchable catalogue, one page at a time.
     *
     * This is the WordPress namespace rather than the plugin's own, because the plugin cannot page a
     * search: `v2/search` answers the same handful of instant matches whatever `limit`, `page`, `offset`
     * or `per_page` says, and `v2/posts` reads `limit`/`page` and drops a `search` on the floor, so asking
     * it for one term's second page of results gets the second page of the whole catalogue. Only
     * `wp/v2/posts?search=` both matches and pages, and it is the only place the size of the answer
     * exists at all — the headers `X-WP-Total` and `X-WP-TotalPages`.
     *
     * `_fields` is what makes asking cheap: a post as WordPress writes it carries its rendered content —
     * about 3.2 kB per row, almost all of it a description no result line shows — while the ten fields a
     * summary needs come to about 160 B.
     */
    suspend fun searchPage(term: String, page: Int, pageSize: Int = SEARCH_PAGE_SIZE): SearchPage =
        withContext(Dispatchers.IO) {
            // `call` drops blank parameters, and `wp/v2/posts` with no search is the whole catalogue.
            require(term.isNotBlank()) { "A blank term would page every post instead of matching one." }
            val raw = call(
                wpBase(), "posts",
                mapOf(
                    "search" to term,
                    "per_page" to pageSize.toString(),
                    "page" to page.coerceAtLeast(1).toString(),
                    "orderby" to "relevance",
                    "_fields" to SEARCH_FIELDS,
                ),
            )
            parseSearchPage(raw, page.coerceAtLeast(1), pageSize)
        }

    /**
     * The listing the standard namespace sends, and where the paging stands after it.
     *
     * Split out because nothing here needs a network: a page that came back short is the last page even
     * when the deployment stripped the headers, and a page number past the end arrives as an error object
     * rather than an empty array.
     */
    internal fun parseSearchPage(raw: Raw, page: Int, pageSize: Int): SearchPage {
        if (raw.text.trimStart().startsWith("{")) {
            val error = JSONObject(raw.text)
            throw IOException(error.optString("message").ifBlank { "Search failed" })
        }
        val arr = org.json.JSONArray(raw.text)
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { PostSummary.parseListing(it) } }
        val hasMore = raw.totalPages?.let { page < it } ?: (items.size >= pageSize)
        return SearchPage(items, raw.total, page, hasMore)
    }

    /** The same deployment's standard WordPress namespace, off the base the catalogue is configured with. */
    internal fun wpBase(base: String = BASE): String = base.removeSuffix("v2/") + "wp/v2/"

    /**
     * Category screen payload: the term plus a server-defined module tree (see [CategoryPage]).
     * This is the endpoint the official app's CategoryActivity uses, rather than a flat post query.
     */
    suspend fun category(id: Long): CategoryPage = withContext(Dispatchers.IO) {
        CategoryPage.parse(payload(get("categories/$id")))
    }

    /**
     * The client's own update check (`v2/get_latest_version?dev=`). The deployment currently answers
     * with an empty version, which simply means "no update" — the same thing the official app shows.
     */
    suspend fun latestVersion(dev: String = "android"): LatestVersion = withContext(Dispatchers.IO) {
        LatestVersion.parse(payload(get("get_latest_version", mapOf("dev" to dev))))
    }

    /** The "Similar apps" rail on a detail page. Returns a bare array, not the usual envelope payload object. */
    suspend fun recommended(id: Long): List<PostSummary> = withContext(Dispatchers.IO) {
        val arr = payloadArray(get("posts/recommended/$id"))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { PostSummary.parse(it) } }
    }

    suspend fun hotSearch(): List<String> = withContext(Dispatchers.IO) {
        val arr = payloadArray(get("hot_search"))
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
    }
}
