package dev.aurastore.store.data.sources

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException

/**
 * APKPorts — console and PC games ported to Android.
 *
 * The site is a server-rendered Remix app. Its home draws named rails (*Newly Added*, *Featured
 * Games*, *Trending This Week*, *Most Popular*), its `/games` and `/category/<slug>` pages page a
 * grid with `?page=N`, and its search is `/search?q=<term>` with the same grid.
 *
 * A game page links `/dl/<game>/file/<file>` rather than a storage host. That route serves the
 * bytes only after the site has minted an install intent:
 *
 * ```
 * POST /api/install-intents/<game>   {"promotion":"smartlink"}   -> {"intentToken":"…"}
 * GET  /dl/<game>/file/<file>?intent=<token>                      -> the file
 * ```
 *
 * The token is per game and short lived, so it is minted when the page's file list is read and the
 * address the link redirects to is kept instead of the unlock link: a second unlock inside the
 * site's cooldown is answered with the game page, while the storage address still serves the file.
 * [dev.aurastore.store.data.Hosts] supplies the referer that storage host expects.
 */
object ApkPortsSource : Source {

    override val id = SourceId.APKPORTS
    private const val BASE = "https://apkports.com"
    private const val GAMES_NEWEST = "$BASE/games?sort=newest"

    /** How long the home document is reused, so a section's two reads are one request. */
    private const val HOME_TTL_MS = 60_000L

    private val homeLock = Any()
    private var homeAt = 0L
    private var homeHtml: String? = null

    override suspend fun search(query: String): List<SourceApp> {
        val term = query.trim()
        // The site's own search box requires two characters; one is not a term it answers.
        if (term.length < 2) return emptyList()
        return parseCards(Web.get("$BASE/search?q=${Web.encode(term)}")).take(40)
    }

    /** The site's own newest list, which is the first rail's full page. */
    override suspend fun latest(): List<SourceApp> = parseCards(Web.get(GAMES_NEWEST)).take(20)

    /** The site's front page as the rows it draws, in its own order. */
    override suspend fun shelves(): List<SourceShelf> = parseShelves(home())

    /** The categories the home page's *Hot Category* rail publishes. */
    override suspend fun categories(): List<SourceCategory> = parseCategories(home())

    override suspend fun appsPage(category: SourceCategory, page: Int): SourcePage {
        val base = category.url ?: return SourcePage(emptyList())
        val html = Web.get(pageAddress(base, page))
        return SourcePage(parseCards(html), hasMore = hasPageAfter(html, page))
    }

    override suspend fun allAppsPage(page: Int): SourcePage {
        val html = Web.get(pageAddress(GAMES_NEWEST, page))
        return SourcePage(parseCards(html), hasMore = hasPageAfter(html, page))
    }

    /**
     * The page's download buttons, resolved as far as each host allows.
     *
     * The page links `/dl/<game>/file/<file>` rather than a storage host. Each link is asked where
     * it redirects, with the site's one-shot intent for the game, because a second unlock inside the
     * site's cooldown is answered with the game page.
     *
     * A landing on GitHub or APKVision's storage is the file itself. Google Drive answers a large
     * file with a confirmation form, and Yandex with a public-resource API: both are followed to
     * the address that serves bytes. Every other host — vexfile, Gofile, ModsFire — wraps the file
     * in a page this app cannot press, so its link is kept and marked [SourceFile.viaBrowser].
     * Dropping those was why a page could show one APK and hide the several data archives beside it.
     *
     * The storage address is what the queue gets for an in-app file, not the unlock link: the unlock
     * is spent the moment it is asked for, while the storage address still works an hour later.
     * GitHub and APKVision's storage name their files, which is where the saved name comes from.
     */
    override suspend fun files(app: SourceApp): List<SourceFile> {
        val pageUrl = app.pageUrl ?: app.key
        val parsed = parseFiles(Web.get(pageUrl), pageUrl, app.title)
        if (parsed.isEmpty()) return parsed
        val game = parsed.firstNotNullOfOrNull { parseGameId(it.url) }
        // A failed mint is not an empty page: the buttons still exist, and their own page can finish
        // the unlock in a browser. The files are marked for the browser below instead of vanishing.
        val token = game?.let { runCatching { mintIntent(it) }.getOrNull() }
        return parsed.map { file ->
            val landing = token
                ?.let { runCatching { Web.redirect(withIntent(file.url, it), BASE) }.getOrNull() }
                ?: return@map file.copy(viaBrowser = true)
            val direct = when {
                isDirect(landing) -> landing
                isDrive(landing) -> runCatching { resolveDrive(landing) }.getOrNull()
                isYandex(landing) -> runCatching { resolveYandex(landing) }.getOrNull()
                else -> null
            }
            if (direct != null) {
                file.copy(url = direct, fileName = nameOf(direct) ?: file.fileName)
            } else {
                file.copy(url = landing, viaBrowser = true)
            }
        }
    }

