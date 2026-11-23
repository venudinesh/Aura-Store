package dev.aurastore.store.ui.detail

import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which row an app's page reports progress for.
 *
 * A post can own the package and the game data published beside it, and only one of them moves at a
 * time. This rule is what keeps a finished package from hiding the data file that is still coming —
 * the page used to watch the package alone, so the dock and the queue knew about the transfer and the
 * page that asked for it did not.
 */
class DetailTransferTest {

    private fun row(
        id: String,
        state: DownloadState,
        dataFile: Boolean = false,
        postId: Long = 7L,
        origin: Long? = null,
    ) = DownloadItem(
        id = id,
        postId = postId,
        originPostId = origin,
        title = "Game",
        iconUrl = null,
        versionLabel = "1.0",
        fileLabel = id,
        url = "https://example.test/$id",
        fileName = id,
        state = state,
        dataFile = dataFile,
    )

    @Test
    fun `a mirror row fetched by this page belongs to this page`() {
        val rows = listOf(
            row("mine.apk", DownloadState.READY),
            row("mirror.apk", DownloadState.DOWNLOADING, postId = -299L, origin = 7L),
        )
        assertEquals(listOf("mine.apk", "mirror.apk"), detailRows(rows, 7L).map { it.id })
        assertEquals("mirror.apk", detailTransferRow(detailRows(rows, 7L))?.id)
    }

    @Test
    fun `another page's row is not this page's row`() {
        val rows = listOf(
            row("mine.apk", DownloadState.READY),
            row("theirs.apk", DownloadState.DOWNLOADING, postId = 9L),
        )
        assertEquals(listOf("mine.apk"), detailRows(rows, 7L).map { it.id })
    }

    @Test
    fun `a downloading data file is the row a finished package page reports`() {
        val rows = listOf(
            row("game.apk", DownloadState.READY),
            row("game-data.zip", DownloadState.DOWNLOADING, dataFile = true),
        )
        assertEquals("game-data.zip", detailTransferRow(rows)?.id)
    }

    @Test
    fun `the package wins while it is the one moving and the data waits`() {
        val rows = listOf(
            row("game.apk", DownloadState.DOWNLOADING),
            row("game-data.zip", DownloadState.QUEUED, dataFile = true),
        )
        assertEquals("game.apk", detailTransferRow(rows)?.id)
    }

    @Test
    fun `verifying outranks a paused row and paused outranks a queued one`() {
        val paused = row("game.apk", DownloadState.PAUSED)
        val verifying = row("game-data.zip", DownloadState.VERIFYING, dataFile = true)
        assertEquals(
            "game-data.zip",
            detailTransferRow(listOf(paused, verifying))?.id,
        )
        assertEquals(
            "game.apk",
            detailTransferRow(listOf(paused, row("game-data.zip", DownloadState.QUEUED, dataFile = true)))?.id,
        )
    }

    @Test
    fun `with nothing moving the package is the page's row`() {
        val rows = listOf(
            row("game-data.zip", DownloadState.READY, dataFile = true),
            row("game.apk", DownloadState.INSTALLED),
        )
        assertEquals("game.apk", detailTransferRow(rows)?.id)
    }

    @Test
    fun `a data-only post still has a row to show`() {
        val rows = listOf(row("game-data.zip", DownloadState.DOWNLOADING, dataFile = true))
        assertEquals("game-data.zip", detailTransferRow(rows)?.id)
    }

    @Test
    fun `an empty post has no row`() {
        assertNull(detailTransferRow(emptyList()))
    }
}
