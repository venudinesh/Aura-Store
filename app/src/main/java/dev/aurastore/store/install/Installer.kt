package dev.aurastore.store.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.aurastore.store.download.DownloadItem
import java.io.File

/**
 * Package installation.
 *
 * A normal (non-privileged) app cannot install silently — that is an Android platform rule, not a
 * missing setting. What can be removed is the *friction* around it:
 *
 *  - the permission is requested once through the app-specific Android screen,
 *  - the package that triggered the request is remembered, and the moment the permission comes back
 *    the install continues by itself (no second tap on Install),
 *  - packages go through [PackageInstaller], so the system performs an **update in place** when the
 *    signature matches, keeping the app's data instead of uninstalling and reinstalling it,
 *  - a bundle's packages go through one session together ([installBundle]): a split set is one app, and
 *    the system has no way to merge two separate installs of the same package,
 *  - a build whose publisher differs from the app already on the device is never handed over to be
 *    refused: [signerOf] catches that here, and the row offers the uninstall the platform requires,
 *  - the result comes back through a broadcast, so the queue can show Installed / Failed instead of
 *    guessing.
 */
object Installer {

    const val ACTION_INSTALL_RESULT = "dev.aurastore.store.INSTALL_RESULT"
    private const val PREFS = "aurastore_installs"
    private const val KEY_PENDING_PATH = "pending_install_path"
    private const val KEY_PENDING_PACKAGE = "pending_install_package"
    private const val KEY_PENDING_ID = "pending_install_id"

    /** Every package of a parked bundle install, one path per line. Absent for a single package. */
    private const val KEY_PENDING_BUNDLE = "pending_install_bundle"

    /** [PackageInstaller] stamps its own wall clock on a commit, so the two can disagree by a little. */
    private const val INSTALL_CLOCK_SLACK_MS = 5_000L

    enum class Outcome {
        /** A session was committed; [InstallResultReceiver] will report the verdict. */
        STARTED,

        /** The system installer sheet is open — only the user can answer it. */
        WAITING_USER,

        NEEDS_PERMISSION,
        FAILED,

        /**
         * The device would not take this set of packages at all.
         *
         * Only a bundle can answer this: a single APK that the session refuses has failed on its own
         * bytes, whereas a set that will not commit is the platform declining splits — a different
         * advice, and a different thing to tell the user.
         */
        UNSUPPORTED,
    }

    /** An install that was parked for the permission and has now been handed over again. */
    data class Resumed(val downloadId: String, val outcome: Outcome)

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        // The app-specific page: one switch, no hunting through the global settings list.
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** True when the package is currently installed for this user. */
    fun isInstalled(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * The publisher of the package currently on the device, in the same shape as
     * [DownloadItem.apkSignerSha256] — so the two can be compared to decide whether a downloaded build
     * is allowed to go in over the top of the installed one.
     *
     * Same certificate list, same digest as [ApkInspector] takes from the file: `apkContentsSigners`
     * first entry, sha-256 of its bytes. Null when the package is not installed or its certificates
     * cannot be read, which is what makes "not installed" and "unreadable" distinguishable from a
     * genuine mismatch at the call site.
     */
    fun signerOf(context: Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        return runCatching {
            val info = context.packageManager.getPackageInfo(packageName, flags)
            val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION") info.signatures?.firstOrNull()
            }
            cert?.toByteArray()?.let { ApkInspector.sha256(it) }
        }.getOrNull()
    }

    /**
     * Whether the install of [item] actually landed, rather than merely looking like it did.
     *
     * Presence alone is not proof. A sheet the user dismissed leaves the previous build installed, and
     * a mod whose file carries the same version code as what is already on the phone answers "is it
     * installed?" with a yes that never happened — which then wrote an install record, pulled that app
     * out of the catalogue sweep and made its real update vanish from the You tab. So the package has
     * to match the file *and* have changed after [DownloadItem.installHandedOverAt].
     */
    fun installConfirmed(context: Context, item: DownloadItem): Boolean {
        val pkg = item.apkPackage ?: return false
        val info = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.getOrNull() ?: return false
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        val sameBuild = item.apkVersionCode == null || item.apkVersionCode == code
        return sameBuild && info.lastUpdateTime >= item.installHandedOverAt - INSTALL_CLOCK_SLACK_MS
    }

