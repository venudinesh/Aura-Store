package dev.aurastore.store.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The queue's own rules, away from Android: the timestamp that decides whether an install really
 * happened (a package that was on the device before the handover cannot be the result of it), what to
 * do when a request names a build the queue already holds, which cached files a row still owns, and when
 * a verified build must not be handed to an installer because the app it would replace is signed by
 * somebody else.
 */
class DownloadItemTest {

    private fun row(vararg changes: Pair<DownloadState, Long>) = DownloadItem(
        id = "3f2a1b90-0000-0000-0000-000000000001",
        postId = 45327,
        title = "Mighty Moto Racers",
        iconUrl = "",
        versionLabel = "1.4.2 - Premium",
        fileLabel = "APK · arm64-v8a · 128 MB",
        url = "https://www.mediafire.com/file/abcd/Mighty.apk",
        fileName = "Mighty.apk",
        state = DownloadState.AWAITING_CONFIRMATION,
        transitions = changes.map { StateChange(it.first, it.second) },
    )

    @Test
    fun `the handover is the last trip to the installer`() {
        val item = row(
            DownloadState.QUEUED to 1_000L,
            DownloadState.DOWNLOADING to 2_000L,
            DownloadState.READY to 3_000L,
            DownloadState.AWAITING_CONFIRMATION to 4_000L,
            DownloadState.READY to 5_000L, // the watchdog gave up and offered Install again
            DownloadState.INSTALLING to 6_000L,
        )
        // The first handover is the one that proved nothing; only the newest can confirm an install.
        assertEquals(6_000L, item.installHandedOverAt)
    }

    @Test
    fun `a row that never reached an installer has no handover`() {
        val item = row(DownloadState.QUEUED to 1_000L, DownloadState.DOWNLOADING to 2_000L)
        assertEquals(0L, item.installHandedOverAt)
    }

    private fun at(state: DownloadState) = row(DownloadState.QUEUED to 1_000L).copy(state = state)

    @Test
    fun `a verified build already in the queue is never fetched twice`() {
        // The bug this pins: tapping Get on an update re-downloaded 143 MB beside a READY row of the
        // same file, because only in-flight rows counted as "already queued".
        assertEquals(Reuse.IDLE, reuseOf(at(DownloadState.READY), autoInstall = false, fileLive = true))
        assertEquals(Reuse.INSTALL, reuseOf(at(DownloadState.READY), autoInstall = true, fileLive = true))
        assertEquals(Reuse.INSTALL, reuseOf(at(DownloadState.INSTALLED), autoInstall = true, fileLive = true))
        // The sheet never answered, or the permission is still off: the file is there, offer it again.
        assertEquals(Reuse.INSTALL, reuseOf(at(DownloadState.AWAITING_CONFIRMATION), true, fileLive = true))
        assertEquals(Reuse.INSTALL, reuseOf(at(DownloadState.NEEDS_PERMISSION), true, fileLive = true))
    }

