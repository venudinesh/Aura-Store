package dev.aurastore.store.data.sources

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** How many rows a site's search draws on one page, where it draws a fixed number. */
private const val SEARCH_PAGE = 12

/** How many rows one site's search contributes before the list stops asking for pages. */
private const val SEARCH_ROWS = 24

/**
 * 9MOD — WordPress. Search is the REST search route; the app page links a download page, whose
 * per-file pages carry the artefact metadata in a base64 blob and mint the real link over
 * `admin-ajax.php`:
 *
 * ```
 * POST action=app_get_nonce&type=download                 -> { data: { nonce } }
 * POST action=app_get_download&post_id&file_index&nonce    -> { data: { url } }
 * ```
 *
 * The minted URL is on `cloud.9mod.space`, which needs the same pair as LiteAPKs' own storage host —
 * the `btoa(btoa(now+3h))` token **and** a referer from either catalogue (verified 22 Oct 2026:
 * referer alone 403, token alone 403, token + referer 200). The minted link arrives with its token,
 * so in practice only the referer has to be added. Both are handled by
 * [dev.aurastore.store.data.Hosts].
 */
object NineModSource : Source {

    override val id = SourceId.NINEMOD
    private const val BASE = "https://9mod.com"
    private const val REST = "$BASE/wp-json/wp/v2"

    override suspend fun search(query: String): List<SourceApp> {
        val json = Web.get("$BASE/wp-json/wp/v2/search?search=${Web.encode(query)}&per_page=40")
        // The search route names the post but not its picture; the posts route names the featured
        // media, and the media route hands back the file. Both are one small request for the page.
        val icons = iconsForPosts(json)
        return parseSearch(json, icons).take(40)
    }

    /**
     * The site's newest posts, read through the same namespace its own search uses.
     *
     * The site is behind a bot wall that answers a desktop browser with 403, so every call here is
     * allowed to fail: a source that cannot be read leaves its section empty rather than taking the
     * screen with it.
     */
    override suspend fun latest(): List<SourceApp> {
        val json = runCatching {
            Web.get("$REST/posts?per_page=12&orderby=date&_fields=id,title,link,featured_media")
        }.getOrNull().orEmpty()
        return parseRestPosts(json, iconsForPosts(json))
    }

    /**
     * The site's front page, as far as its API publishes it: one *Latest* row of its newest posts.
     *
     * 9MOD's home draws a *Featured* strip too, but that strip is behind the bot wall this machine
     * meets; the posts route still answers, and its order is the site's own order of publishing.
     */
    override suspend fun shelves(): List<SourceShelf> {
        val json = runCatching {
            Web.get("$REST/posts?per_page=20&orderby=date&_fields=id,title,link,featured_media")
        }.getOrNull().orEmpty()
        val apps = parseRestPosts(json, iconsForPosts(json))
        return if (apps.isEmpty()) emptyList() else listOf(SourceShelf("Latest", apps, ShelfMore.All))
    }

    /** The site's own category terms. */
    override suspend fun categories(): List<SourceCategory> = parseRestCategories(
        runCatching { Web.get("$REST/categories?per_page=30&_fields=id,name,count,slug") }
            .getOrNull().orEmpty(),
    )

    /** The posts of one term, one WordPress page at a time. */
    override suspend fun appsPage(category: SourceCategory, page: Int): SourcePage {
        val id = category.id.toLongOrNull() ?: return SourcePage(emptyList())
        val perPage = 20
        val json = runCatching {
            Web.get("$REST/posts?categories=$id&per_page=$perPage&page=$page&_fields=id,title,link,featured_media")
        }.getOrNull().orEmpty()
        val apps = parseRestPosts(json, iconsForPosts(json))
        // A page past the last one is a 400, which reads as an empty answer here.
        return SourcePage(apps, hasMore = apps.size >= perPage)
    }

    /** The whole posts route, newest first, one WordPress page at a time. */
    override suspend fun allAppsPage(page: Int): SourcePage {
        val perPage = 20
        val n = page.coerceAtLeast(1)
        val json = runCatching {
            Web.get("$REST/posts?per_page=$perPage&page=$n&orderby=date&_fields=id,title,link,featured_media")
        }.getOrNull().orEmpty()
        val apps = parseRestPosts(json, iconsForPosts(json))
        return SourcePage(apps, hasMore = apps.size >= perPage)
    }

