package dev.aurastore.store.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The site adapters are only as good as their selectors, so each parser is pinned against a real
 * page saved from the live site (`app/src/test/resources/fixtures`, captured 18 Oct 2026).
 * A site redesign should fail these rather than silently return nothing.
 */
class WebSourcesTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8)
            .readText()

    // ------------------------------------------------------------------ 9MOD

    @Test
    fun `9mod reads its WordPress search results`() {
        val json = """
            [{"id":783,"title":"Spotify","url":"https://9mod.com/spotify.html"},
             {"id":1,"title":"Spotify Lite","url":"https://9mod.com/spotify-lite.html"}]
        """.trimIndent()
        val apps = NineModSource.parseSearch(json)
        assertEquals(2, apps.size)
        assertEquals("Spotify", apps[0].title)
        assertEquals("https://9mod.com/spotify.html", apps[0].pageUrl)
        assertEquals(SourceId.NINEMOD, apps[0].source)
    }

    @Test
    fun `9mod finds the download page and its per-file pages`() {
        val app = fixture("9mod_app.html")
        val downloadPage = NineModSource.downloadPageOf(app, "https://9mod.com/spotify.html")
        assertEquals("https://9mod.com/download/spotify-783", downloadPage)

        // The base download page lists one link per file (the /1, /2, /3 pages are its children).
        val listing = fixture("9mod_download.html")
        val pages = NineModSource.filePagesOf(listing, downloadPage!!)
        assertEquals(listOf(0, 1, 2), pages.map { it.first })
        assertTrue(pages.all { it.second.startsWith("https://9mod.com/download/spotify-783/") })
    }

    @Test
    fun `9mod decodes the base64 file blob and the post id`() {
        val info = NineModSource.parseFileInfo(fixture("9mod_download_file.html"))
        assertEquals(783L, info.postId)
        assertTrue(info.fileName.endsWith(".apk"))
        assertEquals("198.3 MB", info.size)
        assertTrue(info.note!!.contains("Mod 1"))
    }

    @Test
    fun `9mod carries the post's picture onto the search row`() {
        val json = """
            [{"id":783,"title":"Spotify","url":"https://9mod.com/spotify.html"},
             {"id":1,"title":"Spotify Lite","url":"https://9mod.com/spotify-lite.html"}]
        """.trimIndent()
        val icons = mapOf(783L to "https://9mod.com/wp-content/uploads/2024/06/spotify.webp")
        val apps = NineModSource.parseSearch(json, icons)
        assertEquals("https://9mod.com/wp-content/uploads/2024/06/spotify.webp", apps[0].iconUrl)
        // A post the media route had nothing for keeps the placeholder rather than a wrong picture.
        assertEquals(null, apps[1].iconUrl)
    }

    @Test
    fun `9mod carries the post's picture onto the posts route rows`() {
        val json = """
            [{"id":1601,"title":{"rendered":"WhatsApp Messenger"},
              "link":"https://9mod.com/whatsapp-messenger.html","featured_media":1602}]
        """.trimIndent()
        val icons = mapOf(1601L to "https://9mod.com/wp-content/uploads/2024/06/whatsapp-messenger.webp")
        val apps = NineModSource.parseRestPosts(json, icons)
        assertEquals(1, apps.size)
        assertEquals("WhatsApp Messenger", apps[0].title)
        assertEquals("https://9mod.com/wp-content/uploads/2024/06/whatsapp-messenger.webp", apps[0].iconUrl)
    }

    @Test
    fun `9mod reads the direct file rows off the download page`() {
        val files = NineModSource.directFilesOf(
            fixture("9mod_files.html"),
            "https://9mod.com/download/whatsapp-messenger-1601",
        )
        assertEquals(2, files.size)
        assertEquals("2.26.16.73", files[0].label)
        assertEquals("131.9M", files[0].size)
        assertEquals("apk", files[0].note)
        assertTrue(files[0].url.endsWith("WhatsApp%20Messenger-2.26.16.73.apk"))
        assertEquals("2.26.44.90", files[1].label)
        assertEquals("156.2M", files[1].size)
        assertEquals("xapk", files[1].note)
        assertTrue(files[1].url.endsWith("WhatsApp%20Messenger-2.26.44.90.xapk"))
        assertTrue(files.all { it.url.startsWith("https://gp4.9mod.com/") })
    }

    @Test
    fun `9mod shows percent escaped file names the way a person reads them`() {
        assertEquals(
            "Spotify Lite v1.9.0.72404 (Premium) Fix.apk",
            percentDecode("Spotify%20Lite%20v1.9.0.72404%20%28Premium%29%20Fix.apk"),
        )
        // Anything that is not an escape is left alone, including a lone percent sign.
        assertEquals("100% mod.apk", percentDecode("100% mod.apk"))
        assertEquals("plain.apk", percentDecode("plain.apk"))
    }

    // ----------------------------------------------------------------- 5play

    @Test
    fun `5play search keeps only the result rows`() {
        val apps = FivePlaySource.parseSearch(fixture("5play_search.html"), "https://5play.org/en/")
        // The page also carries a "Recommend" shelf (Minecraft, Toca Boca…) that is not a result.
        assertEquals("expected exactly the two search rows", 2, apps.size)
        assertTrue(apps.all { Regex("/en/\\d+-[a-z0-9-]+\\.html$").containsMatchIn(it.key) })
        val spotify = apps.first { it.key == "https://5play.org/en/2304-spotify-music.html" }
        assertEquals("Spotify", spotify.title)
        assertEquals("v9.0.30", spotify.version)
        assertEquals("Music & Audio", spotify.subtitle)
        assertNotNull("the row's cover is the row's icon", spotify.iconUrl)
        assertFalse(apps.any { it.title.contains("Minecraft") })
    }

    @Test
    fun `5play resolves the signed file url from the cdn page`() {
        val links = FivePlaySource.parseFileLinks(fixture("5play_app.html"), "https://5play.org/en/2304-spotify-music.html")
        assertEquals(1, links.size)
        assertTrue(links[0].first.startsWith("https://5play.org/en/?do=cdn&id="))
        assertTrue(links[0].second.contains("Download"))

        val direct = FivePlaySource.parseCdnLink(fixture("5play_cdn.html"))
        assertNotNull(direct)
        assertTrue(direct!!.contains(".r2.cloudflarestorage.com/"))
        assertTrue(direct.contains("X-Amz-Signature="))
    }

    // ------------------------------------------------------------ GetModsAPK

    @Test
    fun `getmodsapk reads every card under the results heading`() {
        val apps = GetModsApkSource.parseSearch(fixture("getmodsapk_search.html"), "https://getmodsapk.com")
        assertEquals(12, apps.size)
        // Both URL shapes the site uses: with an id and with just a slug.
        assertTrue(apps.any { it.key.endsWith("/8255-spotify-music-mod-apk") })
        assertTrue(apps.any { it.key.endsWith("/spotify-lite-mod-apk") })
        assertTrue(apps.all { !it.key.contains("/download") })
        assertTrue(apps.none { it.key.contains("/page/") })

        val spotify = apps.first { it.key.endsWith("/spotify-x-mod-apk") }
        assertEquals("Spotify", spotify.title)
        assertEquals("v9.1.92.3058", spotify.version)
        assertEquals("45 MB", spotify.size)
        assertNotNull(spotify.iconUrl)
    }

    @Test
    fun `getmodsapk walks download page to mirror to direct file`() {
        val mirrors = GetModsApkSource.parseMirrors(fixture("getmodsapk_download.html"))
        assertTrue(mirrors.size >= 1)
        assertTrue(mirrors.all { it.first.contains("/download/") })
        assertTrue(mirrors.any { it.second.contains("Download") })

        val direct = GetModsApkSource.parseDirectFile(fixture("getmodsapk_mirror.html"))
        assertNotNull(direct)
        assertTrue(direct!!.startsWith("https://files.5modapk.com/"))
        assertTrue(direct.endsWith(".apk"))
    }

    // ------------------------------------------------------------------- AN1

    @Test
    fun `an1 reads search results and the direct file page`() {
        val apps = An1Source.parseSearch(fixture("an1_search.html"), "https://an1.com/")
        assertEquals(2, apps.size)
        assertTrue(apps.any { it.key == "https://an1.com/7586-wobbly-life-mod.html" })
        // The card's title attribute is the site's own name for the app, not the slug.
        assertEquals("Wobbly Life (MOD, Full Game)", apps[0].title)

        val files = An1Source.parseDirectFiles(fixture("an1_file.html"), "https://an1.com/file_7586-dw.html")
        assertTrue(files.any { it.startsWith("https://files.an1.co/") && it.endsWith(".apk") })
    }

    // ------------------------------------------------------------- ApkVision

    @Test
    fun `apkvision reads its browse cards from search response`() {
        val apps = ApkVisionSource.parseSearchCards(fixture("apkvision_cards.html"))
        assertEquals(2, apps.size)
        assertEquals("Subway horror: CHAPTER 1-3", apps[0].title)
        assertEquals("v1.8", apps[0].version)
        assertEquals(SourceId.APKVISION, apps[0].source)
        assertEquals("Subway Surfers", apps[1].title)
        assertEquals("v3.71.10", apps[1].version)
    }

    @Test
    fun `apkvision finds intermediate download pages`() {
        val appHtml = fixture("apkvision_app.html")
        val dlPages = ApkVisionSource.parseDownloadPages(appHtml, "https://apkvision.org/games/role-playing/subway-horror-chapter-1-3-127184/")
        assertEquals(1, dlPages.size)
        assertTrue(dlPages[0].contains("/download/v1.8-apk/"))
    }

    @Test
    fun `apkvision finds direct apk and xapk files on download page`() {
        val dlHtml = fixture("apkvision_download.html")
        val files = ApkVisionSource.parseDirectFiles(dlHtml, "https://apkvision.org/games/role-playing/subway-horror-chapter-1-3-127184/download/v1.8-apk/")
        assertEquals(1, files.size)
        assertTrue(files[0].url.contains("dl.apkvision.org"))
        assertTrue(files[0].url.endsWith(".xapk"))
        assertEquals("181.57 MB", files[0].size)
    }

    // ---------------------------------------------------------------- registry

    @Test
    fun `every source has a distinct id and the catalogue is first`() {
        assertEquals(6, Sources.all.size)
        assertEquals(SourceId.LITEAPKS, Sources.all.first().id)
        assertEquals(Sources.all.size, Sources.all.map { it.id }.distinct().size)
    }

    /** An off switch that still searched would be a lie, so an empty set has to mean none. */
    @Test
    fun `enabled honours the set exactly`() {
        assertEquals(0, Sources.enabled(emptySet()).size)
        assertEquals(
            listOf(SourceId.AN1),
            Sources.enabled(setOf("AN1")).map { it.id },
        )
        assertEquals(
            listOf(SourceId.LITEAPKS, SourceId.NINEMOD),
            Sources.enabled(setOf("LITEAPKS", "NINEMOD", "GONE")).map { it.id },
        )
    }
}
