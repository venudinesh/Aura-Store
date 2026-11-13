package dev.aurastore.store.ui.you

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
import dev.aurastore.store.data.AppIconCache
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.AutoUpdateMode
import dev.aurastore.store.data.InstalledApps
import dev.aurastore.store.data.LibraryEntry
import dev.aurastore.store.data.Settings
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.AutoUpdater
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.PackageLookup
import dev.aurastore.store.install.UpdateTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.DownloadMenuItemPill
import dev.aurastore.store.ui.DownloadRow
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.PlayBadge
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.ScrollToTopOnRetap
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.SmallCircleAction
import dev.aurastore.store.ui.StoreActions
import dev.aurastore.store.ui.formatWhen
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.ui.skeletonPulse
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.theme.PillShape
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing

/**
 * Play's "You" tab in its "Manage apps & device" shape.
 *
 * The screen used to stack four headings of the same size and print the same count three times,
 * which read as a list of sections rather than one list with two views. So the hierarchy is now:
 * a single title band, a segmented control that carries the counts, one toolbar per view that
 * states what the list is and holds its batch action, and small tracked group labels inside.
 * Nothing here changed what a row can do — update, open, page, uninstall, pause-free queueing and
 * the download ledger all behave exactly as before.
 */
@Composable
fun YouScreen(
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    onOpenDownload: (String) -> Unit = {},
    /** Bumped by the shell when the tab already showing is tapped again. */
    scrollTopKey: Int = 0,
) {
    val context = LocalContext.current
    val actions = rememberStoreActions()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    // Null until the first pass lands. A pass reaches the network, so the screen says "checking"
    // with skeletons instead of showing a finished-looking list that happens to be empty.
    var targets by remember { mutableStateOf<List<UpdateTarget>?>(null) }
    var coverage by remember { mutableStateOf(PackageLookup.Coverage(0, 0, 0)) }
    // Packages the sweep asked about and the catalogue does not carry. Cached misses are the only
    // apps with a verdict of "nothing here", so a row can say so instead of going quiet.
    var notInCatalogue by remember { mutableStateOf<Set<String>>(emptySet()) }
    var canInstall by remember { mutableStateOf(Installer.canInstall(context)) }
    var showSystem by remember { mutableStateOf(false) }

    LifecycleResumeEffect(context) {
        canInstall = Installer.canInstall(context)
        onPauseOrDispose { }
    }
    var showAllApps by remember { mutableStateOf(false) }
    var forceNextPass by remember { mutableStateOf(false) }
    var checkToken by remember { mutableIntStateOf(0) }
    val autoQueued = remember { mutableStateMapOf<Long, Boolean>() }
    val listState = rememberLazyListState()
    Settings.load(context)
    val autoUpdate by Settings.autoUpdate.collectAsStateWithLifecycle()
    val lastSync by Settings.lastSync.collectAsStateWithLifecycle()
    // The one count this screen prints, and "1 apps" reads like a bug. Resolved through the
    // composition's own resources, so rotating or switching language redraws it.
    val countApps: @Composable (Int) -> String = { n ->
        pluralStringResource(R.plurals.you_count_apps, n, n)
    }

    LaunchedEffect(refreshKey, showSystem, checkToken) {
        entries = withContext(Dispatchers.IO) { InstalledApps.load(context) }
        canInstall = Installer.canInstall(context)
        AppState.refresh(context)
        // One rule for the screen, the badge and the background worker, in [AutoUpdater]. Comparing
        // the catalogue's `lastest_version` with the installed version name — which this used to do
        // here — answered "update available" forever for a mod build, because the post is labelled
        // with the original app's version while the APK inside it reports the mod's own.
        val force = forceNextPass
        forceNextPass = false
        targets = AutoUpdater.available(context, entries, force = force)
        // Read after the pass, so the count includes whatever this sweep just learned.
        val cache = withContext(Dispatchers.IO) { PackageLookup.readCache(context) }
        coverage = PackageLookup.coverage(AutoUpdater.sweepTargets(context, entries), cache)
        notInCatalogue = cache.filterValues { it.postId == null }.keys
        if (entries.isNotEmpty()) Settings.markSynced(context)
    }

    // The dismissed builds, so an ignore is a setting the user can see and undo rather than a row that
    // silently vanished. Read from the same store the filter uses, so the two cannot disagree. The
    // filter below is what makes a row dismissed on this screen leave now rather than at the next
    // sweep; [AutoUpdater.available] is still the authority for what a fresh pass may offer.
    val ignored by Settings.ignoredUpdates.collectAsStateWithLifecycle()
    val updateList = (targets ?: emptyList()).filterNot {
        Settings.isUpdateIgnored(it.packageName, it.catalogueVersion)
    }
    val inPlace = updateList.filter { it.autoUpdatable }
    val notFromHere = updateList.filterNot { it.autoUpdatable }
    val active = downloads.filter { it.isActive }
    val finished = downloads.filterNot { it.isActive }
    val disposable = downloads.filter { it.isDisposable }
    val visible = entries.filter { showSystem || !it.isSystem }
    val ourInstalls = installs.values.map { it.packageName }.toSet()

    // Auto-update: queue every available update on the network the user chose. Only the apps this
    // store installed are eligible; the rest are a signature gamble and wait for a tap.
    LaunchedEffect(autoUpdate, inPlace.map { it.packageName }.joinToString(), canInstall) {
        if (autoUpdate == AutoUpdateMode.OFF) return@LaunchedEffect
        if (!Settings.allowedNow(context, autoUpdate)) return@LaunchedEffect
        inPlace.forEach { target ->
            if (autoQueued[target.postId] != true) {
                autoQueued[target.postId] = true
                actions.update(target)
            }
        }
    }

    // Resolved here rather than in the list: the building scope is not a composition, and the note is
    // text that comes out of the resource table.
    val coverageLine = if (targets != null) coverageNote(coverage) else null

    ScrollToTopOnRetap(scrollTopKey, listState)

    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = 28.dp + LocalNavBarInset.current)) {
        // ------------------------------------------------------------------ the one title band
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp)) {
                Text(stringResource(R.string.tab_you), style = MaterialTheme.typography.displaySmall)
            }
        }

        // The two views, and the only place their counts are printed.
        item {
            ViewSwitcher(
                updatesCount = updateList.size,
                appsCount = entries.count { !it.isSystem },
                showAllApps = showAllApps,
                onSelect = { showAllApps = it },
            )
        }

        // The strip that goes away the moment the user grants the permission should leave, not vanish:
        // everything under it slides up behind it as it does.
        item {
            AnimatedVisibility(
                visible = !canInstall,
                enter = fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) +
                    slideInVertically(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) { it / 2 },
                exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                    slideOutVertically(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) { -it / 2 },
            ) {
                PermissionStrip(onAllow = { Installer.openUnknownSourcesSettings(context) })
            }
        }

        if (!showAllApps) {
            // ---------------------------------------------------------------- updates view
            item {
                StatusStrip(
                    text = if (targets == null) {
                        stringResource(R.string.you_checking)
                    } else {
                        stringResource(
                            R.string.you_checked,
                            formatWhen(lastSync) ?: stringResource(R.string.you_just_now),
                        )
                    },
                ) {
                    if (inPlace.isNotEmpty()) {
                        InstallButton(
                            RowAction(ActionKind.UPDATE, label = stringResource(R.string.you_update_all)),
                            onClick = { inPlace.forEach { target -> actions.update(target) } },
                            compact = true,
                        )
                    }
                }
            }

            when {
                targets == null -> item { GroupCard { LoadingRows() } }

                updateList.isEmpty() -> item {
                    AllClear(
                        onCheck = {
                            forceNextPass = true
                            checkToken++
                        },
                    )
                }

                else -> {
                    if (inPlace.isNotEmpty()) {
                        item {
                            GroupHeader(stringResource(R.string.you_from_us))
                        }
                        item {
                            GroupCard {
                                inPlace.forEachIndexed { index, target ->
                                    if (index > 0) RowRule()
                                    UpdateRow(
                                        target = target,
                                        busy = downloads.firstOrNull {
                                            it.postId == target.postId && !it.dataFile && it.isActive
                                        },
                                        onUpdate = { actions.update(target) },
                                        onOpen = { Installer.launch(context, target.packageName) },
                                        onIgnore = { actions.ignoreUpdate(target) },
                                    )
                                }
                            }
                        }
                    }

                    if (notFromHere.isNotEmpty()) {
                        // Its own group, and its own word, because these are not ours to install
                        // quietly: the listing is newer, but the build may be signed by someone else.
                        item {
                            GroupHeader(stringResource(R.string.you_outside))
                        }
                        item {
                            GroupCard {
                                notFromHere.forEachIndexed { index, target ->
                                    if (index > 0) RowRule()
                                    UpdateRow(
                                        target = target,
                                        busy = downloads.firstOrNull {
                                            it.postId == target.postId && !it.dataFile && it.isActive
                                        },
                                        primaryLabel = R.string.you_get,
                                        kind = ActionKind.INSTALL,
                                        onUpdate = { actions.update(target) },
                                        onOpen = { onOpenPost(target.postId) },
                                        onIgnore = { actions.ignoreUpdate(target) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // The ignored builds, named. An ignore that only removed a row would be indistinguishable
            // from a bug once the user forgot tapping it, so the tab says what was dismissed and gives
            // it back. It sits under the update list and in that view only: it is a footnote to the
            // list, not a section of the library.
            if (!showAllApps && ignored.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.you_ignored_updates)) }
                item {
                    GroupCard {
                        ignored.entries.forEachIndexed { index, (packageName, version) ->
                            if (index > 0) RowRule()
                            IgnoredRow(
                                packageName = packageName,
                                version = version,
                                onRestore = { Settings.unignoreUpdate(context, packageName) },
                            )
                        }
                    }
                }
            }
        } else {
            // ---------------------------------------------------------------- all apps view
            item {
                StatusStrip(text = countApps(visible.size)) {
                    PlayChip(
                        label = stringResource(R.string.you_system),
                        selected = showSystem,
                        onClick = { showSystem = !showSystem },
                    )
                }
            }
            if (entries.isEmpty()) {
                item { LoadingRows() }
            } else {
                items(visible, key = { it.packageName }) { entry ->
                    AppRowLine(
                        entry = entry,
                        modifier = Modifier.animateItem(PlayMotion.spec<Float>(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)),
                        fromAuraStore = entry.packageName in ourInstalls,
                        notInCatalogue = entry.postId == null && entry.packageName in notInCatalogue,
                        onOpen = { Installer.launch(context, entry.packageName) },
                        onUninstall = {
                            Installer.uninstall(context, entry.packageName)
                            Installer.forget(context, entry.packageName)
                        },
                    )
                }
            }
        }

        // How far the budgeted sweep actually got. It stays on the screen — an update list that is
        // quiet about what it did not look at is the §5.16 blind spot wearing a badge — but it says
        // nothing when there is nothing missing: "8 of 8 checked" is a fact about a sweep that worked,
        // and the tab is not a status page for the sweep. It belongs to the update list, so it sits
        // above Downloads rather than after them; at the bottom it read as a stray sentence about the
        // queue.
        if (coverageLine != null) {
            item {
                Text(
                    coverageLine,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                )
            }
        }

        // ---------------------------------------------------------------- downloads, both views
        // A queue with nothing in it is not a section. The badge in the brand bar is where a download
        // is announced and where it is gone back to, so an empty one here was only a heading and a
        // sentence explaining a thing that was not happening.
        if (downloads.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.sheet_downloads),
                    onMore = if (disposable.isNotEmpty()) actions::clearFinished else null,
                    moreLabel = stringResource(R.string.you_clear),
                )
            }
        }
        if (active.isNotEmpty() && finished.isNotEmpty()) {
            item { GroupHeader(stringResource(R.string.you_in_progress)) }
        }
        items(active, key = { it.id }) { item ->
            DownloadLine(
                item = item,
                actions = actions,
                onOpenPost = onOpenPost,
                onOpenDownload = onOpenDownload,
                modifier = Modifier.animateItem(PlayMotion.spec<Float>(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)),
            )
        }
        if (finished.isNotEmpty()) {
            if (active.isNotEmpty()) {
                item { GroupHeader(stringResource(R.string.you_not_in_progress)) }
            }
            items(finished, key = { it.id }) { item ->
                DownloadLine(
                    item = item,
                    actions = actions,
                    onOpenPost = onOpenPost,
                    onOpenDownload = onOpenDownload,
                    modifier = Modifier.animateItem(PlayMotion.spec<Float>(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)),
                )
            }
        }

        // The way into the verification explanation used to be a card here, which put a third door on a
        // subject the account sheet and every app's detail page already open. The tab is a list of
        // things on the device; an essay about how files are checked is not one of them.
    }
}