    /**
     * Installs (or updates in place) the package in [file].
     *
     * When the unknown-sources permission is missing the file is remembered and the settings screen
     * is opened; [resumePending] finishes the job when the user comes back.
     *
     * One package only: the set a bundle unpacks goes through [installBundle], because this path's
     * first choice is the system sheet, and the sheet takes a single package.
     */
    fun install(context: Context, file: File, item: DownloadItem? = null): Outcome {
        if (!canInstall(context)) {
            rememberPending(context, file, item)
            openUnknownSourcesSettings(context)
            return Outcome.NEEDS_PERMISSION
        }
        // The official Liteapks client hands the APK to the system installer through a FileProvider
        // intent, and so do we: on several OEM ROMs (Realme/ColorOS among them) a PackageInstaller
        // session from a sideloaded app is aborted with STATUS_FAILURE_ABORTED, while the intent path
        // opens the normal installer sheet. The session remains as a fallback.
        //
        // The two paths differ in one important way: a session calls back, an intent sheet does not.
        // Saying WAITING_USER rather than STARTED is what lets the queue describe the real state and
        // offer the sheet again instead of claiming an install it has no news about.
        if (startInstallIntent(context, file, item)) return Outcome.WAITING_USER
        return startSession(context, listOf(file), item)
    }

    /**
     * Installs a bundle's packages as one commit.
     *
     * A split set is one app: the base without its `config.arm64_v8a` is a package the platform refuses
     * as incomplete, and two sessions cannot be merged into one installed app afterwards. So every APK
     * goes into a single [PackageInstaller] session, which is the only public way to hand the system a
     * set — the intent path is not an option here, because the sheet it opens takes one package.
     *
     * The archive they came out of is not this object's business: [files] is the whole of what goes in.
     */
    fun installBundle(context: Context, files: List<File>, item: DownloadItem? = null): Outcome {
        if (files.isEmpty()) return Outcome.FAILED
        if (!canInstall(context)) {
            rememberPending(context, files, item)
            openUnknownSourcesSettings(context)
            return Outcome.NEEDS_PERMISSION
        }
        return startSession(context, files, item)
    }

    private fun startInstallIntent(context: Context, file: File, item: DownloadItem?): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Several apps on a power user's phone can handle an APK (file managers, 9ADB, Termux),
            // so target the platform installer explicitly instead of showing a chooser.
            val handlers = context.packageManager
                .queryIntentActivities(intent, 0)
                .map { it.activityInfo }
            val system = handlers.firstOrNull { it.packageName.contains("packageinstaller", ignoreCase = true) }
            if (system != null) intent.setClassName(system.packageName, system.name)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Continues an install that was parked while the permission was being granted.
     *
     * Returns the download id it continued, so the queue can follow the install, or null when there
     * was nothing to continue. It goes through the intent path first for the same reason [install]
     * does: a session created by a sideloaded app is aborted outright by some system installers —
     * LineageOS on Android 16 (Redmi Note 8 Pro) answered the callback with status -1 and no
     * message, which surfaced in the UI as "Installer status -1" and left a verified file
     * looking failed.
     */
    fun resumePending(context: Context): Resumed? {
        if (!canInstall(context)) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PENDING_PATH, null) ?: return null
        val id = prefs.getString(KEY_PENDING_ID, null)
        val bundle = prefs.getString(KEY_PENDING_BUNDLE, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.map(::File)
            ?.toList()
            .orEmpty()
        prefs.edit {
            remove(KEY_PENDING_PATH)
            remove(KEY_PENDING_PACKAGE)
            remove(KEY_PENDING_ID)
            remove(KEY_PENDING_BUNDLE)
        }
        // A parked bundle is the only shape that can still go in as a set; if its pieces went with the
        // cache there is nothing to hand over, and saying FAILED beats installing half an app.
        if (bundle.size > 1) {
            if (bundle.any { !it.isFile }) return null
            val outcome = startSession(context, bundle, null, id)
            return if (outcome == Outcome.FAILED || id == null) null else Resumed(id, outcome)
        }
        val file = File(path)
        if (!file.isFile) return null
        val outcome = if (startInstallIntent(context, file, null)) {
            Outcome.WAITING_USER
        } else {
            startSession(context, listOf(file), null, id)
        }
        if (outcome == Outcome.FAILED || id == null) return null
        return Resumed(id, outcome)
    }

