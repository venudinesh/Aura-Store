package dev.aurastore.store.data

import androidx.compose.runtime.Immutable

import org.json.JSONArray
import org.json.JSONObject

/**
 * Models mirroring the catalogue API contract.
 *
 * Endpoint base: https://liteapks.com/wp-json/v2/  (see blend_blueprint/01_liteapks/api/)
 * Envelope: { "status": Int, "message": String, "data": <payload> }
 */

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key, "")
    return v.ifBlank { null }
}

/**
 * A field that is only a URL when it is one at all.
 *
 * A post with no thumbnail sends `"thumbnail": false`, and `optString` turns any value into text — so the
 * boolean arrives downstream as the string "false", which is a very short relative path and not a picture.
 */
private fun JSONObject.optUrlOrNull(key: String): String? = optStringOrNull(key)?.takeIf {
    it.startsWith("http", ignoreCase = true)
}

/**
 * Catalogue text is WordPress HTML: entities such as `&amp;`, `&#8217;` and `&#038;` appear in
 * titles, genres and slider captions, so they are decoded once here rather than at every call site.
 * Numeric entities are decoded generically — the catalogue uses several different codes for the
 * same punctuation depending on which editor wrote the post.
 */
internal fun String.decodeEntities(): String = this
    .replace(Regex("&#(\\d{1,7});")) { codePoint(it.groupValues[1], 10, it.value) }
    .replace(Regex("&#[xX]([0-9a-fA-F]{1,6});")) { codePoint(it.groupValues[1], 16, it.value) }
    .replace("&quot;", "\"")
    .replace("&nbsp;", " ")
    // Ampersand last, so "&amp;#038;" style double-encoding does not turn into "&#038;" twice.
    .replace("&amp;", "&")
    .trim()

/**
 * A numeric entity as the characters it actually is.
 *
 * `toChar()` truncates anything above the BMP — an emoji written as `&#129513;` is two `Char`s, not one —
 * into a private-use glyph, and a number too large to be a code point would throw rather than be left as
 * the text that failed to decode.
 */
internal fun codePoint(digits: String, radix: Int, asWritten: String): String {
    val point = digits.toIntOrNull(radix) ?: return asWritten
    if (point !in 0..0x10FFFF) return asWritten
    return String(Character.toChars(point))
}

private fun JSONObject.optLongOrZero(key: String): Long = if (has(key) && !isNull(key)) optLong(key, 0L) else 0L
private fun JSONObject.optDoubleOrZero(key: String): Double =
    if (has(key) && !isNull(key)) optDouble(key, 0.0) else 0.0

/**
 * The package a Play listing points at: `…store/apps/details?id=com.spotify.music` gives
 * `com.spotify.music`.
 *
 * This is the only way the catalogue names the app a post actually is. Nothing else in a post
 * carries a package id — the title is display text ("Spotify X Gold"), and the download links are
 * files, not metadata — so it is the query parameter that makes "which installed app does this post
 * belong to" answerable at all. Posts with no Play counterpart send an empty URL.
 */
internal fun playPackageId(url: String?): String? =
    Regex("""[?&]id=([A-Za-z0-9_.]+)""").find(url.orEmpty())
        ?.groupValues?.getOrNull(1)
        ?.takeIf { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) }

data class Slider(
    val id: Long,
    val title: String?,
    val subtitle: String?,
    val banner: String?,
    val thumbnail: String?,
) {
    companion object {
        fun parse(o: JSONObject) = Slider(
            id = o.optLongOrZero("id"),
            title = (o.optStringOrNull("title") ?: o.optStringOrNull("name"))?.decodeEntities(),
            subtitle = o.optStringOrNull("category")?.decodeEntities(),
            banner = o.optStringOrNull("banner"),
            thumbnail = o.optStringOrNull("thumbnail"),
        )
    }
}

/**
 * A server-defined module. `module_type` maps to a renderer and `params` carries the query that
 * fills it, so a screen's sections are decided by the catalogue rather than by this client:
 *
 * | `module_type` | rendering |
 * |---|---|
 * | `three_up` | a titled rail of cards, `limit` wide |
 * | `tab` | a chip row; each `tab_item` child is one ordering of the same list |
 * | `tab_item` | one of those orderings (`order_by`: `modified`, `post_views`, `post_views_week`) |
 * | absent, with `cat_ids` | the category picker, in the catalogue's own order |
 *
 * The `type` field is a different thing — it marks a module that links to a curated collection
 * (`Category`) — so it is kept separately as [contentType] rather than overloading [type].
 */
