package dev.aurastore.store.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The package id is the one field that ties a listing to an app on the device, and the catalogue
 * hides it inside a Play URL rather than publishing it as its own key. These fixtures are the live
 * shapes: a listing with a counterpart, a listing without one, and a listing whose URL is not a
 * listing at all.
 */
class PostSummaryTest {

    private fun post(id: Long, title: String, version: String?, originalUrl: String?) = PostSummary.parse(
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("lastest_version", version ?: JSONObject.NULL)
            .put("original_download_url", originalUrl ?: JSONObject.NULL)
            .put("size", "72M"),
    )

    @Test
    fun `a play listing names the package it is the page for`() {
        val spotify = post(98, "Spotify", "9.1.94.1912", "https://play.google.com/store/apps/details?id=com.spotify.music")
        assertEquals("com.spotify.music", spotify.packageName)
        assertEquals("9.1.94.1912", spotify.latestVersion)
    }

    @Test
    fun `a package id is read wherever it sits in the query`() {
        val withMore = "https://play.google.com/store/apps/details?hl=en&id=com.roblox.client&token=abc"
        assertEquals("com.roblox.client", playPackageId(withMore))
    }

    @Test
    fun `a mod with no play counterpart has no package`() {
        // The live catalogue sends an empty string for these, not a missing key.
        assertNull(post(519765, "Spotify X Gold", "9.1.94.1912", "").packageName)
        assertNull(post(1, "No url at all", "1.0", null).packageName)
    }

    @Test
    fun `something that is not a package id is rejected`() {
        assertNull(playPackageId("https://play.google.com/store/apps/details?id=not-a-package"))
        assertNull(playPackageId("https://play.google.com/store/apps/details?id=singleword"))
        assertNull(playPackageId("https://liteapks.com/spotify/"))
        assertNull(playPackageId(null))
    }

    @Test
    fun `a post with no picture does not offer the word false as one`() {
        // Live from `v2/posts/21644`: a post without a featured image sends `"thumbnail": false` inside
        // `images`, and any value read as text is the string "false" — which is a path, and not a picture.
        val bare = PostSummary.parse(
            JSONObject()
                .put("id", 21644)
                .put("title", "Omlet Arcade")
                .put("banner", false)
                .put("images", JSONObject().put("thumbnail", false).put("image", false)),
        )
        assertNull(bare.imageUrl)
        assertNull(bare.bannerUrl)

        val withPicture = PostSummary.parse(
            JSONObject()
                .put("id", 11909)
                .put("title", "Minecraft")
                .put("images", JSONObject().put("thumbnail", "https://liteapks.com/a-150x150.png")),
        )
        assertEquals("https://liteapks.com/a-150x150.png", withPicture.imageUrl)
    }

    @Test
    fun `a paged listing reads the row out of the shape the theme writes`() {
        // The paged source is WordPress, not the plugin: the title is an object, the picture is one flat
        // field, and the genre, publisher and type only exist as the post's own CSS classes.
        val listing = PostSummary.parseListing(
            JSONObject()
                .put("id", 11909)
                .put("title", JSONObject().put("rendered", "Minecraft &amp; Friends"))
                .put("thumbnail", "https://liteapks.com/minecraft-150x150.png")
                .put("size", "607M")
                .put("version", "1.26.70.21")
                .put("rating_avg", 3.9)
                .put("views", "2769992")
                .put("modified", "2026-09-27 01:41:39")
                .put(
                    "class_list",
                    JSONArray()
                        .put("post-11909")
                        .put("type-post")
                        .put("status-publish")
                        .put("category-arcade")
                        .put("developer-mojang")
                        .put("app_type-mod")
                        .put("app_type-online"),
                ),
        )
        assertEquals("Minecraft & Friends", listing.title)
        assertEquals("https://liteapks.com/minecraft-150x150.png", listing.imageUrl)
        assertEquals("Arcade", listing.genre)
        assertEquals("Mojang", listing.publisher)
        assertEquals("Mod, Online", listing.type)
        assertEquals("1.26.70.21", listing.latestVersion)
        assertEquals("607M", listing.size)
        // Views is a number from the plugin and a string from WordPress; a row reads the same either way.
        assertEquals(2769992L, listing.views)
        assertEquals("2026-09-27 01:41:39", listing.updatedAt)
        assertNull("and nothing here says which package the build installs", listing.packageName)
    }

    @Test
    fun `a paged listing with neither classes nor a picture is still a row`() {
        val listing = PostSummary.parseListing(
            JSONObject().put("id", 5).put("title", JSONObject().put("rendered", "No Terms")),
        )
        assertEquals("No Terms", listing.title)
        assertNull(listing.imageUrl)
        assertNull(listing.genre)
        assertNull(listing.publisher)
        assertNull(listing.type)
        assertEquals(0.0, listing.ratingAvg, 0.001)
        assertEquals(0L, listing.views)
    }
}