    override suspend fun detail(app: SourceApp): SourceDetail {
        val pageUrl = app.pageUrl ?: app.key
        return parseDetail(Web.get(pageUrl), pageUrl)
    }

    /** The home document, read once for the shelves and the category strip beside them. */
    private fun home(): String = synchronized(homeLock) {
        val now = System.currentTimeMillis()
        val cached = homeHtml
        if (cached != null && now - homeAt < HOME_TTL_MS) {
            cached
        } else {
            Web.get(BASE).also {
                homeHtml = it
                homeAt = now
            }
        }
    }

    /**
     * Pure: the game cards a rail, a listing page or a search page draws.
     *
     * One card is one anchor carrying `game-card`; the title, version and port type sit in the body.
     * The picture's `src` is the 320px cover, which is the art the app page uses.
     */
    fun parseCards(html: String): List<SourceApp> =
        Regex(
            """<a\b([^>]*\bclass="[^"]*\bgame-card\b[^"]*"[^>]*)>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .mapNotNull { match ->
                val href = tagAttribute(match.groupValues[1], "href") ?: return@mapNotNull null
                val body = match.groupValues[2]
                val title = Regex(
                    """<h3\b[^>]*class="[^"]*\bgame-title\b[^"]*"[^>]*>([\s\S]*?)</h3>""",
                    RegexOption.IGNORE_CASE,
                ).find(body)?.groupValues?.get(1)?.let(::decodeEntities)?.trim()?.ifBlank { null }
                    ?: titleFromPortSlug(href.substringBefore('?').trimEnd('/').substringAfterLast('/'))
                val version = Regex(
                    """<div\b[^>]*class="[^"]*\bgame-version\b[^"]*"[^>]*>([\s\S]*?)</div>""",
                    RegexOption.IGNORE_CASE,
                ).find(body)?.groupValues?.get(1)?.let(Web::text)?.ifBlank { null }
                val badge = Regex(
                    """<span\b[^>]*class="[^"]*\bport-type-badge\b[^"]*"[^>]*>([\s\S]*?)</span>""",
                    RegexOption.IGNORE_CASE,
                ).find(body)?.groupValues?.get(1)?.let(Web::text)?.ifBlank { null }
                val image = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).find(body)?.value
                val icon = image?.let {
                    tagAttribute(it, "src") ?: tagAttribute(it, "srcset")?.substringBefore(' ')
                }?.let { Web.absolute(BASE, it) }
                val pageUrl = Web.absolute(BASE, href)
                SourceApp(
                    source = SourceId.APKPORTS,
                    key = pageUrl,
                    title = title,
                    version = version,
                    subtitle = badge,
                    iconUrl = icon,
                    pageUrl = pageUrl,
                )
            }
            .distinctBy { it.key }
            .toList()

    /**
     * Pure: the front page's game rails.
     *
     * The first rail is the category strip and draws no game cards, so it is left out by the same
     * rule that keeps an empty row off the screen. A rail with a *See more* link leads to its own
     * sorted listing; a hand-picked rail without one leads to the whole catalogue.
     */
    fun parseShelves(html: String): List<SourceShelf> =
        Regex(
            """<section\b[^>]*class="[^"]*\brail-section\b[^"]*"[^>]*>([\s\S]*?)</section>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .mapNotNull { match ->
                val section = match.groupValues[1]
                val title = Regex(
                    """<h2\b[^>]*class="[^"]*\bsection-title\b[^"]*"[^>]*>([\s\S]*?)</h2>""",
                    RegexOption.IGNORE_CASE,
                ).find(section)?.groupValues?.get(1)?.let(Web::text)?.ifBlank { null }
                    ?: return@mapNotNull null
                val apps = parseCards(section)
                if (apps.isEmpty()) return@mapNotNull null
                val moreHref = Regex(
                    """<a\b[^>]*class="[^"]*\bsee-more-link\b[^"]*"[^>]*>""",
                    RegexOption.IGNORE_CASE,
                ).find(section)?.value?.let { tagAttribute(it, "href") }
                val more = if (moreHref != null) {
                    ShelfMore.Category(
                        SourceCategory(
                            source = SourceId.APKPORTS,
                            id = "all:" + moreHref.substringAfter("sort=", "newest"),
                            name = title,
                            url = Web.absolute(BASE, moreHref),
                        ),
                    )
                } else {
                    ShelfMore.All
                }
                SourceShelf(title, apps, more)
            }
            .toList()

    /** Pure: the *Hot Category* rail's links, in the site's own order. */
    fun parseCategories(html: String): List<SourceCategory> =
        Regex(
            """<a\b([^>]*\bclass="[^"]*\bcategory-card\b[^"]*"[^>]*)>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .mapNotNull { match ->
                val href = tagAttribute(match.groupValues[1], "href") ?: return@mapNotNull null
                val name = Regex("""<span\b[^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE)
                    .find(match.groupValues[2])?.groupValues?.get(1)?.let(Web::text)?.ifBlank { null }
                    ?: labelFromSlug(href.trimEnd('/').substringAfterLast('/'))
                val url = Web.absolute(BASE, href)
                SourceCategory(
                    source = SourceId.APKPORTS,
                    id = url.trimEnd('/').substringAfterLast('/'),
                    name = name,
                    url = url,
                )
            }
            .distinctBy { it.id }
            .toList()

    /** Pure: the game page's prose and its full-size screenshot strip. */
    fun parseDetail(html: String, base: String): SourceDetail {
        val description = proseAfter(html, "class=\"game-description")
            .ifBlank { metaContent(html, "description") }
        val screenshots = Regex(
            """<div\b[^>]*class="[^"]*\bscreenshot-item\b[^"]*"[^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .flatMap { imageUrlsIn(it.groupValues[1]).asSequence() }
            .map { Web.absolute(base, it) }
            .distinct()
            .toList()
        return SourceDetail(description = description, screenshots = screenshots)
    }

    /**
     * Pure: the download buttons a game page draws.
     *
     * The site labels the button *Download <file>*; the file's own name is what the queue should
     * save. Some buttons print a description instead (*APKS v1.4.4 5.33 GB*), and those get a name
     * made from the app and the artefact's type, because the button never published one. A data
     * archive has no extension at all, so its type gives it one — the queue must know it is not a
     * package to hand to the installer.
     */
    fun parseFiles(
        html: String,
        base: String,
        title: String = "",
    ): List<SourceFile> =
        Regex(
            """<a\b([^>]*\bclass="[^"]*\bdownload-artifact-card\b[^"]*"[^>]*)>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .mapNotNull { match ->
                val href = tagAttribute(match.groupValues[1], "href") ?: return@mapNotNull null
                if (!href.contains("/dl/")) return@mapNotNull null
                val body = match.groupValues[2]
                val printed = Regex("""<strong\b[^>]*>([\s\S]*?)</strong>""", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)?.let(Web::text).orEmpty()
                    .replace(Regex("^Download\\s+", RegexOption.IGNORE_CASE), "")
                    .trim()
                val size = Regex(
                    """<span\b[^>]*class="[^"]*\bdownload-artifact-size\b[^"]*"[^>]*>([\s\S]*?)</span>""",
                    RegexOption.IGNORE_CASE,
                ).find(body)?.groupValues?.get(1)?.let(Web::text)?.ifBlank { null }
                val type = Regex(
                    """<span\b[^>]*class="[^"]*\bdownload-artifact-type\b[^"]*"[^>]*>([\s\S]*?)</span>""",
                    RegexOption.IGNORE_CASE,
                ).find(body)?.groupValues?.get(1)?.let(Web::text)?.uppercase()?.ifBlank { null }
                // A button that printed a size is describing the build, not naming the file; the
                // label is the words before it. The full line is still what the name is built from.
                val label = printed
                    .replace(Regex("""\s*\d+(?:[.,]\d+)?\s*[KMGTP]i?B.*$""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { type ?: "File" }
                SourceFile(
                    label = label,
                    url = Web.absolute(base, href),
                    size = size,
                    note = type,
                    fileName = fileNameFor(printed, label, title, type),
                )
            }
            .distinctBy { it.url }
            .toList()

    /** The game id in a `/dl/<game>/file/<file>` link. */
    fun parseGameId(url: String): String? =
        Regex("""/dl/([0-9a-fA-F-]{36})/file/""").find(url)?.groupValues?.get(1)

    /** A download link with the intent that lets it answer. */
    internal fun withIntent(url: String, token: String): String =
        url + (if ('?' in url) "&" else "?") + "intent=" + Web.encode(token)

    /**
     * Whether a link's destination serves the bytes itself.
     *
     * GitHub hands release assets to `release-assets.githubusercontent.com`; APKVision's own
     * storage answers on `dl.apkvision.org` and `dl.apkvision.net`. Drive and Yandex are not in
     * this list because they need one more step ([resolveDrive], [resolveYandex]); every other host
     * the site uses wraps the file in a page with its own button and is offered as a browser link.
     */
    internal fun isDirect(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.let(DIRECT_HOSTS::matches) == true

    /** True when [url] is a Google Drive file page, which needs the confirmation step. */
    internal fun isDrive(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.let { DRIVE_HOST.matches(it) } == true

    /** True when [url] is a Yandex Disk public link, which the public-resource API can open. */
    internal fun isYandex(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.let { YANDEX_HOST.matches(it) } == true

    /** The Drive file id in a `/file/d/<id>/view` link or an `?id=<id>` link. */
    internal fun driveId(url: String): String? =
        DRIVE_ID.find(url)?.groupValues?.get(1)

    /**
     * Pure: the direct address Drive's own confirmation form points at.
     *
     * A file over the scan limit is answered with a page whose `download-form` carries the signed
     * `uuid` that turns the warning away. Rebuilding that form's action and fields is the same
     * request a browser makes when its *Download anyway* button is pressed.
     */
    internal fun driveFormUrl(html: String): String? {
        val form = Regex(
            """<form\b[^>]*\bid="download-form"[^>]*>([\s\S]*?)</form>""",
            RegexOption.IGNORE_CASE,
        ).find(html) ?: return null
        val action = tagAttribute(form.value, "action") ?: return null
        val fields = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(form.groupValues[1])
            .mapNotNull { input ->
                val name = tagAttribute(input.value, "name") ?: return@mapNotNull null
                name to (tagAttribute(input.value, "value") ?: "")
            }
            .toList()
        return action.toHttpUrlOrNull()?.newBuilder()?.apply {
            fields.forEach { (name, value) -> addQueryParameter(name, value) }
        }?.build()?.toString()
    }

    /** Drive's `/file/d/<id>/view` page into the address that serves its bytes. */
    private fun resolveDrive(landing: String): String? {
        val id = driveId(landing) ?: return null
        var next = "https://drive.google.com/uc?export=download&id=$id"
        // `uc` answers with a 303 to a page that looks like the file until its content type is read;
        // that page carries the form whose `uuid` turns the scan warning away. Three hops is more
        // than Drive uses, and the loop stops the moment a hop serves bytes.
        repeat(3) {
            val hop = Web.peek(next, range = "bytes=0-0")
            hop.location?.let {
                next = it
                return@repeat
            }
            driveFormUrl(hop.body.orEmpty())?.let { return it }
            if (hop.status in 200..299 && hop.contentType?.startsWith("text/html") != true) {
                return next
            }
            return null
        }
        return null
    }

    /** Yandex's public page into the signed download address its API hands out. */
    private fun resolveYandex(landing: String): String? {
        val json = Web.get(
            "https://cloud-api.yandex.net/v1/disk/public/resources/download?public_key=" + Web.encode(landing),
        )
        return runCatching { JSONObject(json).optString("href") }.getOrNull()
            ?.takeIf { it.startsWith("http") }
    }

    /** The filename a storage link ends in, or null when it ends in an id or nothing. */
    internal fun nameOf(url: String): String? {
        // Yandex's signed address ends in `/download`; the name is a query parameter beside it.
        Regex("""[?&](?:filename|file|name)=([^&#]+)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(1)?.let(::percentDecode)
            ?.takeIf { '.' in it && '/' !in it }
            ?.let { return it }
        return percentDecode(url.substringBefore('#').substringBefore('?').substringAfterLast('/'))
            .takeIf { it.isNotBlank() && '.' in it }
    }

    private val DIRECT_HOSTS = Regex(
        """(?:github\.com|objects\.githubusercontent\.com|release-assets\.githubusercontent\.com|dl\d*\.apkvision\.(?:org|net))""",
        RegexOption.IGNORE_CASE,
    )

    private val DRIVE_HOST = Regex("""(?:drive|docs)\.google\.com""", RegexOption.IGNORE_CASE)
    private val DRIVE_ID = Regex("""(?:/file/d/|[?&]id=)([A-Za-z0-9_-]{10,})""")
    private val YANDEX_HOST = Regex("""(?:disk\.yandex\.(?:ru|com)|yadi\.sk)""", RegexOption.IGNORE_CASE)

    /** The site's unlock step: one intent per game, which every file of that game accepts. */
    fun mintIntent(gameId: String): String {
        val body = Web.postJson(
            "$BASE/api/install-intents/$gameId",
            """{"promotion":"smartlink"}""",
            referer = BASE,
        )
        val token = runCatching { JSONObject(body).optString("intentToken") }.getOrNull().orEmpty()
        if (token.isBlank()) throw IOException("APKPorts did not unlock this game's files.")
        return token
    }

    /** `…/games?sort=newest` page two -> `…/games?sort=newest&page=2`; page one is the address given. */
    internal fun pageAddress(base: String, page: Int): String =
        if (page <= 1) base else base + (if ('?' in base) "&" else "?") + "page=$page"

    /** Whether the pagination draws a link to the page after [page]. */
    internal fun hasPageAfter(html: String, page: Int): Boolean =
        Regex("""[?&](?:amp;)?page=${page + 1}\b""").containsMatchIn(html)

    /**
     * The name the queue writes.
     *
     * A button that printed a filename keeps it. One that printed a build description gets the
     * app's own name and the type's extension, so a game's data archive does not land as an
     * extensionless id or, worse, as something the installer is offered.
     */
    private fun fileNameFor(printed: String, label: String, title: String, type: String?): String {
        if (printed.matches(FILE_NAME)) return printed
        val stem = listOf(title, label)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { "APKPorts file" }
        return stem + extensionFor(type)
    }

    /** A button's prose is a filename only when it ends in an extension the site publishes. */
    private val FILE_NAME = Regex(
        """.*\.(apk|apks|xapk|zip|rar|7z|iso|obb|exe|tar|gz|bin)$""",
        RegexOption.IGNORE_CASE,
    )

    private fun extensionFor(type: String?): String = when (type?.uppercase()) {
        "APK" -> ".apk"
        "APKS" -> ".apks"
        "XAPK" -> ".xapk"
        "OBB" -> ".obb"
        "ROMFS" -> ".romfs"
        "ZIP" -> ".zip"
        "RAR" -> ".rar"
        "7Z" -> ".7z"
        "ISO" -> ".iso"
        "EXE" -> ".exe"
        // The site's DATA artefacts are its game data archives, which it serves as a zip.
        "DATA" -> ".zip"
        else -> ".bin"
    }
}

/**
 * The name a page's own path carries when the card did not draw one.
 *
 * APKPorts paths end with a six-character id (`shrek-2-the-game-981d58`), which is not part of the
 * game's name and is dropped before the words are read.
 */
private fun titleFromPortSlug(slug: String): String =
    titleFromSlug(slug.replace(Regex("-[0-9a-f]{6}$"), ""))
