package dev.aurastore.store.ui

import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The route stack, tested where it can be: the encoding, and the two rules the shell leans on.
 *
 * The bug this replaced was not a wrong comparison, it was a missing history — so the tests that
 * matter are the ones about *order*: a stack of three decodes back to three, in the same order, and
 * an entry this build cannot read is dropped rather than guessed at.
 */
class RouteTest {

    @Test
    fun `every route survives a round trip`() {
        val routes = listOf(
            Route.Detail(519765L),
            Route.Download("2f8a1c3e-0000-4000-8000-000000000001"),
            Route.Category(0L),
            Route.Category(482L),
            Route.Listing(
                "Editor's Choice",
                mapOf("taxonomy" to "app_type", "tax_ids" to "50", "limit" to "9"),
            ),
            Route.Settings,
            Route.SettingsGroup(SettingsSection.NAVIGATION),
            Route.Downloads,
            Route.Manage,
            Route.Device,
            Route.About,
            Route.Wishlist,
            Route.Source(SourceId.LITEAPKS),
            Route.Source(SourceId.APKVISION, "action"),
            Route.SourcePage(
                SourceApp(
                    source = SourceId.FIVEPLAY,
                    key = "https://5play.org/en/2304-spotify-music.html",
                    title = "Spotify",
                    version = "v9.0.30",
                    size = "42 MB",
                    subtitle = "Music & Audio",
                    iconUrl = "https://cdn.5play.org/icon.png",
                    pageUrl = "https://5play.org/en/2304-spotify-music.html",
                    cataloguePostId = 519765L,
                ),
            ),
            // A row that has only what its card showed still has to come back a page, not nothing.
            Route.SourcePage(SourceApp(source = SourceId.AN1, key = "https://an1.com/1-app.html", title = "App")),
        )
        routes.forEach { route ->
            assertEquals("round trip failed for $route", route, decodeRoute(encodeRoute(route)))
        }
    }

    @Test
    fun `a listing keeps query values that look like separators`() {
        val route = Route.Listing("New & updated", mapOf("order_by" to "a&b=c", "tax_ids" to "12"))
        assertEquals(route, decodeRoute(encodeRoute(route)))
    }

    @Test
    fun `a stack keeps its order through encode and decode`() {
        // The whole point of the stack: back walks it from the top, so order is the information.
        val stack = listOf(
            Route.Category(482L),
            Route.Detail(519765L),
            Route.Download("abc"),
        ).map(::encodeRoute)

        val decoded = stack.mapNotNull(::decodeRoute)

        assertEquals(
            listOf(Route.Category(482L), Route.Detail(519765L), Route.Download("abc")),
            decoded,
        )
    }

    @Test
    fun `an unreadable entry decodes to null rather than a guess`() {
        // A stack written by a future build, or a hand-edited bundle. Dropping the entry lands the
        // user on the page below it; inventing one would open something they never asked for.
        assertNull(decodeRoute(""))
        assertNull(decodeRoute("nonsense"))
        assertNull(decodeRoute("detail"))
        assertNull(decodeRoute("detail\u0001not-a-number"))
        assertNull(decodeRoute("source\u0001NOT_A_SOURCE"))
        assertNull(decodeRoute("download\u0001"))
        assertNull(decodeRoute("settings-group\u0001NOT_A_SECTION"))
        assertNull(decodeRoute("settings-group"))
    }

    @Test
    fun `settings does not stack on itself`() {
        assertFalse(stacksOn(Route.Settings, Route.Settings))
    }

    @Test
    fun `two different pages stack`() {
        // Two app pages in a row is a walk through related apps and a real history: back must return
        // to the previous app, so it stacks.
        assertTrue(stacksOn(Route.Detail(1L), Route.Detail(2L)))
        assertTrue(stacksOn(Route.Category(1L), Route.Detail(1L)))
        assertTrue(stacksOn(null, Route.Settings))
    }

    @Test
    fun `a route draws the screen the shell animates to`() {
        assertEquals(Screen.Detail(7L), Route.Detail(7L).toScreen())
        assertEquals(Screen.Category(0L), Route.Category(0L).toScreen())
        assertEquals(
            Screen.Listing("Editor's Choice", mapOf("tax_ids" to "50")),
            Route.Listing("Editor's Choice", mapOf("tax_ids" to "50")).toScreen(),
        )
        assertEquals(Screen.Settings, Route.Settings.toScreen())
        assertEquals(
            Screen.SettingsGroup(SettingsSection.NAVIGATION),
            Route.SettingsGroup(SettingsSection.NAVIGATION).toScreen(),
        )
        assertEquals(Screen.Downloads, Route.Downloads.toScreen())
        assertEquals(Screen.Manage, Route.Manage.toScreen())
        assertEquals(Screen.Device, Route.Device.toScreen())
        assertEquals(Screen.About, Route.About.toScreen())
        assertEquals(Screen.Wishlist, Route.Wishlist.toScreen())
        assertEquals(Screen.Download("x"), Route.Download("x").toScreen())
        // The query belongs to the visit, not to the stack entry.
        assertEquals(
            Screen.Source(SourceId.LITEAPKS, "spotify"),
            Route.Source(SourceId.LITEAPKS).toScreen("spotify"),
        )
        // A category of the source's own grouping is part of the page it opens.
        assertEquals(
            Screen.Source(SourceId.APKVISION, "", "action"),
            Route.Source(SourceId.APKVISION, "action").toScreen(),
        )
        val app = SourceApp(source = SourceId.NINEMOD, key = "https://9mod.com/spotify.html", title = "Spotify")
        assertEquals(Screen.SourcePage(app), Route.SourcePage(app).toScreen())
    }
}