data class Module(
    val title: String?,
    val type: String,
    val contentType: String?,
    val banner: String?,
    val params: Map<String, String>,
    val items: List<Module>,
) {
    /** `order_by` this module (or tab item) sorts by, when it defines one. */
    val orderBy: String? get() = params["order_by"]?.takeIf { it.isNotBlank() }

    /** The catalogue's own category ordering for a picker module (`cat_ids`). */
    val categoryIds: List<Long>
        get() = params["cat_ids"]?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

    companion object {
        fun parse(o: JSONObject): Module {
            val params = mutableMapOf<String, String>()
            o.optJSONObject("params")?.let { p ->
                for (key in p.keys()) params[key] = p.optString(key)
            }
            val nested = o.optJSONArray("content") ?: JSONArray()
            val items = (0 until nested.length()).mapNotNull { i ->
                nested.optJSONObject(i)?.let { parse(it) }
            }
            return Module(
                title = o.optStringOrNull("title"),
                type = o.optString("module_type", "list"),
                contentType = o.optStringOrNull("type"),
                banner = o.optStringOrNull("banner"),
                params = params,
                items = items,
            )
        }
    }

    /**
     * Query map for GET /v2/posts derived from the module parameters.
     *
     * The `limit` the module asks for is clamped to 30 because that is a page, not a total: a rail that
     * wants more keeps asking, so the ceiling only has to keep one round trip off the wire.
     */
    fun toPostQuery(page: Int = 1, defaultLimit: Int = 12): Map<String, String> {
        val q = mutableMapOf<String, String>()
        params["taxonomy"]?.takeIf { it.isNotBlank() }?.let { q["taxonomy"] = it }
        params["tax_ids"]?.takeIf { it.isNotBlank() }?.let { q["tax_ids"] = it }
        params["order_by"]?.takeIf { it.isNotBlank() }?.let { q["order_by"] = it }
        q["limit"] = (params["limit"]?.toIntOrNull() ?: defaultLimit).coerceIn(1, 30).toString()
        q["page"] = page.coerceAtLeast(1).toString()
        return q
    }
}

data class HomeTab(val title: String, val modules: List<Module>)

data class Home(val sliders: List<Slider>, val tabs: List<HomeTab>) {
    companion object {
        fun parse(o: JSONObject): Home {
            val slidersArr = o.optJSONArray("sliders") ?: JSONArray()
            val sliders = (0 until slidersArr.length()).mapNotNull { i ->
                slidersArr.optJSONObject(i)?.let { Slider.parse(it) }
            }
            val tabsArr = o.optJSONArray("tabs") ?: JSONArray()
            val tabs = (0 until tabsArr.length()).mapNotNull { i ->
                val t = tabsArr.optJSONObject(i) ?: return@mapNotNull null
                val content = t.optJSONArray("content") ?: JSONArray()
                val modules = (0 until content.length()).mapNotNull { j ->
                    content.optJSONObject(j)?.let { Module.parse(it) }
                }
                HomeTab(title = t.optString("title", "Home"), modules = modules)
            }
            return Home(sliders, tabs)
        }
    }
}