    @Test
    fun `a row that is moving is left alone`() {
        listOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.VERIFYING)
            .forEach { assertEquals(Reuse.IDLE, reuseOf(at(it), autoInstall = true, fileLive = false)) }
        // Bytes on disk mid-transfer are partial; nothing about this row should restart.
        assertEquals(Reuse.IDLE, reuseOf(at(DownloadState.DOWNLOADING), autoInstall = true, fileLive = true))
    }

    @Test
    fun `a row without its file is run again`() {
        // Android can empty the cache under a finished row; the same request has to put the bytes back.
        assertEquals(Reuse.RESUME, reuseOf(at(DownloadState.READY), autoInstall = true, fileLive = false))
        assertEquals(Reuse.RESUME, reuseOf(at(DownloadState.PAUSED), autoInstall = false, fileLive = false))
        assertEquals(Reuse.RESUME, reuseOf(at(DownloadState.FAILED), autoInstall = false, fileLive = false))
    }

    @Test
    fun `only a row can keep a cached apk from the sweep`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "apk-cache-${System.nanoTime()}").apply { mkdirs() }
        val held = "45327-3f2a1b90-Mighty.apk"
        // The name a row took when the directory already held its build: still the row's own bytes.
        val renamed = "45327-3f2a1b90-Mighty (2).apk"
        val stranger = "973583-11110000-Win-Slot.apk"
        try {
            listOf(held, renamed, stranger).forEach { File(dir, it).writeText("x") }
            val item = at(DownloadState.READY).copy(localPath = File(dir, renamed).absolutePath)
            // One row protects both spellings of its file, because the prefix is the same either way.
            assertEquals(setOf(stranger), orphanApkFiles(dir, listOf(item)).map { it.name }.toSet())
            // A queue that forgot its rows owns nothing here, so every file is somebody's leftover.
            assertEquals(setOf(held, renamed, stranger), orphanApkFiles(dir, emptyList()).map { it.name }.toSet())
            // A missing cache directory is not a list of orphans.
            assertEquals(emptySet<String>(), orphanApkFiles(File(dir, "nope"), emptyList()).map { it.name }.toSet())
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `a bundle row owns its archive and every piece unpacked from it`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "apk-cache-${System.nanoTime()}").apply { mkdirs() }
        val archive = "45327-3f2a1b90-Mighty.xapk"
        // What one catalogue build unpacks into on a real device: a base and its language, ABI and
        // density splits, all under the row's own prefix.
        val pieces = listOf("45327-3f2a1b90-mighty.apk", "45327-3f2a1b90-config.arm64_v8a.apk")
        val stranger = "973583-11110000-Win-Slot.apk"
        try {
            (listOf(archive) + pieces + stranger).forEach { File(dir, it).writeText("x") }
            val item = at(DownloadState.INSTALLED).copy(
                fileName = "Mighty.xapk",
                localPath = File(dir, archive).absolutePath,
                bundleFiles = pieces.map { File(dir, it).absolutePath },
            )
            // Half a footprint protected is worse than none: the sweep would delete a live app's splits.
            val left = orphanApkFiles(dir, listOf(item)).map { it.name }.toSet()
            assertEquals(setOf(stranger), left)
            // And the row's own delete takes all three — the archive is the only copy of the pieces.
            assertEquals(listOf(archive) + pieces, ownedFiles(item).map { it.name })
            // A queue that forgot the row forgot its claim on all of them.
            val withNoRows = orphanApkFiles(dir, emptyList()).map { it.name }.toSet()
            assertEquals((listOf(archive) + pieces + stranger).toSet(), withNoRows)
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    private fun verified(signer: String?) = at(DownloadState.READY).copy(
        apkPackage = "com.game.mighty",
        apkSignerSha256 = signer,
    )

    @Test
    fun `a different publisher is the only thing that counts as a clash`() {
        assertTrue(signatureClash("aaa1", "bbb2"))
        assertFalse(signatureClash("aaa1", "aaa1"))
        // The two digests are produced by different code paths; hex case is not a different certificate.
        assertFalse(signatureClash("Aaa1", "aaa1"))
        // Unknown on either side is not evidence of anything, and the installer keeps the final say.
        assertFalse(signatureClash(null, "bbb2"))
        assertFalse(signatureClash("aaa1", null))
    }

    @Test
    fun `a verified build is held only while the app it cannot replace is still there`() {
        val item = verified("bbb2")
        // The case the path exists for: the bytes are paid for and the platform would refuse them anyway.
        assertTrue(installHold(item, "aaa1", packagePresent = true))
        // Same publisher: an update in place, the app keeps its data, nothing to wait for.
        assertFalse(installHold(item, "bbb2", packagePresent = true))
        // Nothing installed means nothing to clash with, which is what a replacement install looks like.
        assertFalse(installHold(item, null, packagePresent = false))
        // A row already told to wait keeps waiting when the certificate cannot be read — the alternative
        // is opening an installer that is about to be refused.
        assertTrue(installHold(item.copy(uninstallFirst = true), null, packagePresent = true))
        // And a row whose publisher turned out to fit is not held by its own stale warning.
        assertFalse(installHold(item.copy(uninstallFirst = true), "bbb2", packagePresent = true))
    }

    @Test
    fun `clearing the list only takes rows whose story is over`() {
        // "Not downloading" is not the same question, and getting it wrong deletes a build the user
        // asked for and has not installed yet — including one waiting behind an uninstall.
        assertTrue(at(DownloadState.INSTALLED).isDisposable)
        assertTrue(at(DownloadState.CANCELED).isDisposable)
        for (state in listOf(
            DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.VERIFYING,
            DownloadState.PAUSED, DownloadState.READY, DownloadState.FAILED,
            DownloadState.NEEDS_PERMISSION, DownloadState.AWAITING_CONFIRMATION,
        )) {
            assertFalse("$state still owns something the user may want", at(state).isDisposable)
        }
        assertFalse(verified("aaa1").copy(uninstallFirst = true).isDisposable)
    }
}
