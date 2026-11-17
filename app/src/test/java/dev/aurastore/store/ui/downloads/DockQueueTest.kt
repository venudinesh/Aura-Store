package dev.aurastore.store.ui.downloads

import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order the download panel flips through, and the rule that decides when a swipe turns it.
 *
 * Both are plain functions, and both are the parts a device pass cannot inspect: the panel shows one
 * row at a time, so the row three swipes away and the threshold that reaches it are invisible until
 * somebody counts. These cases pin the order, the ends and the flick.
 */
class DockQueueTest {

    private fun row(id: String, state: DownloadState) = DownloadItem(
        id = id,
        postId = id.hashCode().toLong(),
        title = id,
        iconUrl = null,
        versionLabel = "1.0",
        fileLabel = "$id.apk",
        url = "https://example.test/$id.apk",
        fileName = "$id.apk",
        state = state,
    )

    @Test
    fun `the panel opens on the row that is moving and keeps the rest behind it`() {
        val rows = listOf(
            row("ready", DownloadState.READY),
            row("paused", DownloadState.PAUSED),
            row("queued", DownloadState.QUEUED),
            row("downloading-old", DownloadState.DOWNLOADING),
            row("verifying", DownloadState.VERIFYING),
        )
        // Newest first inside the moving group: the queue is appended to, so the last row is the
        // request the user just made.
        assertEquals(
            listOf("verifying", "downloading-old", "queued", "paused", "ready"),
            dockCandidates(rows).map { it.id },
        )
    }

    @Test
    fun `the install-ready rows follow the paused ones and the finished rows are absent`() {
        val rows = listOf(
            row("installed", DownloadState.INSTALLED),
            row("failed", DownloadState.FAILED),
            row("canceled", DownloadState.CANCELED),
            row("installing", DownloadState.INSTALLING),
            row("awaiting", DownloadState.AWAITING_CONFIRMATION),
            row("permission", DownloadState.NEEDS_PERMISSION),
        )
        assertEquals(
            listOf("permission", "awaiting"),
            dockCandidates(rows).map { it.id },
        )
        assertTrue(dockCandidates(rows).none { it.state == DownloadState.INSTALLED })
    }

    @Test
    fun `an empty queue has no pages`() {
        assertTrue(dockCandidates(emptyList()).isEmpty())
    }

    @Test
    fun `a drag left turns forward and a drag right turns back`() {
        assertEquals(1, DockSwipe.step(0, 3, dx = -60f, velocityX = 0f, widthPx = 300f))
        assertEquals(-1, DockSwipe.step(1, 3, dx = 60f, velocityX = 0f, widthPx = 300f))
    }

    @Test
    fun `the ends clamp instead of wrapping`() {
        assertEquals(0, DockSwipe.step(0, 3, dx = 60f, velocityX = 0f, widthPx = 300f))
        assertEquals(0, DockSwipe.step(2, 3, dx = -60f, velocityX = 0f, widthPx = 300f))
    }

    @Test
    fun `a fast flick commits below the distance`() {
        // Two pixels of travel, but the hand was moving at 950 px a second: the card turns.
        assertEquals(1, DockSwipe.step(1, 3, dx = -2f, velocityX = -950f, widthPx = 300f))
        assertEquals(-1, DockSwipe.step(1, 3, dx = 2f, velocityX = 950f, widthPx = 300f))
    }

    @Test
    fun `a short slow nudge, an unmeasured card and a one-row queue stay put`() {
        assertEquals(0, DockSwipe.step(1, 3, dx = -20f, velocityX = 200f, widthPx = 300f))
        assertEquals(0, DockSwipe.step(1, 3, dx = -100f, velocityX = 0f, widthPx = 0f))
        assertEquals(0, DockSwipe.step(1, 1, dx = -100f, velocityX = 0f, widthPx = 300f))
        assertEquals(0, DockSwipe.step(-1, 3, dx = -100f, velocityX = 0f, widthPx = 300f))
        assertEquals(0, DockSwipe.step(3, 3, dx = -100f, velocityX = 0f, widthPx = 300f))
    }

    @Test
    fun `the commit fraction is the edge the gesture has to cross`() {
        val edge = 300f * DockSwipe.COMMIT_FRACTION
        assertEquals(0, DockSwipe.step(1, 3, dx = -edge, velocityX = 0f, widthPx = 300f))
        assertEquals(1, DockSwipe.step(1, 3, dx = -(edge + 0.5f), velocityX = 0f, widthPx = 300f))
    }
}
