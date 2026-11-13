package dev.aurastore.store.install

import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import dev.aurastore.store.download.DownloadState
import java.util.concurrent.ConcurrentHashMap

/**
 * The sentence for a status the system reported without one.
 *
 * "Installer status -1" in front of a user is what this table exists to prevent — and §3n is the reason
 * the negative branch is a sentence rather than a failure: on this ROM `-1` was the platform *asking*, not
 * refusing. It sits out of the receiver because a private method of a [android.content.BroadcastReceiver]
 * is out of reach of the JVM tests that pin every other sentence in this app. The session callback below
 * never calls it: a boolean carries no reason to word.
 *
 * A cancelled install is not in the table because it never gets here — `STATUS_FAILURE_ABORTED` is handed
 * to [dev.aurastore.store.download.DownloadEngine.onInstallCanceled] before the status is read, since the
 * user saying no is not a fault the file has.
 */
internal fun installerReason(status: Int): String = when (status) {
    PackageInstaller.STATUS_FAILURE_BLOCKED ->
        "Android blocked this install — Play Protect or a device policy refused it"
    PackageInstaller.STATUS_FAILURE_CONFLICT ->
        "This build conflicts with the copy already installed — remove that one first"
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This build is not compatible with this device"
    PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded file is not a valid APK"
    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space to install this package"
    else ->
        if (status < 0) "The installation did not complete — try again"
        else "The system installer rejected this package (status $status)"
}

/**
 * Whether the end of a session is allowed to move a row, and where to.
 *
 * A verdict only touches a row that is still waiting for one. The commit broadcast describes the same
 * session and carries the reason, so whichever arrives first says it and the other has to leave the row
 * alone — otherwise a late *finished, unsuccessful* could rewrite an *Installed* the broadcast had
 * already earned, and a row that has gone back to Ready, failed, or been deleted is not this session's
 * business any more. The queue asks the same question of a broadcast's cancel, which is what stops the
 * second report of one closing from undoing a row the first one already settled.
 */
internal sealed interface SessionAction {
    /** Nothing for this ending to do: the row is already resolved, by a voice with more to say. */
    data object Ignore : SessionAction

    /** The package is on the device. */
    data object Installed : SessionAction

    /**
     * The installer is done and the package is not on the device.
     *
     * Not a failure: a session ends this way when the user answers *Cancel*, and the file on disk is
     * exactly as verified as it was. The row goes back to Ready, where the one tap that tries again is
     * already the control on show.
     */
    data object NotInstalled : SessionAction
}

internal fun sessionAction(state: DownloadState, success: Boolean): SessionAction {
    if (state != DownloadState.INSTALLING && state != DownloadState.AWAITING_CONFIRMATION) {
        return SessionAction.Ignore
    }
    return if (success) SessionAction.Installed else SessionAction.NotInstalled
}

/**
 * The `PackageInstaller` session callback, and the reason it has to be held.
 *
 * A callback that nothing in this process still points at can be collected before the platform has
 * anything to say, so each is held here under the id it watches until the session finishes — which is also
 * the moment it stops being useful, because a finished session has one thing left to report.
 *
 * What this adds to the commit broadcast is the *fact that a handover ended*, for every session this app
 * created: a bundle's, the fallback a single APK takes when no system sheet will accept the intent, and a
 * session that came back asking the user to confirm and was then dismissed. A sheet opened *through an
 * intent* makes its own session, under the installer's uid, and a normal app cannot register for that one,
 * so §5.5 stays where it was for that path.
 */
internal object InstallSessions {

    /** Where an ending goes. Set by the queue when it is built; null means nobody is listening. */
    @Volatile
    var sink: ((downloadId: String?, success: Boolean) -> Unit)? = null

    private val held = ConcurrentHashMap<Int, PackageInstaller.SessionCallback>()

    /**
     * Watches [sessionId] until it finishes.
     *
     * [downloadId] is captured rather than looked up later: the row a session belongs to is decided at
     * the handover, and a queue that has since changed is answered by [sessionAction], not by guessing.
     * The callback itself carries no package name — `SessionCallback.onFinished` gives an id and a
     * boolean — which is another reason the row is found by the id this app already knew.
     */
    fun watch(installer: PackageInstaller, sessionId: Int, downloadId: String?) {
        val callback = object : PackageInstaller.SessionCallback() {
            override fun onCreated(id: Int) = Unit
            override fun onBadgingChanged(id: Int) = Unit
            override fun onActiveChanged(id: Int, active: Boolean) = Unit
            override fun onProgressChanged(id: Int, progress: Float) = Unit

            override fun onFinished(id: Int, success: Boolean) {
                // The id is checked rather than assumed, because the row a call belongs to is decided by
                // [sessionId] and not by a call having arrived. Measured on the Redmi: one closing of the
                // watched session reached this callback twice, and the row's own state absorbed the
                // second; this line is what makes a call about any other session equally unable to speak
                // for this one.
                if (id != sessionId) return
                held.remove(id)
                // Logged before the hand-off, because on a device whose broadcast answers first this is
                // the only trace that the second voice spoke at all — and whether it does is exactly what
                // §6 item 2 has to measure rather than assume.
                android.util.Log.i("AuraStoreInstall", "session $id finished success=$success")
                sink?.invoke(downloadId, success)
            }
        }
        held[sessionId] = callback
        runCatching { installer.registerSessionCallback(callback, Handler(Looper.getMainLooper())) }
            .onFailure {
                // A callback the platform would not take is the state before this round existed: the
                // broadcast answers the same session, so say what was lost and carry on.
                held.remove(sessionId)
                android.util.Log.w("AuraStoreInstall", "no session callback for $sessionId")
            }
    }
}
