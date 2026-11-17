package dev.aurastore.store.ui.manage

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aurastore.store.R
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
import dev.aurastore.store.install.Updates
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.DownloadMenuItemPill
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.SmallCircleAction
import dev.aurastore.store.ui.formatWhen
import dev.aurastore.store.ui.library.GroupCard
import dev.aurastore.store.ui.library.GroupHeader
import dev.aurastore.store.ui.library.LoadingRows
import dev.aurastore.store.ui.library.PageBar
import dev.aurastore.store.ui.library.RowRule
import dev.aurastore.store.ui.library.StatusStrip
import dev.aurastore.store.ui.library.TABULAR
import dev.aurastore.store.ui.library.coverageNote
import dev.aurastore.store.ui.library.rememberAppIcon
import dev.aurastore.store.ui.library.AppGlyph
import dev.aurastore.store.ui.rememberStoreActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the catalogue has newer for the apps on this phone.
 *
 * This was the "Updates" half of the You tab's segmented control. A segment named after a page reads
 * as a filter, so a catalogue update sat in the same list as an uninstall, and the tab's title said
 * neither. As a page it says what it is, has room for the sweep's own status, and the back arrow is
 * the way out rather than a tab that never opens.
 */
@Composable
fun ManageScreen(
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val actions = rememberStoreActions()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    // Null until the first pass lands. A pass reaches the network, so the page says "checking" with
    // skeletons instead of showing a finished-looking list that happens to be empty.
    var targets by remember { mutableStateOf<List<UpdateTarget>?>(null) }
    var coverage by remember { mutableStateOf(PackageLookup.Coverage(0, 0, 0)) }
    var canInstall by remember { mutableStateOf(Installer.canInstall(context)) }
    var forceNextPass by remember { mutableStateOf(false) }
    var checkToken by remember { mutableIntStateOf(0) }
    val autoQueued = remember { mutableStateMapOf<Long, Boolean>() }
    val autoUpdate by Settings.autoUpdate.collectAsStateWithLifecycle()
    val lastSync by Settings.lastSync.collectAsStateWithLifecycle()

    LifecycleResumeEffect(context) {
        canInstall = Installer.canInstall(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(checkToken, installs) {
        AppState.refresh(context)
        canInstall = Installer.canInstall(context)
        entries = withContext(Dispatchers.IO) { InstalledApps.load(context) }
        // One rule for the screen, the badge and the background worker, in [AutoUpdater]. Comparing
        // the catalogue's `lastest_version` with the installed version name — which the tab used to do
        // here — answered "update available" forever for a mod build, because the post is labelled
        // with the original app's version while the APK inside it reports the mod's own.
        val force = forceNextPass
        forceNextPass = false
        targets = AutoUpdater.available(context, entries, force = force)
        // Read after the pass, so the count includes whatever this sweep just learned.
        val cache = withContext(Dispatchers.IO) { PackageLookup.readCache(context) }
        coverage = PackageLookup.coverage(AutoUpdater.sweepTargets(context, entries), cache)
        if (entries.isNotEmpty()) Settings.markSynced(context)
    }

    // The dismissed builds, so an ignore is a setting the user can see and undo rather than a row that
    // silently vanished. Read from the same store the filter uses, so the two cannot disagree.
    val ignored by Settings.ignoredUpdates.collectAsStateWithLifecycle()
    // What each package's own record says went in. An install writes that record before the queue
    // reports success, so a row whose offered build is now the build on the device leaves at once
    // rather than waiting for the next sweep to answer.
    val installedLabels = remember(entries) {
        entries.mapNotNull { entry -> entry.installedLabel?.let { label -> entry.packageName to label } }
            .toMap()
    }
    val updateList = (targets ?: emptyList()).filterNot {
        Settings.isUpdateIgnored(it.packageName, it.catalogueVersion) ||
            installedLabels[it.packageName] == it.catalogueVersion
    }
    val inPlace = updateList.filter { it.autoUpdatable }
    val notFromHere = updateList.filterNot { it.autoUpdatable }

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

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item { PageBar(stringResource(R.string.you_manage), onBack) }

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
                    item { GroupHeader(stringResource(R.string.you_from_us)) }
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
                    item { GroupHeader(stringResource(R.string.you_outside)) }
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
        // from a bug once the user forgot tapping it, so the page says what was dismissed and gives
        // it back. It is a footnote to the list, not a section of it.
        if (ignored.isNotEmpty()) {
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

        // How far the budgeted sweep actually got. An update list that is quiet about what it did not
        // look at is the blind spot wearing a badge; it says nothing when there is nothing missing,
        // because "8 of 8 checked" is a fact about a sweep that worked.
        if (coverageLine != null) {
            item {
                Text(
                    coverageLine,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

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
                Updates.delta(target.installedVersion, target.catalogueVersion),
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

/** A row whose download is moving shows the ring and the percentage, like every other row. */
private fun progressAction(item: DownloadItem): RowAction = when (item.state) {
    DownloadState.QUEUED -> RowAction(ActionKind.QUEUED, item.progress)
    DownloadState.VERIFYING -> RowAction(ActionKind.VERIFYING, item.progress)
    else -> RowAction(ActionKind.DOWNLOADING, item.progress)
}
