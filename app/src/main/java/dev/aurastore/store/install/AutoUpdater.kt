package dev.aurastore.store.install

import android.content.Context
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.InstalledApps
import dev.aurastore.store.data.LibraryEntry
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One app on this device that the catalogue has a newer build for. */
data class UpdateTarget(
    val postId: Long,
    val packageName: String,
    /** The name on this device, so a row can be drawn without loading the package list again. */
    val title: String,
    /**
     * The version an update would bring in: the newest build label this post can hand over, for both
     * kinds of row.
     *
     * Not the catalogue's `lastest_version`, which names the *original* app the listing was written for
     * and can be a build the post ships no file of. See [buildIsOffered].
     */
    val catalogueVersion: String?,
    val installedVersion: String?,
    /**
     * Whether a newer build may be fetched without asking.
     *
     * True only for a package this store installed: its build label is the second half of an honest
     * comparison, and its signature is one we already saw go in, so the file lands as an update in
     * place. Anything else carries a gamble — the catalogue's build for someone else's app is signed
     * by whoever published it, and a mismatched signature is refused by the platform, after the
     * bytes have been paid for. Those rows wait for a tap.
     */
    val autoUpdatable: Boolean,
)

/**
 * The update flow, shared by the You screen and the background worker.
 *
 * Both need the same two things — which apps have a newer build, and queueing one so it installs in
 * place — and neither belongs in a Composable. Keeping the rule here means the policy cannot be
 * honoured on screen and ignored in the background, which is exactly how the first version of this
 * ended up re-downloading the same build twice.
 *
 * Two kinds of app are answered here. A package this store installed is compared label against label
 * through [UpdateScanner]; every other app is first matched to a listing by [PackageLookup] and then
 * compared by version order. See [Updates] for why those are two different questions.
 */
object AutoUpdater {

    /**
     * What has an update, reading through [UpdateScanner]'s and [PackageLookup]'s caches.
     *
     * [entries] may be passed in when the caller already read the package manager; loading it again
     * costs a device-wide scan for no information.
     */
    suspend fun available(
        context: Context,
        entries: List<LibraryEntry>? = null,
        force: Boolean = false,
    ): List<UpdateTarget> {
        val installed = entries ?: withContext(Dispatchers.IO) { InstalledApps.load(context) }

        // 1. apps this store installed, compared build label against build label.
        val ours = installed.filter { it.postId != null }
        val ids = ours.mapNotNull { it.postId }.distinct()
        val labels = if (ids.isEmpty()) emptyMap() else UpdateScanner.labels(context, ids, force = force)
        val fromHere = ours.mapNotNull { entry ->
            val postId = entry.postId ?: return@mapNotNull null
            val label = labels[postId] ?: return@mapNotNull null
            val installed = entry.installedLabel ?: entry.versionName
            if (!Updates.isAvailable(installed, label)) return@mapNotNull null
            // The row shows the same two strings the rule above compared, so a record that predates
            // version labels says so instead of quietly pairing a versionName with a build label.
            UpdateTarget(postId, entry.packageName, entry.label, label, installed, autoUpdatable = true)
        }

        // 2. everything else the device can open, looked up by name in the catalogue.
        //    `lastest_version` is cheap and wrong: it names the original app, so it can point at a
        //    build the post carries no file of. It stays as the screen that decides which posts are
        //    worth a request; the number the row shows — and the one the tap is verified against — is
        //    the newest build label from the post's own files, the same fact our installs use.
        val elsewhere = sweepTargets(context, installed)
        val found = PackageLookup.lookups(context, elsewhere, force = force)
        val candidates = elsewhere.mapNotNull { entry ->
            val answer = found[entry.packageName] ?: return@mapNotNull null
            val postId = answer.postId ?: return@mapNotNull null
            if (Updates.isNewer(entry.versionName, answer.originalVersion)) postId to entry else null
        }
        val builds = if (candidates.isEmpty()) emptyMap()
        else UpdateScanner.labels(context, candidates.map { it.first }.distinct(), force = force)
        val notFromHere = candidates.mapNotNull { (postId, entry) ->
            val shown = matchedBuild(found[entry.packageName]?.originalVersion, builds[postId], entry.versionName)
                ?: return@mapNotNull null
            UpdateTarget(postId, entry.packageName, entry.label, shown, entry.versionName, autoUpdatable = false)
        }

        val targets = fromHere + notFromHere
        // An ignored update is one the user dismissed at a specific version. Filtering here rather
        // than in the screen is the point: the badge, the batch action and the background worker all
        // read this list, so an ignore that only hid a row would still let the worker fetch it.
        val kept = targets.filterNot { Settings.isUpdateIgnored(it.packageName, it.catalogueVersion) }
        UpdateScanner.reportPending(kept.size)
        return kept
    }