/** Tabular figures, so a column of version strings and hashes lines up. */
private const val TABULAR = "\"tnum\""

// ---------------------------------------------------------------------------------------- chrome

/** The two views of the tab, in one tonal track: the only place the counts are printed. */
@Composable
private fun ViewSwitcher(
    updatesCount: Int,
    appsCount: Int,
    showAllApps: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(4.dp)) {
            SwitchSegment(
                label = stringResource(R.string.you_view_updates),
                count = updatesCount,
                selected = !showAllApps,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(false) },
            )
            SwitchSegment(
                label = stringResource(R.string.you_view_all_apps),
                count = appsCount,
                selected = showAllApps,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(true) },
            )
        }
    }
}

@Composable
private fun SwitchSegment(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = PillShape
    val scheme = MaterialTheme.colorScheme
    // The segment fills and its text brightens on the same spline, so the switch reads as one control
    // moving rather than two labels being repainted.
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = PlayMotion.spec(PlayMotion.SHORT, StandardEasing),
        label = "segment-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = PlayMotion.spec(PlayMotion.SHORT, StandardEasing),
        label = "segment-content",
    )
    Surface(
        shape = shape,
        color = container,
        modifier = modifier.clip(shape).playClickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR),
                    color = content,
                )
            }
        }
    }
}

