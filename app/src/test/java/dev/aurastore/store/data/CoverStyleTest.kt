package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The cover's stored name and the shipped default. */
class CoverStyleTest {

    @Test
    fun `every style round trips through its id`() {
        CoverStyle.entries.forEach { style ->
            assertEquals(style, CoverStyle.from(style.id))
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = CoverStyle.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a fresh install wears the house cover`() {
        assertEquals(CoverStyle.HOUSE, CoverStyle.from(null))
        assertEquals(CoverStyle.HOUSE, CoverStyle.from(""))
        assertEquals(CoverStyle.HOUSE, CoverStyle.from("no-such-cover"))
    }

    @Test
    fun `the drawn covers have no bitmap and the special pack has one`() {
        assertFalse(CoverStyle.HOUSE.special)
        assertFalse(CoverStyle.INK.special)
        val pack = CoverStyle.entries.filter { it.special }
        assertEquals(31, pack.size)
        pack.forEach { style -> assertNotEquals(0, style.art) }
    }
}
