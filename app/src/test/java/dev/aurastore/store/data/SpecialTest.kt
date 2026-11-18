package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Special pack: thirty anime, thirty posters each, and the names a choice is stored under. */
class SpecialTest {

    @Test
    fun `every anime holds thirty posters and ids that match their number`() {
        assertEquals(30, SpecialAnime.entries.size)
        SpecialAnime.entries.forEachIndexed { index, anime ->
            assertEquals("${index + 1}", anime.id)
            assertEquals(anime.name, 30, anime.posters.size)
            assertEquals(anime.name, 30, anime.posters.toSet().size)
            assertTrue(anime.name, anime.posters.all { it != 0 })
        }
    }

    @Test
    fun `every poster round trips through its choice`() {
        SpecialAnime.entries.forEach { anime ->
            anime.posters.forEachIndexed { index, _ ->
                val pick = SpecialPick(anime, index)
                assertEquals(pick, SpecialPick.from(pick.choice))
            }
        }
    }

    @Test
    fun `the first pack's one-picture choice still resolves to the first poster`() {
        assertEquals(SpecialAnime.NARUTO, SpecialPick.from("special:1")?.anime)
        assertEquals(0, SpecialPick.from("special:1")?.poster)
        assertEquals(SpecialPick(SpecialAnime.DANDADAN, 0), SpecialPick.from("special:30"))
    }

    @Test
    fun `a drawn face, a photo and the mark are not special`() {
        assertNull(SpecialPick.from(null))
        assertNull(SpecialPick.from(""))
        assertNull(SpecialPick.from(CUSTOM_AVATAR))
        assertNull(SpecialPick.from(GameFace.KNIGHT.choice))
        assertNull(SpecialPick.from("special:99"))
        assertNull(SpecialPick.from("special:0"))
    }

    @Test
    fun `a stored poster below one clamps to the first poster`() {
        assertEquals(SpecialPick(SpecialAnime.NARUTO, 0), SpecialPick.from("special:1:0"))
    }

    @Test
    fun `a poster out of range clamps to the gallery instead of failing`() {
        assertEquals(SpecialPick(SpecialAnime.NARUTO, 29), SpecialPick.from("special:1:99"))
        assertEquals(SpecialPick(SpecialAnime.NARUTO, 0), SpecialPick.from("special:1:-4"))
        assertEquals(SpecialPick(SpecialAnime.ONE_PIECE, 29).art, SpecialPick(SpecialAnime.ONE_PIECE, 40).art)
    }

    @Test
    fun `every anime has a cover style with the same id and the first poster on it`() {
        SpecialAnime.entries.forEach { anime ->
            val style = CoverStyle.forAnime(anime)
            assertNotNull(anime.name, style.anime)
            assertEquals(anime.name, anime, style.anime)
            assertEquals(anime.name, "special-${anime.id}", style.id)
            assertEquals(anime.name, anime.posters.first(), style.art)
            assertTrue(anime.name, style.special)
        }
    }

    @Test
    fun `a cover poster is clamped to the anime's gallery`() {
        val style = CoverStyle.forAnime(SpecialAnime.FRIEREN)
        assertEquals(SpecialAnime.FRIEREN.posters.last(), style.poster(40))
        assertEquals(SpecialAnime.FRIEREN.posters.first(), style.poster(-1))
        assertNull(CoverStyle.INK.poster(3))
    }
}
