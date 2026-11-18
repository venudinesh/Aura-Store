package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ring's stored name, and the promise that a missing one is no ring at all. */
class AvatarFrameTest {

    @Test
    fun `every frame round trips through its id`() {
        AvatarFrame.entries.forEach { frame ->
            assertEquals(frame, AvatarFrame.from(frame.id))
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = AvatarFrame.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a missing or unknown id falls back to none`() {
        assertEquals(AvatarFrame.NONE, AvatarFrame.from(null))
        assertEquals(AvatarFrame.NONE, AvatarFrame.from(""))
        assertEquals(AvatarFrame.NONE, AvatarFrame.from("not-a-frame"))
    }

    @Test
    fun `only the rings that move are marked animated`() {
        val still = setOf(
            AvatarFrame.NONE,
            AvatarFrame.RING,
            AvatarFrame.GOLD,
            AvatarFrame.LAUREL,
        )
        AvatarFrame.entries.forEach { frame ->
            assertEquals(frame.name, frame !in still, frame.animated)
        }
        assertFalse(AvatarFrame.NONE.animated)
        assertTrue(AvatarFrame.EMBER.animated)
        assertTrue(AvatarFrame.ZENITH.animated)
        assertEquals(69, AvatarFrame.entries.size)
    }

    @Test
    fun `every anime in the pack has one ring and no ring has two`() {
        val anime = AvatarFrame.entries.mapNotNull { it.anime }
        assertEquals(SpecialAnime.entries.size, anime.size)
        assertEquals(SpecialAnime.entries.toSet(), anime.toSet())
        AvatarFrame.entries.filter { it.anime != null }.forEach { frame ->
            val entry = frame.anime
            assertNotNull(frame.name, entry)
            assertEquals(frame.name, entry?.label, frame.label)
            assertTrue(frame.name, frame.animated)
            assertTrue(frame.name, frame.id.startsWith("anime-"))
        }
    }
}
