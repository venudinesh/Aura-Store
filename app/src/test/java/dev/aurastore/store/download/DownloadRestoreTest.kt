package dev.aurastore.store.download

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The queue is persisted, but the files it describes live in `cache/apk`, which Android is free to empty
 * while the app is stopped. So the useful behaviour is not the reload — it is what a row is *allowed to
 * claim* after a restart, and each of these is a row that would otherwise offer something the device
 * cannot deliver.
 */
class DownloadRestoreTest {

    private val dir: File = Files.createTempDirectory("aurastore-restore").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun row(state: DownloadState, id: String = "3f2a1b90-0000-0000-0000-000000000001") =
        DownloadItem(
            id = id,
            postId = 45327,
            title = "Mighty Moto Racers",
            iconUrl = null,
            versionLabel = "1.4.2",
            fileLabel = "APK · arm64 · 128 MB",
            url = "https://liteapks.dev/45327/Mighty.apk",
            fileName = "Mighty.apk",
            state = state,
            localPath = onDisk(id).absolutePath,
        )

    /** The path [dev.aurastore.store.download.DownloadEngine] writes this row's bytes to. */
    private fun onDisk(id: String): File = File(dir, "45327-${id.take(8)}-Mighty.apk")

    private fun write(id: String, bytes: Int): File = onDisk(id).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `a transfer cut off mid-stream resumes from the bytes that remained`() {
        write("3f2a1b90-0000-0000-0000-000000000001", 500)
        val restored = DownloadEngine.reattach(
            row(DownloadState.DOWNLOADING).copy(bytesDownloaded = 41_943_040L, totalBytes = 134_217_728L),
            dir,
        )
        // The saved tick is a guess about the moment the process died; the file is what is on the device.
        assertEquals(DownloadState.QUEUED, restored.state)
        assertEquals(500L, restored.bytesDownloaded)
        assertEquals(onDisk("3f2a1b90-0000-0000-0000-000000000001").absolutePath, restored.localPath)
        assertNull(restored.error)
    }

    @Test
    fun `a row left with no bytes on disk starts again from nothing`() {
        val restored = DownloadEngine.reattach(row(DownloadState.QUEUED).copy(bytesDownloaded = 9_000L), dir)
        assertEquals(DownloadState.QUEUED, restored.state)
        assertEquals(0L, restored.bytesDownloaded)
    }

    @Test
    fun `a hash that never finished is re-fetched rather than trusted`() {
        write("3f2a1b90-0000-0000-0000-000000000001", 5_000)
        val restored = DownloadEngine.reattach(
            row(DownloadState.VERIFYING).copy(bytesDownloaded = 5_000L, sha256 = "a".repeat(64)),
            dir,
        )
        assertEquals(DownloadState.QUEUED, restored.state)
    }

    @Test
    fun `a finished download the cache lost says so instead of offering Install`() {
        val restored = DownloadEngine.reattach(
            row(DownloadState.READY).copy(
                bytesDownloaded = 128L,
                totalBytes = 128L,
                sha256 = "a".repeat(64),
                apkPackage = "com.game.mighty",
            ),
            dir,
        )
        assertEquals(DownloadState.FAILED, restored.state)
        assertEquals(FailureKind.STORAGE, restored.failure?.kind)
        assertTrue(restored.failure!!.worthRetrying)
        assertNull(restored.localPath)
        assertNull(restored.sha256)
        assertEquals(0L, restored.bytesDownloaded)
    }

    @Test
    fun `a truncated package is not held as ready and its remains are deleted`() {
        val file = write("3f2a1b90-0000-0000-0000-000000000001", 400)
        val restored = DownloadEngine.reattach(
            row(DownloadState.READY).copy(bytesDownloaded = 1_000L, totalBytes = 1_000L),
            dir,
        )
        assertEquals(DownloadState.FAILED, restored.state)
        assertFalse("a half APK must not age in the cache", file.exists())
    }

    @Test
    fun `a paused row keeps its place in the file`() {
        write("3f2a1b90-0000-0000-0000-000000000001", 700)
        val restored = DownloadEngine.reattach(
            row(DownloadState.PAUSED).copy(bytesDownloaded = 700L, totalBytes = 2_000L),
            dir,
        )
        assertEquals(DownloadState.PAUSED, restored.state)
        assertEquals(700L, restored.bytesDownloaded)
    }

    @Test
    fun `a failed row keeps the reason it failed`() {
        val stored = row(DownloadState.FAILED).copy(
            localPath = null,
            failure = Failures.of(
                HttpStatusException(404, "https://liteapks.dev/45327/Mighty.apk"),
                { "the catalogue" },
                "https://liteapks.dev/45327/Mighty.apk",
            ),
        )
        // A FAILED row owns no file by definition; rewriting its message as "the file is gone" would
        // replace a 404 the user can act on with a storage complaint about a file that was deleted anyway.
        assertEquals(stored, DownloadEngine.reattach(stored, dir))
    }

    @Test
    fun `an install that finished while the app was dead is still installed`() {
        val stored = row(DownloadState.INSTALLED).copy(apkPackage = "com.game.mighty")
        assertEquals(stored, DownloadEngine.reattach(stored, dir))
    }

    @Test
    fun `a finished row stops telling the user to raise the install sheet`() {
        // The bundle path writes this advice when the platform asks to be confirmed and no sheet can be
        // raised from the background. A row restored as Installed — by the receiver or by the watchdog —
        // must not keep telling the user to tap Install for something already on the phone.
        val stored = row(DownloadState.INSTALLED).copy(
            apkPackage = "com.game.mighty",
            notice = "Android wants you to confirm this install — tap Install to show that sheet.",
        )
        assertEquals(null, DownloadEngine.reattach(stored, dir).notice)
        // A row still waiting for that sheet is the one case the advice is true, so it survives.
        write("3f2a1b90-0000-0000-0000-000000000001", 1_000)
        val waiting = row(DownloadState.AWAITING_CONFIRMATION).copy(
            bytesDownloaded = 1_000L,
            totalBytes = 1_000L,
            notice = stored.notice,
        )
        assertEquals(stored.notice, DownloadEngine.reattach(waiting, dir).notice)
    }

    @Test
    fun `a row waiting on the system sheet is left for the package to answer`() {
        write("3f2a1b90-0000-0000-0000-000000000001", 1_000)
        val stored = row(DownloadState.AWAITING_CONFIRMATION).copy(
            bytesDownloaded = 1_000L,
            totalBytes = 1_000L,
            apkPackage = "com.game.mighty",
        )
        // Whether it landed is a question for PackageManager, not for the file — reconcileInstalls answers it.
        assertEquals(stored, DownloadEngine.reattach(stored, dir))
    }

    @Test
    fun `a cancelled row is still cancelled`() {
        val stored = row(DownloadState.CANCELED)
        assertEquals(stored, DownloadEngine.reattach(stored, dir))
    }
}
