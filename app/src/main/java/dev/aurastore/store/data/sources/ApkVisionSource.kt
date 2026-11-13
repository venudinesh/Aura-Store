package dev.aurastore.store.data.sources

import org.json.JSONObject

/**
 * ApkVision — WordPress catalogue with custom AJAX search and direct file distribution.
 *
 * Search requests mint a search token (nonce) on `/?s=<query>` and fetch results from
 * `wp-admin/admin-ajax.php` via `action=ajax_search`.
 *
 * Detail pages link intermediate `/download/<version>/` pages which serve direct downloads
 * from `dl.apkvision.org`.
 */
object ApkVisionSource : Source {

    override val id = SourceId.APKVISION
    private const val BASE = "https://apkvision.org"
    private const val AJAX_URL = "$BASE/wp-admin/admin-ajax.php"

    /** The site's own page of what changed last. */
    private const val UPDATED_URL = "$BASE/updated/"
    private const val GAMES_URL = "$BASE/games/"
    private const val APPS_URL = "$BASE/app/"

    override suspend fun search(query: String): List<SourceApp> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val searchPage = Web.get("$BASE/?s=${Web.encode(trimmed)}")
        val nonce = extractNonce(searchPage)
        val html = if (!nonce.isNullOrBlank()) {
            val postBody = "action=ajax_search&s=${Web.encode(trimmed)}&paged=1&per_page=40&nonce=${Web.encode(nonce)}"
            val json = runCatching { Web.post(AJAX_URL, postBody, referer = "$BASE/?s=${Web.encode(trimmed)}") }.getOrNull()
            json?.let { parseAjaxResponse(it) } ?: searchPage
        } else {
            searchPage
        }
        return parseSearchCards(html).take(40)
    }

    /**
     * The site's own list of what was updated last.
     *
     * `/updated/` is the page the site links from its header with the words "View latest games
     * updates", so the row a person taps here is the same row they would have reached from there.
     */
    override suspend fun latest(): List<SourceApp> = parseGameCards(Web.get(UPDATED_URL)).take(20)

    /**
     * The site's own genres, for games and for apps.
     *
     * Two chip rows, one under `/games/` and one under `/app/`, and the site calls the games' row
     * "All" for the whole shelf — which is not a category of its own and is left out.
     */
    override suspend fun categories(): List<SourceCategory> {
        val games = parseChips(Web.get(GAMES_URL))
        val apps = parseChips(Web.get(APPS_URL))
        return (games + apps).distinctBy { it.id }
    }

    /** The apps of one category, read from the page the site lists that category on. */
    override suspend fun appsIn(category: SourceCategory): List<SourceApp> {
        val url = category.url ?: return emptyList()
        return parseGameCards(Web.get(url)).take(24)
    }

    /** Pure: the chip row under a shelf page. The first chip is the whole shelf and is not a category. */
    fun parseChips(html: String): List<SourceCategory> =
        Regex("<a class=\"apk-games-chip[^\"]*\" href=\"([^\"]+)\"[^>]*>([^<]*)</a>")
            .findAll(html)
            .mapNotNull { match ->
                val url = match.groupValues[1]
                val name = decodeEntities(match.groupValues[2]).trim()
                if (name.isBlank() || name.equals("All", ignoreCase = true)) return@mapNotNull null
                SourceCategory(
                    source = SourceId.APKVISION,
                    id = url.trimEnd('/').substringAfterLast('/'),
                    name = name,
                    url = url,
                )
            }
            .distinctBy { it.id }
            .toList()

    /**
     * Pure: the card grid a shelf or an update page draws.
     *
     * The site uses one card shape everywhere: art, title, the edition it publishes (a mod note, or
     * nothing) and the version. The card is an anchor with the game's own path in it, so the key and
     * the page url are the same string.
     */
    fun parseGameCards(html: String): List<SourceApp> = html.split("<a ")
        .filter { block -> block.contains("apk-games-card") }
        .mapNotNull { block ->
            val href = Regex("href=\"([^\"]+)\"").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("apk-games-card__title\">([^<]*)<").find(block)?.groupValues?.get(1)
            val version = Regex("apk-games-card__version[^>]*>([^<]*)<").find(block)?.groupValues?.get(1)
            val edition = Regex("apk-games-card__edition\">([^<]*)<").find(block)?.groupValues?.get(1)
            val icon = Regex("<img[^>]*src=\"([^\"]+)\"").find(block)?.groupValues?.get(1)
            SourceApp(
                source = SourceId.APKVISION,
                key = href,
                title = title?.let { Web.text(it) }
                    ?.ifBlank { null }
                    ?: titleFromPageSlug(href.trimEnd('/').substringAfterLast('/')),
                version = version?.trim()?.ifBlank { null },
                subtitle = edition?.trim()?.ifBlank { null },
                iconUrl = icon?.trim()?.ifBlank { null },
                pageUrl = href,
            )
        }
        .distinctBy { it.key }

    override suspend fun files(app: SourceApp): List<SourceFile> {
        val pageUrl = app.pageUrl ?: app.key
        val pageHtml = Web.get(pageUrl)
        val downloadPages = parseDownloadPages(pageHtml, pageUrl)
        if (downloadPages.isEmpty()) return emptyList()

        return downloadPages.flatMap { dlPageUrl ->
            runCatching {
                val dlHtml = Web.get(dlPageUrl, referer = pageUrl)
                parseDirectFiles(dlHtml, dlPageUrl)
            }.getOrDefault(emptyList())
        }.distinctBy { it.url }
    }

    fun extractNonce(html: String): String? =
        Regex("""apkFindConfig\s*=\s*\{.*?"nonce"\s*:\s*"([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)

    fun parseAjaxResponse(json: String): String? = runCatching {
        JSONObject(json).optJSONObject("data")?.optString("html")
    }.getOrNull()

    fun parseSearchCards(html: String): List<SourceApp> {
        val pattern = Regex("""<a class="apk-browse-card" href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.findAll(html).mapNotNull { m ->
            val href = m.groupValues[1]
            val inner = m.groupValues[2]
            val title = Regex("""<span class="apk-browse-card__title">([^<]+)</span>""").find(inner)?.groupValues?.get(1)?.let { Web.text(it) }
                ?: titleFromSlug(href.trimEnd('/').substringAfterLast('/'))
            val version = Regex("""<span class="apk-browse-card__version">([^<]+)</span>""").find(inner)?.groupValues?.get(1)?.trim()?.ifBlank { null }
            val icon = Regex("""<img[^>]*src="([^"]+)"""").find(inner)?.groupValues?.get(1)?.trim()?.ifBlank { null }
            SourceApp(
                source = SourceId.APKVISION,
                key = href,
                title = title,
                version = version,
                iconUrl = icon,
                pageUrl = href,
            )
        }.distinctBy { it.key }.toList()
    }

    fun parseDownloadPages(html: String, base: String): List<String> =
        Regex("""href="([^"]+/download/[^"]+/)"""")
            .findAll(html)
            .map { Web.absolute(base, it.groupValues[1]) }
            .distinct()
            .toList()
            .ifEmpty {
                Regex("""href="([^"]+/download/[^"]+)"""")
                    .findAll(html)
                    .map { Web.absolute(base, it.groupValues[1]) }
                    .distinct()
                    .toList()
            }

    fun parseDirectFiles(html: String, base: String): List<SourceFile> {
        val anchors = Regex("""<a\s+[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        return anchors.findAll(html).mapNotNull { m ->
            val href = m.groupValues[1]
            val rawText = m.groupValues[2]
            if (href.startsWith("#") || href.contains("sai-split") || href.contains("apkvision-store")) {
                return@mapNotNull null
            }
            val isDirect = href.contains("dl.apkvision.org") ||
                Regex("""\.(apk|xapk|zip)$""", RegexOption.IGNORE_CASE).containsMatchIn(href)
            if (!isDirect) return@mapNotNull null

            val cleanText = Web.text(rawText)
            val sizeMatch = Regex("""(\d+(?:\.\d+)?\s*(?:GB|MB|KB))""", RegexOption.IGNORE_CASE).find(cleanText)?.groupValues?.get(1)
            val noteMatch = Regex("""(MOD:[^·]+|Original|Full)""", RegexOption.IGNORE_CASE).find(cleanText)?.groupValues?.get(1)?.trim()
            val label = cleanText.ifBlank { href.substringAfterLast('/') }

            SourceFile(
                label = label,
                url = Web.absolute(base, href),
                size = sizeMatch,
                note = noteMatch,
            )
        }.distinctBy { it.url }.toList()
    }
}

/**
 * The name a page's own path carries, when the card did not draw one.
 *
 * The site's paths end with the post's own number — `neon-slash-135598` — and that number is not
 * part of the app's name, so it is dropped before the words are read.
 */
private fun titleFromPageSlug(slug: String): String {
    val withoutId = slug.replace(Regex("-\\d+$"), "")
    return titleFromSlug(if (withoutId.isBlank()) slug else withoutId)
}
