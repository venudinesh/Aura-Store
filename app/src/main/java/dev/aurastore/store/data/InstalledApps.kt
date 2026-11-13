package dev.aurastore.store.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

data class LibraryEntry(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val hasLaunchIntent: Boolean,
    /** Catalogue post this package was installed from, when it was installed through this app. */
    val postId: Long?,
    /** The catalogue's label of the build that was installed, the only thing comparable with a new one. */
    val installedLabel: String?,
    val knownSigner: String?,
    /** When this package last changed on the device. Used to decide which apps are worth a lookup. */
    val lastUpdateAt: Long,
)

/**
 * Everything the Library screen needs.
 *
 * The catalogue API has no batch "which of these packages have updates" endpoint. So an app that
 * came through this app is tracked by what we recorded at install time — the post id and the label of
 * the build that went in — and compared label against label. Every other app is only reachable by
 * name: [dev.aurastore.store.install.PackageLookup] searches the catalogue for its label and keeps the
 * listing whose Play URL names this package. Neither rule compares the APK's `versionName` with a
 * build label — see [dev.aurastore.store.install.Updates].
 */
object InstalledApps {

    private const val PREFS = "aurastore_installs"

    /** How long a real install can take between the package landing and us recording it. */
    private const val RECORD_LAG_SLACK_MS = 5L * 60 * 1000

    /**
     * Whether a remembered install is backed by a package that actually changed.
     *
     * Rows used to be marked installed whenever the package was merely *present*, which wrote records
     * for updates the user never accepted. Honouring one hides the app from the catalogue name sweep —
     * it reads as ours — so a genuine update to it disappears from the You tab for good. A record
     * older than the package's own last change is that lie, and dropping it lets the sweep see the app
     * again.
     */
    internal fun recordIsLive(claimedAt: Long, lastUpdateAt: Long): Boolean =
        claimedAt <= 0L || lastUpdateAt >= claimedAt - RECORD_LAG_SLACK_MS

    fun load(context: Context): List<LibraryEntry> {
        val pm = context.packageManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remembered = mutableMapOf<String, Long>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("post:") && value is Long && value > 0) {
                remembered[key.removePrefix("post:")] = value
            }
        }

        val packages = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            emptyList()
        }

        return packages.mapNotNull { info ->
            val appInfo: ApplicationInfo = info.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val claimedAt = prefs.getLong("installedAt:${info.packageName}", 0L)
            LibraryEntry(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(info.packageName),
                versionName = info.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION") info.versionCode.toLong()
                },
                isSystem = isSystem,
                hasLaunchIntent = pm.getLaunchIntentForPackage(info.packageName) != null,
                postId = remembered[info.packageName]?.takeIf { recordIsLive(claimedAt, info.lastUpdateTime) },
                installedLabel = prefs.getString("label:${info.packageName}", null),
                knownSigner = prefs.getString("signer:${info.packageName}", null),
                lastUpdateAt = info.lastUpdateTime,
            )
        }.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
    }

    fun installedVersion(context: Context, packageName: String): String? =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull()

    /**
     * The catalogue label of the build this store installed for [packageName], or null when the
     * package was not installed from here (or the record predates labels).
     *
     * The detail page compares this against the label it is offering, because that is the pair the
     * update sweep compares — an APK's own `versionName` against a catalogue label is not a version
     * order, it is two different naming schemes. Reading the preference directly keeps a page about
     * one app from scanning every package on the device.
     */
    fun installedLabel(context: Context, packageName: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("label:$packageName", null)
}
