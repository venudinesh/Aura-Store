package dev.aurastore.store.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dev.aurastore.store.R
import dev.aurastore.store.install.AutoUpdater
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.InstallRecord
import dev.aurastore.store.data.PostDetail
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.ApkExport
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.Obb
import dev.aurastore.store.install.ObbInstaller
import dev.aurastore.store.install.QuickInstall
import dev.aurastore.store.install.UpdateTarget
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.sources.Sources
import dev.aurastore.store.ui.source.SourcePicker
import dev.aurastore.store.ui.source.SourceRequest
import dev.aurastore.store.ui.source.enqueueSourceFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One place where a row's button decides what to do.
 *
 * The Play Store's rule is that the same control installs, updates, resumes or opens depending on
 * the state of that app on *this* device — so the label and the action are derived from the same
 * inputs here: the download queue, and the record written when a package was installed from us.
 */
@Stable
class StoreActions(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    fun actionFor(post: PostSummary, installs: Map<Long, InstallRecord>, downloads: List<DownloadItem>): RowAction {
        // The app's own row, never the game data queued beside it.
        val item = downloads.firstOrNull { it.postId == post.id && !it.dataFile }
        return when (item?.state) {
            DownloadState.DOWNLOADING -> RowAction(ActionKind.DOWNLOADING, item.progress)
            DownloadState.QUEUED -> RowAction(ActionKind.QUEUED, item.progress)
            DownloadState.VERIFYING -> RowAction(ActionKind.VERIFYING, item.progress)
            DownloadState.PAUSED -> RowAction(ActionKind.PAUSED)
            DownloadState.READY -> RowAction(
                ActionKind.INSTALL,
                label = if (item.uninstallFirst) context.getString(R.string.action_uninstall) else null,
            )
            DownloadState.NEEDS_PERMISSION -> RowAction(ActionKind.PERMISSION)
            // The sheet is open somewhere behind this app and only the user can answer it; the row
            // offers Install again so there is always a control that does something.
            DownloadState.AWAITING_CONFIRMATION -> RowAction(ActionKind.INSTALL)
            DownloadState.FAILED -> RowAction(ActionKind.FAILED)
            // The queue already knows it installed, whether or not the post -> package record made
            // it to disk yet.
            DownloadState.INSTALLED -> RowAction(ActionKind.OPEN)
            else -> if (installs.containsKey(post.id)) RowAction(ActionKind.OPEN) else RowAction(ActionKind.INSTALL)
        }
    }

    /** The click handler that matches [actionFor]. */
    fun primary(post: PostSummary): () -> Unit = { runPrimary(post) }

    /**
     * The same decision as [primary], as a plain call.
     *
     * A row used to be handed `primary(post)`, which allocates a closure per row per recomposition —
     * a hundred rows meant a hundred lambdas every time the feed re-read its state. The row now
     * passes one stable reference and this resolves the action when the finger lands, which is also
     * the only moment the answer is worth computing.
     */
    fun runPrimary(post: PostSummary, askForSource: Boolean = true) {
        val installs = AppState.installs.value
        val item = DownloadEngine.items.value.firstOrNull { it.postId == post.id && !it.dataFile }
        when {
            item != null && item.state == DownloadState.NEEDS_PERMISSION ->
                Installer.openUnknownSourcesSettings(context)
            item != null && item.state == DownloadState.READY && item.uninstallFirst ->
                uninstallFirst(item)
            item != null && (item.state == DownloadState.READY || item.state == DownloadState.AWAITING_CONFIRMATION) ->
                DownloadEngine.install(context, item.id)
            item != null && item.state == DownloadState.PAUSED -> DownloadEngine.resume(context, item.id)
            item != null && item.state == DownloadState.FAILED -> DownloadEngine.retry(context, item.id)
            item != null && item.state == DownloadState.INSTALLED ->
                item.apkPackage?.let { Installer.launch(context, it) }
            item != null && item.isActive -> DownloadEngine.toggle(context, item.id)
            installs.containsKey(post.id) -> {
                val pkg = installs[post.id]?.packageName
                if (pkg != null) Installer.launch(context, pkg)
            }
            askForSource -> enqueue(post)
            else -> enqueueDirect(post)
        }
    }

    /**
     * Whether the download has to ask where it comes from.
     *
     * The question is only worth asking when there is more than one answer: another catalogue is
     * switched on, or the user has named a source they want by default. With the catalogue alone,
     * the old one-tap path runs and nothing is asked.
     */
    private fun shouldAskForSource(): Boolean {
        val enabled = Settings.sources.value
        val others = Sources.enabled(enabled).count { it.id != SourceId.LITEAPKS }
        return others > 0 || Settings.defaultSource.value != null
    }

    fun enqueue(post: PostSummary) {
        // The source is asked first whenever there is more than one place the file could come from.
        if (shouldAskForSource()) {
            SourcePicker.open(
                SourceRequest(
                    title = post.title,
                    cataloguePostId = post.id,
                    preferred = Settings.defaultSource.value,
                ),
            )
            return
        }
        enqueueDirect(post)
    }

    /**
     * Queues the catalogue's own build without asking where it comes from.
     *
     * A row that already names its source has answered the question. The global search passes this
     * for its catalogue rows, which carry the `LiteAPKs` pill; the app page still asks, because a
     * page reached from the shelf has not named a place.
     */
    fun enqueueDirect(post: PostSummary) {
        scope.launch {
            // "Nothing published" and "nothing this phone can run" are different answers. The second
            // one used to surface only at the install sheet, after the whole download.
            when (val outcome = QuickInstall.choose(post.id)) {
                is QuickInstall.Outcome.Ready -> queue(post, outcome.pick)
                QuickInstall.Outcome.None -> context.toast(R.string.toast_no_download)
                is QuickInstall.Outcome.NotForDevice -> context.toast(R.string.toast_no_build_for_device)
            }
        }
    }

    /**
     * Downloads an app a search row already attributed to a site.
     *
     * The row carries the source, so the sheet's "which source" question is already answered. One
     * file goes straight into the queue; several open the sheet narrowed to that site, because the
     * remaining question is which artefact (`apk` or `xapk`), not which catalogue.
     */
    fun download(app: SourceApp) {
        scope.launch {
            val files = runCatching {
                withContext(Dispatchers.IO) { Sources.byId(app.source).files(app) }
            }.getOrDefault(emptyList())
            when {
                files.size == 1 -> {
                    enqueueSourceFile(context, app, files.single())
                    context.toast(R.string.src_downloading, app.title, app.source.label, short = true)
                }
                files.isNotEmpty() -> SourcePicker.open(
                    SourceRequest(
                        title = app.title,
                        preferred = app.source,
                        only = app.source,
                        app = app,
                    ),
                )
                else -> context.toast(R.string.toast_no_download)
            }
        }
    }

    /** Downloads one specific artefact, when the user picked a build instead of the default. */
    fun enqueue(post: PostSummary, versionLabel: String, file: DownloadFile, detail: PostDetail? = null) {
        // The source is asked first, even when the build was picked here: the catalogue is one of the
        // answers, and the sheet shows that build among the others.
        if (shouldAskForSource()) {
            SourcePicker.open(
                SourceRequest(
                    title = post.title,
                    cataloguePostId = post.id,
                    preferred = Settings.defaultSource.value,
                ),
            )
            return
        }
        scope.launch {
            // The artefacts of the version that was chosen, so a hand-picked game build still gets
            // the data it was published with.
            val extras = detail?.let { Obb.companions(it, versionLabel, file.url) }.orEmpty()
            if (extras.isEmpty() && detail == null) {
                DownloadEngine.enqueue(context, post, file, versionLabel, post.id)
                return@launch
            }
            queue(post, QuickInstall.Pick(versionLabel, file, extras))
        }
    }

    private fun queue(post: PostSummary, pick: QuickInstall.Pick) {
        DownloadEngine.enqueue(context, post, pick, post.id)
        if (pick.extras.isEmpty()) return
        context.toastPlural(R.plurals.toast_extra_queued, pick.extras.size, post.title, pick.extras.size)
    }

    fun resume(item: DownloadItem) = DownloadEngine.resume(context, item.id)

    fun install(item: DownloadItem) {
        if (item.dataFile) {
            place(item)
            return
        }
        if (item.state == DownloadState.NEEDS_PERMISSION) {
            Installer.openUnknownSourcesSettings(context)
            return
        }
        DownloadEngine.install(context, item.id)
    }

    /**
     * Moves a game's expansion file into `Android/obb`. The row keeps the outcome as its note, because
     * "placed" and "waiting for you to move it" look identical from the file list and behave very
     * differently when the game starts.
     */
    fun place(item: DownloadItem) {
        scope.launch {
            val outcome = DownloadEngine.placeData(context, item.id)
            when (outcome) {
                is ObbInstaller.Outcome.Placed -> context.toast(R.string.toast_data_placed, outcome.dir)
                is ObbInstaller.Outcome.Manual -> context.toast(R.string.toast_data_exported, outcome.exportedTo)
                // The installer's own sentence, kept because it names the step the user has to take
                // with a path in it — not a label this screen translates.
                is ObbInstaller.Outcome.Refused -> Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggle(item: DownloadItem) = DownloadEngine.toggle(context, item.id)

    /**
     * Stops offering [target]'s build until the catalogue publishes a newer one.
     *
     * Recorded against the version the row was drawn from, so this dismisses *that* build rather than
     * the app. A tap is the only thing that writes it; the list is then rebuilt from the same rule the
     * badge and the background worker read, so one ignore cannot be honoured in one place and not in
     * another.
     */
    fun ignoreUpdate(target: UpdateTarget, onDone: () -> Unit = {}) {
        Settings.ignoreUpdate(context, target.packageName, target.catalogueVersion)
        // An ignore that only removed a row would look like a row that was never there, so the tap
        // says what it did — and a row with no build label records nothing, which the user is told
        // rather than left to discover on the next sweep.
        context.toast(
            if (Settings.isUpdateIgnored(target.packageName, target.catalogueVersion)) {
                R.string.toast_update_ignored
            } else {
                R.string.toast_update_ignore_failed
            },
            short = true,
        )
        onDone()
    }

    fun unignoreUpdate(target: UpdateTarget, onDone: () -> Unit = {}) {
        Settings.unignoreUpdate(context, target.packageName)
        onDone()
    }

    /**
     * Puts the artefact's own link on the clipboard — never the signed one. The signature belongs to
     * this device's request, expires in three hours, and the storage host checks a referer with it,
     * so copying that instead would share a credential that works nowhere.
     */
    fun copyLink(item: DownloadItem) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clipboard_link_label), item.url))
        context.toast(R.string.toast_link_copied, short = true)
    }

    fun open(item: DownloadItem) {
        item.apkPackage?.let { Installer.launch(context, it) }
    }

    /**
     * Takes the row off the list, and takes its bytes with it — the narrow door, beside
     * [deleteFromDevice]'s wide one.
     *
     * It never touches an installed package: dropping a row that had already placed an app must not
     * quietly uninstall it. The artefact goes because a row *is* the only thing that knows those
     * bytes — [orphanApkFiles] sweeps anything no row owns on the next idle moment anyway, so keeping
     * them here would only hide a deletion by one launch. A user who wants the file keeps it with
     * [saveToDevice] first, which is why that item sits above this one in the same menu.
     */
    fun remove(item: DownloadItem) = DownloadEngine.remove(context, item.id)

    fun clearFinished() = DownloadEngine.clearFinished(context)

    fun grantInstallPermission() = Installer.openUnknownSourcesSettings(context)

    /**
     * Downloads the newest build the catalogue lists for [target] and installs it in place as soon
     * as it verifies — the Play Store's update flow, not a fresh install. The rule itself lives in
     * [AutoUpdater], because the background worker has to run exactly the same one.
     */
    fun update(target: UpdateTarget, onQueued: () -> Unit = {}) {
        scope.launch {
            if (!AutoUpdater.queueUpdate(context, target)) {
                context.toast(R.string.toast_no_newer)
                return@launch
            }
            onQueued()
        }
    }

    /**
     * Removes the app that is blocking a verified build, so that build can go in as a fresh install.
     *
     * Only the system's own uninstall sheet can delete a package, so this opens it and waits: the queue
     * watches for the removal and hands the row's file over the moment the package is gone. The install
     * record is deliberately *kept* — the user may answer *Cancel*, and an app this store installed must
     * not quietly change families because someone tapped once.
     */
    fun uninstallFirst(item: DownloadItem) {
        val pkg = item.apkPackage ?: return
        Installer.uninstall(context, pkg)
    }

    /** Copies a verified file into Downloads/AuraStore so it can be kept after the queue clears. */
    fun saveToDevice(item: DownloadItem) {
        scope.launch {
            val file = item.localPath?.let { File(it) }
            // A game's archive keeps its own extension; forcing .apk onto an .obb produces a file
            // nothing will open.
            val extension = item.fileName.substringAfterLast('.', "apk").takeIf { it.length <= 5 } ?: "apk"
            val safeName = "${item.title}-${item.versionLabel}"
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(80) + ".$extension"
            val where = withContext(Dispatchers.IO) {
                if (file == null) null else ApkExport.save(context, file, safeName)
            }
            if (where == null) context.toast(R.string.toast_nothing_to_save)
            else context.toast(R.string.toast_saved_to, where)
        }
    }

    /**
     * Deletes the row's downloaded files from local storage and drops the row from the queue.
     *
     * Never touches an installed package: deleting the local download must not uninstall the app on
     * the device or forget the record that this store placed it. Every file deletion happens inside
     * `DownloadEngine.remove`, which frees both a bundle's unpacked splits and its archive.
     */
    fun deleteFromDevice(item: DownloadItem) {
        context.toast(R.string.toast_file_deleted, short = true)
        DownloadEngine.remove(context, item.id)
    }
}

@Composable
fun rememberStoreActions(): StoreActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { StoreActions(context, scope) }
}

/** `Toast.makeText(context, resId, …)` only exists from API 34 and this app ships from 27. */
private fun Context.toast(@StringRes id: Int, vararg args: Any, short: Boolean = false) =
    Toast.makeText(this, getString(id, *args), if (short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

private fun Context.toastPlural(@PluralsRes id: Int, quantity: Int, vararg args: Any) =
    Toast.makeText(
        this,
        resources.getQuantityString(id, quantity, *args),
        Toast.LENGTH_LONG,
    ).show()
