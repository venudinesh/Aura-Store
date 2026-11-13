package dev.aurastore.store.ui

import dev.aurastore.store.data.sources.SourceId
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
            Route.Settings,
            Route.Source(SourceId.LITEAPKS),
            Route.Source(SourceId.APKVISION, "action"),
        )
        routes.forEach { route ->
            assertEquals("round trip failed for $route", route, decodeRoute(encodeRoute(route)))
        }
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
        assertEquals(Screen.Settings, Route.Settings.toScreen())
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
    }
}
