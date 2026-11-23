package dev.aurastore.store.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * APKPorts' markup, pinned against the shape the live site served on 23 Nov 2026.
 *
 * The site is a Remix app: one card anchor, one rail section, one download-artifact anchor per
 * file. The download route is the part that can go wrong quietly — it answers an HTML unlock page
 * without an intent — so the file list is tested for the names and sizes the page printed, and the
 * intent is tested for the game it extracts.
 */
class ApkPortsSourceTest {

    private fun fixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name")
        assumeTrue("$name is a page captured locally, not a file the repository publishes", stream != null)
        return stream!!.bufferedReader(Charsets.UTF_8).readText()
    }

    private val card = """
        <a class="game-card" style="text-decoration:none;color:inherit" href="/game/shrek-2-the-game-981d58" data-discover="true">
          <div class="game-image-wrapper"><img class="game-image" srcSet="/uploads/thumbs/cover_1-320.webp 320w, /uploads/thumbs/cover_1-480.webp 480w" sizes="(max-width: 767px) 42vw, 200px" alt="Shrek 2: The Game Android port cover art" loading="eager" src="/uploads/thumbs/cover_1-320.webp" decoding="async"/></div>
          <div class="game-details"><h3 class="game-title">Shrek 2: The Game</h3><div class="game-version">v0.2</div><span class="port-type-badge port-type-badge--unofficial"><i class="fa-solid fa-code-branch"></i> Unofficial</span></div>
        </a>
    """.trimIndent()

    @Test
    fun `reads the game cards a rail or a listing draws`() {
        val apps = ApkPortsSource.parseCards(card)

        assertEquals(1, apps.size)
        assertEquals("Shrek 2: The Game", apps[0].title)
        assertEquals("v0.2", apps[0].version)
        assertEquals("Unofficial", apps[0].subtitle)
        assertEquals("https://apkports.com/game/shrek-2-the-game-981d58", apps[0].key)
        assertEquals(apps[0].key, apps[0].pageUrl)
        assertEquals("https://apkports.com/uploads/thumbs/cover_1-320.webp", apps[0].iconUrl)
        assertEquals(SourceId.APKPORTS, apps[0].source)
    }

    @Test
    fun `a card without a drawn title falls back to its own path`() {
        val bare = card.replace("<h3 class=\"game-title\">Shrek 2: The Game</h3>", "")
        val apps = ApkPortsSource.parseCards(bare)

        assertEquals("Shrek 2 The Game", apps[0].title)
    }

    @Test
    fun `reads the front page's named rails and leaves the category strip out`() {
        val home = """
            <section class="rail-section"><div class="rail-inner">
              <div class="rail-header"><h2 class="section-title"><i class="fa-solid fa-fire"></i>Hot Category</h2>
              <a class="see-more-link" href="/games" data-discover="true">See more</a></div>
              <div class="categories-rail"><a class="category-card" href="/category/action"><i class="fa-solid fa-bolt"></i><span>Action</span></a></div>
            </div></section>
            <section class="rail-section"><div class="rail-inner">
              <div class="rail-header"><h2 class="section-title">Newly Added</h2>
              <a class="see-more-link" href="/games?sort=newest" data-discover="true">See more</a></div>
              $card
            </div></section>
            <section class="rail-section"><div class="rail-inner">
              <div class="rail-header"><h2 class="section-title">Featured Games</h2></div>
              $card
            </div></section>
        """.trimIndent()
        val shelves = ApkPortsSource.parseShelves(home)

        assertEquals(listOf("Newly Added", "Featured Games"), shelves.map { it.title })
        val newest = shelves[0].more as ShelfMore.Category
        assertEquals("https://apkports.com/games?sort=newest", newest.category.url)
        assertEquals("all:newest", newest.category.id)
        // A hand-picked rail publishes no term, so its full list is the whole catalogue.
        assertEquals(ShelfMore.All, shelves[1].more)
    }

    @Test
    fun `reads the hot category rail`() {
        val home = """
            <a class="category-card" href="/category/action" data-discover="true"><i class="fa-solid fa-bolt"></i><span>Action</span></a>
            <a class="category-card" href="/category/role-playing" data-discover="true"><i class="fa-solid fa-dice"></i><span>Role-Playing</span></a>
        """.trimIndent()
        val categories = ApkPortsSource.parseCategories(home)

        assertEquals(listOf("Action", "Role-Playing"), categories.map { it.name })
        assertEquals(listOf("action", "role-playing"), categories.map { it.id })
        assertEquals("https://apkports.com/category/action", categories[0].url)
    }

    @Test
    fun `reads the game page's prose and its screenshots`() {
        val page = """
            <h1 class="modal-title detail-title">Shrek 2: The Game</h1>
            <div class="screenshots-wrapper">
              <div class="screenshot-item"><img alt="screenshot 1" loading="lazy" src="https://img3.pixhost.to/images/7365/813819542_39b5.jpg" decoding="async"/></div>
              <div class="screenshot-item"><img alt="screenshot 2" loading="lazy" src="https://img3.pixhost.to/images/7365/813819737_cbf9.jpg" decoding="async"/></div>
            </div>
            <div class="game-description markdown-body is-collapsed"><h2>Shrek 2: The Game APK for Android</h2>
              <p>Shrek 2 follows the film's story.</p></div>
            <button type="button" class="read-more-btn">Read more</button>
        """.trimIndent()
        val detail = ApkPortsSource.parseDetail(page, "https://apkports.com/game/shrek-2-the-game-981d58")

        assertTrue(detail.description.contains("Shrek 2 follows"))
        assertFalse(detail.description.contains("Read more"))
        assertEquals(2, detail.screenshots.size)
        assertTrue(detail.screenshots.all { it.startsWith("https://img3.pixhost.to/") })
    }

    @Test
    fun `reads every download button with its own name, size and type`() {
        val page = """
            <ol class="download-package-list">
              <li><a class="download-artifact-card tone-standard" href="/dl/981d5834-dbfa-424a-adf2-598cba3b2368/file/85105ab7-14e8-4786-82c6-58b935766e1d"><span class="download-artifact-copy"><strong>Download Shrek2-uni-0.1.0-27.apk</strong><small>Required for this version</small></span><span class="download-artifact-meta"><span class="download-artifact-size">56 MB</span><span class="download-artifact-type">APK</span></span></a></li>
              <li><a class="download-artifact-card tone-data" href="/dl/981d5834-dbfa-424a-adf2-598cba3b2368/file/0f1dffa1-15a3-4fb7-99ed-87b54ee659fa"><span class="download-artifact-copy"><strong>Download Game data rus version</strong><small>Required for this version</small></span><span class="download-artifact-meta"><span class="download-artifact-size">392.50 MB</span><span class="download-artifact-type">DATA</span></span></a></li>
              <li><a class="download-artifact-card tone-standard" href="/dl/b448ac62-9828-4fcf-9094-5b89a283afbb/file/src_0dc2a5a3d0a7e52c01175f2ab553"><span class="download-artifact-copy"><strong>Download APKS v1.4.4 5.33 GB Full APKS</strong><small>Required for this version</small></span><span class="download-artifact-meta"><span class="download-artifact-size">5.3 GB</span><span class="download-artifact-type">APKS</span></span></a></li>
            </ol>
        """.trimIndent()
        val files = ApkPortsSource.parseFiles(page, "https://apkports.com/game/shrek-2-the-game-981d58", "Shrek 2: The Game")

        assertEquals(3, files.size)
        assertEquals("Shrek2-uni-0.1.0-27.apk", files[0].label)
        assertEquals("56 MB", files[0].size)
        assertEquals("APK", files[0].note)
        assertEquals("Shrek2-uni-0.1.0-27.apk", files[0].fileName)

        assertEquals("Game data rus version", files[1].label)
        assertEquals("392.50 MB", files[1].size)
        assertEquals("DATA", files[1].note)
        assertEquals("Shrek 2: The Game - Game data rus version.zip", files[1].fileName)

        assertEquals("APKS v1.4.4", files[2].label)
        assertEquals("5.3 GB", files[2].size)
        assertEquals("APKS", files[2].note)
        assertEquals("Shrek 2: The Game - APKS v1.4.4.apks", files[2].fileName)
        assertTrue(files.all { it.url.startsWith("https://apkports.com/dl/") })
    }

    @Test
    fun `keeps the links whose host serves bytes and drops the pages`() {
        assertTrue(ApkPortsSource.isDirect("https://github.com/a/b/releases/download/v1/Game.apk"))
        assertTrue(ApkPortsSource.isDirect("https://dl.apkvision.org/felvidek/Felvidek-v1.0.4-full-apkvision.apk"))
        assertTrue(ApkPortsSource.isDirect("https://dl.apkvision.net/left-4-dead/L4D.7z"))
        assertTrue(ApkPortsSource.isDirect("https://dl3.apkvision.org/x/y.zip"))
        assertFalse(ApkPortsSource.isDirect("https://vexfile.com/download/q5bRPbzLSj"))
        assertFalse(ApkPortsSource.isDirect("https://drive.google.com/file/d/1P1ZLbzjP7U7U6PUsBd3nOQAoYMVMjw-T/view"))
        assertFalse(ApkPortsSource.isDirect("https://gofile.io/d/abc"))
        assertFalse(ApkPortsSource.isDirect("not a url"))
    }

    @Test
    fun `takes the real name from the storage link`() {
        assertEquals(
            "Left-4-Dead-Game-of-the-Year-Edition-apkvision.7z",
            ApkPortsSource.nameOf("https://dl.apkvision.net/left-4-dead/Left-4-Dead-Game-of-the-Year-Edition-apkvision.7z"),
        )
        assertEquals(
            "Shrek2-uni-0.1.0-27.apk",
            ApkPortsSource.nameOf("https://github.com/a/b/releases/download/v1/Shrek2-uni-0.1.0-27.apk?token=x"),
        )
        assertNull(ApkPortsSource.nameOf("https://dl.apkvision.org/x/85105ab7-14e8-4786-82c6-58b935766e1d"))
        assertEquals(
            "https://apkports.com/dl/1/file/2?intent=abc",
            ApkPortsSource.withIntent("https://apkports.com/dl/1/file/2", "abc"),
        )
    }

    @Test
    fun `finds the game a download link belongs to`() {
        val link = "https://apkports.com/dl/981d5834-dbfa-424a-adf2-598cba3b2368/file/85105ab7-14e8-4786-82c6-58b935766e1d"
        assertEquals("981d5834-dbfa-424a-adf2-598cba3b2368", ApkPortsSource.parseGameId(link))
        assertNull(ApkPortsSource.parseGameId("https://apkports.com/game/shrek-2-the-game-981d58"))
        assertNull(ApkPortsSource.parseGameId("https://apkvision.org/download/1"))
    }

    @Test
    fun `pages a listing with the query the site actually uses`() {
        assertEquals("https://apkports.com/games?sort=newest", ApkPortsSource.pageAddress("https://apkports.com/games?sort=newest", 1))
        assertEquals("https://apkports.com/games?sort=newest&page=2", ApkPortsSource.pageAddress("https://apkports.com/games?sort=newest", 2))
        assertEquals("https://apkports.com/category/action?page=3", ApkPortsSource.pageAddress("https://apkports.com/category/action", 3))
        assertTrue(ApkPortsSource.hasPageAfter("""<a href="/games?sort=newest&amp;page=2">2</a>""", 1))
        assertFalse(ApkPortsSource.hasPageAfter("""<a href="/games?sort=newest">1</a>""", 1))
    }

    // -------------------------------------------------------------- captured pages

    @Test
    fun `the captured front page has its rails and its categories`() {
        val home = fixture("apkports_home.html")
        val shelves = ApkPortsSource.parseShelves(home)
        val categories = ApkPortsSource.parseCategories(home)

        assertEquals(listOf("Newly Added", "Featured Games", "Trending This Week", "Most Popular"), shelves.map { it.title })
        assertTrue(shelves.all { it.apps.isNotEmpty() })
        assertEquals(12, categories.size)
        assertEquals("Action", categories.first().name)
    }

    @Test
    fun `the captured game page has its files with sizes`() {
        val page = fixture("apkports_app.html")
        val files = ApkPortsSource.parseFiles(page, "https://apkports.com/game/shrek-2-the-game-981d58", "Shrek 2: The Game")

        assertEquals(3, files.size)
        assertTrue(files.all { it.size != null })
        assertEquals("Shrek2-uni-0.1.0-27.apk", files[0].fileName)
        assertTrue(files.drop(1).all { it.note == "DATA" })
        assertTrue(files.drop(1).all { it.fileName!!.endsWith(".zip") })
    }
}
