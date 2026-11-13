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

    /** The site's own category terms. */
    override suspend fun categories(): List<SourceCategory> = parseRestCategories(
        runCatching { Web.get("$REST/categories?per_page=30&_fields=id,name,count,slug") }
            .getOrNull().orEmpty(),
    )

    /** The posts of one term. */
    override suspend fun appsIn(category: SourceCategory): List<SourceApp> {
        val id = category.id.toLongOrNull() ?: return emptyList()
        val json = runCatching {
            Web.get("$REST/posts?categories=$id&per_page=20&_fields=id,title,link,featured_media")
        }.getOrNull().orEmpty()
        return parseRestPosts(json, iconsForPosts(json))
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
     * The site's own grouping.
     *
     * 5play splits its catalogue into games and apps and publishes no genre tree below that, so those
     * two are the categories it has.
     */
    override suspend fun categories(): List<SourceCategory> = listOf(
        SourceCategory(
            source = SourceId.FIVEPLAY,
            id = "games",
            name = "Games",
            url = "${BASE}android/games/",
        ),
        SourceCategory(
            source = SourceId.FIVEPLAY,
            id = "apps",
            name = "Apps",
            url = "${BASE}android/apps/",
        ),
    )

    /** The apps of one shelf, read from the page the site lists that shelf on. */
    override suspend fun appsIn(category: SourceCategory): List<SourceApp> {
        val url = category.url ?: return emptyList()
        return parseShelfCards(Web.get(url)).take(24)
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
     * The site's own categories, read from the sitemap it publishes for them.
     *
     * The category pages are `/game/<slug>` and `/app/<slug>` — singular, while the menu links to
     * `/games/` and `/apps/` — so the sitemap is the only place both spellings exist.
     */
    override suspend fun categories(): List<SourceCategory> =
        parseCategorySitemap(Web.get("$BASE/categories-sitemap.xml"))

    /** The apps of one category, read from the page the site lists that category on. */
    override suspend fun appsIn(category: SourceCategory): List<SourceApp> {
        val url = category.url ?: return emptyList()
        return parseCategoryApps(Web.get(url)).take(24)
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

    /** Pure: the category pages of a sitemap. */
    fun parseCategorySitemap(xml: String): List<SourceCategory> =
        Regex("<loc>https://getmodsapk\\.com/(?:game|app)/([a-z0-9-]+)</loc>")
            .findAll(xml)
            .map { match ->
                val slug = match.groupValues[1]
                SourceCategory(
                    source = SourceId.GETMODSAPK,
                    id = slug,
                    name = labelFromSlug(slug),
                    url = "https://getmodsapk.com/$slug",
                )
            }
            .distinctBy { it.id }
            .toList()

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

    /**
     * The site's newest apps.
     *
     * The home page is the site's own catalogue page and lists its apps in the order it publishes
     * them, so the first of them are the newest ones.
     */
    override suspend fun latest(): List<SourceApp> = parseAppLinks(Web.get(BASE)).take(16)

    /** The site's own genres, as the links its own menu carries. */
    override suspend fun categories(): List<SourceCategory> = parseCategories(Web.get(BASE))

    /** The apps of one genre, read from the page the site lists that genre on. */
    override suspend fun appsIn(category: SourceCategory): List<SourceApp> {
        val url = category.url ?: return emptyList()
        return parseAppLinks(Web.get(url)).take(24)
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
                SourceApp(
                    source = SourceId.AN1,
                    key = url,
                    title = decodeEntities(link.groupValues[4]).trim().ifBlank { null }
                        ?: titleFromSlug(link.groupValues[3]),
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

    /** Pure: direct files on the `/file_<id>-dw.html` page. */
    fun parseDirectFiles(html: String, base: String): List<String> =
        Regex("href=\"([^\"]+\\.[a-z0-9]{2,4})\"")
            .findAll(html)
            .map { Web.absolute(base, it.groupValues[1]) }
            .filter { it.contains("files.an1.") && Regex("\\.(apk|zip|apks|xapk)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .distinct()
            .toList()
}
