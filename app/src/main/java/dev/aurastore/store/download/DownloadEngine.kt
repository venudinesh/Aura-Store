package dev.aurastore.store.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.aurastore.store.MainActivity
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.Hosts
import dev.aurastore.store.data.SecureTransport
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.sources.percentDecode
import dev.aurastore.store.install.ApkInspector
import dev.aurastore.store.install.InstallSessions
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.SessionAction
import dev.aurastore.store.install.Obb
import dev.aurastore.store.install.ObbInstaller
import dev.aurastore.store.install.PackageEventReceiver
import dev.aurastore.store.install.QuickInstall
import dev.aurastore.store.install.Xapk
import dev.aurastore.store.install.sessionAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadState {
    QUEUED, DOWNLOADING, PAUSED, VERIFYING, READY, NEEDS_PERMISSION,

    /**
     * The system installer sheet is open. Unlike INSTALLING (a `PackageInstaller` session, which
     * calls back), the intent path gets no answer at all, so this state says what is really true:
     * the app is waiting for the user, and tapping it opens the sheet again.
     */
    AWAITING_CONFIRMATION,

    INSTALLING, INSTALLED, FAILED, CANCELED
}

/** One entry of a row's history, oldest first. */
data class StateChange(val state: DownloadState, val at: Long)

@androidx.compose.runtime.Immutable
data class DownloadItem(
    val id: String,
    val postId: Long,
    val title: String,
    val iconUrl: String?,
    val versionLabel: String,
    val fileLabel: String,
    val url: String,
    val fileName: String,
    val state: DownloadState = DownloadState.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val error: String? = null,
    val sha256: String? = null,
    /**
     * The digest the *source* published for this artefact, when it publishes one. Compared with
     * [sha256] before the row is allowed to become READY; null for every catalogue file, because
     * the catalogue publishes none.
     */
    val expectedSha256: String? = null,
    val apkPackage: String? = null,
    val apkVersionCode: Long? = null,
    val apkSignerSha256: String? = null,
    val apkVersionName: String? = null,
    val localPath: String? = null,
    /** An update the store queued itself: install as soon as the package verifies. */
    val autoInstall: Boolean = false,
    /**
     * This build is verified and ready, but the package on the device was signed by somebody else, so
     * the platform will refuse it as an update. The row is not installable until that package is gone.
     */
    val uninstallFirst: Boolean = false,
    /** A host-side substitution worth telling the user about, e.g. "host offline, used another build". */
    val notice: String? = null,
    /**
     * A game's expansion file rather than its package. Those rows download and hash like any other,
     * but are never handed to an installer: they have to land in `Android/obb`, which Android will not
     * let this app write (see [dev.aurastore.store.install.ObbInstaller]).
     */
    val dataFile: Boolean = false,
    /**
     * The packages a bundle was unpacked into, base first, and what an installer is handed.
     *
     * [localPath] stays the archive: it is the artefact the transfer paid for and hashed, the name the
     * row was quoted, and the thing every package here came out of, so a row whose pieces Android
     * cleared from the cache unpacks them again from it. Empty for a single package, and the two lists
     * together are what the row owns on disk.
     */
    val bundleFiles: List<String> = emptyList(),
    /**
     * The bundle's archive carries expansion files inside it, so it is not derived cache.
     *
     * The packages come out of it again whenever the cache loses them, and the game's data has no other
     * copy on this phone: a row that knows this keeps its archive, and its row keeps offering *Add data*.
     * False for every single package, and for a bundle whose archive holds nothing but packages.
     */
    val dataInside: Boolean = false,
    /** Smoothed bytes per second over the last few progress ticks; 0 until a sample is worth showing. */
    val bytesPerSecond: Long = 0L,
    /** null while the total size or the speed is unknown. */
    val etaSeconds: Long? = null,
    /** Every state this row has passed through, oldest first. */
    val transitions: List<StateChange> = emptyList(),
    /** Why the row failed, in a kind the UI can give advice for. */
    val failure: Failure? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isActive: Boolean
        get() = state == DownloadState.QUEUED || state == DownloadState.DOWNLOADING || state == DownloadState.VERIFYING

    /**
     * Whether clearing the list may take this row, and its file, away.
     *
     * Not the same question as [isActive]: a transfer that is paused, failed or waiting for a permission
     * is not busy, but the bytes it holds are still the point of it. The two states here are the ones
     * where the row has done its job — the app is on the device, or the user gave up — and a row waiting
     * on an uninstall is the clearest case of a finished-looking entry that is nothing of the sort.
     */
    val isDisposable: Boolean
        get() = state == DownloadState.INSTALLED || state == DownloadState.CANCELED

    /**
     * When this row last went to the installer, or 0 when it never did.
     *
     * The moment is what turns "the package is on the device" into "the package was installed by us":
     * anything that proves an install has to have happened after the handover.
     */
    val installHandedOverAt: Long
        get() = transitions.lastOrNull {
            it.state == DownloadState.INSTALLING || it.state == DownloadState.AWAITING_CONFIRMATION
        }?.at ?: 0L

    /**
     * Whether the artefact this row asked for is a set of packages rather than one.
     *
     * Judged from the name, so it is already true while the bytes are still arriving — which is what
     * the row's progress line reads, and the same test the verify step branches on.
     */
    val isBundle: Boolean
        get() = Xapk.isBundle(fileName)
}

/** What to do with the row a duplicate request collapsed onto. */
internal enum class Reuse { INSTALL, RESUME, IDLE }

/**
 * The rule [DownloadEngine] applies when a request names a build the queue already has a row for,
 * given whether the bytes that row points at are really still there.
 */
internal fun reuseOf(item: DownloadItem, autoInstall: Boolean, fileLive: Boolean): Reuse = when {
    // Nothing to install: a row still in flight will make its file, anything else has to run again.
    !fileLive -> if (item.isActive) Reuse.IDLE else Reuse.RESUME

    // A whole file the installer can take: an update asks for the sheet, a plain download waits.
    item.state == DownloadState.READY || item.state == DownloadState.INSTALLED ||
        item.state == DownloadState.NEEDS_PERMISSION || item.state == DownloadState.AWAITING_CONFIRMATION ->
        if (autoInstall) Reuse.INSTALL else Reuse.IDLE

    else -> Reuse.IDLE
}

/**
 * Whether a verified build and the package already on the device come from different publishers.
 *
 * Android refuses an update whose signature does not match, *after* the bytes have been paid for, and
 * nothing short of holding both files reveals it beforehand — the catalogue publishes no signer digest.
 * Once the artefact is on disk this is a fact rather than a gamble: [installedSigner] is the running
 * package's certificate, [fileSigner] the one [ApkInspector] read out of the download. Either side
 * being unknown is read as "no evidence of a clash", because the row is about to be handed over and the
 * installer, not this rule, gets the final say.
 */
internal fun signatureClash(installedSigner: String?, fileSigner: String?): Boolean =
    installedSigner != null && fileSigner != null && !installedSigner.equals(fileSigner, ignoreCase = true)

/**
 * Whether [item]'s file must not be handed to an installer yet, given what the package manager says
 * about the app that would be updated in place.
 *
 * Nothing installed means nothing to clash with, so a replacement install — the app removed, the
 * catalogue's build going in fresh — is allowed through. With the app present, the live certificate
 * comparison decides; the row's own [DownloadItem.uninstallFirst] is the fallback for the one case the
 * comparison cannot see, a package whose certificates this app could not read.
 */
internal fun installHold(item: DownloadItem, installedSigner: String?, packagePresent: Boolean): Boolean = when {
    !packagePresent -> false
    installedSigner != null -> signatureClash(installedSigner, item.apkSignerSha256)
    else -> item.uninstallFirst
}

/**
 * The files in the queue's cache directory that no row in [items] names — bytes nothing will ever
 * delete again, because clearing the list only frees what a row still points at.
 *
 * A row is the only handle this app has on its file, and a row can go without the file going with it:
 * the process killed while its state was still being written, a queue stored before rows carried
 * paths, an update installed by the platform over our head. So the ownership test is a name prefix
 * (`postId-<first 8 of the row id>`) plus whatever the row's stored [DownloadItem.localPath] actually
 * is, because a resumed row may have taken the next free name in a directory that already held a
 * same-named build, and either spelling of that row is still the row's own bytes.
 */
internal fun orphanApkFiles(dir: File, items: List<DownloadItem>): List<File> {
    if (!dir.isDirectory) return emptyList()
    val owned = HashSet<String>()
    items.forEach { item ->
        owned += "${item.postId}-${item.id.take(8)}"
        item.localPath?.let { owned += File(it).name }
    }
    return dir.listFiles().orEmpty().filter { file ->
        file.isFile && owned.none { prefix -> file.name.startsWith(prefix) }
    }
}

