package dev.aurastore.store.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dev.aurastore.store.data.AppRelease
import dev.aurastore.store.data.AppReleases
import dev.aurastore.store.data.Settings
import dev.aurastore.store.install.Installer
import dev.aurastore.store.version.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Why an update did not reach the installer, in the one word the screen needs. */
enum class AppUpdateFailure { NETWORK, NO_CHECKSUM, CHECKSUM, INSTALLER }

/**
 * The store's own update, from "not asked yet" to "the system installer is open".
 *
 * [Available] is an offer waiting for an answer, not a download; [Downloading] carries the bytes so
 * the dialog can draw the same bar the queue draws; [Idle] after a finished download means the sheet
 * is the system's now and this app has nothing left to say about it.
 */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val received: Long, val total: Long) : AppUpdateState {
        val progress: Float
            get() = if (total <= 0L) 0f else (received.toFloat() / total).coerceIn(0f, 1f)
    }
    data class Installing(val release: AppRelease) : AppUpdateState
    data class Failed(val release: AppRelease?, val failure: AppUpdateFailure) : AppUpdateState
}

/** What this installation is, as the version row and the comparison both need it. */
data class InstalledBuild(val version: AppVersion?, val name: String, val code: Long)

/**
 * Checks GitHub for a newer build of Aura Store, downloads it, verifies its published SHA-256, and
 * hands it to the existing installer.
 *
 * The comparison is [AppVersion] against the version name in the package record, so `1.10.0` is
 * newer than `1.9.9` rather than alphabetically smaller. The asset is picked by signature: a debug
 * build takes the debug APK, a release build the signed one, because Android refuses the other. A
 * release with no hash is not installed at all — the check is the point of the feature, and a
 * download nobody can verify is not an update this store hands over.
 */
object AppUpdater {

    private const val TAG = "AuraUpdate"

    /** How often an automatic check may ask GitHub. Manual checks ignore it. */
    private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000

    /** The longest a check may hold the state before it is called a network failure. */
    private const val CHECK_TIMEOUT_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** The newest release the last check found, kept after the offer is dismissed. */
    private val _lastRelease = MutableStateFlow<AppRelease?>(null)
    val lastRelease: StateFlow<AppRelease?> = _lastRelease.asStateFlow()

    fun installed(context: Context): InstalledBuild {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val name = info?.versionName.orEmpty().ifBlank { "0.0.0" }
        val code = info?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                it.longVersionCode
            } else {
                @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        } ?: 0L
        return InstalledBuild(AppVersion.parse(name), name, code)
    }

    /** True when [release] names a build above what is installed. */
    fun isNewer(context: Context, release: AppRelease): Boolean {
        val installed = installed(context)
        return installed.version?.let { release.version > it } ?: (release.version.code > installed.code)
    }

    /**
     * The check the store runs when it opens.
     *
     * Silent about being current and about a version whose offer was already shown; a failure is
     * also left for the Settings row rather than covering the first screen of the app.
     */
    fun autoCheck(context: Context) {
        val app = context.applicationContext
        if (!Settings.appAutoUpdate.value) return
        val now = System.currentTimeMillis()
        if (now - Settings.appUpdateCheckedAt.value < CHECK_EVERY_MS) return
        check(app, manual = false)
    }

    /** Asks GitHub now. [manual] is the Settings tap, which always opens the offer. */
    fun check(context: Context, manual: Boolean = true) {
        val app = context.applicationContext
        val current = _state.value
        if (current is AppUpdateState.Checking ||
            current is AppUpdateState.Downloading ||
            current is AppUpdateState.Installing
        ) {
            return
        }
        _state.value = AppUpdateState.Checking
        scope.launch {
            val result = runCatching {
                withTimeout(CHECK_TIMEOUT_MS) { AppReleases.latest(isDebuggable(app)) }
            }
            // A check that never reached GitHub is not a check; leaving the timestamp alone means
            // the next launch tries again rather than waiting out the throttle on a dead network.
            if (result.isSuccess) Settings.markAppUpdateChecked(app)
            result.exceptionOrNull()?.let { Log.w(TAG, "GitHub release check failed", it) }
            val release = result.getOrNull()
            // A failed check keeps the offer the last good one found; it says nothing about GitHub.
            if (release != null) _lastRelease.value = release
            _state.value = when {
                result.isFailure -> AppUpdateState.Failed(null, AppUpdateFailure.NETWORK)
                release == null -> AppUpdateState.UpToDate
                !isNewer(app, release) -> AppUpdateState.UpToDate
                !manual && Settings.appUpdatePrompted.value == release.version.text -> AppUpdateState.Idle
                else -> {
                    if (!manual) Settings.markAppUpdatePrompted(app, release.version.text)
                    AppUpdateState.Available(release)
                }
            }
        }
    }

    /** Re-opens an offer the user put down, without asking GitHub again. */
    fun present(release: AppRelease) {
        val current = _state.value
        if (current is AppUpdateState.Downloading || current is AppUpdateState.Installing) return
        _state.value = AppUpdateState.Available(release)
    }

    /** Closes the offer. The next automatic check may raise it again after [CHECK_EVERY_MS]. */
    fun dismiss() {
        val current = _state.value
        if (current is AppUpdateState.Downloading || current is AppUpdateState.Installing) return
        _state.value = AppUpdateState.Idle
    }

    /**
     * Streams [release] to the cache, hashes it as it arrives, and opens the installer when the hash
     * matches. The file is deleted on every failure path, so a bad download cannot be installed by
     * the next attempt.
     */
    fun downloadAndInstall(context: Context, release: AppRelease) {
        val app = context.applicationContext
        val current = _state.value
        if (current is AppUpdateState.Downloading || current is AppUpdateState.Installing) return
        if (release.sha256 == null) {
            _state.value = AppUpdateState.Failed(release, AppUpdateFailure.NO_CHECKSUM)
            return
        }
        _state.value = AppUpdateState.Downloading(release, 0L, release.apkSize)
        scope.launch {
            val target = File(File(app.cacheDir, "apk").apply { mkdirs() }, "aurastore-${release.version.text}.apk")
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetch(release, target) { received, total ->
                        _state.value = AppUpdateState.Downloading(release, received, total)
                    }
                }
            }
            if (result.isFailure) {
                target.delete()
                val failure = if (result.exceptionOrNull() is ChecksumMismatch) {
                    AppUpdateFailure.CHECKSUM
                } else {
                    AppUpdateFailure.NETWORK
                }
                _state.value = AppUpdateState.Failed(release, failure)
                return@launch
            }
            _state.value = AppUpdateState.Installing(release)
            val outcome = withContext(Dispatchers.Main) { Installer.install(app, target) }
            if (outcome == Installer.Outcome.FAILED || outcome == Installer.Outcome.UNSUPPORTED) {
                _state.value = AppUpdateState.Failed(release, AppUpdateFailure.INSTALLER)
            } else {
                Settings.markAppUpdatePrompted(app, release.version.text)
                _state.value = AppUpdateState.Idle
            }
        }
    }

    private fun fetch(release: AppRelease, target: File, onProgress: (Long, Long) -> Unit) {
        val expected = release.sha256 ?: throw IOException("no published hash")
        AppReleases.open(release.apkUrl).use { response ->
            val total = response.body.contentLength().takeIf { it > 0L } ?: release.apkSize
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            response.body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        onProgress(received, total)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) throw ChecksumMismatch()
        }
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private class ChecksumMismatch : IOException("the download did not match its published SHA-256")
}
