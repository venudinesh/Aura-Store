package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two records the pack's deletion keeps: which posters are gone, and where a copy comes from. */
class PackLibraryTest {

    @Test
    fun `a removal is stored as the anime id and the poster index`() {
        assertEquals("2:22", PackLibrary.key(SpecialAnime.ONE_PIECE, 22))
        assertEquals("30:0", PackLibrary.key(SpecialAnime.DANDADAN, 0))
        assertEquals("2:22", PackLibrary.key("2", 22))
    }

    @Test
    fun `a stored set round trips and is read back in one piece`() {
        val stored = "2:22,1:0,30:29"
        assertEquals(setOf("1:0", "2:22", "30:29"), PackLibrary.decode(stored))
    }

    @Test
    fun `a corrupted preference costs only the entries that cannot be read`() {
        assertEquals(setOf("2:22"), PackLibrary.decode("2:22, ,nonsense,12,-1:4"))
        assertEquals(emptySet<String>(), PackLibrary.decode(""))
        assertEquals(emptySet<String>(), PackLibrary.decode(null))
    }

    @Test
    fun `a downloaded poster keeps the name it was committed under`() {
        assertEquals("special_poster_2_23.jpg", PackArt.name("2", 22))
        assertEquals("special_poster_30_1.jpg", PackArt.name("30", 0))
    }

    @Test
    fun `a download is the committed file on the branch that holds it`() {
        val url = PackArt.url("17", 4)
        assertTrue(url, url.startsWith("https://"))
        assertTrue(url, url.endsWith("/app/src/main/res/drawable-nodpi/special_poster_17_5.jpg"))
        assertTrue(url, url.contains("venudinesh/Aura-Store/kotlin/"))
    }

    @Test
    fun `the picture that replaces a deleted one is the next on the shelf`() {
        val shelf = listOf(0, 1, 3, 4, 9)
        assertEquals(3, PackLibrary.successor(1, shelf))
        assertEquals(9, PackLibrary.successor(4, shelf))
    }

    @Test
    fun `a deleted last poster hands the profile to the one before it`() {
        assertEquals(4, PackLibrary.successor(9, listOf(0, 4)))
        assertEquals(null, PackLibrary.successor(9, emptyList()))
    }

    @Test
    fun `every anime and poster names a distinct file`() {
        val names = SpecialAnime.entries.flatMap { anime ->
            anime.posters.indices.map { PackArt.name(anime.id, it) }
        }
        assertEquals(900, names.size)
        assertEquals(900, names.toSet().size)
        assertFalse(names.any { it.contains(":") })
    }
}
