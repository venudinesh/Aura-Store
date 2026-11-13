package dev.aurastore.store.install

import android.content.pm.PackageInstaller
import dev.aurastore.store.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The two decisions made where a session ends.
 *
 * A session can finish with a status the platform gave no words for, and more than one voice can report
 * the same session — the commit broadcast and the callback added this round. So the two things worth
 * pinning are that a bare code never reaches a user as a code, and that a voice which arrives second
 * cannot move a row somebody already answered.
 */
class InstallSessionsTest {

    @Test
    fun `every status the platform answers with has a sentence of its own`() {
        assertEquals(
            "This build conflicts with the copy already installed — remove that one first",
            installerReason(PackageInstaller.STATUS_FAILURE_CONFLICT),
        )
        assertEquals(
            "This build is not compatible with this device",
            installerReason(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE),
        )
        assertEquals(
            "The downloaded file is not a valid APK",
            installerReason(PackageInstaller.STATUS_FAILURE_INVALID),
        )
        assertEquals(
            "Not enough free space to install this package",
            installerReason(PackageInstaller.STATUS_FAILURE_STORAGE),
        )
        assertEquals(
            "Android blocked this install — Play Protect or a device policy refused it",
            installerReason(PackageInstaller.STATUS_FAILURE_BLOCKED),
        )
    }

    @Test
    fun `a cancel has no sentence because it is not a refusal`() {
        // The receiver answers `STATUS_FAILURE_ABORTED` on the queue's cancel path before this table is
        // consulted, and it stays out of the table on purpose: should a build ever route it here, the
        // generic sentence is the honest one, and nothing gets to dress a user's "no" up as a fault.
        assertEquals(
            "The system installer rejected this package (status 3)",
            installerReason(PackageInstaller.STATUS_FAILURE_ABORTED),
        )
    }

    @Test
    fun `a bare status is never shown as a status`() {
        // The bug this table exists for: LineageOS on the Note 8 Pro answered an aborted session with -1
        // and no message, which used to surface as "Installer status -1".
        assertEquals(
            "The installation did not complete — try again",
            installerReason(-1),
        )
        assertFalse(installerReason(-1).contains("-1"))

        // A code we do not know keeps its number, because there is nothing better to say about it — but
        // it arrives as a sentence about the installer, not as a bare integer.
        assertEquals(
            "The system installer rejected this package (status 42)",
            installerReason(42),
        )
    }

    @Test
    fun `a row still waiting for an answer is moved by the session`() {
        assertEquals(SessionAction.Installed, sessionAction(DownloadState.INSTALLING, success = true))
        assertEquals(SessionAction.NotInstalled, sessionAction(DownloadState.INSTALLING, success = false))
        // A session that came back asking to be confirmed is still this app's session; when the user
        // dismisses that question, the closing of the session is the only thing that can say so.
        assertEquals(SessionAction.Installed, sessionAction(DownloadState.AWAITING_CONFIRMATION, success = true))
        assertEquals(SessionAction.NotInstalled, sessionAction(DownloadState.AWAITING_CONFIRMATION, success = false))
    }

    @Test
    fun `a row somebody already answered is left alone`() {
        // The broadcast carries the reason and usually arrives first, so the callback has to be the one
        // that stays quiet — a late "finished, unsuccessful" must not undo an Installed the broadcast
        // already earned, and a row that went back to Ready, failed or was cancelled is not this
        // session's business any more.
        for (state in DownloadState.entries - setOf(DownloadState.INSTALLING, DownloadState.AWAITING_CONFIRMATION)) {
            assertEquals("$state", SessionAction.Ignore, sessionAction(state, success = true))
            assertEquals("$state", SessionAction.Ignore, sessionAction(state, success = false))
        }
    }
}
