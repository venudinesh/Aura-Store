package dev.aurastore.store.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * The shelf parsers: what a source published last, and how it groups what it published.
 *
 * Each one is pinned against the markup the live site served on 11 Nov 2026, trimmed to the shape
 * the parser reads. A site redesign should fail these rather than silently return nothing, because
 * an empty shelf looks exactly like a source with nothing new.
 */
class SourceShelvesTest {

    // ------------------------------------------------------------------ ApkVision

    @Test
    fun `apkvision reads its genre chips and leaves the whole shelf out`() {
        val html = """
            <a class="apk-games-chip is-active" href="https://apkvision.org/games/">All</a>
            <a class="apk-games-chip" href="https://apkvision.org/games/action/">Action</a>
            <a class="apk-games-chip" href="https://apkvision.org/games/role-playing/">Role Playing</a>
        """.trimIndent()
        val categories = ApkVisionSource.parseChips(html)
        assertEquals(2, categories.size)
        assertEquals("Action", categories[0].name)
        assertEquals("action", categories[0].id)
        assertEquals("https://apkvision.org/games/action/", categories[0].url)
        assertEquals(SourceId.APKVISION, categories[0].source)
        // "All" is the whole shelf, not a category of its own.
        assertFalse(categories.any { it.name == "All" })
    }

    @Test
    fun `apkvision reads a card grid with its title, version and edition`() {
        val html = """
            <a data-game-id="101697" class="apk-games-card" href="https://apkvision.org/games/strategy/iron-marines-invasion-101697/">
              <span class="apk-games-card__art"><img width="140" height="140" src="https://apkvision.org/wp-content/uploads/iron.png" class="attachment" /></span>
              <span class="apk-games-card__body">
                <span class="apk-games-card__title">Iron Marines Invasion</span>
                <span class="apk-games-card__edition">MOD: Free Shopping</span>
                <span class="apk-games-card__meta"><time class="apk-games-card__version apk-games-card__updated" datetime="2026-11-11">v1.4.2</time></span>
              </span>
            </a>
        """.trimIndent()
        val apps = ApkVisionSource.parseGameCards(html)
        assertEquals(1, apps.size)
        val app = apps[0]
        assertEquals("Iron Marines Invasion", app.title)
        assertEquals("v1.4.2", app.version)
        assertEquals("MOD: Free Shopping", app.subtitle)
        assertEquals("https://apkvision.org/wp-content/uploads/iron.png", app.iconUrl)
        assertEquals("https://apkvision.org/games/strategy/iron-marines-invasion-101697/", app.pageUrl)
    }

    @Test
    fun `apkvision falls back to the slug when a card carries no title`() {
        val html = """<a class="apk-games-card" href="https://apkvision.org/games/other/neon-slash-135598/"></a>"""
        val apps = ApkVisionSource.parseGameCards(html)
        assertEquals(1, apps.size)
        assertEquals("Neon Slash", apps[0].title)
    }

    // --------------------------------------------------------------------- 5play

    @Test
    fun `5play reads the section a shelf heading belongs to`() {
        val html = """
            <section class="nx-section" aria-labelledby="nx-h-new-games">
              <h2 class="nx-section__title" id="nx-h-new-games">New games</h2>
              <article class="nx-app"><h3 class="nx-app__title"><a href="https://5play.org/en/1-one.html">One</a></h3></article>
            </section>
            <section class="nx-section" aria-labelledby="nx-h-popular-games">
              <h2 class="nx-section__title" id="nx-h-popular-games">Popular games</h2>
              <article class="nx-app"><h3 class="nx-app__title"><a href="https://5play.org/en/2-two.html">Two</a></h3></article>
            </section>
        """.trimIndent()
        val section = FivePlaySource.sectionHtml(html, "nx-h-new-games")
        assertTrue(section.contains("One"))
        assertFalse(section.contains("Two"))
        assertEquals("", FivePlaySource.sectionHtml(html, "nx-h-nothing"))
    }