@Immutable
data class PostSummary(
    val id: Long,
    val title: String,
    val imageUrl: String?,
    val bannerUrl: String?,
    val publisher: String?,
    val genre: String?,
    val size: String?,
    val type: String?,
    val modInfo: String?,
    /**
     * The catalogue's `lastest_version` (the site's own spelling): the version of the *original* app
     * the post describes — not the label of the build the post ships, which is `versions[].version`.
     */
    val latestVersion: String?,
    /** The package this post's build installs, read from the original Play listing. Null when the catalogue lists none. */
    val packageName: String?,
    val ratingAvg: Double,
    val ratingCount: Int,
    val views: Long,
    val updatedAt: String?,
) {
    companion object {
        fun parse(o: JSONObject): PostSummary {
            val images = o.optJSONObject("images")
            return PostSummary(
                id = o.optLongOrZero("id"),
                title = (o.optStringOrNull("title") ?: o.optStringOrNull("name") ?: "(untitled)").decodeEntities(),
                imageUrl = images?.optUrlOrNull("thumbnail") ?: images?.optUrlOrNull("image"),
                bannerUrl = o.optUrlOrNull("banner") ?: images?.optUrlOrNull("image"),
                publisher = o.optStringOrNull("publisher")?.decodeEntities(),
                genre = o.optStringOrNull("genre")?.decodeEntities(),
                size = o.optStringOrNull("size"),
                type = o.optStringOrNull("type")?.decodeEntities(),
                modInfo = o.optStringOrNull("mod_info")?.decodeEntities(),
                latestVersion = o.optStringOrNull("lastest_version"),
                packageName = playPackageId(o.optStringOrNull("original_download_url")),
                ratingAvg = o.optDoubleOrZero("rating_avg"),
                ratingCount = o.optInt("rating_count", 0),
                views = o.optLongOrZero("post_views"),
                updatedAt = o.optStringOrNull("updated_at"),
            )
        }

        /**
         * The same row as the standard WordPress namespace writes it, which is where a term can be paged.
         *
         * Three things move. The title is an object whose `rendered` field is the text. There is one image,
         * flat on the post, not an `images` block. And the genre, the publisher and the app's type are not
         * words at all: the theme hands them as the post's own CSS classes — `category-arcade`,
         * `developer-mojang`, `app_type-mod` — so they are read back out of the slug. The Play URL is not
         * in this shape, so a row that came in by paging has no package to match against until the user
         * opens it, which is the one field the result line does not show.
         */
        fun parseListing(o: JSONObject): PostSummary {
            val classes = o.optJSONArray("class_list")?.let { list ->
                (0 until list.length()).map { list.optString(it) }
            }.orEmpty()
            return PostSummary(
                id = o.optLongOrZero("id"),
                title = (o.optJSONObject("title")?.optStringOrNull("rendered") ?: "(untitled)").decodeEntities(),
                imageUrl = o.optUrlOrNull("thumbnail"),
                bannerUrl = null,
                publisher = classes.labelWithPrefix("developer-"),
                genre = classes.labelWithPrefix("category-"),
                size = o.optStringOrNull("size"),
                type = classes.labelsWithPrefix("app_type-"),
                modInfo = o.optStringOrNull("mod")?.decodeEntities(),
                latestVersion = o.optStringOrNull("version"),
                packageName = null,
                ratingAvg = o.optDoubleOrZero("rating_avg"),
                ratingCount = 0,
                views = o.optLongOrZero("views"),
                updatedAt = o.optStringOrNull("modified"),
            )
        }
    }
}

/** The theme puts a post's terms in its CSS classes, one per prefix: `category-arcade`, `developer-mojang`. */
private fun List<String>.labelWithPrefix(prefix: String): String? =
    firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.toLabel()

/** Several terms of one kind, in the order the site writes them: `MOD, OFFLINE` for a mod that runs offline. */
private fun List<String>.labelsWithPrefix(prefix: String): String? =
    filter { it.startsWith(prefix) }.joinToString(", ") { it.removePrefix(prefix).toLabel() }.ifBlank { null }

private fun String.toLabel(): String =
    split('-').joinToString(" ") { if (it.isBlank()) it else it.replaceFirstChar { c -> c.uppercaseChar() } }

data class DownloadFile(
    val kind: String,
    val role: String,
    val abi: String,
    val sizeText: String,
    val url: String,
    val note: String?,
    val recommended: Boolean,
) {
    companion object {
        fun parse(o: JSONObject) = DownloadFile(
            kind = o.optString("version_download_type", "apk"),
            role = o.optString("role", "main"),
            abi = o.optString("abi", "universal"),
            sizeText = o.optString("version_download_size", ""),
            url = o.optString("version_download_link", ""),
            note = o.optStringOrNull("version_download_note"),
            recommended = o.optBoolean("recommended", false),
        )
    }
}

data class Version(
    val label: String,
    val note: String?,
    val files: List<DownloadFile>,
) {
    companion object {
        fun parse(o: JSONObject): Version {
            val arr = o.optJSONArray("version_downloads") ?: JSONArray()
            val files = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { DownloadFile.parse(it) }
            }
            return Version(
                label = o.optString("version", "").decodeEntities(),
                note = o.optStringOrNull("version_note")?.decodeEntities(),
                files = files.filter { it.url.isNotBlank() },
            )
        }
    }
}

/**
 * One panel of a post page. The catalogue names these itself — `About`, `Note`, `MOD Info` — and
 * the official client shows them as switchable HTML panels, so the titles come from the server and
 * this client only decides which text to show when there is no matching panel of its own.
 */
data class PostTab(
    val title: String,
    val subTitle: String?,
    val content: String,
    val selected: Boolean,
)