    /**
     * Pure: `[{id, title:{rendered}, link, featured_media}]` from the WordPress posts route.
     *
     * [icons] is keyed by post id; a post the media route had nothing for keeps a null icon and the
     * row draws its placeholder, which is the same answer as a site that publishes no picture.
     */
    fun parseRestPosts(json: String, icons: Map<Long, String> = emptyMap()): List<SourceApp> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("link", "").ifBlank { return@mapNotNull null }
            SourceApp(
                source = SourceId.NINEMOD,
                key = url,
                title = decodeEntities(o.optJSONObject("title")?.optString("rendered", "").orEmpty())
                    .ifBlank { titleFromSlug(url.substringAfterLast('/')) },
                iconUrl = icons[o.optLong("id", 0L)],
                pageUrl = url,
            )
        }
    }.getOrDefault(emptyList())

    /**
     * The picture each post in [postsJson] published, keyed by post id.
     *
     * The search route carries no `featured_media` field, so the ids it names are asked for once
     * through the posts route; the featured ids are then asked for once through the media route.
     * A failure at either step leaves the rows without pictures rather than without rows.
     */
    private suspend fun iconsForPosts(postsJson: String): Map<Long, String> = runCatching {
        val array = JSONArray(postsJson)
        val featured = LinkedHashMap<Long, Long>()
        val missing = ArrayList<Long>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            if (id <= 0L) continue
            val media = o.optLong("featured_media", 0L)
            if (media > 0L) featured[id] = media else missing += id
        }
        if (missing.isNotEmpty()) {
            val body = Web.get(
                "$REST/posts?include=${missing.joinToString(",")}&per_page=100&_fields=id,featured_media",
            )
            val found = JSONArray(body)
            for (i in 0 until found.length()) {
                val o = found.optJSONObject(i) ?: continue
                val id = o.optLong("id", 0L)
                val media = o.optLong("featured_media", 0L)
                if (id > 0L && media > 0L) featured[id] = media
            }
        }
        if (featured.isEmpty()) return@runCatching emptyMap<Long, String>()
        val mediaIds = featured.values.distinct()
        val body = Web.get(
            "$REST/media?include=${mediaIds.joinToString(",")}&per_page=100&_fields=id,source_url",
        )
        val byMedia = HashMap<Long, String>()
        val mediaArray = JSONArray(body)
        for (i in 0 until mediaArray.length()) {
            val o = mediaArray.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val url = o.optString("source_url", "")
            if (id > 0L && url.isNotBlank()) byMedia[id] = url
        }
        featured.mapNotNull { (post, media) -> byMedia[media]?.let { post to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** Pure: `[{id, name, count, slug}]` from the WordPress categories route. */
    fun parseRestCategories(json: String): List<SourceCategory> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("id", 0L)
            val name = decodeEntities(o.optString("name", "")).trim()
            if (id <= 0L || name.isBlank()) return@mapNotNull null
            SourceCategory(
                source = SourceId.NINEMOD,
                id = id.toString(),
                name = name,
                count = o.optInt("count", 0).takeIf { it > 0 },
                url = "$BASE/category/${o.optString("slug", id.toString())}/",
            )
        }
    }.getOrDefault(emptyList())

    /** Pure: `[{id, title, url}]` from the WordPress search route, with [icons] keyed by post id. */
    fun parseSearch(json: String, icons: Map<Long, String> = emptyMap()): List<SourceApp> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url", "").ifBlank { return@mapNotNull null }
            SourceApp(
                source = SourceId.NINEMOD,
                key = url,
                title = decodeEntities(o.optString("title", url.substringAfterLast('/'))),
                iconUrl = icons[o.optLong("id", 0L)],
                pageUrl = url,
            )
        }
    }.getOrDefault(emptyList())

    override suspend fun files(app: SourceApp): List<SourceFile> {
        val pageUrl = app.pageUrl ?: app.key
        val page = Web.get(pageUrl)
        val downloadPage = downloadPageOf(page, pageUrl) ?: return emptyList()
        val listing = Web.get(downloadPage, referer = pageUrl)
        // The site now puts the storage link straight on the download page: one anchor per artefact,
        // with the version as its name and the badge (`apk`, `xapk`) beside the size. The per-file
        // pages the old parser walked are gone, so they are the fallback rather than the road.
        directFilesOf(listing, downloadPage).takeIf { it.isNotEmpty() }?.let { return it }
        val filePages = filePagesOf(listing, downloadPage)
        val nonce = runCatching { requestNonce(downloadPage) }.getOrNull()

        return filePages.mapNotNull { (index, url) ->
            runCatching {
                val fileHtml = Web.get(url, referer = downloadPage)
                val info = parseFileInfo(fileHtml)
                val direct = nonce?.let { runCatching { requestDownload(downloadPage, app.key, info.postId, index, it) }.getOrNull() }
                if (direct.isNullOrBlank()) return@runCatching null
                SourceFile(
                    label = info.fileName.ifBlank { "File ${index + 1}" },
                    url = direct,
                    size = info.size,
                    note = info.note,
                )
            }.getOrNull()
        }
    }

    /**
     * The post's own page: its prose and the pictures it carries.
     *
     * 9MOD is WordPress, so the body is the theme's entry content and the pictures are whatever the
     * post embeds. The site refuses this desktop with 403, so the parser asks for the shapes WordPress
     * itself emits rather than for a theme class that cannot be checked live.
     */
    override suspend fun detail(app: SourceApp): SourceDetail {
        val pageUrl = app.pageUrl ?: app.key
        return parseDetail(Web.get(pageUrl), pageUrl)
    }

    /** Pure: the prose and galleries of a 9MOD post page. */
    fun parseDetail(html: String, base: String): SourceDetail {
        val body = proseAfter(html, "class=\"entry-content\"")
            .ifBlank { proseAfter(html, "itemprop=\"description\"") }
        val screenshots = (
            galleryLinks(html, "gallery-item", base) +
                imageUrlsIn(body).map { Web.absolute(base, it) } +
                jsonLdScreenshots(html) +
                tagsHaving(html, "meta", "itemprop", "screenshot")
                    .mapNotNull { tagAttribute(it, "content") }
                    .map { Web.absolute(base, it) }
            )
            .distinct()
        return SourceDetail(
            description = body.ifBlank { metaContent(html, "description") },
            screenshots = screenshots,
        )
    }

    /**
     * Pure: the artefacts a download page links directly.
     *
     * Each row is `<a href="https://gp4.9mod.com/…" class="android-item file-item">` around a
     * `p.filename` (the version), a badge (`apk`, `xapk`) and a size. The href is already the file,
     * on a host that answers a range request without a token or a referer.
     */
    fun directFilesOf(html: String, base: String): List<SourceFile> =
        Regex("<a\\b([^>]*class=\"[^\"]*android-item file-item[^\"]*\"[^>]*)>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                val href = Regex("href=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                    ?.replace("&amp;", "&")
                    ?.ifBlank { null }
                    ?: return@mapNotNull null
                if (!Regex("\\.(apk|xapk|zip|obb)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(href)) {
                    return@mapNotNull null
                }
                val block = match.groupValues[2]
                val label = Regex("class=\"filename[^\"]*\"[^>]*>\\s*([^<]+?)\\s*<")
                    .find(block)?.groupValues?.get(1)?.trim()?.ifBlank { null }
                    ?: href.substringAfterLast('/').substringBefore('?')
                val badge = Regex("<span[^>]*>\\s*(apk|xapk|zip|obb)\\s*</span>", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.get(1)?.lowercase()
                val size = Regex("<span>\\s*([\\d.]+\\s*[KMGT]i?B?)\\s*</span>", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.get(1)?.trim()
                SourceFile(label = label, url = Web.absolute(base, href), size = size, note = badge)
            }
            .distinctBy { it.url }
            .toList()

    /** Pure: the first `…/download/<slug>-<id>` link inside an app page. */
    fun downloadPageOf(html: String, base: String): String? =
        Regex("href=\"(/download/[^\"]+)\"").find(html)?.let { Web.absolute(base, it.groupValues[1]) }
            ?: Regex("href=\"(https://9mod\\.com/download/[^\"]+)\"").find(html)?.groupValues?.get(1)

    /** Pure: `(index, url)` for each per-file page, in page order. */
    fun filePagesOf(html: String, base: String): List<Pair<Int, String>> =
        Regex("href=\"([^\"]*/download/[^\"]+/(\\d+))\"")
            .findAll(html)
            .map { it.groupValues[2].toInt() - 1 to Web.absolute(base, it.groupValues[1]) }
            .distinctBy { it.second }
            .sortedBy { it.first }
            .toList()

    data class FileInfo(
        val fileName: String,
        val size: String?,
        val note: String?,
        val version: String?,
        val postId: Long,
    )

    /** Pure: the base64 `encodedFileData` blob plus `postId` from a 9MOD per-file page. */
    fun parseFileInfo(html: String): FileInfo {
        val postId = Regex("const\\s+postId\\s*=\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val blob = Regex("encodedFileData\\s*=\\s*'([A-Za-z0-9+/=]+)'").find(html)?.groupValues?.get(1)
            ?: Regex("encodedFileData\\s*=\\s*\"([A-Za-z0-9+/=]+)\"").find(html)?.groupValues?.get(1)
        val json = blob?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() }
        return FileInfo(
            // The blob writes names percent-encoded; show them the way a person writes them.
            fileName = percentDecode(o?.optString("fileName", "").orEmpty()),
            size = o?.optString("size", "")?.trim()?.ifBlank { null },
            note = o?.optString("note", "")?.trim()?.ifBlank { null },
            version = o?.optString("version", "")?.trim()?.ifBlank { null },
            postId = postId,
        )
    }

    private fun requestNonce(downloadPage: String): String {
        val body = Web.post(
            "$BASE/wp-admin/admin-ajax.php",
            "action=app_get_nonce&type=download",
            referer = downloadPage,
        )
        return JSONObject(body).optJSONObject("data")?.optString("nonce").orEmpty()
    }

    private fun requestDownload(downloadPage: String, pageKey: String, postId: Long, index: Int, nonce: String): String {
        val suffix = Regex("(\\d+)$").find(pageKey.substringBefore('/').substringAfterLast('-'))?.value
            ?: Regex("-(\\d+)$").find(pageKey)?.groupValues?.get(1)
            ?: ""
        val id = if (postId > 0) postId else suffix.toLongOrNull() ?: 0L
        val body = Web.post(
            "$BASE/wp-admin/admin-ajax.php",
            "action=app_get_download&post_id=$id&file_index=$index&app_download_nonce=${Web.encode(nonce)}",
            referer = downloadPage,
        )
        return JSONObject(body).optJSONObject("data")?.optString("url").orEmpty()
    }
}

/**
 * 5play — DLE. Search is `?do=search&subaction=search&story=`; the app page links one or more
 * `?do=cdn&id=` endpoints, and each of those pages embeds the file's **pre-signed R2 URL** in the
 * `#btn-download-cdn` anchor. That URL needs no referer (verified 206) and expires in an hour, so
 * it is resolved when the user opens the file list rather than stored.
 */
object FivePlaySource : Source {

    override val id = SourceId.FIVEPLAY
    private const val BASE = "https://5play.org/en/"

    /** The site's two shelves, shared by the chip row and by the rows that lead back to them. */
    private val gamesCategory = SourceCategory(
        source = SourceId.FIVEPLAY,
        id = "games",
        name = "Games",
        url = "${BASE}android/games/",
    )

    private val appsCategory = SourceCategory(
        source = SourceId.FIVEPLAY,
        id = "apps",
        name = "Apps",
        url = "${BASE}android/apps/",
    )

    /**
     * The site's newest shelves.
     *
     * The home page is a row of shelves and the site names two of them "New games" and "New apps" —
     * those are its newest apps, in its own order. The other shelves (Popular, Recommended, Useful)
     * are rankings rather than recency and are left out.
     */
    override suspend fun latest(): List<SourceApp> {
        val home = Web.get(BASE)
        val games = parseShelfCards(sectionHtml(home, "nx-h-new-games"))
        val apps = parseShelfCards(sectionHtml(home, "nx-h-new-apps"))
        return (games + apps).distinctBy { it.key }.take(24)
    }

    /**
     * The site's front page as its own rows.
     *
     * Recommended is a feature strip rather than an app card, so it is read by [parseFeatureCards];
     * the two game rows and the two app rows are the site's own splits of new from popular.
     */
    override suspend fun shelves(): List<SourceShelf> {
        val home = Web.get(BASE)
        return listOf(
            // The feature strip has no term of its own; everything the site lists is the full list
            // behind it.
            SourceShelf(
                "Recommended",
                parseFeatureCards(sectionHtml(home, "nx-h-recom")),
                ShelfMore.All,
            ),
            SourceShelf(
                "New games",
                parseShelfCards(sectionHtml(home, "nx-h-new-games")),
                ShelfMore.Category(gamesCategory),
            ),
            SourceShelf(
                "Popular games",
                parseShelfCards(sectionHtml(home, "nx-h-pop-games")),
                ShelfMore.Category(gamesCategory),
            ),
            SourceShelf(
                "New apps",
                parseShelfCards(sectionHtml(home, "nx-h-new-apps")),
                ShelfMore.Category(appsCategory),
            ),
            SourceShelf(
                "Popular apps",
                parseShelfCards(sectionHtml(home, "nx-h-pop-apps")),
                ShelfMore.Category(appsCategory),
            ),
        ).filter { it.apps.isNotEmpty() }
    }

    /**
     * The site's own grouping.
     *
     * 5play splits its catalogue into games and apps and publishes no genre tree below that, so those
     * two are the categories it has.
     */
    override suspend fun categories(): List<SourceCategory> = listOf(gamesCategory, appsCategory)

    /** The apps of one shelf, read from the page the site lists that shelf on. */
    override suspend fun appsPage(category: SourceCategory, page: Int): SourcePage {
        val base = category.url ?: return SourcePage(emptyList())
        val html = Web.get(pagedPath(base, page))
        return SourcePage(parseShelfCards(html), hasMore = linksPageAfter(html, page))
    }

    /**
     * Everything the site lists, newest first: its games listing and its apps listing on the same
     * page number, one after the other. The site publishes no single list across both, so a page of
     * this is the games' page and the apps' page together.
     */
    override suspend fun allAppsPage(page: Int): SourcePage {
        val n = page.coerceAtLeast(1)
        val gamesHtml = runCatching { Web.get(pagedPath("${BASE}android/games/", n)) }.getOrNull().orEmpty()
        val appsHtml = runCatching { Web.get(pagedPath("${BASE}android/apps/", n)) }.getOrNull().orEmpty()
        val rows = (parseShelfCards(gamesHtml) + parseShelfCards(appsHtml)).distinctBy { it.key }
        return SourcePage(
            rows,
            hasMore = linksPageAfter(gamesHtml, n) || linksPageAfter(appsHtml, n),
        )
    }

    /** Pure: the `<section>` a shelf heading belongs to, or nothing when the heading is not there. */
    fun sectionHtml(html: String, headingId: String): String {
        val at = html.indexOf("id=\"$headingId\"")
        if (at < 0) return ""
        val from = html.lastIndexOf("<section", at)
        val to = html.indexOf("<section", at)
        return html.substring(if (from >= 0) from else at, if (to > at) to else html.length)
    }

    /** Pure: the `nx-app` cards of a shelf. */
    fun parseShelfCards(html: String): List<SourceApp> = parseCards(html, BASE, rowsOnly = false)

    /**
     * Pure: the feature strip at the top of the home page.
     *
     * A feature card is an `nx-feature` article: artwork, the app's icon, its name and a line such as
     * *MOD · Android 7.0+*. It is not an `nx-app` card, so [parseShelfCards] does not see it.
     */
    fun parseFeatureCards(html: String): List<SourceApp> =
        html.split("<article")
            .filter { block -> block.contains("nx-feature") }
            .mapNotNull { block ->
                val href = Regex("<a href=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val title = Regex("nx-feature__title[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)?.let { Web.text(it) }
                    ?: titleFromSlug(href.substringAfterLast('/').substringBeforeLast(".html"))
                val meta = Regex("nx-feature__sub[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)?.let { Web.text(it) }?.trim()
                val icon = Regex("nx-appicon\"><img src=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                SourceApp(
                    source = SourceId.FIVEPLAY,
                    key = Web.absolute(BASE, href),
                    title = title,
                    version = Regex("v[0-9][^\\s·]*").find(meta.orEmpty())?.value,
                    subtitle = meta?.replace(Regex("\\s*·\\s*"), " · ")?.ifBlank { null },
                    iconUrl = icon?.trim()?.ifBlank { null }?.let { src -> Web.absolute(BASE, src) },
                    pageUrl = Web.absolute(BASE, href),
                )
            }
            .distinctBy { it.key }
            .toList()

    override suspend fun search(query: String): List<SourceApp> {
        val encoded = Web.encode(query)
        val first = parseSearch(
            Web.get("$BASE?do=search&subaction=search&search_start=1&story=$encoded"),
            BASE,
        )
        // The site draws twelve results a page and says how many it has. A term with a full first
        // page is worth one more request, because the answer to "all of them" is not the first twelve.
        if (first.size < SEARCH_PAGE) return first
        val second = runCatching {
            Web.get("$BASE?do=search&subaction=search&search_start=2&story=$encoded")
        }.getOrNull()
        return (first + parseSearch(second.orEmpty(), BASE)).distinctBy { it.key }.take(SEARCH_ROWS)
    }

    /**
     * Pure: the search page's result rows.
     *
     * A result is an `nx-app nx-app--row` article, while the home page's shelves use the plain
     * `nx-app` card. That one class is what keeps a site's own recommendation rail out of its answer
     * to a term.
     */
    fun parseSearch(html: String, base: String): List<SourceApp> = parseCards(html, base, rowsOnly = true)

    private fun parseCards(html: String, base: String, rowsOnly: Boolean): List<SourceApp> =
        html.split("<article")
            .filter { block -> block.contains("nx-app") && (!rowsOnly || block.contains("nx-app--row")) }
            .mapNotNull { block ->
                val href = Regex("<a href=\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
                val title = Regex("nx-app__title[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)?.let { Web.text(it) }
                val version = Regex("nx-app__ver\"[^>]*title=\"([^\"]*)\"").find(block)?.groupValues?.get(1)
                val meta = Regex("nx-app__meta[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1).orEmpty()
                val category = Regex("<span>([^<]+)</span>").find(meta)
                    ?.groupValues?.get(1)
                    ?.let { Web.text(it).trim() }
                val icon = Regex("<img[^>]*src=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                SourceApp(
                    source = SourceId.FIVEPLAY,
                    key = Web.absolute(base, href),
                    title = title?.ifBlank { null }
                        ?: titleFromSlug(href.substringAfterLast('/').substringBeforeLast(".html")),
                    version = version?.trim()?.ifBlank { null },
                    // The row's own category when it prints one; a shelf card prints a mod badge instead.
                    subtitle = category?.ifBlank { null }
                        ?: if (block.contains("nx-badge--mod")) "MOD" else null,
                    iconUrl = icon?.trim()?.ifBlank { null }?.let { src -> Web.absolute(base, src) },
                    pageUrl = Web.absolute(base, href),
                )
            }
            .distinctBy { it.key }
            .toList()


    override suspend fun files(app: SourceApp): List<SourceFile> {
        val pageUrl = app.pageUrl ?: app.key
        val html = Web.get(pageUrl)
        return parseFileLinks(html, pageUrl).mapNotNull { (url, label) ->
            runCatching {
                val cdn = Web.get(url, referer = pageUrl)
                val direct = parseCdnLink(cdn) ?: return@runCatching null
                SourceFile(label = label, url = direct)
            }.getOrNull()
        }
    }

    /**
     * The app's own page: the full *About* prose and the screenshot strip.
     *
     * The card only carries a one-line badge; the page has the long text under `#nx-desc` and the
     * screenshots under the section the site labels `nx-h-shots`. The strip links the full-size file
     * from each anchor and loads a medium copy in the `<img>`, so the href is what a viewer gets.
     */
    override suspend fun detail(app: SourceApp): SourceDetail {
        val pageUrl = app.pageUrl ?: app.key
        return parseDetail(Web.get(pageUrl), pageUrl)
    }

    /** Pure: a 5play app page's description and screenshots. */
    fun parseDetail(html: String, base: String): SourceDetail {
        val description = proseAfter(html, "id=\"nx-desc\"")
            .ifBlank { proseAfter(html, "class=\"nx-apkmm__desc\"") }
            .ifBlank { metaContent(html, "description") }
        val shots = sectionHtml(html, "nx-h-shots")
        val screenshots = galleryLinks(shots.ifBlank { html }, "highslide", base)
        return SourceDetail(description = description, screenshots = screenshots)
    }

    /** Pure: `(do=cdn url, label)` pairs inside the download box. */
    fun parseFileLinks(html: String, base: String): List<Pair<String, String>> =
        Regex("href=\"([^\"]*\\?do=cdn&(?:amp;)?id=\\d+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { m -> Web.absolute(base, m.groupValues[1].replace("&amp;", "&")) to Web.text(m.groupValues[2]) }
            .distinctBy { it.first }
            .toList()

    /** Pure: the signed R2 URL from the `Downloading file …` interstitial. */
    fun parseCdnLink(html: String): String? =
        Regex("id=\"btn-download-cdn\"[^>]*href=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: Regex("href=\"(https://[a-z0-9.]+\\.r2\\.cloudflarestorage\\.com/[^\"]+)\"").find(html)?.groupValues?.get(1)
}

/**
 * GetModsAPK — Laravel. Search is `/search?query=`; the app page links `/download`, which lists
 * mirrors as `/download/<mirrorId>`; each mirror page holds the direct `files.5modapk.com` link.
 */
object GetModsApkSource : Source {

    override val id = SourceId.GETMODSAPK
    private const val BASE = "https://getmodsapk.com"

    /** The whole games shelf, which is the full list behind both game rows on the front page. */
    private val gamesCategory = SourceCategory(
        source = SourceId.GETMODSAPK,
        id = "games",
        name = "Games",
        url = "$BASE/games",
    )

    /** The headings the site puts over a card grid on its front page. */
    private val HOME_SHELF_TITLES = setOf("Featured", "Updated Games", "New Games")

    /**
     * The site's own sitemap of app pages, newest first.
     *
     * The site's `/games/` and `/apps/` shelves are drawn by its own script and carry no app links in
     * the markup, but the sitemap it publishes does: one `<loc>` per app page, newest first. The
     * first sitemap is the current one and the later ones are the archive.
     */
    override suspend fun latest(): List<SourceApp> =
        parseSitemap(Web.get("$BASE/posts-sitemap1.xml")).take(20)

    /**
     * The site's front page, which is three card grids under its own headings.
     *
     * The cards carry the site's numbering: a version and a size under each name, and a `MOD` or
     * `Updated` badge where it has one. Those are the values a person sees on the site, so they are
     * what the card in the app prints.
     */
    override suspend fun shelves(): List<SourceShelf> =
        parseHomeShelves(Web.get(BASE))

    /**
     * The site's own categories, read from the sitemap it publishes for them.
     *
     * A category lives under the kind of thing it groups — `/game/<slug>` or `/app/<slug>` — and the
     * menu links to the plural shelves instead, so the sitemap is the only place both the prefix and
     * the term exist. Dropping the prefix lands on a 404.
     */
    override suspend fun categories(): List<SourceCategory> =
        parseCategorySitemap(Web.get("$BASE/categories-sitemap.xml"))

    /** The apps of one category, one page of the site's own listing at a time. */
    override suspend fun appsPage(category: SourceCategory, page: Int): SourcePage {
        val base = category.url ?: return SourcePage(emptyList())
        val n = page.coerceAtLeast(1)
        val url = if (n == 1) base else "$base?page=$n"
        val html = Web.get(url)
        return SourcePage(parseCategoryApps(html), hasMore = html.contains("?page=${n + 1}"))
    }

    /**
     * The whole sitemap, sliced, because the site publishes one document rather than a paged list.
     *
     * The sitemap is the only place the site lists every app page in one order, so it is read once
     * and kept for half an hour; each page of the grid is a window on it.
     */
    @Volatile
    private var sitemap: List<SourceApp>? = null

    @Volatile
    private var sitemapAt = 0L

    private fun sitemap(): List<SourceApp> {
        val now = System.currentTimeMillis()
        sitemap?.let { if (now - sitemapAt < 30L * 60 * 1000) return it }
        val rows = runCatching { parseSitemap(Web.get("$BASE/posts-sitemap1.xml")) }.getOrDefault(emptyList())
        if (rows.isNotEmpty()) {
            sitemap = rows
            sitemapAt = now
        }
        return rows
    }

    override suspend fun allAppsPage(page: Int): SourcePage {
        val (rows, hasMore) = pageWindow(sitemap(), page, perPage = 24)
        return SourcePage(rows, hasMore)
    }

    /** Pure: the app pages of a sitemap, in the order the sitemap lists them. */
    fun parseSitemap(xml: String): List<SourceApp> =
        Regex("<loc>([^<]+)</loc>")
            .findAll(xml)
            .map { match ->
                val url = match.groupValues[1].trim()
                val slug = url.trimEnd('/').substringAfterLast('/')
                SourceApp(
                    source = SourceId.GETMODSAPK,
                    key = url,
                    title = titleFromPageSlug(slug),
                    pageUrl = url,
                )
            }
            .distinctBy { it.key }
            .toList()

    /** Pure: the category pages of a sitemap, keeping the `game`/`app` prefix they live under. */
    fun parseCategorySitemap(xml: String): List<SourceCategory> =
        Regex("<loc>https://getmodsapk\\.com/(game|app)/([a-z0-9-]+)</loc>")
            .findAll(xml)
            .map { match ->
                val kind = match.groupValues[1]
                val slug = match.groupValues[2]
                SourceCategory(
                    source = SourceId.GETMODSAPK,
                    id = "$kind/$slug",
                    name = labelFromSlug(slug),
                    url = "https://getmodsapk.com/$kind/$slug",
                )
            }
            .distinctBy { it.id }
            .toList()

    /**
     * Pure: the three headings the home page uses, each with the cards under it.
     *
     * A heading is an `<h2>`; its block runs to the next `<h2>`, which is where the next grid
     * begins. Matching the heading tag rather than the word keeps a menu link with the same words
     * from starting a shelf early. Anything the site adds below (blog rows, footer links) has no
     * `<h3>` card and is dropped by the card parser.
     */
    fun parseHomeShelves(html: String): List<SourceShelf> {
        val headings = Regex("<h2[^>]*>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        return headings.mapIndexedNotNull { index, match ->
            val title = Web.text(match.groupValues[1]).trim()
            if (title !in HOME_SHELF_TITLES) return@mapIndexedNotNull null
            val start = match.range.last + 1
            val end = headings.getOrNull(index + 1)?.range?.first ?: html.length
            val apps = parseHomeCards(html.substring(start, end))
            if (apps.isEmpty()) null else {
                // The two game rows are windows on the games shelf; the featured row is the site's
                // pick across both shelves, so its full list is everything the sitemap holds.
                val more = if (title == "Featured") {
                    ShelfMore.All
                } else {
                    ShelfMore.Category(gamesCategory)
                }
                SourceShelf(title, apps, more)
            }
        }
    }

    /** Pure: the cards in one home grid. */
    fun parseHomeCards(html: String): List<SourceApp> =
        html.split("<a href=\"https://getmodsapk.com/")
            .drop(1)
            .mapNotNull { block ->
                if (!block.contains("<h3")) return@mapNotNull null
                val href = "https://getmodsapk.com/" + block.substringBefore('"')
                val title = Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)?.let { Web.text(it) }
                    ?: return@mapNotNull null
                val version = Regex("class=\"text-xs[^\"]*\">(v[0-9][^<]*)<").find(block)
                    ?.groupValues?.get(1)?.trim()?.ifBlank { null }
                val size = Regex("Size:</span>\\s*<span>([^<]+)</span>").find(block)
                    ?.groupValues?.get(1)?.trim()?.ifBlank { null }
                val note = Regex("inline-flex items-center gap-1 text-xs[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)
                    ?.replace(Regex("<[^>]+>"), "")?.trim()?.ifBlank { null }
                val icon = Regex("<img[^>]*src=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                SourceApp(
                    source = SourceId.GETMODSAPK,
                    key = href,
                    title = title,
                    version = version,
                    size = size,
                    subtitle = note,
                    iconUrl = icon,
                    pageUrl = href,
                )
            }
            .distinctBy { it.key }

    /** Pure: the app cards of a category page, with the title each card draws. */
    fun parseCategoryApps(html: String): List<SourceApp> {
        val links = Regex("href=\"(https://getmodsapk\\.com/([^\"/?#]+))/\"").findAll(html).toList()
        return links.mapIndexedNotNull { index, match ->
            val slug = match.groupValues[2]
            if (slug in NON_APP_PATHS) return@mapIndexedNotNull null
            val card = html.substring(
                match.range.last,
                links.getOrNull(index + 1)?.range?.first ?: html.length,
            )
            SourceApp(
                source = SourceId.GETMODSAPK,
                key = match.groupValues[1],
                title = Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)
                    .find(card)?.groupValues?.get(1)?.let { Web.text(it) }?.ifBlank { null }
                    ?: titleFromSlug(slug),
                version = Regex("<span class=\"text-xs[^\"]*\">(v[0-9][^<]*)</span>")
                    .find(card)?.groupValues?.get(1)?.trim()?.ifBlank { null },
                size = Regex("Size:</span>\\s*<span>([^<]+)</span>")
                    .find(card)?.groupValues?.get(1)?.trim()?.ifBlank { null },
                iconUrl = Regex("<img[^>]*src=\"([^\"]+)\"").find(card)?.groupValues?.get(1)
                    ?.trim()?.ifBlank { null },
                pageUrl = match.groupValues[1],
            )
        }.distinctBy { it.key }.toList()
    }

    override suspend fun search(query: String): List<SourceApp> {
        val encoded = Web.encode(query)
        val first = parseSearch(Web.get("$BASE/search?query=$encoded"), BASE)
        if (first.size < SEARCH_PAGE) return first
        // The page draws twelve cards and pages with `?page=`. A full first page is worth the second.
        val second = runCatching { Web.get("$BASE/search?query=$encoded&page=2") }.getOrNull()
        return (first + parseSearch(second.orEmpty(), BASE)).distinctBy { it.key }.take(SEARCH_ROWS)
    }

    /**
     * Pure: the cards under the page's `Results` heading.
     *
     * Two things matter here. App URLs are not all `\/<id>-<slug>/` — many are plain
     * `\/spotify-lite-mod-apk/` — and the page repeats the same card markup for its "recent" and
     * "popular" shelves, so the parse starts at the heading instead of scanning the document.
     */
    fun parseSearch(html: String, base: String): List<SourceApp> {
        val results = html.substringAfter(">Results</h2>", html)
        val links = Regex("href=\"(https://getmodsapk\\.com/([^\"/?#]+))/\"").findAll(results).toList()
        return links.mapIndexedNotNull { index, m ->
            val slug = m.groupValues[2]
            if (slug in NON_APP_PATHS || m.groupValues[1].contains("/download")) return@mapIndexedNotNull null
            val card = results.substring(m.range.last, links.getOrNull(index + 1)?.range?.first ?: results.length)
            SourceApp(
                source = SourceId.GETMODSAPK,
                key = m.groupValues[1],
                title = Regex("<h3[^>]*>(.*?)</h3>", RegexOption.DOT_MATCHES_ALL)
                    .find(card)
                    ?.let { Web.text(it.groupValues[1]) }
                    ?.ifBlank { null }
                    ?: titleFromSlug(slug),
                version = Regex("<span class=\"text-xs[^\"]*\">(v[0-9][^<]*)</span>")
                    .find(card)?.groupValues?.get(1)?.trim()?.ifBlank { null },
                size = Regex("Size:</span>\\s*<span>([^<]+)</span>")
                    .find(card)?.groupValues?.get(1)?.trim()?.ifBlank { null },
                iconUrl = Regex("<img src=\"([^\"]+)\"").find(card)?.groupValues?.get(1),
                pageUrl = m.groupValues[1],
            )
        }.distinctBy { it.key }.toList()
    }

    /** Site sections that live at `/<name>/` but are not app pages. */
    private val NON_APP_PATHS = setOf("games", "apps", "blog", "page", "search", "category")

    override suspend fun files(app: SourceApp): List<SourceFile> {
        val pageUrl = app.pageUrl ?: app.key
        val page = Web.get(pageUrl)
        val downloadPage = Regex("href=\"(https://getmodsapk\\.com/[^\"]+/download)\"").find(page)?.groupValues?.get(1)
            ?: return emptyList()
        val listing = Web.get(downloadPage, referer = pageUrl)
        return parseMirrors(listing).mapNotNull { (mirrorUrl, label) ->
            runCatching {
                val mirror = Web.get(mirrorUrl, referer = downloadPage)
                val direct = parseDirectFile(mirror) ?: return@runCatching null
                SourceFile(label = label.ifBlank { "Mirror" }, url = direct)
            }.getOrNull()
        }
    }

    /**
     * The app's own page: the long post content and its screenshot tray.
     *
     * The tray is an anchor per picture with `data-lightbox="app-screenshots"`; the href is the full
     * image and the `<img>` beside it is a lazy placeholder, so the anchor is the one to follow.
     */
    override suspend fun detail(app: SourceApp): SourceDetail {
        val pageUrl = app.pageUrl ?: app.key
        return parseDetail(Web.get(pageUrl), pageUrl)
    }

    /** Pure: a GetModsAPK post's description and screenshots. */
    fun parseDetail(html: String, base: String): SourceDetail {
        val description = proseAfter(html, "class=\"post-content")
            .ifBlank { metaContent(html, "description") }
        val screenshots = tagsHaving(html, "a", "data-lightbox", "app-screenshots")
            .mapNotNull { tagAttribute(it, "href") }
            .map { Web.absolute(base, it) }
            .distinct()
        return SourceDetail(description = description, screenshots = screenshots)
    }

    /** Pure: `(mirror url, label)` pairs on the download page. */
    fun parseMirrors(html: String): List<Pair<String, String>> =
        Regex("href=\"(https://getmodsapk\\.com/[^\"]+/download/\\d+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { m -> m.groupValues[1] to Web.text(m.groupValues[2]) }
            .distinctBy { it.first }
            .toList()

    /** Pure: the first direct APK on a mirror page. */
    fun parseDirectFile(html: String): String? =
        Regex("href=\"(https://files\\.5modapk\\.com/[^\"]+)\"").find(html)?.groupValues?.get(1)
}

/**
 * The name a page's own path carries.
 *
 * The site's paths are a number, the name, and the words it marks a build with — `8438-clash-royale-
 * free-mod-apk` — and only the middle part is the app's name.
 */
private fun titleFromPageSlug(slug: String): String {
    var trimmed = slug
    while (true) {
        val next = trimmed.replace(Regex("-(mod|apk|apks|premium|pro|free|download)+$"), "")
        if (next == trimmed) break
        trimmed = next
    }
    return titleFromSlug(trimmed)
}

/**
 * AN1 — DLE, modded games. Search is the DLE search route; the app page links `/file_<id>-dw.html`,
 * whose page holds one or more direct `files.an1.co` links.
 */
object An1Source : Source {

    override val id = SourceId.AN1
    private const val BASE = "https://an1.com/"

    /** The site's whole games catalogue, the full list behind its *Latest games* row. */
    private val gamesCategory = SourceCategory(
        source = SourceId.AN1,
        id = "games",
        name = "Games",
        url = "${BASE}games/",
    )

    /**
     * The site's newest apps.
     *
     * The home page is the site's own catalogue page and lists its apps in the order it publishes
     * them, so the first of them are the newest ones.
     */
    override suspend fun latest(): List<SourceApp> = parseAppLinks(Web.get(BASE)).take(16)

    /**
     * The site's front page: one *Latest games* row, with the developer and rating each card prints.
     *
     * AN1 does not put a version on its cards; the rating is the number it does print, so that is the
     * one the app carries rather than an invented version.
     */
    override suspend fun shelves(): List<SourceShelf> {
        val apps = parseAppLinks(Web.get(BASE)).take(24)
        return if (apps.isEmpty()) emptyList() else {
            listOf(SourceShelf("Latest games", apps, ShelfMore.Category(gamesCategory)))
        }
    }

    /** The site's own genres, as the links its own menu carries. */
    override suspend fun categories(): List<SourceCategory> = parseCategories(Web.get(BASE))

    /** The apps of one genre, one page of the site's own listing at a time. */
    override suspend fun appsPage(category: SourceCategory, page: Int): SourcePage {
        val base = category.url ?: return SourcePage(emptyList())
        val html = Web.get(pagedPath(base, page))
        return SourcePage(parseAppLinks(html), hasMore = linksPageAfter(html, page))
    }

    /**
     * Everything on the site, one page of its games catalogue at a time.
     *
     * AN1's home page is news; its catalogue is `/games/`, which pages through 281 pages of the same
     * cards the home draws, with the artwork, developer and rating on each one. That is the list a
     * person means when they ask what the site has.
     */
    override suspend fun allAppsPage(page: Int): SourcePage {
        val html = Web.get(pagedPath("${BASE}games/", page))
        return SourcePage(parseAppLinks(html), hasMore = linksPageAfter(html, page))
    }

    /** Pure: the genre links in the site's own menu. */
    fun parseCategories(html: String): List<SourceCategory> =
        Regex("href=\"(/games/([a-z0-9-]+)/)\"")
            .findAll(html)
            .map { match ->
                val slug = match.groupValues[2]
                SourceCategory(
                    source = SourceId.AN1,
                    id = slug,
                    name = labelFromSlug(slug),
                    url = Web.absolute(BASE, match.groupValues[1]),
                )
            }
            .distinctBy { it.id }
            .toList()

    /** Pure: app links `/<id>-<slug>.html`, with the artwork and the name the site draws. */
    fun parseAppLinks(html: String): List<SourceApp> {
        // The home page draws its newest apps as cards, and a card carries the artwork and the site's
        // own name for the app. A category page has plain links and no artwork.
        val cards = html.split("<div class=\"item\">")
            .drop(1)
            .mapNotNull { block ->
                val link = Regex("href=\"([^\"]*?/(\\d+)-([a-z0-9-]+)\\.html)\"[^>]*title=\"([^\"]*)\"")
                    .find(block) ?: return@mapNotNull null
                val url = Web.absolute(BASE, link.groupValues[1])
                val icon = Regex("<img[^>]*src=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
                // The card prints the developer under the name and the rating beside it; AN1 puts
                // no version on a card, so those two are the line the app carries.
                val developer = Regex("developer[^\"]*\">([^<]*)<")
                    .find(block)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
                    ?.ifBlank { null }
                val rating = Regex("current-rating[^>]*>([^<]*)<")
                    .find(block)?.groupValues?.get(1)?.trim()?.ifBlank { null }
                SourceApp(
                    source = SourceId.AN1,
                    key = url,
                    title = decodeEntities(link.groupValues[4]).trim().ifBlank { null }
                        ?: titleFromSlug(link.groupValues[3]),
                    subtitle = listOfNotNull(developer, rating).joinToString(" · ").ifBlank { null },
                    iconUrl = icon?.trim()?.ifBlank { null },
                    pageUrl = url,
                )
            }
            .distinctBy { it.key }
        if (cards.isNotEmpty()) return cards
        return Regex("href=\"([^\"]*?/(\\d+)-([a-z0-9-]+)\\.html)\"")
            .findAll(html)
            .map { match ->
                val url = Web.absolute(BASE, match.groupValues[1])
                SourceApp(
                    source = SourceId.AN1,
                    key = url,
                    title = titleFromSlug(match.groupValues[3]),
                    pageUrl = url,
                )
            }
            .distinctBy { it.key }
            .toList()
    }

    override suspend fun search(query: String): List<SourceApp> {
        val html = Web.get("${BASE}index.php?do=search&subaction=search&story=${Web.encode(query)}")
        return parseSearch(html, BASE).take(20)
    }

    /**
     * Pure: the search page's own result cards.
     *
     * The results are drawn as `item` cards, the same shape the home page uses, so the card parser
     * reads the site's title and artwork rather than the words in the URL.
     */
    fun parseSearch(html: String, base: String): List<SourceApp> = parseAppLinks(html).take(20)

    override suspend fun files(app: SourceApp): List<SourceFile> {
        val pageUrl = app.pageUrl ?: app.key
        val page = Web.get(pageUrl)
        val downloadPage = Regex("href=\"(/file_\\d+-dw\\.html)\"").find(page)
            ?.groupValues?.get(1)
            ?.let { Web.absolute(pageUrl, it) }
            ?: return emptyList()
        val filePage = Web.get(downloadPage, referer = pageUrl)
        return parseDirectFiles(filePage, downloadPage).map { url ->
            SourceFile(label = url.substringAfterLast('/'), url = url)
        }
    }

    /**
     * The app's own page: the spoiler prose and the screenshot strip.
     *
     * The text sits in `div.description`, which also holds the comment spoiler; the screenshots are
     * the page's `meta[itemprop=screenshot]` entries, each naming the full-size file rather than the
     * thumbnail the page draws.
     */
    override suspend fun detail(app: SourceApp): SourceDetail {
        val pageUrl = app.pageUrl ?: app.key
        return parseDetail(Web.get(pageUrl), pageUrl)
    }

    /** Pure: an AN1 app page's description and screenshots. */
    fun parseDetail(html: String, base: String): SourceDetail {
        val description = proseAfter(html, "class=\"description\"", "class=\"app_screens")
            .ifBlank { proseAfter(html, "itemprop=\"description\"") }
            .ifBlank { metaContent(html, "description") }
        val screenshots = tagsHaving(html, "meta", "itemprop", "screenshot")
            .mapNotNull { tagAttribute(it, "content") }
            .map { Web.absolute(base, it) }
            .distinct()
        return SourceDetail(description = description, screenshots = screenshots)
    }

    /** Pure: direct files on the `/file_<id>-dw.html` page. */
    fun parseDirectFiles(html: String, base: String): List<String> =
        Regex("href=\"([^\"]+\\.[a-z0-9]{2,4})\"")
            .findAll(html)
            .map { Web.absolute(base, it.groupValues[1]) }
            .filter { it.contains("files.an1.") && Regex("\\.(apk|zip|apks|xapk)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .distinct()
            .toList()
}
