package dev.aurastore.store.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.aurastore.store.download.DownloadEngine

/**
 * The system's own "this package went on / off the device" broadcasts.
 *
 * The install sheet this app opens through an intent answers to nobody: it is another process, and
 * unless the package lands in a `PackageInstaller` session — which ColorOS and LineageOS both abort
 * outright for a sideloaded app — there is no callback at all. That is why a queue row used to sit on
 * "Installing…" forever after the user pressed Cancel, and why a finished install was only noticed
 * the next time the app came to the foreground.
 *
 * `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REPLACED` are sent by the platform to every app, so the
 * queue can be corrected the moment the package exists. Registered once for the life of the process
 * by [dev.aurastore.store.AuraStoreApp]; the filters are protected system broadcasts, so it is
 * registered as not-exported and nothing outside the system can spoof it.
 */
class PackageEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg.isBlank()) return
        val added = intent.action == Intent.ACTION_PACKAGE_ADDED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED
        if (added) {
            DownloadEngine.onPackageChanged(context.applicationContext, pkg, present = true)
            return
        }
        // A replace arrives as REMOVED (with EXTRA_REPLACING) and then as ADDED; only a real removal
        // may move a row back, otherwise every update would flicker through it.
        val replaced = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (!replaced) {
            DownloadEngine.onPackageChanged(context.applicationContext, pkg, present = false)
        }
    }

    companion object {
        /** The filter this receiver is registered with — kept in one place with the handler. */
        val actions: List<String> = listOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED,
        )
    }
}