/**
 * The strip under the switcher: when this view was last looked at, how far the sweep got, and its batch action.
 *
 * It carries no title on purpose — the segment above already named the view, and a heading here is
 * what put four same-size titles on one screen before.
 */
@Composable
private fun StatusStrip(text: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/**
 * How far the budgeted name sweep has got, in one line.
 *
 * A searched-and-missed app and an app still waiting behind the per-pass bound both draw no row, so
 * "no updates" alone cannot tell the two apart. The clauses that are empty drop out rather than
 * printing "0 not asked yet".
 */
@Composable
private fun coverageNote(coverage: PackageLookup.Coverage): String? {
    // Each clause drops out when it is empty, so the line never prints "0 not asked yet" — and when
    // every clause is empty the whole line goes, rather than reporting that nothing is wrong.
    // One clause, not three. The sweep's arithmetic is a footnote, and three fragments joined by dot
    // separators read like a status page. The number that changes a reader's next move is how much of
    // the phone the sweep has actually seen.
    return when {
        coverage.pending > 0 ->
            stringResource(R.string.you_coverage_checked, coverage.answered, coverage.total)
        coverage.missed > 0 -> stringResource(R.string.you_coverage_missed, coverage.missed)
        else -> null
    }
}

/** The small tracked label that names a group of rows, with its caveat under it. */
@Composable
private fun GroupHeader(label: String, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (!note.isNullOrBlank()) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(content = content)
    }
}

