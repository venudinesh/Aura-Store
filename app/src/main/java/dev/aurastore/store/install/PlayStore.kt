package dev.aurastore.store.install

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.aurastore.store.data.playPackageId

/**
 * Linking back to the app's own store page.
 *
 * The catalogue stores the original `play.google.com` URL for every app (`original_download_url`).
 * Rather than printing that URL in the info table, the detail page turns it into a button that opens
 * the Play Store app (with the browser as a fallback).
 */
object PlayStore {

    /** `https://play.google.com/store/apps/details?id=com.spotify.music` -> `com.spotify.music`. */
    fun packageId(url: String?): String? = playPackageId(url)

    /** Returns true when the Play Store (or, failing that, a browser) was started. */
    fun open(context: Context, packageName: String): Boolean {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Phones with a second store (TapTap, Aptoide, ...) also answer market://, which turns this
        // into an "Open with" chooser. Target Google Play explicitly when it is installed.
        val play = runCatching {
            context.packageManager.queryIntentActivities(market, 0)
                .map { it.activityInfo }
                .firstOrNull { it.packageName == "com.android.vending" || it.packageName.contains("vending") }
        }.getOrNull()
        if (play != null) {
            market.setClassName(play.packageName, play.name)
            if (runCatching { context.startActivity(market) }.isSuccess) return true
        }
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return when {
            runCatching { context.startActivity(market) }.isSuccess -> true
            runCatching { context.startActivity(web) }.isSuccess -> true
            else -> false
        }
    }
}
