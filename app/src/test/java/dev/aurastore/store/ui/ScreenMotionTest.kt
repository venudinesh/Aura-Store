package dev.aurastore.store.ui

import dev.aurastore.store.R
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules the storefront's motion is built on, kept away from Compose so they are checkable without
 * a device: which way a page travels, how far an entrance stagger reaches, and what a queue row says
 * about itself while it morphs.
 *
 * The direction rules matter more than they look. The shell routes out of six independent variables, so
 * a transition that guessed its direction from "the target changed" would slide backwards whenever a
 * tab opened a page and then closed it again.
 */
class ScreenMotionTest {

    private val games = Screen.Feed(0)
    private val apps = Screen.Feed(1)
    private val search = Screen.Feed(2)
    private val racing = Screen.Category(9L)
    private val detail = Screen.Detail(45327L)
    private val settings = Screen.Settings
    private val source = Screen.Source(SourceId.NINEMOD, "mega")
    private val download = Screen.Download("3f2a1b90-0000-0000-0000-000000000001")

    @Test
    fun `only the tabs sit at the surface`() {
        assertEquals(0, games.depth())
        assertEquals(0, search.depth())
        assertEquals(1, racing.depth())
        assertEquals(1, settings.depth())
        assertEquals(1, source.depth())
        assertEquals(1, download.depth())
        assertEquals(2, detail.depth())
    }

    @Test
    fun `a tab sliding sideways never reads as a push`() {
        assertEquals(Move.SLIDE_FORWARD, moveOf(games, apps))
        assertEquals(Move.SLIDE_BACK, moveOf(apps, games))
        // Same depth, so the depth rule alone could not tell these two apart.
        assertEquals(Move.SLIDE_FORWARD, moveOf(apps, search))
    }

    @Test
    fun `a deeper page is pushed onto the one behind it and popped back off`() {
        assertEquals(Move.PUSH, moveOf(games, detail))
        assertEquals(Move.PUSH, moveOf(racing, detail))
        assertEquals(Move.PUSH, moveOf(games, racing))
        assertEquals(Move.POP, moveOf(detail, racing))
        assertEquals(Move.POP, moveOf(detail, games))
        assertEquals(Move.POP, moveOf(racing, games))
    }

    @Test
    fun `sibling pages at one depth still arrive on top`() {
        // A category swapped for settings is not a back move: nothing was unwound.
        assertEquals(Move.PUSH, moveOf(racing, settings))
        assertEquals(Move.PUSH, moveOf(source, download))
    }

    @Test
    fun `staying on a page animates nothing`() {
        assertEquals(Move.STAY, moveOf(games, games))
        assertEquals(Move.STAY, moveOf(detail, detail))
        assertEquals(Move.STAY, moveOf(Screen.Feed(1), apps))
    }

    @Test
    fun `the stagger grows by one frame and then stops`() {
        assertEquals(0, staggerDelay(0))
        assertEquals(40, staggerDelay(1))
        assertEquals(200, staggerDelay(5))
        // Past the cap the rest arrive with it, or a thirty-row rail would still be arriving.
        assertEquals(200, staggerDelay(6))
        assertEquals(200, staggerDelay(30))
        // A position below the rail's first row cannot buy a negative wait.
        assertEquals(0, staggerDelay(-2))
    }

    @Test
    fun `a caller can retune the stagger without breaking the cap`() {
        assertEquals(90, staggerDelay(3, stepMs = 30, cap = 6))
        assertEquals(120, staggerDelay(9, stepMs = 30, cap = 4))
        assertEquals(0, staggerDelay(4, stepMs = 0))
    }

    private fun row(changes: DownloadState, vararg flags: String) = DownloadItem(
        id = "3f2a1b90-0000-0000-0000-000000000001",
        postId = 45327,
        title = "Mighty Moto Racers",
        iconUrl = "",
        versionLabel = "1.4.2 - Premium",
        fileLabel = "APK · arm64-v8a · 128 MB",
        url = "https://www.mediafire.com/file/abcd/Mighty.apk",
        fileName = "Mighty.apk",
        state = changes,
        dataFile = "data" in flags,
        uninstallFirst = "uninstall" in flags,
    )

    @Test
    fun `a ready row says what will actually happen`() {
        // The row's line is a resource id and its arguments since the eighteenth round, so the claim these
        // tests pin is "which sentence", not "what the sentence says in English". StringsResourceTest reads
        // the ids back out of strings.xml and checks that the wording and the placeholders are the ones the
        // row asks for.
        assertEquals(R.string.dl_state_ready, downloadRowStatus(row(DownloadState.READY)).id)
        // The file verified, but the platform will refuse it: the row is not installable yet.
        assertEquals(R.string.row_needs_uninstall, downloadRowStatus(row(DownloadState.READY, "uninstall")).id)
        assertEquals(R.string.row_ready_to_add, downloadRowStatus(row(DownloadState.READY, "data")).id)
        assertEquals(R.string.dl_state_data_placed, downloadRowStatus(row(DownloadState.INSTALLED, "data")).id)
        assertEquals(R.string.dl_state_installed, downloadRowStatus(row(DownloadState.INSTALLED)).id)
    }

    @Test
    fun `a row in transfer reports its bytes and no size it does not have`() {
        assertEquals(R.string.row_progress_bytes, downloadRowStatus(row(DownloadState.QUEUED)).id)
        assertEquals(
            listOf("0 B", "—"),
            downloadRowStatus(row(DownloadState.QUEUED)).args,
        )
        assertEquals(R.string.row_verifying, downloadRowStatus(row(DownloadState.VERIFYING)).id)
        assertEquals(R.string.row_reading_archive, downloadRowStatus(row(DownloadState.VERIFYING, "data")).id)
        assertEquals(
            R.string.row_awaiting_confirm,
            downloadRowStatus(row(DownloadState.AWAITING_CONFIRMATION)).id,
        )
        assertEquals(R.string.row_needs_permission, downloadRowStatus(row(DownloadState.NEEDS_PERMISSION)).id)
    }

    @Test
    fun `a failed row prints the engine's reason and the resource only when there is none`() {
        val failed = row(DownloadState.FAILED).copy(error = "The host refused the file")
        assertEquals(null, downloadRowStatus(failed).id)
        assertEquals("The host refused the file", downloadRowStatus(failed).text)
        assertEquals(R.string.dl_state_failed, downloadRowStatus(row(DownloadState.FAILED)).id)
    }
}