/** Hairline between two rows in a card, inset to where the text starts. */
@Composable
private fun RowRule() {
    Box(
        Modifier.fillMaxWidth().padding(start = 60.dp, end = 12.dp).height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    )
}

/** One row, one control: the permission a pending install is waiting on. */
@Composable
private fun PermissionStrip(onAllow: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.you_installs_blocked),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                InstallButton(RowAction(ActionKind.PERMISSION), onClick = onAllow, compact = true)
            }
        }
    }
}

// ----------------------------------------------------------------------------------------- rows

/**
 * An app the catalogue has a newer build for.
 *
 * The delta is the whole story of the row, so it gets the tabular line beside the icon and the
 * pill carries the queue state: a ring and a percentage while the bytes are moving.
 */
@Composable
private fun UpdateRow(
    target: UpdateTarget,
    busy: DownloadItem?,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onIgnore: () -> Unit,
    kind: ActionKind = ActionKind.UPDATE,
    @StringRes primaryLabel: Int = R.string.action_update,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val icon = rememberAppIcon(target.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(icon = icon, label = target.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                target.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                versionDelta(target),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        InstallButton(
            action = busy?.let { progressAction(it) }
                ?: RowAction(kind, label = stringResource(primaryLabel)),
            onClick = { if (busy == null) onUpdate() },
            compact = true,
        )
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            SmallCircleAction(
                Glyph.More,
                stringResource(R.string.a11y_more),
            ) { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(vertical = 4.dp, horizontal = 6.dp),
            ) {
                DownloadMenuItemPill(
                    icon = Glyph.Close,
                    label = stringResource(R.string.row_menu_ignore_update),
                    onClick = {
                        menuOpen = false
                        onIgnore()
                    },
                )
            }
        }
    }
}

/**
 * One dismissed update, and the way back.
 *
 * Deliberately quieter than [UpdateRow]: no install pill, no page to open, just what was ignored and a
 * text button that puts it back in the list. The package name is shown because the device's label is
 * not stored alongside the entry — the app may have been uninstalled since, and the package is the one
 * identifier that still means something then.
 */