    private fun rememberPending(context: Context, file: File, item: DownloadItem?) =
        rememberPending(context, listOf(file), item)

    private fun rememberPending(context: Context, files: List<File>, item: DownloadItem?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_PENDING_PATH, files.first().absolutePath)
            putString(
                KEY_PENDING_BUNDLE,
                if (files.size > 1) files.joinToString("\n") { it.absolutePath } else null,
            )
            putString(KEY_PENDING_PACKAGE, item?.apkPackage)
            putString(KEY_PENDING_ID, item?.id)
        }
    }

    /**
     * Writes [files] into one session and commits it.
     *
     * Each package is written under its own name, the way the queue names it on disk, and the platform
     * works out which of them is the base and which are its splits from the manifests inside.
     */
    private fun startSession(
        context: Context,
        files: List<File>,
        item: DownloadItem?,
        explicitId: String? = null,
    ): Outcome {
        val installer = context.packageManager.packageInstaller
        return try {
            // MODE_FULL_INSTALL over an existing package is an update in place: same signature
            // means the system keeps the app's data instead of uninstalling it first.
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply { setSize(files.sumOf { it.length() }) }
            val sessionId = installer.createSession(params)
            // The one channel that can hear about a sheet the user cancelled while the app was alive; the
            // commit broadcast usually says the same thing and whichever arrives first wins.
            InstallSessions.watch(installer, sessionId, explicitId ?: item?.id)
            installer.openSession(sessionId).use { session ->
                files.forEach { file ->
                    file.inputStream().use { input ->
                        session.openWrite(file.name, 0, file.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                }
                val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = ACTION_INSTALL_RESULT
                    putExtra("download_id", explicitId ?: item?.id)
                    putExtra("file_path", files.first().absolutePath)
                    putExtra("package_name", item?.apkPackage)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    flags,
                )
                session.commit(pending.intentSender)
            }
            Outcome.STARTED
        } catch (e: Exception) {
            forgetPending(context)
            // A device that will not take the set as committed is a different problem from a bad file,
            // and the queue can only say which of the two the user is looking at.
            if (files.size > 1) Outcome.UNSUPPORTED else Outcome.FAILED
        }
    }

    private fun forgetPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_PENDING_PATH)
            remove(KEY_PENDING_PACKAGE)
            remove(KEY_PENDING_ID)
            remove(KEY_PENDING_BUNDLE)
        }
    }

    /** Remembers which catalogue post a package came from, so the library can check for updates. */
    fun rememberInstalled(context: Context, item: DownloadItem) {
        val pkg = item.apkPackage ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putLong("post:$pkg", item.postId)
            putString("label:$pkg", item.versionLabel)
            putLong("versionCode:$pkg", item.apkVersionCode ?: -1L)
            putString("signer:$pkg", item.apkSignerSha256)
            putLong("installedAt:$pkg", System.currentTimeMillis())
        }
    }

    fun forget(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove("post:$packageName")
            remove("label:$packageName")
            remove("versionCode:$packageName")
            remove("signer:$packageName")
            remove("installedAt:$packageName")
        }
    }

    /**
     * Opens the system's own uninstall sheet for [packageName]; nothing here deletes a package silently.
     *
     * Two callers mean two different things by it. Removing a download removes our record with it, while
     * a row waiting on a publisher clash asks for the removal alone and keeps the record — the user may
     * answer *Cancel*, and an app this store installed must not change families because of a tap.
     *
     * @return false when the platform refused to show the sheet at all, which is the one outcome the
     *   caller has to say something about: a control that swallows a tap teaches the wrong lesson.
     */
    fun uninstall(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.fold(
            onSuccess = { true },
            onFailure = { e ->
                android.util.Log.w("Aura Store", "uninstall sheet refused for $packageName", e)
                false
            },
        )
    }

    fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