data class PostDetail(
    val summary: PostSummary,
    val description: String,
    val tabs: List<PostTab>,
    val gallery: List<String>,
    val versions: List<Version>,
    val originalUrl: String?,
) {
    /** The panel the catalogue marks selected, else the first one, else the post body. */
    val primaryTab: PostTab?
        get() = tabs.firstOrNull { it.selected } ?: tabs.firstOrNull()

    /** The panel that explains what was modified, whatever this post happens to call it. */
    val modTab: PostTab?
        get() = tabs.firstOrNull { it.title.contains("MOD", true) }
            ?: tabs.firstOrNull { it.title.contains("Note", true) }

    companion object {
        fun parse(o: JSONObject): PostDetail {
            val galleryArr = o.optJSONArray("gallery") ?: JSONArray()
            val gallery = (0 until galleryArr.length()).mapNotNull { galleryArr.optString(it).ifBlank { null } }
            val tabsArr = o.optJSONArray("tabs") ?: JSONArray()
            val tabs = (0 until tabsArr.length()).mapNotNull { i ->
                val t = tabsArr.optJSONObject(i) ?: return@mapNotNull null
                PostTab(
                    title = t.optString("title", "").decodeEntities(),
                    subTitle = t.optStringOrNull("sub_title")?.decodeEntities(),
                    content = t.optString("content", ""),
                    selected = t.optBoolean("isSelected", false),
                )
            }
            val versionsArr = o.optJSONArray("versions") ?: JSONArray()
            val versions = (0 until versionsArr.length()).mapNotNull { i ->
                versionsArr.optJSONObject(i)?.let { Version.parse(it) }
            }
            return PostDetail(
                summary = PostSummary.parse(o),
                description = o.optStringOrNull("content") ?: "",
                tabs = tabs,
                gallery = gallery,
                versions = versions,
                originalUrl = o.optStringOrNull("original_download_url"),
            )
        }
    }
}

/**
 * `GET v2/categories/{id}` — the shape the official client uses for a category screen: the term
 * itself plus a module tree, exactly like the home page. That is why our Browse tab renders rails
 * from here instead of guessing at a flat post list.
 */
data class CategoryPage(
    val id: Long,
    val name: String,
    val description: String?,
    val count: Int,
    val modules: List<Module>,
) {
    companion object {
        fun parse(o: JSONObject): CategoryPage {
            val category = o.optJSONObject("category")
            val content = o.optJSONArray("content") ?: JSONArray()
            return CategoryPage(
                id = category?.optLongOrZero("term_id") ?: 0L,
                name = category?.optStringOrNull("name")?.decodeEntities() ?: "Category",
                description = category?.optStringOrNull("description")?.decodeEntities(),
                count = category?.optInt("count", 0) ?: 0,
                modules = (0 until content.length()).mapNotNull { i ->
                    content.optJSONObject(i)?.let { Module.parse(it) }
                },
            )
        }
    }
}

/** `GET v2/get_latest_version?dev=` — the client's own self-update contract. */
data class LatestVersion(
    val version: String?,
    val forceUpdate: Boolean,
    val downloadUrl: String?,
) {
    companion object {
        fun parse(o: JSONObject) = LatestVersion(
            version = o.optStringOrNull("latest_version"),
            forceUpdate = o.optBoolean("force_update", false),
            downloadUrl = o.optStringOrNull("download_url"),
        )
    }
}

data class Category(
    val id: Long,
    val name: String,
    val slug: String?,
    val parent: Long,
    val count: Int,
    val children: List<Category>,
) {
    /**
     * Everything under this term. The server's own `count` only covers posts tagged with the term
     * itself, so a parent reads as nearly empty: `Games` reports 5 while its 20 children report
     * ~13 000 between them (measured 22 Oct 2026).
     */
    val total: Int get() = count + children.sumOf { it.total }

    companion object {
        private fun parseOne(o: JSONObject): Category {
            val childrenArr = o.optJSONArray("children") ?: JSONArray()
            val children = (0 until childrenArr.length()).mapNotNull { i ->
                childrenArr.optJSONObject(i)?.let { parseOne(it) }
            }
            return Category(
                id = o.optLongOrZero("term_id"),
                name = o.optString("name", "").decodeEntities(),
                slug = o.optStringOrNull("slug"),
                parent = o.optLongOrZero("parent"),
                count = o.optInt("count", 0),
                children = children,
            )
        }

        fun parseList(o: JSONObject): List<Category> {
            val list = o.optJSONArray("list") ?: JSONArray()
            return (0 until list.length()).mapNotNull { i ->
                list.optJSONObject(i)?.let { parseOne(it) }
            }
        }
    }
}