/**
 * Every file a row owns: the artefact it downloaded and, for a bundle, the packages unpacked out of it.
 *
 * [orphanApkFiles] keeps these safe from the sweep by name; this is the other half of the same rule — a
 * row that goes, or that lost what it points at, takes all of its bytes with it. An archive and the set
 * unpacked from it are one row's footprint, and freeing only the first would leave the second ageing in
 * the cache behind a "cached" counter that has already stopped counting it.
 */
internal fun ownedFiles(item: DownloadItem): List<File> =
    (listOfNotNull(item.localPath) + item.bundleFiles).map(::File)

/** Deletes everything [item] owns. A delete that fails is a file Android will reclaim with the cache. */
private fun purgeFiles(item: DownloadItem) = ownedFiles(item).forEach { runCatching { it.delete() } }

/**
 * In-app download engine.
 *
 * States: QUEUED -> DOWNLOADING <-> PAUSED -> VERIFYING -> READY -> (INSTALLING) -> INSTALLED
 *                                                     -> NEEDS_PERMISSION (unknown-sources off)
 *         any -> FAILED / CANCELED
 *
 * Every file is hashed while it streams and then inspected before it may be installed (see
 * [ApkInspector]). The catalogue publishes no hashes, so this proves integrity and identity locally
 * rather than comparing against a server digest. Pausing keeps the partial file and resuming asks
 * the CDN for the remaining byte range, re-hashing the bytes already on disk first so the digest is
 * still over the whole file.
 */
object DownloadEngine {

    private const val TAG = "Aura Store"
    private const val CHANNEL_ID = "downloads"
    private const val NOTIFICATION_ID = 4711

    /** How many times one download may move to another file when a host refuses it. */
    private const val MAX_HOST_SWITCHES = 2

