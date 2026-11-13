package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * One page of the searchable catalogue: the shape the standard WordPress namespace writes, and where the
 * paging stands once it has been read.
 *
 * The bodies here are the live ones, trimmed to the ten fields a result line asks for. Everything worth
 * pinning about paging is decided from two things a response carries and one thing it may not: the array
 * of posts, the `X-WP-Total`/`X-WP-TotalPages` headers, and the absence of both when a proxy strips them
 * — which is the case that must still finish.
 */
class SearchPageTest {

    /** A two-row page in the shape `wp/v2/posts?search=…&_fields=…` answers with. */
    private val twoRows = """
        [{"id":977674,"modified":"2026-10-24T21:06:43","title":{"rendered":"Minecraft Dungeons"},
          "class_list":["post-977674","type-post","category-role-playing","developer-mojang","app_type-mod"],
          "thumbnail":"https://liteapks.com/wp-content/uploads/2026/10/icon0-150x150.jpg",
          "mod":"Full Game","version":"1.4","size":"2.8G","rating_avg":3.7,"views":"2955"},
         {"id":11909,"modified":"2026-09-27T01:41:39","title":{"rendered":"Minecraft &#8211; Pocket"},
          "class_list":["post-11909","category-arcade","developer-mojang","app_type-editors-choice","app_type-mod"],
          "thumbnail":"https://liteapks.com/wp-content/uploads/2022/06/minecraft-150x150.png",
          "size":"607M","rating_avg":3.9,"views":2769992}]
    """.trimIndent()

    private fun parsed(
        page: Int = 1,
        pageSize: Int = 20,
        body: String = twoRows,
        total: Int? = null,
        totalPages: Int? = null,
    ) = Api.parseSearchPage(Api.Raw(body, total, totalPages), page, pageSize)

    @Test
    fun `a page of listings becomes the rows a result line shows`() {
        val result = parsed()
        assertEquals(2, result.items.size)
        val dungeons = result.items[0]
        assertEquals(977674L, dungeons.id)
        assertEquals("Minecraft Dungeons", dungeons.title)
        assertEquals("Role Playing", dungeons.genre)
        assertEquals("Mojang", dungeons.publisher)
        assertEquals("Mod", dungeons.type)
        assertEquals("Full Game", dungeons.modInfo)
        assertEquals("1.4", dungeons.latestVersion)
        assertEquals("2.8G", dungeons.size)
        assertEquals(3.7, dungeons.ratingAvg, 0.001)
        assertEquals(2955L, dungeons.views)
        assertEquals("https://liteapks.com/wp-content/uploads/2026/10/icon0-150x150.jpg", dungeons.imageUrl)

        // The entity is the site's own dash, and two app types on one post read as the list the site writes.
        assertEquals("Minecraft – Pocket", result.items[1].title)
        assertEquals("Editors Choice, Mod", result.items[1].type)
        assertEquals(2769992L, result.items[1].views)
        assertNull("this shape does not name the package a post installs", result.items[1].packageName)
        assertNull("a row with no mod line has no mod text", result.items[1].modInfo)
    }

    @Test
    fun `the headers decide when there is another page`() {
        assertTrue("page 1 of 3 has a successor", parsed(page = 1, totalPages = 3).hasMore)
        assertFalse(parsed(page = 3, totalPages = 3).hasMore)
        assertEquals(21, parsed(total = 21).total)
    }

    @Test
    fun `a short page is the last one even when no header says so`() {
        // A CDN that strips X-WP-TotalPages leaves only the count of rows to go on, and a page that came
        // back smaller than it was asked for cannot have a full page of matches left behind it.
        assertFalse(parsed(page = 2, pageSize = 20).hasMore)
        val full = parsed(page = 1, pageSize = 2)
        assertTrue("a page exactly as large as asked for may have a next one", full.hasMore)
        assertNull("and no total was offered", full.total)
    }

    @Test
    fun `a page past the end is the server's own refusal`() {
        val body = """
            {"code":"rest_post_invalid_page_number",
             "message":"The page number requested is larger than the number of pages available.",
             "data":{"status":400}}
        """.trimIndent()
        val error = runCatching { parsed(page = 99, body = body) }.exceptionOrNull()
        assertTrue("got $error", error is IOException)
        assertEquals("The page number requested is larger than the number of pages available.", error!!.message)
    }

    @Test
    fun `a refusal with no message still says what failed`() {
        val error = runCatching { parsed(body = """{"code":"rest_invalid_param","data":{"status":400}}""") }
            .exceptionOrNull()
        assertEquals("Search failed", error!!.message)
    }

    @Test
    fun `the standard namespace hangs off whatever base the catalogue is served from`() {
        // So that pointing the app at a catalogue of your own stays one edit rather than two that can drift.
        assertEquals("https://liteapks.com/wp-json/wp/v2/", Api.wpBase("https://liteapks.com/wp-json/v2/"))
        assertEquals("https://catalogue.example/wp-json/wp/v2/", Api.wpBase("https://catalogue.example/wp-json/v2/"))
    }
}
