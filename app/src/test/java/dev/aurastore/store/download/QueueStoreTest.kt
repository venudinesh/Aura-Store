package dev.aurastore.store.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue is now the one piece of state this app has to survive a restart with, and it is stored as
 * JSON written by this build and read by whatever build comes after it. So these tests are about the
 * decoding, not the file format: a row that cannot be read must be dropped rather than thrown at the
 * caller, and a field that is absent must mean *nothing there*, not the text `null`.
 */
class QueueStoreTest {

    private fun row(
        id: String = "3f2a1b90-0000-0000-0000-000000000001",
        state: DownloadState = DownloadState.READY,
    ) = DownloadItem(
        id = id,
        postId = 45327,
        title = "Mighty Moto Racers",
        iconUrl = "https://liteapks.com/wp-content/uploads/45327.jpg",
        versionLabel = "1.4.2 - Premium",
        fileLabel = "APK · arm64-v8a · 128 MB",
        url = "https://www.mediafire.com/file/abcd/Mighty.apk",
        fileName = "Mighty.apk",
        state = state,
        bytesDownloaded = 134_217_728L,
        totalBytes = 134_217_728L,
        error = null,
        sha256 = "a".repeat(64),
        expectedSha256 = "b".repeat(64),
        apkPackage = "com.game.mighty",
        apkVersionCode = 20_304_001L,
        apkSignerSha256 = "c".repeat(64),
        apkVersionName = "1.4.2",
        localPath = "/data/user/0/dev.aurastore.store/cache/apk/45327-3f2a1b90-Mighty.apk",
        autoInstall = true,
        notice = "mediafire.com refused it — switched build",
        dataFile = false,
        bytesPerSecond = 2_411_725L,
        etaSeconds = 42L,
        transitions = listOf(
            StateChange(DownloadState.QUEUED, 1_700_000_000_000L),
            StateChange(DownloadState.DOWNLOADING, 1_700_000_001_000L),
            StateChange(DownloadState.READY, 1_700_000_060_000L),
        ),
        failure = null,
    )

    @Test
    fun `the whole row comes back`() {
        val stored = row()
        val back = QueueStore.parse(QueueStore.render(listOf(stored)))
        // The two derived rates are the only fields deliberately not written; everything else is the row.
        assertEquals(listOf(stored.copy(bytesPerSecond = 0L, etaSeconds = null)), back)
    }

    @Test
    fun `a speed measured before the restart is not claimed after it`() {
        val back = QueueStore.parse(QueueStore.render(listOf(row())))
        assertEquals(0L, back.single().bytesPerSecond)
        assertNull(back.single().etaSeconds)
    }

    @Test
    fun `an absent field stays absent instead of becoming the text null`() {
        val stored = row(
            id = "3f2a1b90-0000-0000-0000-000000000002",
            state = DownloadState.FAILED,
        ).copy(
            iconUrl = null,
            sha256 = null,
            expectedSha256 = null,
            apkPackage = null,
            apkVersionCode = null,
            apkSignerSha256 = null,
            apkVersionName = null,
            localPath = null,
            notice = null,
            error = "The host stopped answering.",
        )
        val back = QueueStore.parse(QueueStore.render(listOf(stored))).single()
        assertEquals(stored.copy(bytesPerSecond = 0L, etaSeconds = null), back)
        assertNull(back.iconUrl)
        assertNull(back.apkPackage)
        assertNull(back.localPath)
    }

    @Test
    fun `a row waiting on an uninstall keeps waiting across a restart`() {
        // The explanation the row gave is the reason its Install button is an Uninstall button; losing it
        // would put a working-looking Install back on a build the platform will refuse.
        val stored = row().copy(uninstallFirst = true)
        val back = QueueStore.parse(QueueStore.render(listOf(stored))).single()
        assertTrue(back.uninstallFirst)
        assertEquals(stored.copy(bytesPerSecond = 0L, etaSeconds = null), back)
        // And a queue written before the field existed reads as "not waiting", never as a missing value.
        assertFalse(QueueStore.parse("""[{"id":"abc","state":"READY"}]""").single().uninstallFirst)
    }

    @Test
    fun `a bundle keeps its pieces in the order the installer needs them`() {
        // The engine installs the set through one session, base first, so the order is part of the row —
        // and a restart that lost it would hand the package manager a config APK before its app.
        val stored = row().copy(
            fileName = "Mighty.xapk",
            bundleFiles = listOf(
                "/data/user/0/dev.aurastore.store/cache/apk/45327-3f2a1b90-Mighty.apk",
                "/data/user/0/dev.aurastore.store/cache/apk/45327-3f2a1b90-config.arm64_v8a.apk",
            ),
            dataInside = true,
        )
        val back = QueueStore.parse(QueueStore.render(listOf(stored))).single()
        assertEquals(stored.bundleFiles, back.bundleFiles)
        assertTrue(back.dataInside)
        // The row's own name is what makes it a bundle, and that survives the restart with it.
        assertTrue(back.isBundle)
        assertEquals(stored.copy(bytesPerSecond = 0L, etaSeconds = null), back)
    }

    @Test
    fun `a queue written before bundles names no pieces`() {
        val back = QueueStore.parse("""[{"id":"abc","state":"READY"}]""").single()
        assertTrue(back.bundleFiles.isEmpty())
        assertFalse(back.dataInside)
        assertFalse(back.isBundle)
        // An entry that names nothing is no piece either: the row must not claim a file it cannot show.
        assertTrue(QueueStore.parse("""[{"id":"abc","state":"READY","bundleFiles":["","  "]}]""")
            .single().bundleFiles.isEmpty())
    }

    @Test
    fun `a failure keeps its kind and its advice`() {
        val stored = row().copy(
            state = DownloadState.FAILED,
            failure = Failures.integrityFailure("b".repeat(64), "a".repeat(64)),
        )
        val back = QueueStore.parse(QueueStore.render(listOf(stored))).single()
        assertEquals(FailureKind.INTEGRITY, back.failure?.kind)
        assertEquals(stored.failure, back.failure)
        assertTrue(back.failure!!.worthRetrying)
    }

    @Test
    fun `a row written by an older build decodes with what it has`() {
        val back = QueueStore.parse("""[{"id":"abc","state":"READY","title":"Old"}]""").single()
        assertEquals("abc", back.id)
        assertEquals("Old", back.title)
        assertEquals(DownloadState.READY, back.state)
        assertEquals(-1L, back.totalBytes)
        assertEquals(0L, back.bytesDownloaded)
        assertNull(back.localPath)
        assertNull(back.failure)
        assertTrue(back.transitions.isEmpty())
    }

    @Test
    fun `a record the app cannot read is dropped, not thrown`() {
        assertTrue(QueueStore.parse("not json at all").isEmpty())
        assertTrue(QueueStore.parse("{}").isEmpty())
        // One row the build cannot make sense of (no id, an unknown state name) alongside one it can.
        val back = QueueStore.parse(
            """[{"state":"READY"},{"id":"abc","state":"TIME_TRAVELLED"},{"id":"def","state":"PAUSED"}]""",
        )
        assertEquals(listOf("abc", "def"), back.map { it.id })
        assertEquals(DownloadState.FAILED, back[0].state)
        assertEquals(DownloadState.PAUSED, back[1].state)
    }

    @Test
    fun `an empty queue is stored as an empty queue`() {
        assertTrue(QueueStore.parse(QueueStore.render(emptyList())).isEmpty())
    }
}