    @Test
    fun `5play reads a shelf card with its version and its mod note`() {
        val html = """
            <article class="nx-app">
              <div class="nx-appicon"><img src="https://cdn.5play.org/posts/one.webp" alt=""></div>
              <div class="nx-app__badges"><span class="nx-badge nx-badge--mod">Mod</span></div>
              <div class="nx-app__body">
                <h3 class="nx-app__title nx-clamp-2"><a href="https://5play.org/en/2483-winked.html">Winked: Episodes of Romance</a></h3>
                <p class="nx-app__meta"><span class="nx-app__ver" title="v3.14.3">v3.14.3</span></p>
              </div>
            </article>
        """.trimIndent()
        val apps = FivePlaySource.parseShelfCards(html)
        assertEquals(1, apps.size)
        assertEquals("Winked: Episodes of Romance", apps[0].title)
        assertEquals("v3.14.3", apps[0].version)
        assertEquals("MOD", apps[0].subtitle)
        assertEquals("https://cdn.5play.org/posts/one.webp", apps[0].iconUrl)
    }

    // ---------------------------------------------------------------------- AN1

    @Test
    fun `an1 reads its genre menu`() {
        val html = """
            <a href="/games/action/">Action</a>
            <a href="/games/role-playing/">RPG</a>
            <a href="/games/action/">Action again</a>
        """.trimIndent()
        val categories = An1Source.parseCategories(html)
        assertEquals(2, categories.size)
        assertEquals("Action", categories[0].name)
        assertEquals("action", categories[0].id)
        assertEquals("https://an1.com/games/action/", categories[0].url)
        assertEquals("Role Playing", categories[1].name)
    }

    @Test
    fun `an1 reads its app links in the order the page lists them`() {
        val html = """
            <a href="https://an1.com/7109-race-max-pro-mod.html">Race Max Pro</a>
            <a href="https://an1.com/7422-driving-school.html">Driving School</a>
        """.trimIndent()
        val apps = An1Source.parseAppLinks(html)
        assertEquals(2, apps.size)
        assertEquals("Race Max Pro", apps[0].title)
        assertEquals("https://an1.com/7109-race-max-pro-mod.html", apps[0].pageUrl)
    }

    // -------------------------------------------------------------- GetModsAPK

    @Test
    fun `getmodsapk reads its app sitemap in the order it lists it`() {
        val xml = """
            <urlset><url><loc>https://getmodsapk.com/8438-clash-royale-free-mod-apk/</loc><lastmod>2026-11-11</lastmod></url>
            <url><loc>https://getmodsapk.com/3118-marvel-contest-of-champions-mod-apk/</loc></url></urlset>
        """.trimIndent()
        val apps = GetModsApkSource.parseSitemap(xml)
        assertEquals(2, apps.size)
        assertEquals("Clash Royale", apps[0].title)
        assertEquals("https://getmodsapk.com/8438-clash-royale-free-mod-apk/", apps[0].pageUrl)
    }

    @Test
    fun `getmodsapk reads its category sitemap`() {
        val xml = """
            <urlset><url><loc>https://getmodsapk.com/game/strategy</loc></url>
            <url><loc>https://getmodsapk.com/app/tools</loc></url></urlset>
        """.trimIndent()
        val categories = GetModsApkSource.parseCategorySitemap(xml)
        assertEquals(2, categories.size)
        assertEquals("Strategy", categories[0].name)
        assertEquals("strategy", categories[0].id)
        assertEquals("https://getmodsapk.com/strategy", categories[0].url)
        assertEquals("Tools", categories[1].name)
    }

    @Test
    fun `getmodsapk reads the app cards of a category page`() {
        val html = """
            <div class="grid">
              <a href="https://getmodsapk.com/8438-clash-royale-free-mod-apk/">
                <h3>Clash Royale</h3>
                <span class="text-xs text-gray-500">v1.2.3</span>
                <span>Size:</span> <span>120 MB</span>
              </a>
              <a href="https://getmodsapk.com/games/"><h3>Games</h3></a>
            </div>
        """.trimIndent()
        val apps = GetModsApkSource.parseCategoryApps(html)
        assertEquals(1, apps.size)
        assertEquals("Clash Royale", apps[0].title)
        assertEquals("v1.2.3", apps[0].version)
        assertEquals("120 MB", apps[0].size)
    }

