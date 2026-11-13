package dev.aurastore.store.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import dev.aurastore.store.download.DownloadEngine

/**
 * Receives the system's verdict for a [PackageInstaller] session, so the queue can move to
 * Installed (and remember the package) or show the real failure reason.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Installer.ACTION_INSTALL_RESULT) return
        val id = intent.getStringExtra("download_id")
        val packageName = intent.getStringExtra("package_name")
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        android.util.Log.i("AuraStoreInstall", "session result status=$status message=$message pkg=$packageName")
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Not a failure: the platform took the packages and wants the user to say yes. The session is
            // committed by then, so the only way that question can be asked is by launching the
            // confirmation sent back with the answer — a bundle has no other sheet, and without this the
            // row reports an install that was merely waiting.
            DownloadEngine.onInstallAwaitingUser(
                context = context.applicationContext,
                downloadId = id,
                packageName = packageName,
                sheetShown = showUserAction(context, intent),
            )
            return
        }
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            // Also not a failure: this is the answer the platform gives when the sheet was closed without
            // a yes — the user tapping *Cancel*, or an OEM installer aborting the session. Its message
            // ("INSTALL_FAILED_ABORTED: User rejected permissions" on the Redmi) reads like a fault and
            // names a permission nobody withheld, so the row goes back to Ready instead of quoting it.
            DownloadEngine.onInstallCanceled(context.applicationContext, id, packageName)
            return
        }
        DownloadEngine.onInstallResult(
            context = context.applicationContext,
            downloadId = id,
            packageName = packageName,
            success = status == PackageInstaller.STATUS_SUCCESS,
            message = message?.takeIf { it.isNotBlank() } ?: installerReason(status),
        )
    }

    /**
     * Asks the system to show its own install confirmation.
     *
     * Where that question is filed is not something the SDK publishes: this ROM hands it back as a plain
     * [Intent] under `EXTRA_INTENT`, while the name other builds use is the hidden `USER_ACTION` key, and
     * some of those wrap it as an [IntentSender]. All of them are started rather than one of them doing
     * nothing at all. A receiver's context needs `NEW_TASK` to start anything, and the start can still be
     * refused as a background one — which is why the caller is told whether the sheet is on its way
     * instead of being left to assume it.
     */
    private fun showUserAction(context: Context, result: Intent): Boolean {
        val extras = result.extras
        val action = extras?.get(Intent.EXTRA_INTENT) ?: extras?.get(USER_ACTION)
        if (action == null) {
            android.util.Log.w("AuraStoreInstall", "no confirmation in ${extras?.keySet()}")
            return false
        }
        return when (action) {
            is Intent -> runCatching {
                context.startActivity(action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            is IntentSender -> runCatching {
                context.startIntentSender(action, null, 0, 0, Intent.FLAG_ACTIVITY_NEW_TASK)
            }.isSuccess
            else -> false
        }
    }

    private companion object {
        /** The key some builds file their install confirmation under; the SDK keeps it hidden. */
        const val USER_ACTION = "android.content.pm.extra.USER_ACTION"
    }
}
