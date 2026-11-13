package dev.aurastore.store.ui.downloads

import dev.aurastore.store.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The download panel's card as a meter: where the fill ends and how strongly it is painted.
 *
 * The docking code itself needs a screen, but the two decisions that make the fill read — the width
 * it reaches and the tone it is painted in — are plain functions, and they are the parts a phone pass
 * cannot inspect at 29 % and 38 % a second apart. These cases pin both, plus the two inputs the
 * engine can hand over without meaning to: a fraction outside 0..1 and a NaN.
 */
class DockProgressTest {

    @Test
    fun `the fill covers the card up to the fraction`() {
        // A 300 dp card: the same three positions the handset was photographed at.
        assertEquals(87f, dockFillWidth(300f, 0.29f), 0.001f)
        assertEquals(114f, dockFillWidth(300f, 0.38f), 0.001f)
        assertEquals(300f, dockFillWidth(300f, 1f), 0.001f)
        assertEquals(0f, dockFillWidth(300f, 0f), 0.001f)
        // Monotonic: a later tick never draws less than an earlier one.
        assertTrue(dockFillWidth(300f, 0.38f) > dockFillWidth(300f, 0.29f))
    }

    @Test
    fun `a fraction outside the card is pinned to its edges`() {
        assertEquals(0f, dockFillWidth(300f, -1.2f), 0.001f)
        assertEquals(300f, dockFillWidth(300f, 4f), 0.001f)
    }

    @Test
    fun `an unmeasured card or a nan fraction draws nothing`() {
        assertEquals(0f, dockFillWidth(0f, 0.5f), 0.001f)
        assertEquals(0f, dockFillWidth(-20f, 0.5f), 0.001f)
        assertEquals(0f, dockFillWidth(300f, Float.NaN), 0.001f)
    }

    @Test
    fun `every row state paints a track, so the fill has an edge`() {
        for (state in DownloadState.entries) {
            for (done in listOf(false, true)) {
                val tone = dockFillTone(state, done)
                assertTrue("$state done=$done needs a track", tone.track > 0.05f)
                assertTrue("$state done=$done fills nothing", tone.start > 0f && tone.end > 0f)
            }
        }
    }

    @Test
    fun `a moving transfer is the loudest fill`() {
        val moving = dockFillTone(DownloadState.DOWNLOADING, done = false)
        assertFalse("moving bytes are not a stopped transfer", moving.muted)
        assertFalse("moving bytes are not a finished one", moving.solid)
        assertEquals(moving, dockFillTone(DownloadState.VERIFYING, done = false))

        val paused = dockFillTone(DownloadState.PAUSED, done = false)
        assertTrue("moving is stronger at the leading edge", moving.start > paused.start)
        assertTrue("and at the trailing edge", moving.end > paused.end)
    }

    @Test
    fun `a paused transfer keeps its fill but greys it`() {
        val paused = dockFillTone(DownloadState.PAUSED, done = false)
        assertTrue("a stopped transfer is muted", paused.muted)
        assertTrue("but the bytes that landed stay drawn", paused.start >= 0.14f)
        // Queued is the same stopped look: nothing is moving there either.
        assertEquals(paused, dockFillTone(DownloadState.QUEUED, done = false))
        assertEquals(paused, dockFillTone(DownloadState.CANCELED, done = false))
    }

    @Test
    fun `a ready file is one flat primary wash`() {
        for (state in listOf(
            DownloadState.READY,
            DownloadState.NEEDS_PERMISSION,
            DownloadState.AWAITING_CONFIRMATION,
        )) {
            val ready = dockFillTone(state, done = true)
            assertEquals("$state is one colour", ready.start, ready.end, 0.001f)
            assertTrue("$state is finished", ready.solid)
            assertFalse("$state is not stopped", ready.muted)
        }
        // done wins even if a caller passes a moving state with a ready flag.
        assertEquals(
            dockFillTone(DownloadState.READY, done = true),
            dockFillTone(DownloadState.DOWNLOADING, done = true),
        )
    }

    @Test
    fun `a queued row draws a track and no fill`() {
        val queued = dockFillTone(DownloadState.QUEUED, done = false)
        assertTrue(queued.track > 0.05f)
        // Progress is zero, so the wash has no width even though it has a colour.
        assertEquals(0f, dockFillWidth(300f, 0f), 0.001f)
        assertTrue(queued.start > 0f)
    }
}
