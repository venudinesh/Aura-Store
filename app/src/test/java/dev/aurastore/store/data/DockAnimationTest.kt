package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download meter catalogue, checked without a screen.
 *
 * The choose-one page renders every entry, so what has to hold is that there are enough styles to
 * make the page worth a visit, that no two rows say the same thing, and that every row carries a note
 * explaining what the eye will see. The shipped wash stays first so an install that never chooses
 * keeps the default it already had.
 */
class DockAnimationTest {

    @Test
    fun `the panel offers at least ten meters`() {
        assertTrue(
            "the picker promises a choice, not a pair",
            DockAnimation.entries.size >= 10,
        )
    }

    @Test
    fun `the shipped wash is still the first style`() {
        assertEquals(DockAnimation.WASH, DockAnimation.entries.first())
    }

    @Test
    fun `the limited editions are a second group of twelve`() {
        val limited = DockAnimation.entries.filter { it.limited }
        val standard = DockAnimation.entries.filterNot { it.limited }
        assertEquals("the limited set is one group", 12, limited.size)
        assertEquals("the standard set is still twelve", 12, standard.size)
        assertEquals(
            "the two groups never interleave",
            DockAnimation.entries.takeLast(12),
            limited,
        )
    }

    @Test
    fun `every meter has its own name`() {
        val labels = DockAnimation.entries.map { it.label }
        assertEquals("two meters share a name", labels.size, labels.toSet().size)
    }

    @Test
    fun `every meter has its own note`() {
        val notes = DockAnimation.entries.map { it.description }
        assertEquals("two meters share a note", notes.size, notes.toSet().size)
    }
}
