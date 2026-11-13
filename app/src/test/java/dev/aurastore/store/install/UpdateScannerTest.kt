package dev.aurastore.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule behind "we did not just re-read sixty posts".
 *
 * A verdict stays usable for six hours; anything older, or never checked, is fetched. These two
 * decisions take the clock as a parameter, so the boundary that decides whether a tab opens with a
 * network burst is testable rather than something to notice on a phone.
 */
class UpdateScannerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a verdict is fresh until the ttl expires`() {
        assertTrue(UpdateScanner.isFresh(now - 1_000L, now))
        assertTrue(UpdateScanner.isFresh(now - UpdateScanner.TTL_MS + 1, now))
        assertFalse(UpdateScanner.isFresh(now - UpdateScanner.TTL_MS, now))
    }

    @Test
    fun `an absent or impossible timestamp is never fresh`() {
        assertFalse(UpdateScanner.isFresh(0L, now))
        assertFalse(UpdateScanner.isFresh(-5L, now))
        // A check from the future (a clock that moved backwards) must not lock the list out forever.
        assertFalse(UpdateScanner.isFresh(now + 10L, now))
    }

    @Test
    fun `only what has aged out is fetched again`() {
        val cache = mapOf(
            1L to Checked("1.2.3", now - 60_000L),
            2L to Checked("9.9", now - UpdateScanner.TTL_MS - 1),
            // A post that was checked and answered with no usable label is still a verdict: the TTL
            // is what makes it worth asking again, not the absence of a version.
            3L to Checked(null, now - 60_000L),
        )
        val posts = listOf(1L, 2L, 3L, 4L)

        // Post 1 is fresh; 2 aged out; 3 has a blank but current verdict; 4 was never checked.
        assertEquals(listOf(2L, 4L), UpdateScanner.needingFetch(cache, posts, now))
    }

    @Test
    fun `a forced check ignores the cache and the duplicates`() {
        val cache = mapOf(1L to Checked("1.2.3", now))
        assertEquals(
            listOf(1L, 2L),
            UpdateScanner.needingFetch(cache, listOf(1L, 1L, 2L), now, force = true),
        )
        assertEquals(
            emptyList<Long>(),
            UpdateScanner.needingFetch(cache, listOf(1L, 1L), now, force = false),
        )
    }

    @Test
    fun `a ttl shorter than the stored verdict refetches it`() {
        val cache = mapOf(1L to Checked("1.2.3", now - 5_000L))
        assertEquals(listOf(1L), UpdateScanner.needingFetch(cache, listOf(1L), now, ttl = 4_000L))
        assertEquals(emptyList<Long>(), UpdateScanner.needingFetch(cache, listOf(1L), now, ttl = 6_000L))
    }
}