@Composable
private fun IgnoredRow(packageName: String, version: String, onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                packageName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                version,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(
                stringResource(R.string.you_ignored_restore),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AppRowLine(
    entry: LibraryEntry,
    fromAuraStore: Boolean,
    notInCatalogue: Boolean,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = rememberAppIcon(entry.packageName)
    // The row is the open action. Every other list on this tab already works that way, "Open" is what
    // a tap on an app has meant since the first phone, and one primary label per row across thirteen
    // rows was half the noise on this screen. Uninstall stays a visible target: it is the one thing
    // here that cannot be undone by tapping the row again.
    Row(
        (if (entry.hasLaunchIntent) modifier.fillMaxWidth().playClickable(onClick = onOpen)
         else modifier.fillMaxWidth())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(icon = icon, label = entry.label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // The package name used to follow the version here, and every one of the thirteen
                    // rows cut it to "com.and…" — long enough to eat the line, short enough to identify
                    // nothing. The version is the fact a reader can act on; the package stays on the
                    // app's own page, and is all that is left to say when there is no version.
                    entry.versionName?.let { "v$it" } ?: entry.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (fromAuraStore) {
                    Spacer(Modifier.width(6.dp))
                    PlayBadge(stringResource(R.string.app_name))
                }
            }
            // A searched-and-missed app otherwise looks exactly like an app the sweep has not reached.
            // Short, because the row's one remaining action claims the rest of the line.
            if (notInCatalogue) {
                Text(
                    stringResource(R.string.you_not_in_catalogue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!entry.isSystem) {
            TextButton(onClick = onUninstall, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(
                    stringResource(R.string.action_uninstall),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AppGlyph(icon: ImageBitmap?, label: String) {
    Box(
        Modifier.size(48.dp).clip(ArtShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = label, modifier = Modifier.size(48.dp))
        }
    }
}

/** The queue row with the digest it verified against, in the gutter under it. */
@Composable
private fun DownloadLine(
    item: DownloadItem,
    actions: StoreActions,
    onOpenPost: (Long) -> Unit,
    onOpenDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        DownloadRow(
            item = item,
            onToggle = { actions.toggle(item) },
            onInstall = { actions.install(item) },
            onDetails = {
                if (item.state == DownloadState.INSTALLED) actions.open(item) else onOpenPost(item.postId)
            },
            onRemove = { actions.remove(item) },
            onSave = { actions.saveToDevice(item) },
            onDelete = { actions.deleteFromDevice(item) },
            onInfo = { onOpenDownload(item.id) },
            onCopyLink = { actions.copyLink(item) },
            onUninstallFirst = { actions.uninstallFirst(item) },
        )
        // The digest used to be printed here — thirty-odd hex characters under every row, which is the
        // loudest thing on the tab and the least readable. It is on the download's own page, where the
        // row's info action already goes, and a row saying "Ready to install" is the conclusion of that
        // check rather than a place to display its working.
    }
}

// --------------------------------------------------------------------------------------- states

@Composable
private fun LoadingRows(rows: Int = 3) {
    // One pulse for the whole screen, from the same helper the feeds use, so a waiting store
    // breathes the same way wherever the user happens to be looking at it.
    val pulse = skeletonPulse()
    Column(Modifier.fillMaxWidth().alpha(pulse)) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(ArtShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier.fillMaxWidth(0.55f).height(14.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth(0.32f).height(11.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(38.dp).width(92.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

/** The good outcome, drawn as one: no updates is a result, not an empty list. */
@Composable
private fun AllClear(onCheck: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Tick, size = 26.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(stringResource(R.string.you_up_to_date), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onCheck) { Text(stringResource(R.string.you_check_again)) }
    }
}

// ---------------------------------------------------------------------------------------- logic

/** "v1.25.25 → v1.25.27", the pair the row is about, with the catalogue's build on the right. */
private fun versionDelta(target: UpdateTarget): String {
    fun label(value: String?): String {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "—"
        // Only a number wants a "v" in front of it — a label that already reads "v1.2" or
        // "rc-5.56150" or "1.4.2 - Premium" is shown as the catalogue wrote it.
        return if (text.first().isDigit()) "v$text" else text
    }
    return "${label(target.installedVersion)} → ${label(target.catalogueVersion)}"
}

/** A row whose download is moving shows the ring and the percentage, like every other row. */
private fun progressAction(item: DownloadItem): RowAction = when (item.state) {
    DownloadState.QUEUED -> RowAction(ActionKind.QUEUED, item.progress)
    DownloadState.VERIFYING -> RowAction(ActionKind.VERIFYING, item.progress)
    else -> RowAction(ActionKind.DOWNLOADING, item.progress)
}

@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(AppIconCache.get(packageName)) }
    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                AppIconCache.load(context, packageName)
            }
        }
    }
    return bitmap
}
