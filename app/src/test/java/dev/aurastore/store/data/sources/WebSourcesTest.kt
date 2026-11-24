package dev.aurastore.store.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The site adapters are only as good as their selectors, so each parser is pinned against a real
 * page saved from the live site (`app/src/test/resources/fixtures`, captured 18 Oct 2026). The
 * captures are third-party markup and stay on the machine that saved them, so a clone without them
 * skips these tests instead of failing them; a site redesign still fails them where the pages exist.
 */
class WebSourcesTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")
        assumeTrue("$name is a page captured locally, not a file the repository publishes", stream != null)
        return stream!!.bufferedReader(Charsets.UTF_8).readText()
    }

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

    @Test
    fun `5play reads the current nx-file download row`() {
        val page = """
            <a class="nx-file" href="https://5play.org/en/download/174821-clockmaker-107.0.0-mod-t-5play.html" rel="nofollow" data-file-id="174821">
                <span class="nx-file__kind" aria-hidden="true"><svg class="nx-i"><use href="#i-package"/></svg></span>
                <span class="nx-file__body">
                    <span class="nx-file__title">Download Clockmaker - Amazing Match 3 v107.0.0 (MOD Free shopping).apk</span>
                    <span class="nx-file__meta"><span class="nx-file__ext">apk</span> · 142.25 Mb</span>
                </span>
                <span class="nx-file__go" aria-hidden="true"><svg class="nx-i"><use href="#i-download"/></svg></span>
            </a>
        """.trimIndent()

        val links = FivePlaySource.parseFileLinks(
            page,
            "https://5play.org/en/5344-clockmaker-amazing-match-3.html",
        )

        assertEquals(1, links.size)
        assertEquals(
            "https://5play.org/en/download/174821-clockmaker-107.0.0-mod-t-5play.html",
            links.single().first,
        )
        assertTrue(links.single().second.contains("Clockmaker"))
        assertTrue(links.single().second.contains("142.25 Mb"))
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

        // The size sits in the button beside the link, and it is the only place the file page prints it.
        val entries = An1Source.parseDirectFileEntries(fixture("an1_file.html"), "https://an1.com/file_7586-dw.html")
        assertEquals(files, entries.map { it.first })
        assertEquals("1765.4Mb", entries.first { it.first.endsWith(".apk") }.second)
    }

    /**
     * Every other source prints the size in the button's own prose, in its own spelling: 5play says
     * `Download apk (78.28 Mb)`, GetModsAPK `Download (168 MB)`, AN1 `Download (1765.4Mb)`. A row that
     * drops it leaves the file card with a name and no size, which is the one fact a person needs
     * before starting a multi-gigabyte zip.
     */
    @Test
    fun `a file button's size is read from its prose`() {
        assertEquals("78.28 Mb", sizeInText("Download apk (78.28 Mb)"))
        assertEquals("168 MB", sizeInText("Download (168 MB)"))
        assertEquals("1765.4Mb", sizeInText("Download (1765.4Mb)"))
        assertEquals("1.3 GB", sizeInText("Server 2 · 1.3 GB · RAR"))
        assertNull(sizeInText("Download"))
        assertNull(sizeInText("APKVision Server 1"))

        assertEquals("wobbly-life-mod_1.1.0.15-an1.com.apk", fileNameFromUrl("https://files.an1.co/wobbly-life-mod_1.1.0.15-an1.com.apk"))
        assertNull(fileNameFromUrl("https://5play.org/en/?do=cdn&id=170715"))
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
        assertEquals("subway-horror-chapter-1-3-v1.8-apkvision.xapk", files[0].label)
    }

    /**
     * The ports put their game data on the second storage host as `.rar`, `.7z` or `.iso`, and the
     * newest split bundles as `.apks`. None of those used to pass the extension filter, so a page
     * whose only package was an `.apks`, or whose data was a `.rar`, listed fewer files than the
     * page itself drew.
     */
    @Test
    fun `apkvision reads every file its buttons publish, not only apk and zip`() {
        val page = """
            <a class="fdl-btn" href="https://dl.apkvision.org/sifu/Sifu-v1.0.apks">Download APKS 1.2 GB</a>
            <a class="fdl-btn" href="https://dl.apkvision.net/darkwatch/Darkwatch-apkvision.rar">Download RAR · APKVision Server 1 1.3 GB · RAR</a>
            <a class="fdl-btn" href="https://dl2.apkvision.net/darkwatch/Darkwatch-apkvision.rar">Download RAR · APKVision Server 2 1.3 GB · RAR</a>
            <a class="fdl-btn" href="https://dl.apkvision.net/forza/Forza-apkvision.7z">Download 7Z 2.4 GB</a>
            <a class="fdl-btn" href="https://dl.apkvision.net/metroid/Metroid-Prime.iso">Download ISO 700 MB</a>
            <a class="fdl-btn" href="https://dl.apkvision.net/deus-ex/deus_ex_goty.exe">Download EXE 1.1 GB</a>
            <a href="https://apkvision.org/games/action/sifu-134807/">Sifu</a>
            <a href="https://apkvision.org/how-to-install-apks/">How to install</a>
        """.trimIndent()
        val files = ApkVisionSource.parseDirectFiles(page, "https://apkvision.org/games/action/darkwatch-135941/download/v1.0-obb/")

        assertEquals(6, files.size)
        assertEquals(
            listOf("Sifu-v1.0.apks", "Darkwatch-apkvision.rar", "Forza-apkvision.7z", "Metroid-Prime.iso", "deus_ex_goty.exe"),
            files.map { it.label }.distinct(),
        )
        // Two mirrors are two rows; the page said which host each one is.
        assertEquals(2, files.count { it.label == "Darkwatch-apkvision.rar" })
        assertTrue(files.any { it.note == "APKVision Server 1" })
        assertTrue(files.any { it.note == "APKVision Server 2" })
        assertEquals("1.3 GB", files.first { it.label == "Darkwatch-apkvision.rar" }.size)
        assertEquals("2.4 GB", files.first { it.label == "Forza-apkvision.7z" }.size)
        // The site's own pages are not files, even when an older check would have taken a path for one.
        assertTrue(files.none { it.url.contains("sifu-134807") || it.url.contains("how-to-install") })
    }

    // ------------------------------------------------------- app pages

    /**
     * A row only carries a card. The page behind it carries the prose and the pictures, and these are
     * the selectors that find them on each site's live markup.
     */
    @Test
    fun `5play detail reads the long description and the full-size screenshots`() {
        val detail = FivePlaySource.parseDetail(fixture("5play_detail.html"), "https://5play.org/en/")
        assertTrue("description was blank", detail.description.length > 200)
        assertTrue(detail.description.contains("Brawl Stars", ignoreCase = true))
        assertTrue("no screenshots", detail.screenshots.size >= 2)
        assertTrue(detail.screenshots.all { it.startsWith("https://cdn.5play.org/") })
        // The strip shows a medium copy in the img; the anchor is the full file.
        assertTrue("thumbnail was taken as the screenshot", detail.screenshots.none { it.contains("/medium/") })
        assertEquals(detail.screenshots.size, detail.screenshots.distinct().size)
    }

    @Test
    fun `an1 detail reads the spoiler prose and the screenshot metas`() {
        val detail = An1Source.parseDetail(fixture("an1_detail.html"), "https://an1.com/")
        assertTrue("description was blank", detail.description.length > 200)
        assertTrue(detail.description.contains("Bowmasters", ignoreCase = true))
        // The description div also holds the FAQ and the sharing block; the cut is at the screenshots.
        assertFalse(detail.description.contains("Comments", ignoreCase = true))
        assertTrue("no screenshots", detail.screenshots.size >= 3)
        assertTrue(detail.screenshots.all { it.startsWith("https://an1.com/uploads/screenshots/") })
        assertTrue(detail.screenshots.none { it.contains("/thumbs/") })
    }

    @Test
    fun `getmodsapk detail reads the post content and the lightbox anchors`() {
        val detail = GetModsApkSource.parseDetail(fixture("getmodsapk_detail.html"), "https://getmodsapk.com/")
        assertTrue("description was blank", detail.description.length > 200)
        assertTrue(detail.description.contains("Clash Royale", ignoreCase = true))
        assertTrue("no screenshots", detail.screenshots.size >= 2)
        assertTrue(detail.screenshots.all { it.startsWith("https://getmodsapk.com/storage/media/") })
        assertEquals(detail.screenshots.size, detail.screenshots.distinct().size)
    }

    @Test
    fun `apkvision detail reads the post description and its structured screenshots`() {
        val detail = ApkVisionSource.parseDetail(fixture("apkvision_detail.html"), "https://apkvision.org/")
        assertTrue("description was blank", detail.description.length > 200)
        assertTrue(detail.description.contains("WWE", ignoreCase = true))
        assertTrue("no screenshots", detail.screenshots.size >= 3)
        assertTrue(detail.screenshots.all { it.startsWith("https://img.apkvision.org/") })
        assertEquals(detail.screenshots.size, detail.screenshots.distinct().size)
    }

    @Test
    fun `9mod detail reads the entry content and the images it carries`() {
        val detail = NineModSource.parseDetail(fixture("9mod_app.html"), "https://9mod.com/")
        assertTrue("description was blank", detail.description.isNotBlank())
        assertTrue(detail.screenshots.all { it.startsWith("http") })
    }

    // ---------------------------------------------------------------- registry

    @Test
    fun `every source has a distinct id and the catalogue is first`() {
        assertEquals(7, Sources.all.size)
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