    // --------------------------------------------------------------------- 9MOD

    @Test
    fun `9mod reads its newest posts from the WordPress namespace`() {
        val json = """
            [{"id":135647,"link":"https://9mod.com/spotify.html","title":{"rendered":"Spotify"}},
             {"id":135652,"link":"https://9mod.com/angry-birds.html","title":{"rendered":"Angry Birds"}}]
        """.trimIndent()
        val apps = NineModSource.parseRestPosts(json)
        assertEquals(2, apps.size)
        assertEquals("Spotify", apps[0].title)
        assertEquals("https://9mod.com/spotify.html", apps[0].pageUrl)
    }

    @Test
    fun `9mod reads its category terms`() {
        val json = """
            [{"id":3,"name":"Music","count":120,"slug":"music"},
             {"id":4,"name":"Games &amp; Apps","count":0,"slug":"games-apps"}]
        """.trimIndent()
        val categories = NineModSource.parseRestCategories(json)
        assertEquals(2, categories.size)
        assertEquals("Music", categories[0].name)
        assertEquals(120, categories[0].count)
        // A term with nothing in it is still a term, and its count is simply not known.
        assertNull(categories[1].count)
        assertEquals("Games & Apps", categories[1].name)
    }

    @Test
    fun `9mod reads nothing out of an answer that is not json`() {
        assertTrue(NineModSource.parseRestPosts("<html>403</html>").isEmpty())
        assertTrue(NineModSource.parseRestCategories("<html>403</html>").isEmpty())
    }

    // ----------------------------------------------------------------- the hub

    @Test
    fun `a title carries the term the user typed`() {
        assertTrue(SourceHub.titleMatches("Spotify Music", "spotify"))
        assertTrue(SourceHub.titleMatches("VLC for Android", "vlc"))
        assertFalse(SourceHub.titleMatches("Spotify Music", "tiktok"))
        assertFalse(SourceHub.titleMatches(null, "spotify"))
        assertFalse(SourceHub.titleMatches("Anything", "  "))
    }

    // ------------------------------------------------------------ search terms

    @Test
    fun `a run-together word is split and trimmed for an index`() {
        val variants = searchRelaxations("candycrush")
        assertTrue("a run-together word is offered as two", variants.contains("candy crush"))
        assertTrue("its head is offered as the app's name", variants.contains("candyc"))
        assertFalse("the term itself is not a relaxation", variants.contains("candycrush"))
    }

    @Test
    fun `a term with words loses its last word`() {
        assertEquals(listOf("spider-man"), searchRelaxations("spider-man unlimited"))
    }

    @Test
    fun `a short term is not relaxed into a common word`() {
        assertTrue(searchRelaxations("free").isEmpty())
        assertTrue(searchRelaxations("abc").isEmpty())
        assertTrue(searchRelaxations("spider").isEmpty())
    }

    @Test
    fun `a thin answer is asked again with the relaxed term and named rows come first`() = runBlocking {
        val asked = mutableListOf<String>()
        val source = object : Source {
            override val id = SourceId.AN1
            override suspend fun search(query: String): List<SourceApp> {
                asked += query
                return when (query) {
                    // A site's loose match, not the app that was typed.
                    "spiderman" -> listOf(app("Marvel Avengers Academy", "https://an1.com/5291-marvel.html"))
                    "spider" -> listOf(
                        app("Spider Fighter", "https://an1.com/7237-spider-fighter-3-mod.html"),
                        app("Spider-Man Unlimited", "https://an1.com/1-spider-man-unlimited.html"),
                    )
                    else -> emptyList()
                }
            }

            override suspend fun files(app: SourceApp): List<SourceFile> = emptyList()
        }

        val rows = source.searchRelaxed("spiderman")
        assertTrue("the trimmed word must be asked", asked.contains("spider"))
        assertEquals(
            listOf("Spider Fighter", "Spider-Man Unlimited", "Marvel Avengers Academy"),
            rows.map { it.title },
        )
    }

    private fun app(title: String, key: String) =
        SourceApp(source = SourceId.AN1, key = key, title = title)
}
