package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(38, AvatarFrame.entries.size)
    }
}