    /**
     * Transient network failures (`Connection reset`, timeouts) are retried from the byte already on
     * disk, the way the Play Store's queue does — a transfer that dies at 90% must not need a tap.
     */
    private const val MAX_TRANSIENT_RETRIES = 3
    private val RETRY_DELAYS_MS = longArrayOf(1_000, 3_000, 6_000)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // streaming
            .addNetworkInterceptor(SecureTransport.HttpsOnly)
            .build()
    }

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items
    
    /** Only notify aggregate if queue state actually changed (not just progress tick). */
    private var lastNotifyAt = 0L
    private const val MIN_NOTIFY_INTERVAL_MS = 500L

    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * Set by [restore], and the only reason a state change further down the call stack can be persisted:
     * the queue is written from [update], which has no context of its own.
     */
    private var appContext: Context? = null

    @Volatile
    private var restored = false

    /**
     * Download id -> the package an install was handed over for. Kept until the package is seen on the
     * device or the row is deleted, so a slow confirmation still resolves the row.
     */
    private val watched = ConcurrentHashMap<String, String>()

    private val watchdogs = ConcurrentHashMap<String, Job>()

    /** What a row says when the installer sheet came back with nothing installed. */
    private const val unconfirmedNotice =
        "Install was not confirmed — the verified file is still here, tap Install again"

    /**
     * What a row says when the installer closed and the package did not go in.
     *
     * Both voices that can report this land on [onInstallCanceled] and therefore on this one sentence: the
     * commit broadcast answering with `STATUS_FAILURE_ABORTED`, and a session this app committed finishing
     * unsuccessful. The sentence above is what the watchdog concludes after five minutes of silence — a
     * guess about a sheet that may still be open — where this one is about an installer that has gone.
     */
    private const val installerEndedNotice =
        "The installer closed without installing this — the verified file is still here, tap Install again"

    /**
     * What a row says when the platform asked to be confirmed and no sheet could be raised from the
     * background. A bundle has no system UI to fall back on, so this is the only way its session can
     * reach the user — and the advice dies the moment a session is committed or answered, which is why
     * it is one constant rather than a string at each call site.
     */
    private const val confirmSheetNotice =
        "Android wants you to confirm this install — tap Install to show that sheet."

    /** The advice is spent once the row has been re-handover or answered; anything else it said stays. */
    private fun spent(notice: String?): String? = notice.takeUnless { it == confirmSheetNotice }

    /** What a row says when the app on the device would have to be removed before this build can go in. */
    private fun clashNotice(title: String) =
        "A different publisher signed this build, so it cannot replace the $title on this device. " +
            "Uninstall it first — that deletes what the app has stored — and this goes in after."

    /** Download id -> smoothed bytes per second, kept out of the row so a tick does not copy a list. */
    private val rates = ConcurrentHashMap<String, Long>()

    /**
     * Download id -> the HTTP call currently fetching it.
     *
     * Cancelling the coroutine is not enough to stop a transfer: [Transfer.stream] blocks inside
     * `execute()` and in the socket read, neither of which consults a cancellation flag. This is the
     * handle that actually closes the connection, and without it a pause left the transfer running to
     * completion while the row's state said PAUSED — the bytes kept arriving, the progress bar kept
     * filling, and Resume had nothing left to do.
     */
    private val calls = ConcurrentHashMap<String, Call>()

    /**
     * Download id -> the live response whose body is being read.
     *
     * The second handle a pause needs, and the one that actually unblocks the reader: the transfer
     * thread is parked inside `InputStream.read` on this stream, and closing it is what makes that
     * read return immediately rather than when the next buffer happens to arrive. OkHttp's own
     * `cancel()` closes the socket underneath it, but that is not the same moment — and the gap was
     * visible on the device as a row that said PAUSED while the progress bar kept filling.
     */
    private val responses = ConcurrentHashMap<String, okhttp3.Response>()

    /**
     * Download id -> the generation of the transfer that is allowed to write the row.
     *
     * Pause, resume, cancel and remove each open a new generation. A transfer captures the generation
     * it was launched with and refuses to write anything once it is no longer current. Closing the
     * socket and cancelling the job are both asynchronous, so without this the old transfer's catch
     * block could land after `pause()` had written PAUSED and put the row back to DOWNLOADING — the
     * retry loop then kept the bytes coming, which is exactly what a pause is supposed to stop.
     */
    private val epochs = ConcurrentHashMap<String, Long>()

    /** Opens a new transfer generation for [id]; anything launched against an older one is spent. */
    private fun newEpoch(id: String): Long = epochs.merge(id, 1L) { old, _ -> old + 1 } ?: 1L

    /**
     * How long to treat a row as "waiting for the user" before giving it an actionable state back.
     * Deliberately generous: the system sheet on these ROMs shows a security scan first, and guessing
     * too early would tell someone their install was abandoned when they were still reading.
     */
    private const val INSTALL_CONFIRM_TIMEOUT_MS = 5L * 60 * 1000

    fun activeCount(): Int = _items.value.count { it.isActive }

    /**
     * Rebuilds the queue from disk, once, before the first screen reads it.
     *
     * The rows outlive the process; the jobs and the files they point at do not. `cache/apk` is a cache,
     * so Android may have emptied it while the app was stopped, and a transfer that was cut off has to
     * continue from the bytes that remained rather than from a progress tick recorded at the moment of
     * death. Every row is therefore re-attached to what is really on disk before anything can see it, and
     * an install that was handed to the system sheet is resolved by whether the package landed.
     */
    fun restore(context: Context) {
        if (restored) return
        synchronized(this) {
            if (restored) return
            restored = true
            val app = context.applicationContext
            appContext = app
            // The session callback needs somewhere to put what it hears, and the queue is the only
            // listener. Wired here, once, because a verdict before this point has no rows to name.
            InstallSessions.sink = { downloadId, success -> onSessionEnd(downloadId, success) }
            val dir = File(app.cacheDir, "apk")
            val stored = QueueStore.load(app).map { releaseHold(app, reattach(reauditInstalled(app, it), dir)) }
            if (stored.isNotEmpty()) {
                _items.update { current -> current + stored.filter { row -> current.none { it.id == row.id } } }
                persist()

                val resumable = stored.filter { it.isActive }
                if (resumable.isNotEmpty()) {
                    DownloadService.ensureRunning(app)
                    resumable.forEach { row ->
                        val epoch = newEpoch(row.id)
                        jobs[row.id] = scope.launch { run(app, row.id, epoch) }
                    }
                }
                reconcileInstalls(app)
                _items.value
                    .filter { it.state == DownloadState.INSTALLING || it.state == DownloadState.AWAITING_CONFIRMATION }
                    // The sheet this app opened is gone with the process, so the watch starts again: the row
                    // says what is true now, and the watchdog gives it an actionable state back if nothing does.
                    .forEach { watchInstall(app, it.id) }
                if (resumable.isNotEmpty()) notifyAggregate(app)
                Log.i(TAG, "restored ${stored.size} queue rows, ${resumable.size} resumed")
            }
            // Whatever the store no longer names has no row left to delete it, so a start is the moment
            // to notice: otherwise an abandoned download ages in the cache behind a "0 B cached" counter.
            pruneIfIdle(app)
        }
    }

    /**
     * Take back an "Installed" the device does not support.
     *
     * Rows written before a confirmation had to be *proved* claim more than the package manager agrees
     * to: a sheet the user dismissed left the old build in place and the row reading Installed beside an
     * app that never changed. Only a row whose file is still here is revisited — an unprovable row with
     * no file would turn into a lost-file failure over something the user did nothing wrong about, a
     * restore from backup being the obvious one. Runs before [reattach], which then checks the file.
     */
    internal fun reauditInstalled(context: Context, item: DownloadItem): DownloadItem {
        if (item.state != DownloadState.INSTALLED) return item
        if (item.localPath?.let { File(it).isFile } != true) return item
        if (Installer.installConfirmed(context, item)) return item
        return item.copy(
            state = DownloadState.READY,
            error = null,
            failure = null,
            notice = unconfirmedNotice,
        )
    }

    /**
     * A row that was waiting for an uninstall the device has since carried out stops waiting.
     *
     * Its notice was written about an app that is no longer on the phone. The handover itself stays
     * where it has always been — a tap — because a start is not the moment to open a system sheet over
     * something the user asked for in an earlier session; [onPackageChanged] is the one that can act
     * the instant the removal really happens.
     */
    private fun releaseHold(context: Context, item: DownloadItem): DownloadItem {
        if (!item.uninstallFirst || Installer.isInstalled(context, item.apkPackage)) return item
        return item.copy(uninstallFirst = false, notice = null)
    }

    /**
     * What a stored row means now that the process has restarted.
     *
     * Three rows are left exactly as they were written: a FAILED one keeps the reason it really failed
     * rather than being rewritten as a lost file, an INSTALLED one records something that happened to the
     * device, and a CANCELED one owns no file. Only an installed row's stale install advice is dropped.
     */
    internal fun reattach(item: DownloadItem, dir: File): DownloadItem {
        if (item.isActive) {
            val target = targetFor(dir, item.id, item)
            return item.copy(
                state = DownloadState.QUEUED,
                localPath = target.absolutePath,
                bytesDownloaded = runCatching { if (target.isFile) target.length() else 0L }.getOrDefault(0L),
                error = null,
                bytesPerSecond = 0L,
                etaSeconds = null,
            )
        }
        val holdsFile = item.state == DownloadState.PAUSED || item.state == DownloadState.READY ||
            item.state == DownloadState.NEEDS_PERMISSION || item.state == DownloadState.INSTALLING ||
            item.state == DownloadState.AWAITING_CONFIRMATION
        if (!holdsFile) {
            // A finished row's advice about raising the install sheet is spent — the install it asked
            // for happened. What else a finished row may say, like where its game data went, is true.
            if (item.state == DownloadState.INSTALLED) return item.copy(notice = spent(item.notice))
            return item
        }
        val file = item.localPath?.let { File(it) }?.takeIf { it.isFile }
            ?: return lostFile(item)
        if (item.state == DownloadState.PAUSED) return item.copy(bytesDownloaded = file.length())
        // A finished file that came back shorter than the transfer reported was truncated under us; there
        // is no honest state for half an APK, and a partial one cannot be resumed by a fresh request.
        if (item.totalBytes > 0 && file.length() != item.totalBytes) return lostFile(item)
        return item
    }

    /** A row whose file is gone: the install it was offering is not something this app can do any more. */
    private fun lostFile(item: DownloadItem): DownloadItem {
        // Whatever the row still names is ours and unusable — a truncated APK, or a bundle's pieces whose
        // archive went with them. Deleting it is what stops those bytes ageing in the cache.
        purgeFiles(item)
        val failure = Failures.lostFileFailure()
        return item.copy(
            state = DownloadState.FAILED,
            error = failure.message,
            failure = failure,
            localPath = null,
            sha256 = null,
            bundleFiles = emptyList(),
            dataInside = false,
            bytesDownloaded = 0L,
            totalBytes = -1L,
            bytesPerSecond = 0L,
            etaSeconds = null,
        )
    }

    fun enqueue(
        context: Context,
        post: PostSummary?,
        file: DownloadFile,
        versionLabel: String,
        postId: Long,
        autoInstall: Boolean = false,
        expectedSha256: String? = null,
    ): String {
        // One row per (post, url): asking for bytes the queue already holds re-fetched the whole file
        // over a verified copy sitting in our own cache. The existing row is the one to act on.
        _items.value.firstOrNull { it.postId == postId && it.url == file.url }
            ?.let { return reuse(context, it, autoInstall) }
        val id = UUID.randomUUID().toString()
        val name = fileNameFor(file.url)
        val item = DownloadItem(
            id = id,
            postId = postId,
            title = post?.title ?: "App #$postId",
            iconUrl = post?.imageUrl,
            versionLabel = versionLabel.ifBlank { post?.latestVersion.orEmpty() },
            fileLabel = listOf(file.kind, file.abi, file.sizeText).filter { it.isNotBlank() }.joinToString(" · "),
            url = file.url,
            fileName = name,
            autoInstall = autoInstall,
            dataFile = Obb.isDataFile(file),
            expectedSha256 = Failures.normalizeDigest(expectedSha256),
        )
        _items.update { it + item }
        persist()
        DownloadService.ensureRunning(context)
        val epoch = newEpoch(id)
        jobs[id] = scope.launch { run(context.applicationContext, id, epoch) }
        return id
    }

    /**
     * What asking for bytes the queue already holds means: a row that finished and verified is offered
     * for install again, one that stopped half way is picked up. Either way the old row is the answer,
     * because a second row means a second copy of the same file.
     */
    private fun reuse(context: Context, item: DownloadItem, autoInstall: Boolean): String {
        val live = item.localPath?.let { File(it).isFile } == true
        when (reuseOf(item, autoInstall, live)) {
            Reuse.INSTALL -> install(context, item.id)
            Reuse.RESUME -> resume(context, item.id)
            Reuse.IDLE -> Unit
        }
        return item.id
    }

    /**
     * Queues a whole build: the package that was picked and every expansion file the catalogue
     * published with the same version, as separate rows.
     *
     * They are rows rather than one download because they finish into different places — the APK goes
     * to an installer and the data has to reach `Android/obb` — and each needs its own hash.
     */
    fun enqueue(
        context: Context,
        post: PostSummary?,
        pick: QuickInstall.Pick,
        postId: Long,
        autoInstall: Boolean = false,
    ): List<String> {
        val ids = mutableListOf(
            enqueue(context, post, pick.file, pick.versionLabel, postId, autoInstall),
        )
        pick.extras.forEach { extra ->
            ids += enqueue(context, post, extra, pick.versionLabel, postId, autoInstall = false)
        }
        return ids
    }

    fun cancel(context: Context, id: String) {
        newEpoch(id)
        calls.remove(id)?.cancel()
        jobs.remove(id)?.cancel()
        unwatch(id)
        rates.remove(id)
        _items.value.firstOrNull { it.id == id }?.let(::purgeFiles)
        update(id) {
            it.copy(
                state = DownloadState.CANCELED,
                bytesDownloaded = 0,
                totalBytes = -1,
                bytesPerSecond = 0L,
                etaSeconds = null,
            )
        }
        notifyAggregate(context)
        pruneIfIdle(context)
    }

    /** Stops the transfer but keeps the bytes on disk, the way the Play Store's queue does. */
    fun pause(context: Context, id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (!item.isActive) return
        // Open the new generation before closing anything: the transfer's own `onCancelled` compares
        // against it, so the socket going away reads as a pause rather than as a network failure
        // worth retrying.
        //
        // The row is written PAUSED *before* any of that, not after. Everything below is asynchronous
        // — closing a body, cancelling a call, cancelling a job — so a progress tick or the transfer's
        // own catch block can land in the middle of this function. Writing the state last made PAUSED
        // lose that race: the row flicked to paused and went on counting bytes.
        update(id) {
            it.copy(state = DownloadState.PAUSED, error = null, bytesPerSecond = 0L, etaSeconds = null)
        }
        newEpoch(id)
        
        // Both handles. The body is the stream the reader is blocked in; `cancel()` closes the
        // socket underneath it. `response()` is never called from here — it throws "response is not
        // yet available" while the call is executing, which is exactly when a pause is asked for.
        responses.remove(id)?.let { response -> runCatching { response.close() } }
        calls.remove(id)?.let { call -> runCatching { call.cancel() } }

        jobs.remove(id)?.cancel()
        rates.remove(id)
        
        // Settle the partial file's length now so resume starts from exactly what we have. The row is
        // already PAUSED above, which is what lets this through: the guard inside it checks the state,
        // and running it before the state was written made it a no-op — resume then began at zero
        // because the count it was handed disagreed with the file on disk.
        settlePausedBytes(id)
        
        notifyAggregate(context)
        pruneIfIdle(context)
    }

    /**
     * Takes the file's length as the row's true byte count once its transfer has stopped writing.
     *
     * The last progress tick can be up to 250 ms behind the file, and [Transfer] only resumes when
     * the count and the file agree exactly — otherwise it starts the whole download again. Called
     * after the streams are closed, so the length is final. A row that has already been resumed is
     * left alone: its new transfer owns the count.
     */
    private fun settlePausedBytes(id: String) {
        if (_items.value.firstOrNull { it.id == id }?.state != DownloadState.PAUSED) return
        update(id) { row ->
            val path = row.localPath ?: return@update row
            row.copy(bytesDownloaded = runCatching { File(path).length() }.getOrDefault(row.bytesDownloaded))
        }
    }

    fun resume(context: Context, id: String) {
        if (_items.value.none { it.id == id }) return
        rates.remove(id)
        update(id) { row ->
            row.copy(
                state = DownloadState.QUEUED,
                error = null,
                failure = null,
                // Read from the file rather than kept from the tick before the pause: the row and the
                // bytes have to agree, or the resumed transfer discards them and starts over.
                bytesDownloaded = row.localPath
                    ?.let { path -> runCatching { File(path).length() }.getOrDefault(row.bytesDownloaded) }
                    ?: row.bytesDownloaded,
                bytesPerSecond = 0L,
                etaSeconds = null,
            )
        }
        DownloadService.ensureRunning(context)
        val epoch = newEpoch(id)
        jobs[id] = scope.launch { run(context.applicationContext, id, epoch) }
    }

    fun retry(context: Context, id: String) = resume(context, id)

    fun install(context: Context, id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val path = item.localPath ?: return
        if (item.dataFile) return
        // Android empties the cache under a running app, and a restored row may have lost its bytes: an
        // installer handed a path that is not there answers with a sheet about nothing, so the row finds
        // out here instead.
        if (!File(path).isFile) {
            loseFile(context, id)
            return
        }
        // A build signed by somebody else cannot go over the top of the running app: the platform
        // refuses it, after the download. Asking the package manager here turns that refusal into a
        // state the row can explain, and an action that says what it costs.
        val pkg = item.apkPackage
        val present = Installer.isInstalled(context, pkg)
        if (installHold(item, if (present) Installer.signerOf(context, pkg) else null, present)) {
            update(id) { it.copy(uninstallFirst = true, notice = clashNotice(item.title)) }
            notifyAggregate(context)
            return
        }
        if (item.uninstallFirst) {
            // The app is gone (or turned out to fit after all): stop telling the user to remove it.
            update(id) { it.copy(uninstallFirst = false, notice = null) }
        }
        // A bundle hands the installer the set unpacked from its archive, not the archive. Those pieces
        // are derived bytes, so a row that lost them to a cache sweep gets them from the archive again
        // instead of failing something it still holds whole.
        val pieces = installableFiles(item, File(context.cacheDir, "apk"))
        if (pieces == null) {
            loseFile(context, id)
            return
        }
        applyInstallOutcome(
            context,
            id,
            if (pieces.size > 1) Installer.installBundle(context, pieces, item)
            else Installer.install(context, pieces.first(), item),
        )
    }

    /**
     * The packages this row can hand an installer now: the archive's unpacked pieces when the row is a
     * bundle, its own file when it is not.
     *
     * Null means there is nothing installable — the artefact is gone, or a bundle still on the device
     * yields no package. A single-element list is the ordinary case and stays on the system-sheet path,
     * which some OEMs require and which cannot take a set.
     */
    private fun installableFiles(item: DownloadItem, dir: File): List<File>? {
        val archive = item.localPath?.let(::File)?.takeIf { it.isFile } ?: return null
        if (item.bundleFiles.isEmpty()) return listOf(archive)
        val pieces = item.bundleFiles.map(::File)
        if (pieces.all { it.isFile }) return pieces
        val contents = Xapk.read(archive) ?: return null
        return Xapk.extract(archive, dir, prefixOf(item.id, item), contents)
    }

    /** The row's bytes are gone for good: say so, and stop treating the row as installable. */
    private fun loseFile(context: Context, id: String) {
        unwatch(id)
        rates.remove(id)
        update(id) { lostFile(it) }
        notifyAggregate(context)
        pruneIfIdle(context)
    }

    /**
     * Moves a row onto the state the handover really produced, and watches it if the platform will answer.
     *
     * The tap and the parked-permission resume both come through here, so a set this device refused
     * cannot be reported one way by one path and another way by the other.
     */
    private fun applyInstallOutcome(context: Context, id: String, outcome: Installer.Outcome) {
        when (outcome) {
            Installer.Outcome.STARTED ->
                update(id) { it.copy(state = DownloadState.INSTALLING, error = null, notice = spent(it.notice)) }
            Installer.Outcome.WAITING_USER ->
                update(id) {
                    it.copy(
                        state = DownloadState.AWAITING_CONFIRMATION,
                        error = null,
                        notice = spent(it.notice),
                    )
                }
            Installer.Outcome.NEEDS_PERMISSION ->
                update(id) { it.copy(state = DownloadState.NEEDS_PERMISSION) }
            Installer.Outcome.FAILED -> update(id) {
                val failure = Failures.installFailure("The installer could not start")
                it.copy(state = DownloadState.FAILED, error = failure.message, failure = failure)
            }
            Installer.Outcome.UNSUPPORTED -> update(id) {
                val failure = Failures.bundleFailure()
                it.copy(state = DownloadState.FAILED, error = failure.message, failure = failure)
            }
        }
        val state = _items.value.firstOrNull { it.id == id }?.state
        if (state == DownloadState.INSTALLING || state == DownloadState.AWAITING_CONFIRMATION) {
            watchInstall(context, id)
        }
    }

    /**
     * Starts watching one handed-over install.
     *
     * Two things can end it: [onPackageChanged] when the platform announces the package, and a
     * watchdog when nothing happens at all. The watchdog is what fixes the old stuck row — after the
     * timeout the row goes back to Ready with a note, so the user gets an actionable control instead
     * of a spinner. The watch is deliberately *kept* after that, because a user who takes longer than
     * the timeout and then confirms still deserves to see Installed: the late broadcast resolves it.
     */
    private fun watchInstall(context: Context, id: String) {
        val pkg = _items.value.firstOrNull { it.id == id }?.apkPackage ?: return
        watched[id] = pkg
        watchdogs.remove(id)?.cancel()
        watchdogs[id] = scope.launch {
            delay(INSTALL_CONFIRM_TIMEOUT_MS)
            if (watched[id] != pkg) return@launch
            val item = _items.value.firstOrNull { it.id == id }
            if (item != null && Installer.installConfirmed(context, item)) {
                onPackageChanged(context, pkg, present = true)
                return@launch
            }
            resolveUnconfirmed(context, id)
        }
    }

    private fun resolveUnconfirmed(context: Context, id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.state != DownloadState.INSTALLING && item.state != DownloadState.AWAITING_CONFIRMATION) return
        update(id) {
            it.copy(
                state = DownloadState.READY,
                error = null,
                notice = unconfirmedNotice,
            )
        }
        notifyAggregate(context)
    }

    /**
     * A package appeared on (or off) the device. Called by [PackageEventReceiver].
     *
     * Only rows naming that package are touched, so an unrelated install never rewrites the queue; a
     * removal does the reverse, because an uninstalled app must not keep reading "Installed · Open" —
     * and because for one kind of row a removal is the event it was waiting for: the app whose signature
     * blocked it is gone, so its own file can now go in.
     */
    fun onPackageChanged(context: Context, packageName: String, present: Boolean) {
        val matches = _items.value.filter { it.apkPackage == packageName }
        if (matches.isEmpty()) return
        var changed = false
        val released = mutableListOf<String>()
        matches.forEach { item ->
            val handedOver = watched.containsKey(item.id)
            val waiting = item.state == DownloadState.INSTALLING ||
                item.state == DownloadState.AWAITING_CONFIRMATION ||
                // A row the watchdog already gave up on still holds the truth if the sheet did finish.
                (item.state == DownloadState.READY && handedOver)
            when {
                present && waiting -> {
                    update(item.id) {
                        it.copy(state = DownloadState.INSTALLED, error = null, notice = null)
                    }
                    Installer.rememberInstalled(context, item)
                    unwatch(item.id)
                    changed = true
                }
                !present && item.state == DownloadState.INSTALLED -> {
                    update(item.id) {
                        it.copy(
                            state = if (it.localPath != null) DownloadState.READY else DownloadState.CANCELED,
                        )
                    }
                    unwatch(item.id)
                    changed = true
                }
                !present && item.uninstallFirst -> {
                    // The app whose signature blocked this build is off the device, so what could not be
                    // an update in place is now a fresh install of the same file.
                    update(item.id) { it.copy(uninstallFirst = false, notice = null) }
                    released += item.id
                    changed = true
                }
            }
        }
        if (changed) {
            AppState.refresh(context)
            notifyAggregate(context)
        }
        // A row blocked on the signature of the app that just left is blocked on nothing now, and an
        // update the user queued is asking to go in. Resumed rows do the same trick on their own file,
        // so only a row that really has its bytes is handed over.
        released.forEach { id ->
            _items.value.firstOrNull {
                it.id == id && it.autoInstall && it.state == DownloadState.READY &&
                    it.localPath?.let { path -> File(path).isFile } == true
            }?.let { install(context, it.id) }
        }
    }

    private fun unwatch(id: String) {
        watched.remove(id)
        watchdogs.remove(id)?.cancel()
    }

    /**
     * Finishes an install that was parked for the unknown-sources permission, and moves the row to
     * the state the handover actually produced. Without this the row kept saying "Needs install
     * permission" after the install had already run.
     */
    fun resumePendingInstall(context: Context) {
        val resumed = Installer.resumePending(context) ?: return
        applyInstallOutcome(context, resumed.downloadId, resumed.outcome)
        notifyAggregate(context)
    }

    /**
     * The intent-based installer reports through the system UI rather than a callback, so when the
     * app comes back to the foreground any install that really landed is marked installed. The
     * broadcast receiver does this instantly while the process is alive; this is the fallback for an
     * install that finished while the app was stopped.
     *
     * "Landed" is [Installer.installConfirmed], not "the package exists": an update sheet that was
     * dismissed leaves the old build in place, and calling that Installed rewrote the queue, the
     * install record and, through it, the whole update list.
     */
    fun reconcileInstalls(context: Context) {
        var changed = false
        _items.value
            .filter { it.state == DownloadState.INSTALLING || it.state == DownloadState.AWAITING_CONFIRMATION }
            .forEach { item ->
                if (Installer.installConfirmed(context, item)) {
                    update(item.id) {
                        it.copy(state = DownloadState.INSTALLED, error = null, notice = null)
                    }
                    // The intent path cannot report back, so the record that makes the row say Open is
                    // written here. Without it an install finished from the system sheet left the card
                    // showing Install again next to a package that is already on the phone.
                    Installer.rememberInstalled(context, item)
                    unwatch(item.id)
                    changed = true
                }
            }
        if (changed) {
            AppState.refresh(context)
            notifyAggregate(context)
        }
    }

    /**
     * Called when the platform took the packages and asked to be confirmed rather than answering.
     *
     * A bundle has no system sheet to fall back on: the set goes in through one session, and the only way
     * its confirmation reaches the user is the intent that came back with that answer. So the row waits,
     * with its watch, instead of reporting a refusal it never received. And when the sheet could not be
     * started from the background, the row says what to tap — the session is already committed, so a
     * second tap hands the platform a fresh one to ask about.
     */
    fun onInstallAwaitingUser(context: Context, downloadId: String?, packageName: String?, sheetShown: Boolean) {
        val item = _items.value.firstOrNull { it.id == downloadId }
            ?: _items.value.firstOrNull { packageName != null && it.apkPackage == packageName }
            ?: return
        update(item.id) {
            it.copy(
                state = DownloadState.AWAITING_CONFIRMATION,
                error = null,
                failure = null,
                notice = if (sheetShown) spent(it.notice) else confirmSheetNotice,
            )
        }
        watchInstall(context, item.id)
        notifyAggregate(context)
    }

    /** Called by [dev.aurastore.store.install.InstallResultReceiver] when the system answers. */
    fun onInstallResult(
        context: Context,
        downloadId: String?,
        packageName: String?,
        success: Boolean,
        message: String?,
    ) {
        val item = _items.value.firstOrNull { it.id == downloadId }
            ?: _items.value.firstOrNull { packageName != null && it.apkPackage == packageName }
        if (item != null) {
            val failure = if (success) null else Failures.installFailure(message ?: "The system rejected this package")
            update(item.id) {
                it.copy(
                    state = if (success) DownloadState.INSTALLED else DownloadState.FAILED,
                    error = failure?.message,
                    failure = failure,
                    // An answered session has nothing left to advise about; what the row says now is
                    // either nothing (installed) or the failure this call already carries.
                    notice = if (success) null else spent(it.notice),
                )
            }
            if (success) Installer.rememberInstalled(context, item)
            unwatch(item.id)
        }
        AppState.refresh(context)
        notifyAggregate(context)
    }

    /**
     * A session this app created finished: the package went in, or the installer closed without it.
     *
     * The commit broadcast describes the same session and carries the reason, so this is the second
     * voice rather than the loud one — its prize is the case the broadcast leaves out, where a sheet is
     * dismissed and the platform says nothing at all. Without it the row waits out the watchdog; with it
     * the row is back on Ready in the second the installer closes.
     */
    fun onSessionEnd(downloadId: String?, success: Boolean) {
        val context = appContext ?: return
        val item = _items.value.firstOrNull { it.id == downloadId } ?: return
        when (sessionAction(item.state, success)) {
            // The broadcast got there first — the normal outcome on a device that answers, and worth a
            // line, because this is the only way the two voices can be told apart afterwards.
            SessionAction.Ignore ->
                Log.i(TAG, "install session for ${item.id} ended while the row was already ${item.state}")
            SessionAction.Installed ->
                onInstallResult(context, item.id, item.apkPackage, success = true, message = null)
            SessionAction.NotInstalled -> onInstallCanceled(context, item.id, item.apkPackage)
        }
    }

    /**
     * The installer closed and the package did not go in: a cancel, not a refusal.
     *
     * Two voices report this. The commit broadcast answers with `STATUS_FAILURE_ABORTED` and the platform's
     * own words — on the Redmi's sheet, `INSTALL_FAILED_ABORTED: User rejected permissions` — and a session
     * this app committed finishes unsuccessful. Both land here, because the row is in the same position
     * either way: the file is as verified as it was, nothing is wrong with it, and the tap that tries again
     * is the control Ready already shows. Printing the platform's sentence as a failure told the user
     * something had gone wrong when they had only said no.
     *
     * A row that has already moved on is left alone: the second, late report of one cancel must not
     * demote an Installed the first one (or a retry) earned.
     */
    fun onInstallCanceled(context: Context, downloadId: String?, packageName: String?) {
        val item = _items.value.firstOrNull { it.id == downloadId }
            ?: _items.value.firstOrNull { packageName != null && it.apkPackage == packageName }
            ?: return
        if (sessionAction(item.state, success = false) != SessionAction.NotInstalled) return
        update(item.id) {
            it.copy(
                state = DownloadState.READY,
                error = null,
                failure = null,
                notice = installerEndedNotice,
            )
        }
        unwatch(item.id)
        notifyAggregate(context)
        Log.i(TAG, "install of ${item.id} was not completed; row back on Ready")
    }

    fun open(context: Context, item: DownloadItem) {
        item.apkPackage?.let { Installer.launch(context, it) }
    }

    /**
     * Moves a verified expansion file into `Android/obb`.
     *
     * Root places it there; without root the files are exported to Downloads and the row keeps the
     * exact destination. A game that cannot find its data quietly re-downloads it on first launch, so
     * "it is on the phone" is not a useful answer — which of the two happened is.
     *
     * A bundle row whose data rides inside its archive goes through here too, and is the reason
     * [DownloadItem.dataInside] keeps that archive: it is the only copy of the game's data this row has.
     */
    suspend fun placeData(context: Context, id: String): ObbInstaller.Outcome {
        val item = _items.value.firstOrNull { it.id == id }
            ?: return ObbInstaller.Outcome.Refused("This download is no longer in the queue.")
        val path = item.localPath
            ?: return ObbInstaller.Outcome.Refused("The file is not on this device any more.")
        // The app's own row is what tells us the package and version code the data must be named for.
        val sibling = _items.value.firstOrNull { it.postId == item.postId && !it.dataFile && it.apkPackage != null }
        val outcome = ObbInstaller.place(
            context = context,
            id = id,
            source = File(path),
            packageHint = sibling?.apkPackage ?: item.apkPackage,
            versionCode = sibling?.apkVersionCode,
        )
        when (outcome) {
            is ObbInstaller.Outcome.Placed -> update(id) {
                it.copy(state = DownloadState.INSTALLED, error = null, notice = "Placed in ${outcome.dir}")
            }
            is ObbInstaller.Outcome.Manual -> update(id) {
                it.copy(
                    state = DownloadState.READY,
                    error = null,
                    notice = "Copied to ${outcome.exportedTo}. Move ${outcome.names.joinToString()} " +
                        "into ${outcome.dir} before opening the game.",
                )
            }
            is ObbInstaller.Outcome.Refused -> update(id) {
                it.copy(error = null, notice = outcome.message)
            }
        }
        notifyAggregate(context)
        return outcome
    }

    fun remove(context: Context, id: String) {
        newEpoch(id)
        calls.remove(id)?.cancel()
        jobs.remove(id)?.cancel()
        unwatch(id)
        rates.remove(id)
        val item = _items.value.firstOrNull { it.id == id }
        item?.let(::purgeFiles)
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
        notifyAggregate(context)
        pruneIfIdle(context)
    }

    /**
     * Drops the rows whose story is over and deletes their cached files, so clearing the list frees space.
     *
     * [DownloadItem.isDisposable] is the gate, and it is a narrower one than "not downloading": a verified
     * build the user has not installed, a transfer they paused, and a row held behind an uninstall all
     * still own the only copy of something they asked for. Wiping those on a tap is not tidying up.
     */
    fun clearFinished(context: Context) {
        _items.value.filter { it.isDisposable }.forEach { item ->
            unwatch(item.id)
            rates.remove(item.id)
            purgeFiles(item)
        }
        _items.update { list -> list.filterNot { it.isDisposable } }
        persist()
        notifyAggregate(context)
        pruneIfIdle(context)
    }

    fun toggle(context: Context, id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        when (item.state) {
            DownloadState.DOWNLOADING, DownloadState.QUEUED -> pause(context, id)
            DownloadState.PAUSED, DownloadState.FAILED, DownloadState.CANCELED -> resume(context, id)
            else -> Unit
        }
    }

    private suspend fun run(context: Context, id: String, epoch: Long) {
        // A pause, cancel or removal that landed between the launch and this line already opened a
        // newer generation. This transfer owns nothing any more, so it must not even mark the row
        // DOWNLOADING.
        if (epochs[id] != epoch) return
        var item = _items.value.firstOrNull { it.id == id } ?: return
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        var target = targetFor(dir, id, item)
        var switches = 0
        var retries = 0
        /** Every link already spent on this row, so a build or a mirror is never tried twice. */
        val tried = mutableSetOf<String>()
        /** The whole-file digest of the bytes that produced this row's file. */
        var digest: String? = null

        update(id) { it.copy(state = DownloadState.DOWNLOADING, error = null, localPath = target.absolutePath) }

        while (true) {
            // A retry waits out its backoff outside the catch block, so the row can be paused during
            // that delay; the new generation is the signal to stop rather than start another request.
            if (epochs[id] != epoch) return
            // A host that refused a *signed* request is skipped before another request is spent on
            // it. Only refusals that carried the site's token and referer are marked (see [Hosts]),
            // so this means the host is not serving this file, not that we forgot to authenticate.
            if (Hosts.isBlocked(item.url) && switches < MAX_HOST_SWITCHES) {
                tried += item.url
                val alternative = alternativeFor(item, tried)
                if (alternative != null) {
                    switches++
                    item = adopt(id, item, alternative, "${describeHost(item.url)} refused it — switched build")
                    target = targetFor(dir, id, item)
                    continue
                }
                // Nothing else in the post: the same path may still be served by a sibling authority.
                val mirror = if (Hosts.isDead(item.url)) mirrorFor(item, tried) else null
                if (mirror != null) {
                    switches++
                    item = retarget(id, item, mirror, "${describeHost(item.url)} is offline — used a mirror")
                    target = targetFor(dir, id, item)
                    continue
                }
            }

            try {
                var lastTick = 0L
                var lastBytes = -1L
                val result = Transfer.stream(
                    client = client,
                    // Two things the official client omits: the site's signed token, and the
                    // referer its storage domains check. Both are required (see [Hosts]).
                    url = Hosts.signed(item.url),
                    target = target,
                    // The file is the truth, not the last progress tick: a tick can be a buffer behind,
                    // and a resume whose count disagrees with the bytes on disk makes the transfer
                    // delete them and start again from zero. The target belongs to this row alone
                    // (`postId-<id>-name`), so whatever length it has is this row's own progress.
                    resumeFrom = if (target.isFile) target.length() else 0L,
                    referer = Hosts.refererFor(item.url),
                    onProgress = { done, total ->
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 250) {
                            // Speed is measured between two real samples, not read off the byte
                            // counter: a resumed transfer starts from bytes already on disk.
                            val previous = rates[id] ?: 0L
                            val speed = if (lastBytes < 0) {
                                previous
                            } else {
                                TransferRate.speed(previous, done - lastBytes, now - lastTick)
                            }
                            rates[id] = speed
                            lastTick = now
                            lastBytes = done
                            val remaining = if (total > 0) total - done else 0L
                            update(id) {
                                it.copy(
                                    bytesDownloaded = done,
                                    totalBytes = total,
                                    bytesPerSecond = speed,
                                    etaSeconds = TransferRate.etaSeconds(remaining, speed),
                                )
                            }
                            notifyAggregate(context, throttled = true)
                        }
                    },
                    onCancelled = { epochs[id] != epoch },
                    onCall = { calls[id] = it },
                    onResponse = { responses[id] = it },
                )
                calls.remove(id)
                responses.remove(id)
                // Paused at the last byte: keep the row paused and let Resume finish (the next request
                // answers 416 with the whole file already on disk). Marking it VERIFYING here would
                // undo the tap that just happened.
                if (epochs[id] != epoch) {
                    settlePausedBytes(id)
                    return
                }
                Hosts.markServing(item.url)
                digest = result.sha256
                update(id) { it.copy(bytesDownloaded = result.bytes, sha256 = result.sha256) }
                break
            } catch (e: Exception) {
                calls.remove(id)
                responses.remove(id)
                // The transfer was paused, cancelled or removed while it was unwinding: the row
                // belongs to the newer generation now. Writing anything here — a retry, a PAUSED
                // guess, a failure — would race the state the user just asked for.
                if (epochs[id] != epoch) {
                    settlePausedBytes(id)
                    return
                }
                if (e is HttpStatusException) {
                    // A refusal is about the link or the host, not about the network: fall through.
                    retries = MAX_TRANSIENT_RETRIES
                }
                if (e is java.net.UnknownHostException || e.cause is java.net.UnknownHostException) {
                    // The name does not resolve. Retrying cannot change that, and every other row on the
                    // same authority should skip it instead of each waiting out its own timeout.
                    Hosts.markDead(item.url)
                    retries = MAX_TRANSIENT_RETRIES
                }
                val paused = e is kotlinx.coroutines.CancellationException || e.message == Transfer.PAUSED
                if (paused) {
                    // A pause is decided by pause(); if the job simply went away, keep the partial file.
                    val current = _items.value.firstOrNull { it.id == id }?.state
                    if (current != DownloadState.PAUSED) {
                        update(id) { it.copy(state = DownloadState.PAUSED, error = null) }
                    }
                    notifyAggregate(context)
                    pruneIfIdle(context)
                    return
                }

                val refusal = e is HttpStatusException && e.code in REFUSAL_CODES

                // A dropped connection keeps the partial file and continues from where it stopped.
                if (!refusal && retries < MAX_TRANSIENT_RETRIES) {
                    val delay = RETRY_DELAYS_MS[retries.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
                    retries++
                    update(id) {
                        it.copy(
                            state = DownloadState.DOWNLOADING,
                            error = null,
                            bytesDownloaded = runCatching { target.length() }.getOrDefault(0L),
                        )
                    }
                    notifyAggregate(context)
                    kotlinx.coroutines.delay(delay)
                    continue
                }

                if (!refusal) runCatching { target.delete() }
                if (refusal) Hosts.markBlocked(item.url)
                tried += item.url

                val alternative = if (refusal && switches < MAX_HOST_SWITCHES) {
                    alternativeFor(item, tried)
                } else {
                    null
                }
                if (alternative != null) {
                    switches++
                    runCatching { target.delete() }
                    item = adopt(id, item, alternative, "${describeHost(item.url)} refused it — switched build")
                    target = targetFor(dir, id, item)
                    continue
                }

                // Every build in the post is a dead end, but the bytes may still sit on a sibling.
                val mirror = if (refusal && switches < MAX_HOST_SWITCHES) mirrorFor(item, tried) else null
                if (mirror != null) {
                    switches++
                    runCatching { target.delete() }
                    item = retarget(id, item, mirror, "${describeHost(item.url)} is offline — used a mirror")
                    target = targetFor(dir, id, item)
                    continue
                }

                runCatching { target.delete() }
                Log.w(TAG, "download failed: ${item.url}", e)
                val failure = Failures.of(e, ::describeHost, item.url)
                update(id) {
                    it.copy(
                        state = DownloadState.FAILED,
                        error = failure.message,
                        failure = failure,
                        bytesPerSecond = 0L,
                        etaSeconds = null,
                    )
                }
                notifyAggregate(context)
                pruneIfIdle(context)
                return
            }
        }

        // A pause that landed while the last buffer was being written must not be undone here: the
        // user asked for the row to stop. The file is whole, so Resume finishes it — a range past the
        // end answers 416 and the transfer hashes what is already on disk.
        if (epochs[id] != epoch) {
            settlePausedBytes(id)
            return
        }
        update(id) { it.copy(state = DownloadState.VERIFYING, localPath = target.absolutePath) }
        Log.i(
            TAG,
            "downloaded ${target.length()} bytes from ${Hosts.hostOf(item.url)} to ${target.absolutePath}",
        )
        // A source that publishes a digest gets it checked before anything else: complete, installable
        // bytes that are not the published artefact are the one case this app must not wave through to
        // an installer. The catalogue publishes no digest, so most rows skip this.
        val integrity = Failures.integrityFailure(item.expectedSha256, digest)
        if (integrity != null) {
            runCatching { target.delete() }
            // Any packages an earlier verify unpacked describe bytes that are not the published
            // artefact, so they go with the archive they came out of.
            purgeFiles(item)
            Log.w(TAG, "digest mismatch for ${item.url}: ${integrity.hint}")
            update(id) {
                it.copy(
                    state = DownloadState.FAILED,
                    error = integrity.message,
                    failure = integrity,
                    localPath = null,
                    sha256 = digest,
                    bundleFiles = emptyList(),
                    dataInside = false,
                    bytesDownloaded = 0L,
                    totalBytes = -1L,
                    bytesPerSecond = 0L,
                    etaSeconds = null,
                )
            }
            notifyAggregate(context)
            pruneIfIdle(context)
            return
        }
        if (item.dataFile) {
            verifyDataFile(context, id, target)
            pruneIfIdle(context)
            return
        }
        if (item.isBundle) verifyBundle(context, id, target, dir) else verifyPackage(context, id, target)
        notifyAggregate(context)
        val ready = _items.value.firstOrNull { it.id == id }
        if (ready != null && ready.state == DownloadState.READY && ready.autoInstall) {
            install(context, id)
        }
        pruneIfIdle(context)
    }

    /** A single package is verified by parsing it, and the parse is what names the row's app. */
    private fun verifyPackage(context: Context, id: String, target: File) {
        val inspection = ApkInspector.inspect(context, target)
        val packageFailure = if (inspection.valid) null else Failures.packageFailure(bundleOr(inspection.error, target.name))
        update(id) {
            it.copy(
                apkPackage = inspection.packageName,
                apkVersionCode = inspection.versionCode,
                apkVersionName = inspection.versionName,
                apkSignerSha256 = inspection.signerSha256,
                state = if (inspection.valid) DownloadState.READY else DownloadState.FAILED,
                error = packageFailure?.message,
                failure = packageFailure,
            )
        }
    }

    /**
     * A bundle is verified by what is inside it.
     *
     * Its packages come out beside the archive, under this row's own name prefix, and the **base** gives
     * the row the identity a single APK would have: the package, the version, and the signing certificate
     * [installHold] compares against the app already on the device. Without that comparison a bundle would
     * sail past the one check that can predict a refusal, and the user would hear about it from the
     * installer after the bytes were paid for.
     *
     * The archive stays the row's [DownloadItem.localPath] — it is what the transfer hashed, and what the
     * pieces are unpacked again from if Android clears them. The expansion files a bundle may carry are
     * deliberately left inside it: [ObbInstaller] reads them from the archive already, and a gigabyte
     * written out of a zip in order to be written somewhere else again is not what a cache is for.
     */
    private fun verifyBundle(context: Context, id: String, archive: File, dir: File) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        val contents = Xapk.read(archive)
        val pieces = contents?.let { Xapk.extract(archive, dir, prefixOf(item.id, item), it) }
        val inspection = pieces?.firstOrNull()?.let { ApkInspector.inspect(context, it) }
        val failure = when {
            contents == null -> Failures.packageFailure("This bundle is damaged — its packages cannot be read.")
            pieces == null -> Failures.packageFailure("The bundle's packages could not be unpacked — the file is damaged.")
            inspection == null -> Failures.packageFailure("This bundle holds no package Android can install.")
            !inspection.valid -> Failures.packageFailure(bundleOr(inspection.error, archive.name))
            else -> null
        }
        // Half a set is worse than none: a base whose splits did not unpack installs as an app that
        // crashes on launch, and the row would have nothing to show for it.
        if (failure != null) pieces?.forEach { runCatching { it.delete() } }
        val notice = if (failure == null) bundleNotice(pieces.orEmpty(), contents?.expansions?.size ?: 0) else null
        update(id) {
            it.copy(
                apkPackage = inspection?.packageName,
                apkVersionCode = inspection?.versionCode,
                apkVersionName = inspection?.versionName,
                apkSignerSha256 = inspection?.signerSha256,
                bundleFiles = pieces?.map(File::getAbsolutePath).orEmpty(),
                dataInside = (contents?.expansions?.size ?: 0) > 0,
                state = if (failure == null) DownloadState.READY else DownloadState.FAILED,
                error = failure?.message,
                failure = failure,
                // A substitution the host switch already explained outranks this: the row would lose the
                // fact that it is no longer the build the post first named.
                notice = notice ?: it.notice,
            )
        }
    }

    /**
     * What a verified bundle says about itself: the set an installer gets, and whether the game's data
     * rides inside the same archive. The second half matters because a game whose data is missing
     * re-downloads it quietly on first launch, and this row is the only place that can say the archive
     * still holds it.
     */
    private fun bundleNotice(pieces: List<File>, expansions: Int): String {
        val set = "${pieces.size} ${if (pieces.size == 1) "package" else "packages"} in this bundle"
        return if (expansions > 0) "$set, and its game data rides inside — use Add data after installing."
        else "$set, installed together as one app."
    }

    /**
     * A game's data file is verified by what is inside it, not by parsing it as a package: an `.obb`
     * is opaque data and a `.zip` is a container. The name of the expansion file says which app it
     * belongs to, which is what the placement step and the row both need.
     */
    private fun verifyDataFile(context: Context, id: String, target: File) {
        val names = ObbInstaller.expansionsIn(target)
        val packageName = names.firstNotNullOfOrNull { Obb.packageOf(it) }
        val failure = if (names.isEmpty()) Failures.packageFailure("This archive does not hold an expansion file.") else null
        update(id) {
            it.copy(
                apkPackage = packageName,
                state = if (names.isEmpty()) DownloadState.FAILED else DownloadState.READY,
                error = failure?.message,
                failure = failure,
                notice = if (names.isEmpty()) null else "Additional data for this game — add it after the app is installed.",
            )
        }
        notifyAggregate(context)
    }

    private fun targetFor(dir: File, id: String, item: DownloadItem): File =
        File(dir, "${prefixOf(id, item)}${item.fileName}")

    /**
     * The name prefix every file this row writes carries, and the handle [orphanApkFiles] uses to tell a
     * row's bytes from somebody else's.
     *
     * A bundle's unpacked packages are named with it too — which is what makes them this row's files to
     * the sweep, the cache counter and the delete-on-clear path without any of them having to be told.
     */
    private fun prefixOf(id: String, item: DownloadItem) = "${item.postId}-${id.take(8)}-"

    /**
     * Moves the queued item onto another file of the same post, resetting the byte counter because
     * the partial bytes of the refused file cannot be continued by a different one.
     */
    private fun adopt(id: String, item: DownloadItem, file: DownloadFile, notice: String): DownloadItem {
        val name = fileNameFor(file.url)
        val next = item.copy(
            url = file.url,
            fileName = name,
            fileLabel = listOf(file.kind, file.abi, file.sizeText).filter { it.isNotBlank() }.joinToString(" · "),
            bytesDownloaded = 0L,
            totalBytes = -1L,
            sha256 = null,
            // The pieces unpacked from the refused artefact are not pieces of this one.
            bundleFiles = emptyList(),
            dataInside = false,
            // The published digest described the artefact that was refused; another build of the same
            // version is not the same file, so it cannot inherit that expectation.
            expectedSha256 = null,
            notice = notice,
            error = null,
            failure = null,
            bytesPerSecond = 0L,
            etaSeconds = null,
            state = DownloadState.DOWNLOADING,
        )
        _items.update { list -> list.map { if (it.id == id) next else it } }
        persist()
        return next
    }

    /**
     * Another installable file of this post from a host that is still serving. Fetched with no ABI
     * filter so the server returns every artefact the post has, not only this device's slice.
     *
     * A data file never switches: another version's archive would not match the package being
     * installed, so a refusal there is reported as a refusal.
     */
    private suspend fun alternativeFor(item: DownloadItem, exclude: Set<String>): DownloadFile? {
        if (item.dataFile) return null
        val detail = runCatching { Api.post(item.postId, abi = "") }.getOrNull() ?: return null
        return QuickInstall.alternatives(detail, Api.deviceAbis, exclude).firstOrNull()
    }

    /** A sibling authority serving the same path, when the current one has stopped resolving. */
    private fun mirrorFor(item: DownloadItem, tried: Set<String>): String? {
        // A split inside a bundle is addressed relative to the bundle, never on its own.
        if (item.dataFile) return null
        return Hosts.mirrorsFor(item.url, tried).firstOrNull()
    }

    /**
     * The same artefact at another link: only the address and the name derived from it change, so the
     * published digest of the post still describes the bytes that arrive.
     */
    private fun retarget(id: String, item: DownloadItem, url: String, notice: String): DownloadItem {
        val next = item.copy(
            url = url,
            fileName = fileNameFor(url),
            bytesDownloaded = 0L,
            totalBytes = -1L,
            sha256 = null,
            // Pieces unpacked from the previous artefact are not pieces of this one.
            bundleFiles = emptyList(),
            dataInside = false,
            notice = notice,
            error = null,
            failure = null,
            bytesPerSecond = 0L,
            etaSeconds = null,
            state = DownloadState.DOWNLOADING,
        )
        _items.update { list -> list.map { if (it.id == id) next else it } }
        persist()
        return next
    }

    private fun describeHost(url: String): String = Hosts.hostOf(url) ?: "this file's host"

    /**
     * The on-disk name for a link.
     *
     * Taking the last path segment verbatim is not enough: signed storage links carry the whole
     * signature in the query string, so the name runs to hundreds of bytes and the write dies with
     * `ENAMETOOLONG` (a 5play download failed exactly that way). Drop the query and fragment, keep
     * a readable ASCII name, and cap its length while keeping the extension.
     */
    internal fun fileNameFor(url: String): String {
        val segment = percentDecode(url.substringBefore('#').substringBefore('?').substringAfterLast('/'))
        val cleaned = segment
            // Parentheses and the other marks APK names use are legal on every Android filesystem,
            // so only the characters that would confuse a path or a shell are replaced.
            .replace(Regex("[^A-Za-z0-9._()+,-]"), "_")
            .trim('_', '.')
            .ifBlank { "package.apk" }
        if (cleaned.length <= MAX_FILE_NAME) return cleaned
        val extension = cleaned.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= 5 && it != cleaned }
            ?.let { ".$it" }
            .orEmpty()
        return cleaned.take(MAX_FILE_NAME - extension.length) + extension
    }

    /** Comfortably under the 255-byte limit of an ext4/f2fs name, so the id prefix still fits. */
    private const val MAX_FILE_NAME = 80

    /**
     * The catalogue ships games as `.xapk` bundles on some hosts. A bundle is a zip of packages plus
     * their expansion files, and Android's installer takes one package at a time, so say which of the
     * two it is instead of calling a valid bundle an invalid APK.
     */
    internal fun bundleOr(error: String?, name: String): String {
        val lower = name.lowercase()
        return if (lower.endsWith(".xapk") || lower.endsWith(".apks") || lower.endsWith(".zip")) {
            "This artefact is a bundle, not a single package. Pick the .apk build of this version, " +
                "or a host that serves the APK directly."
        } else {
            error ?: "not a valid APK"
        }
    }

    /** Status codes that mean "this link is not going to work", as opposed to a transient error. */
    private val REFUSAL_CODES = setOf(403, 404, 410)


    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        val before = _items.value.firstOrNull { it.id == id }
        _items.update { list ->
            list.map { current ->
                if (current.id != id) return@map current
                val next = transform(current)
                // History is written where the state changes, so no caller has to remember to log it.
                if (next.state == current.state) {
                    next
                } else {
                    next.copy(
                        transitions = (current.transitions + StateChange(next.state, System.currentTimeMillis()))
                            .takeLast(MAX_HISTORY),
                    )
                }
            }
        }
        val after = _items.value.firstOrNull { it.id == id }
        if (before != null && after != null && after.steadyState() != before.steadyState()) persist()
    }

    /**
     * The row minus what a progress tick changes.
     *
     * This is what decides whether the queue is written to disk. Byte counters are excluded on purpose:
     * they move four times a second, and on a restart the file on disk is the real counter, so a tick has
     * nothing worth persisting. Everything else — the state, the error, the digest, the package the row
     * was inspected as — is what a rebuilt queue needs to be honest.
     */
    private fun DownloadItem.steadyState(): DownloadItem =
        copy(bytesDownloaded = 0L, bytesPerSecond = 0L, etaSeconds = null)

    /** Cheap and synchronous: an in-memory commit that the preferences store flushes on its own thread. */
    private fun persist() {
        val context = appContext ?: return
        QueueStore.save(context, _items.value)
    }

    /** Enough history for a page of "what happened", without a long-lived queue growing forever. */
    private const val MAX_HISTORY = 24

    private fun pruneIfIdle(context: Context) {
        if (activeCount() != 0) return
        DownloadService.stop(context)
        runCatching {
            val orphans = orphanApkFiles(File(context.cacheDir, "apk"), _items.value)
            if (orphans.isEmpty()) return
            orphans.forEach { runCatching { it.delete() } }
            Log.i(TAG, "pruned ${orphans.size} cached file(s) no row owns")
        }
    }

    // ---------------------------------------------------------------- notifications

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_downloads_description)
                    setShowBadge(false)
                },
            )
        }
    }

    fun buildNotification(context: Context): Notification {
        val active = _items.value.filter { it.isActive }
        val current = active.firstOrNull { it.state == DownloadState.DOWNLOADING } ?: active.firstOrNull()
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                // The row this notification is about, so tapping it opens that page rather than a tab.
                current?.id?.let { putExtra(MainActivity.EXTRA_DOWNLOAD, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when {
            current == null -> context.getString(R.string.notify_idle)
            active.size > 1 -> context.resources.getQuantityString(R.plurals.notify_many, active.size, active.size)
            else -> current.title
        }
        val text = when {
            current == null -> ""
            active.size > 1 -> context.getString(R.string.notify_many_note)
            current.state == DownloadState.DOWNLOADING -> {
                val total = if (current.totalBytes > 0) {
                    humanSize(current.totalBytes)
                } else {
                    context.getString(R.string.notify_size_unknown)
                }
                context.getString(
                    R.string.notify_progress,
                    (current.progress * 100).toInt(),
                    humanSize(current.bytesDownloaded),
                    total,
                )
            }
            current.state == DownloadState.VERIFYING -> context.getString(R.string.notify_verifying)
            else -> current.state.name.lowercase().replaceFirstChar { it.uppercase() }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(active.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
        if (current != null && current.state == DownloadState.DOWNLOADING && current.totalBytes > 0) {
            builder.setProgress(100, (current.progress * 100).toInt(), false)
        } else if (current != null && current.state == DownloadState.DOWNLOADING) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun notifyAggregate(context: Context, throttled: Boolean = false) {
        val now = System.currentTimeMillis()
        // Throttle notifications - don't spam UI with every 250ms progress tick
        if (throttled && activeCount() > 0 && now - lastNotifyAt < MIN_NOTIFY_INTERVAL_MS) return
        lastNotifyAt = now

        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (activeCount() == 0) {
            runCatching { manager.cancel(NOTIFICATION_ID) }
            return
        }
        // Android 13+ refuses notify() outright without this, and the user can revoke it later.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(NOTIFICATION_ID, buildNotification(context))
        }
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${value.toInt()} ${units[unit]}" else "%.1f %s".format(value, units[unit])
    }
}