    /**
     * The version a name-matched row is allowed to claim, or null when it should not be a row.
     *
     * Both readings have to agree. [original] — the catalogue's `lastest_version` — says the listing has
     * moved past what this device runs, and [pickLabel] is the build a tap on that row would actually
     * hand over. A post that names `5.3.1` while its files stop at the `v4.4.4` the device already runs
     * offers nothing, so no row appears instead of one promising a version no file can deliver. A null
     * [pickLabel] means the same: a post with nothing installable has nothing to give, and a check that
     * has not answered yet answers on the next pass.
     */
    internal fun matchedBuild(original: String?, pickLabel: String?, installed: String?): String? =
        pickLabel?.takeIf { Updates.isNewer(installed, original) && Updates.isNewer(installed, it) }

    /**
     * The apps the name sweep visits: launchable, not ours, not part of the device.
     *
     * A package this store installed is matched through its own record and never needs a search, and
     * the store's own row would answer with itself. The You tab counts coverage against this same list
     * so the number on screen is the number of apps the per-pass budget actually applies to.
     */
    fun sweepTargets(context: Context, installed: List<LibraryEntry>): List<LibraryEntry> = installed
        .filter {
            it.postId == null && !it.isSystem && it.hasLaunchIntent &&
                it.label.isNotBlank() && it.packageName != context.packageName
        }
        .sortedByDescending { it.lastUpdateAt }

    /**
     * Queues the newest build of [target]'s post and installs it over the running one as soon as it
     * verifies. Returns false when the catalogue has no file to offer, so the caller can say so.
     *
     * Asking twice is harmless: [DownloadEngine.enqueue] keeps one row per build and re-offers the
     * file it already verified instead of fetching it again.
     *
     * "Installs over the running one" is what a matched row cannot promise, and the queue finds out the
     * same way the platform would: once the bytes are on disk it compares the two publishers, and a build
     * that will be refused never reaches an installer — its row says so and offers the removal instead.
     * The fetch, which is the expensive part, is paid for either way.
     */
    suspend fun queueUpdate(context: Context, target: UpdateTarget): Boolean {
        val postId = target.postId
        // A row drawn before the ignore was recorded can still be tapped, and the worker can hold a
        // list from before it. The dismissal is checked at the moment of the fetch, not only when the
        // list was built, so nothing already on screen can queue a build the user said no to.
        if (Settings.isUpdateIgnored(target.packageName, target.catalogueVersion)) return false
        val detail = runCatching { Api.post(postId) }.getOrNull() ?: return false
        val pick = QuickInstall.pick(detail, Api.deviceAbis) ?: return false
        if (!buildIsOffered(target, pick.versionLabel)) return false
        DownloadEngine.enqueue(
            context = context,
            post = detail.summary,
            pick = pick,
            postId = postId,
            autoInstall = true,
        )
        // The build we just fetched makes the previous verdict stale.
        UpdateScanner.forget(context, postId)
        return true
    }

    /**
     * Whether the build this post can actually hand over is still the change the row promised.
     *
     * A listing's newest *original* version and its newest *published file* are not the same fact: on
     * the Redmi, AutoResponder for TG read `lastest_version 5.3.1` while the only files attached were
     * the mod v4.4.4 — the build already on the device — and an original 5.2.8 bundle. [available] now
     * builds the row from the file side, so this is the second reading of the same rule, at the moment
     * of the tap: the label the row was drawn from may be up to [UpdateScanner.TTL_MS] old, and a post
     * can lose a file or gain one in between. Each family of row is checked the way it was made.
     */
    internal fun buildIsOffered(target: UpdateTarget, pickLabel: String): Boolean =
        if (target.autoUpdatable) Updates.isAvailable(target.installedVersion, pickLabel)
        else Updates.isNewer(target.installedVersion, pickLabel)

    /**
     * Queue a batch, skipping anything already in flight. Returns how many were queued.
     *
     * Only [UpdateTarget.autoUpdatable] rows are queued: a batch is what the "Update all" button and
     * the daily worker do without looking at one app at a time, and an app this store did not install
     * may not accept the build at all.
     */
    suspend fun queueAll(context: Context, targets: List<UpdateTarget>): Int =
        targets.count { it.autoUpdatable && queueUpdate(context, it) }
}
