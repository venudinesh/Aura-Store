package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wallpaper catalogue, checked without a screen.
 *
 * A persisted enum fails quietly in the two ways all of them do: the default moves under an install
 * that never chose, or a label points at a string nobody typed. The picker renders every entry, so
 * what has to hold is that the plain page stays the default, that every choice is distinct, and that
 * each one carries both a name and the line explaining what the eye will see.
 */
class BackgroundThemeTest {

    @Test
    fun `the plain page is the default`() {
        assertEquals(
            "an install that never opens the picker keeps the page it always had",
            BackgroundTheme.NONE,
            BackgroundTheme.entries.first(),
        )
    }

    @Test
    fun `the picker offers the three effects and the way out`() {
        assertEquals(
            listOf(
                BackgroundTheme.NONE,
                BackgroundTheme.SNOW,
                BackgroundTheme.FIREWORKS,
                BackgroundTheme.MOTION,
            ),
            BackgroundTheme.entries,
        )
    }

    @Test
    fun `every choice has its own name and note`() {
        val labels = BackgroundTheme.entries.map { it.label }
        val notes = BackgroundTheme.entries.map { it.description }
        assertEquals("two tiles would read the same", labels.size, labels.distinct().size)
        assertEquals("two notes would say the same", notes.size, notes.distinct().size)
        assertTrue("every label must name a string", labels.all { it != 0 })
        assertTrue("every note must name a string", notes.all { it != 0 })
    }
}
