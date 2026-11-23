package dev.aurastore.store.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.Hosts
import dev.aurastore.store.data.InstallRecord
import dev.aurastore.store.data.InstalledApps
import dev.aurastore.store.data.PostDetail
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.Version
import dev.aurastore.store.data.Wishlist
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.Obb
import dev.aurastore.store.install.PlayStore
import dev.aurastore.store.install.QuickInstall
import dev.aurastore.store.install.Updates
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.PillShape
import dev.aurastore.store.theme.RatingStar
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.AppCard
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.FactTable
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.Hairline
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.Metric
import dev.aurastore.store.ui.PlayBadge
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.PostIcon
import dev.aurastore.store.ui.RatingBlock
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.ScreenshotCard
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.Share
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StoreCard
import dev.aurastore.store.ui.TransferProgress
import dev.aurastore.store.ui.VerificationDialog
import dev.aurastore.store.ui.formatCount
import dev.aurastore.store.ui.formatDate
import dev.aurastore.store.ui.formatScore
import dev.aurastore.store.ui.formatSize
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.ui.skeletonPulse
import dev.aurastore.store.ui.sharedIcon
import dev.aurastore.store.ui.stripHtml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.ClipData
import android.widget.Toast

/** One selectable artefact: a version label plus one of its files. */
data class Artefact(val versionLabel: String, val file: DownloadFile) {
    val label: String
        get() = listOf(
            file.abi.takeIf { it.isNotBlank() },
            file.kind.takeIf { it.isNotBlank() },
            file.sizeText.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString(" · ")

    val id: String get() = "$versionLabel|${file.url}"
}

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: PostDetail? = null,
    val artefacts: List<Artefact> = emptyList(),
    val selected: Int = 0,
    val similar: List<PostSummary> = emptyList(),
)

class DetailViewModel(private val postId: Long) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(abi: String = Api.deviceAbi) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val detail = Api.post(postId, abi)
                _state.update { current ->
                    val artefacts = artefactsFor(detail.versions)
                    val selected = current.selected.coerceIn(0, (artefacts.size - 1).coerceAtLeast(0))
                    current.copy(loading = false, detail = detail, artefacts = artefacts, selected = selected)
                }
            } catch (e: Exception) {
                // An empty message is still a failure: the non-null marker is what the UI branches on, so
                // the sentence it prints comes from the resource rather than being stuffed in here.
                _state.update { it.copy(loading = false, error = e.message.orEmpty()) }
            }
            val similar = runCatching { Api.recommended(postId) }.getOrDefault(emptyList())
            _state.update { it.copy(similar = similar) }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = index) }

    /**
     * Artefacts in catalogue order, base APKs first, which is the order the drop-down offers them:
     * the same list the Play Store shows when you ask which build you are installing.
     */
    private fun artefactsFor(versions: List<Version>): List<Artefact> {
        val all = versions.flatMap { version -> version.files.map { Artefact(version.label, it) } }
        return all.sortedWith(
            compareBy(
                { !it.file.role.equals("main", true) && it.file.role.isNotBlank() },
                // The build this phone can run comes first, so the Install button's default is one
                // that will not die in the system installer with NO_MATCHING_ABIS.
                { !QuickInstall.matchesAny(it.file, Api.deviceAbis) },
                { !QuickInstall.runsOn(it.file, Api.deviceAbis) },
            ),
        )
    }
}

internal fun detailRows(rows: List<DownloadItem>, postId: Long): List<DownloadItem> =
    rows.filter { it.postId == postId || it.originPostId == postId }

/**
 * The row this page reports progress for.
 *
 * A post can own two rows: the package and the game data the catalogue published with it. The page
 * must follow whichever is actually moving, because a finished package beside a data file that is
 * still downloading is exactly the moment the page used to go silent. A moved row beats a waiting
 * one, a paused row keeps the bytes it has, and with nothing moving the package is the row to show.
 */
internal fun detailTransferRow(rows: List<DownloadItem>): DownloadItem? {
    fun first(state: DownloadState) = rows.firstOrNull { it.state == state }
    return first(DownloadState.DOWNLOADING)
        ?: first(DownloadState.VERIFYING)
        ?: first(DownloadState.PAUSED)
        ?: first(DownloadState.QUEUED)
        ?: rows.firstOrNull { !it.dataFile }
        ?: rows.firstOrNull()
}

@Composable
fun DetailScreen(
    postId: Long,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel(key = "detail-$postId") { DetailViewModel(postId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    // This page's own row, not the queue. Filtered at the flow, so another app's progress tick does
    // not recompose a page about this one four times a second.
    val rows by remember(postId) {
        DownloadEngine.items.map { list -> detailRows(list, postId) }
    }.collectAsStateWithLifecycle(emptyList())
    val actions = rememberStoreActions()
    var menuOpen by remember { mutableStateOf(false) }
    var versionPickerOpen by remember { mutableStateOf(false) }
    var verificationOpen by remember { mutableStateOf(false) }
    var modInfoOpen by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }
    var tabIndex by remember(postId) { mutableIntStateOf(0) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Both are read inside a click callback, where stringResource is not available.
    val linkLabel = stringResource(R.string.clipboard_link_label)
    val shareFallback = stringResource(R.string.share_app_fallback)
    val updateIgnored = stringResource(R.string.detail_update_ignored)
    val wishes by Wishlist.entries.collectAsStateWithLifecycle()
    val wished = wishes.any { it.key == Wishlist.postKey(postId) }
    val wishAdded = stringResource(R.string.wishlist_added)
    val wishRemoved = stringResource(R.string.wishlist_removed)

    LaunchedEffect(postId) { viewModel.load() }

    val detail = state.detail
    // The app's own row. A game's expansion file is queued beside it as a second row, and this page is
    // about the package.
    val item = rows.firstOrNull { !it.dataFile }
    // The row the install card reports progress for. The page used to watch only the package, so a
    // data file moving beside a finished package showed its progress in the dock and the queue but
    // nowhere on the page that asked for it.
    val transfer = detailTransferRow(rows)
    // The row the page's own control speaks for: the one moving, and with nothing moving the package.
    // The pill and its caption used to follow the package alone, so a paused data file left the page
    // offering Install while the dock offered Resume.
    val shown = transfer ?: item
    val installRecord: InstallRecord? = installs[postId]

    // What the update entry needs: the package, the build this page offers, and what the device runs.
    // The comparison is the update sweep's own rule — label against label for a package this store
    // installed, version order for one that came from elsewhere — so the entry appears exactly when
    // the You tab would have a row for it, and stores the same label that row was drawn from.
    val packageName = detail?.summary?.packageName ?: item?.apkPackage
    val offeredBuild = remember(detail) {
        detail?.let { QuickInstall.pick(it, Api.deviceAbis)?.versionLabel }
    }
    // A post can be published without a single artefact this phone can run. Saying so beside the
    // button beats a whole download that ends in the installer's NO_MATCHING_ABIS.
    val noBuildForDevice = remember(detail) {
        val published = detail?.versions?.flatMap { it.files }.orEmpty()
        published.isNotEmpty() && published.none { QuickInstall.runsOn(it, Api.deviceAbis) }
    }
    val installedBuild = remember(packageName, installRecord) {
        when {
            packageName == null -> null
            installRecord != null ->
                InstalledApps.installedLabel(context, packageName) ?: installRecord.versionName
            else -> InstalledApps.installedVersion(context, packageName)
        }
    }
    val updateOffered = packageName != null && offeredBuild != null && installedBuild != null &&
        if (installRecord != null) {
            Updates.isAvailable(installedBuild, offeredBuild)
        } else {
            // The name-matched sweep asks two questions: the listing has moved past the device, and
            // the build this page can hand over has too. One alone offered an "update" the You tab
            // would never show.
            Updates.isNewer(installedBuild, detail?.summary?.latestVersion) &&
                Updates.isNewer(installedBuild, offeredBuild)
        }
    val action = actionFor(installRecord, shown)
    val chosen = state.artefacts.getOrNull(state.selected)

    // One gesture for the whole page: the big pill and the build row resolve the same decision, so
    // the two can never disagree about what a tap does.
    val primaryClick: () -> Unit = {
        when {
            shown?.state == DownloadState.READY && shown.uninstallFirst -> actions.uninstallFirst(shown)
            shown?.state == DownloadState.READY ||
                shown?.state == DownloadState.NEEDS_PERMISSION ||
                shown?.state == DownloadState.AWAITING_CONFIRMATION -> actions.install(shown)
            shown?.state == DownloadState.PAUSED -> actions.resume(shown)
            shown?.state == DownloadState.FAILED -> actions.resume(shown)
            action.kind == ActionKind.OPEN -> {
                // The queue's own package name covers an install that finished through the system
                // sheet before its record was written.
                val pkg = installRecord?.packageName ?: shown?.apkPackage
                if (pkg != null) Installer.launch(context, pkg)
            }
            shown != null && shown.isActive -> DownloadEngine.toggle(context, shown.id)
            chosen != null && !QuickInstall.runsOn(chosen.file, Api.deviceAbis) ->
                Toast.makeText(context, R.string.toast_build_not_for_device, Toast.LENGTH_LONG).show()
            chosen != null && detail != null ->
                actions.enqueue(detail.summary, chosen.versionLabel, chosen.file, detail)
            detail != null -> actions.enqueue(detail.summary)
        }
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 40.dp + LocalNavBarInset.current),
    ) {
        // ------------------------------------------------------------- art, identity, numbers
        item(key = "hero") {
            val post = detail?.summary
            DetailHero(
                post = post,
                postId = postId,
                wished = wished,
                onBack = onBack,
                onWishlist = {
                    val summary = detail?.summary
                    val added = Wishlist.toggle(
                        context,
                        Wishlist.post(
                            postId = postId,
                            title = summary?.title ?: shareFallback,
                            iconUrl = summary?.imageUrl,
                            subtitle = summary?.publisher,
                            size = summary?.size,
                        ),
                    )
                    Toast.makeText(context, if (added) wishAdded else wishRemoved, Toast.LENGTH_SHORT)
                        .show()
                },
                onShare = {
                    Share.app(context, detail?.summary?.title ?: shareFallback, postId)
                },
                onMore = { menuOpen = true },
                menu = {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Only when there is an update to dismiss, and only until it has been dismissed:
                        // the You tab is where an ignore can be seen and undone. The label stored is the
                        // build this page is offering, which is the same string the update row compares
                        // against, so the row disappears on the next pass rather than after a newer build.
                        if (updateOffered && !Settings.isUpdateIgnored(packageName, offeredBuild)) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.row_menu_ignore_update))
                                        Text(
                                            stringResource(R.string.row_menu_ignore_update_note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    Settings.ignoreUpdate(context, packageName, offeredBuild)
                                    Toast.makeText(context, updateIgnored, Toast.LENGTH_SHORT).show()
                                },
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.row_menu_copy_link)) },
                            onClick = {
                                menuOpen = false
                                chosen?.file?.url?.let { url ->
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText(linkLabel, url)),
                                        )
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            enabled = postId > 0,
                            onClick = {
                                menuOpen = false
                                Share.app(
                                    context,
                                    detail?.summary?.title ?: shareFallback,
                                    postId,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_open_playstore)) },
                            enabled = PlayStore.packageId(detail?.originalUrl) != null,
                            onClick = {
                                menuOpen = false
                                PlayStore.packageId(detail?.originalUrl)?.let { PlayStore.open(context, it) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_open_source_browser)) },
                            enabled = detail?.originalUrl != null,
                            onClick = {
                                menuOpen = false
                                detail?.originalUrl?.let { url ->
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                url.toUri(),
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_refresh)) },
                            onClick = {
                                menuOpen = false
                                viewModel.load()
                            },
                        )
                    }
                },
            )
        }

        when {
            state.error != null -> item {
                StateMessage(
                    title = stringResource(R.string.detail_load_failed),
                    body = state.error?.takeIf { it.isNotEmpty() },
                    onRetry = { viewModel.load() },
                )
            }

            detail == null -> item {
                // The same breathing rail the feeds use, so a page that is still loading looks like
                // it is loading rather than like it has nothing to show. The icon slot in the hero
                // above is the landing mark the tapped card flies into.
                val pulse = skeletonPulse()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                        .graphicsLayer { alpha = pulse },
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth(0.7f).height(18.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }

            else -> {
                val post = detail.summary

                // The numbers live in one card rather than floating free: rating, size and reach
                // are the same kind of fact and read as one row of figures.
                item(key = "stats") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StoreCard(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Metric(
                                    Glyph.Star,
                                    if (post.ratingAvg > 0) formatScore(post.ratingAvg) else "—",
                                    stringResource(R.string.detail_reviews, formatCount(post.ratingCount.toLong())),
                                )
                                Hairline()
                                Metric(
                                    Glyph.Download,
                                    formatSize(post.size) ?: "—",
                                    stringResource(R.string.detail_fact_size),
                                )
                                Hairline()
                                Metric(Glyph.Person, formatCount(post.views), stringResource(R.string.detail_fact_views))
                            }
                        }
                    }
                }

                // ------------------------------------------------------- install and its caption
                item(key = "install") {
                    StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        if (transfer != null &&
                            (transfer.isActive || transfer.state == DownloadState.PAUSED)
                        ) {
                            TransferProgress(
                                transfer,
                                showFileName = transfer.id != item?.id,
                            )
                        }
                        InstallButton(
                            action = action,
                            onClick = primaryClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            // Only when there is no queued row to describe: a row that is already
                            // downloading has a truer caption than the catalogue's shortfall.
                            if (noBuildForDevice && shown == null) {
                                stringResource(R.string.caption_no_build_for_device, Api.deviceAbi)
                            } else {
                                installCaption(action, shown, installRecord)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The catalogue mixes storage domains inside one post, and one of them is
                        // refusing every request. Say so before the download starts rather than
                        // failing with a bare HTTP code.
                        val blockedHost = chosen?.file?.url
                            ?.takeIf { Hosts.isBlocked(it) }
                            ?.let { Hosts.hostOf(it) }
                        if (blockedHost != null) {
                            Text(
                                stringResource(R.string.detail_host_blocked, blockedHost),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // A game can ship its data beside its package. The queue gets one row per file
                        // and the user gets told before the download starts, not after a silent one.
                        val extras = if (chosen == null) {
                            emptyList()
                        } else {
                            Obb.companions(detail, chosen.versionLabel, chosen.file.url)
                        }
                        if (extras.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.detail_extra_data,
                                    extras.size,
                                    extras.size,
                                    extras.joinToString(", ") { it.sizeText.ifBlank { it.kind } },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // The build is part of the decision, not a footnote: it sits in the card,
                        // says which artefact the pill will fetch, and opens the chooser itself.
                        if (chosen != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth()
                                    .playClickable(
                                        onClick = { versionPickerOpen = true },
                                        pressedScale = 0.99f,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.chooser_build_title),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        chosen.versionLabel.ifBlank {
                                            stringResource(R.string.chooser_unlabelled)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        listOfNotNull(
                                            chosen.file.abi.ifBlank {
                                                stringResource(R.string.detail_abi_universal)
                                            },
                                            formatSize(chosen.file.sizeText),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    GlyphIcon(
                                        Glyph.ChevronRight,
                                        size = 18.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        label = stringResource(R.string.chooser_build_title),
                                    )
                                }
                            }
                        }
                    }
                }

                // --------------------------------------------- the catalogue's own editorial row
                if (!post.modInfo.isNullOrBlank()) {
                    item(key = "editorial") {
                        EditorialCard(
                            emblem = "🧩",
                            title = post.modInfo,
                            body = stringResource(
                                R.string.detail_mod_notice,
                                post.publisher ?: stringResource(R.string.detail_the_developer),
                            ),
                            actionLabel = stringResource(R.string.detail_read_mod_info),
                            onAction = { modInfoOpen = true },
                        )
                    }
                }

                // ------------------------------------------------------------------- screenshots
                val gallery = detail.gallery
                if (gallery.isNotEmpty()) {
                    item(key = "shots-title") {
                        SectionHeader(stringResource(R.string.detail_screenshots))
                    }
                    item(key = "shots") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(gallery, key = { it }) { url ->
                                ScreenshotCard(url)
                            }
                        }
                    }
                } else {
                    // No gallery: the feature graphic stands in, and a graphic is a banner. It gets
                    // the whole width at its own shape rather than a screenshot card sized for a
                    // phone, which would show a third of it and hide the rest behind a swipe.
                    val banner = post.bannerUrl ?: post.imageUrl
                    if (banner != null) {
                        item(key = "shots-banner") {
                            ArtImage(
                                url = banner,
                                title = post.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .aspectRatio(16f / 9f),
                            )
                        }
                    }
                }

                // ------------------------------------------------------------- about this app
                // The catalogue names its own panels (`About`, `Note`, `MOD Info`), so they are
                // offered here as the switchable panels the official client renders.
                val panels = detail.tabs
                val panel = panels.getOrNull(tabIndex) ?: panels.firstOrNull()
                val about = stripHtml(panel?.content?.takeIf { it.isNotBlank() } ?: detail.description)
                if (about.isNotBlank()) {
                    item(key = "about") {
                        StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            val headerTitle = panel?.title?.takeUnless { it.equals("About", true) }
                                ?: stringResource(R.string.detail_about_title)
                            AnimatedContent(
                                targetState = headerTitle to about,
                                transitionSpec = {
                                    ContentTransform(
                                        targetContentEnter = fadeIn(
                                            PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing),
                                        ),
                                        initialContentExit = fadeOut(
                                            PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing),
                                        ),
                                        sizeTransform = SizeTransform(clip = false),
                                    )
                                },
                                label = "detail-panel",
                            ) { (title, body) ->
                                Column {
                                    Text(title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                    if (panels.size > 1) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            itemsIndexed(panels) { index, tab ->
                                                PlayChip(
                                                    label = tab.title.ifBlank {
                                                        stringResource(R.string.detail_section_fallback, index + 1)
                                                    },
                                                    selected = index == (tabIndex.coerceAtMost(panels.lastIndex)),
                                                    onClick = {
                                                        tabIndex = index
                                                        aboutExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    // Four lines becoming the whole text is a height change, so it grows.
                                    AnimatedContent(
                                        targetState = aboutExpanded,
                                        transitionSpec = {
                                            ContentTransform(
                                                targetContentEnter = fadeIn(
                                                    PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedEasing),
                                                ),
                                                initialContentExit = fadeOut(
                                                    PlayMotion.spec(PlayMotion.SHORT, StandardEasing),
                                                ),
                                                sizeTransform = SizeTransform(clip = true),
                                            )
                                        },
                                        label = "about-lines",
                                    ) { expanded ->
                                        Text(
                                            body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (!aboutExpanded) {
                                TextButton(
                                    onClick = { aboutExpanded = true },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(stringResource(R.string.a11y_see_more))
                                }
                            }
                        }
                    }
                }

                // ------------------------------------------------------------------ what's new
                item(key = "whats-new") {
                    StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.detail_whats_new),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatDate(post.updatedAt) ?: stringResource(R.string.detail_unknown_date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            detail.versions.firstOrNull()?.note?.takeIf { it.isNotBlank() }
                                ?: post.modInfo?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.detail_notes_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (detail.versions.size > 1) {
                            Text(
                                pluralStringResource(
                                    R.plurals.detail_versions_listed,
                                    detail.versions.size,
                                    detail.versions.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ------------------------------------------------------------------ app info
                item(key = "facts") {
                    StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(stringResource(R.string.detail_app_info), style = MaterialTheme.typography.titleMedium)
                        FactTable(
                            rows = listOf(
                                stringResource(R.string.detail_fact_publisher) to (post.publisher ?: "—"),
                                stringResource(R.string.detail_fact_genre) to (post.genre ?: "—"),
                                stringResource(R.string.detail_fact_build) to (post.type ?: "—"),
                                stringResource(R.string.detail_fact_size) to (formatSize(post.size) ?: "—"),
                                stringResource(R.string.detail_fact_version) to (post.latestVersion ?: "—"),
                                stringResource(R.string.detail_fact_updated) to (formatDate(post.updatedAt) ?: "—"),
                                stringResource(R.string.detail_fact_views) to formatCount(post.views),
                            ),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val playId = PlayStore.packageId(detail.originalUrl)
                        if (playId != null) {
                            // Play shows the developer's own listing; here the button opens the
                            // real Play Store page for this package instead of printing a URL.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.detail_play_store),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = { PlayStore.open(context, playId) },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.detail_open_playstore), maxLines = 1)
                                        Spacer(Modifier.width(4.dp))
                                        GlyphIcon(Glyph.ChevronRight, size = 16.dp)
                                    }
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.detail_fact_source),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                detail.originalUrl ?: stringResource(R.string.detail_source_fallback),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // ------------------------------ verification card, in the Data-safety card shape
                item(key = "verified") {
                    StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        InfoRow(
                            Glyph.Shield,
                            stringResource(
                                if (item?.apkPackage != null) R.string.detail_verified_after
                                else R.string.detail_verified_before
                            ),
                            subtitle = if (item?.apkPackage != null) {
                                stringResource(
                                    R.string.detail_package_line,
                                    item.apkPackage,
                                    item.apkVersionCode?.toString() ?: "—",
                                )
                            } else {
                                stringResource(R.string.detail_verified_note)
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow(
                            Glyph.Info,
                            stringResource(R.string.detail_signing_cert),
                            subtitle = item?.apkSignerSha256?.let {
                                stringResource(R.string.detail_signer_sha, it.take(24))
                            } ?: installRecord?.versionName?.let { stringResource(R.string.detail_signer_pinned) }
                                ?: stringResource(R.string.detail_signer_none),
                        )
                        TextButton(onClick = { verificationOpen = true }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.detail_see_details))
                        }
                    }
                }

                // ------------------------------------------------------- ratings and reviews
                item(key = "ratings") { SectionHeader(stringResource(R.string.detail_ratings_title)) }
                item(key = "ratings-block") {
                    RatingBlock(
                        average = post.ratingAvg,
                        count = post.ratingCount,
                        note = stringResource(R.string.detail_ratings_note),
                    )
                }

                // ------------------------------------------------------------- similar apps
                if (state.similar.isNotEmpty()) {
                    item(key = "similar-title") { SectionHeader(stringResource(R.string.detail_similar_apps)) }
                    item(key = "similar") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(state.similar, key = { it.id }) { similar ->
                                AppCard(post = similar, onClick = { onOpenPost(similar.id) })
                            }
                        }
                    }
                }

                item(key = "disclaimer") {
                    HorizontalDivider(Modifier.padding(vertical = 18.dp))
                    Text(
                        stringResource(R.string.detail_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }

    if (versionPickerOpen) {
        VersionPickerDialog(
            artefacts = state.artefacts,
            selected = state.selected,
            onSelect = {
                viewModel.select(it)
                versionPickerOpen = false
            },
            onDismiss = { versionPickerOpen = false },
        )
    }

    if (verificationOpen) {
        VerificationDialog(onDismiss = { verificationOpen = false }, item = item)
    }

    if (modInfoOpen) {
        val body = detail?.modTab?.content ?: detail?.tabs?.getOrNull(1)?.content
        AlertDialog(
            onDismissRequest = { modInfoOpen = false },
            confirmButton = {
                TextButton(onClick = { modInfoOpen = false }) { Text(stringResource(R.string.action_close)) }
            },
            title = { Text(stringResource(R.string.mod_info_title)) },
            text = {
                Text(
                    body?.takeIf { it.isNotBlank() }?.let { stripHtml(it) }
                        ?: detail?.summary?.modInfo
                        ?: stringResource(R.string.mod_dialog_notes_none),
                    // A mod note is free text of any length; at a 2.0× text size it overruns the
                    // dialog and the end of it used to be unreachable.
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}

/**
 * The page's opening: the catalogue's own banner, the app's mark rising out of it, and the three
 * facts that identify the build.
 *
 * The art is cropped to a fixed band and dissolved into the page rather than stopping at a hard
 * edge, so the header reads as one surface instead of a picture glued above a list. The bar controls
 * sit on a scrim because they have to be legible over whatever the catalogue published.
 */
@Composable
private fun DetailHero(
    post: PostSummary?,
    postId: Long,
    wished: Boolean,
    onBack: () -> Unit,
    onWishlist: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    menu: @Composable () -> Unit,
) {
    val page = MaterialTheme.colorScheme.background
    val back = stringResource(R.string.a11y_back)
    val wishLabel = stringResource(
        if (wished) R.string.a11y_wishlist_remove else R.string.a11y_wishlist_add,
    )
    val shareLabel = stringResource(R.string.action_share)
    val moreLabel = stringResource(R.string.a11y_more)
    Column {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            if (post != null) {
                AsyncImage(
                    model = post.bannerUrl ?: post.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.34f to Color.Black.copy(alpha = 0.10f),
                        0.62f to Color.Transparent,
                        0.88f to page.copy(alpha = 0.92f),
                        1f to page,
                    ),
                ),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroButton(Glyph.Back, back, onBack)
                Spacer(Modifier.weight(1f))
                HeroButton(Glyph.Heart, wishLabel, onWishlist, filled = wished)
                HeroButton(Glyph.Share, shareLabel, onShare)
                Box {
                    HeroButton(Glyph.More, moreLabel, onMore)
                    menu()
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (-30).dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(86.dp)
                    .shadow(10.dp, ArtShape)
                    .clip(ArtShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(3.dp),
            ) {
                if (post != null) {
                    PostIcon(post, size = 80.dp)
                } else {
                    // Pinned, so the tapped card's mark has somewhere to land while the page fetches.
                    Box(
                        Modifier.size(80.dp).clip(ArtShape).sharedIcon(postId, pin = true)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 14.dp)) {
                // A catalogue often repeats the app's own name as its publisher; printing it twice
                // spends a line of the header on nothing.
                val publisher = when {
                    post == null -> null
                    post.publisher.isNullOrBlank() -> stringResource(R.string.detail_unknown_publisher)
                    post.publisher.equals(post.title, ignoreCase = true) -> null
                    else -> post.publisher
                }
                if (publisher != null) {
                    Text(
                        publisher,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    post?.title ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        post?.genre?.takeIf { it.isNotBlank() },
                        post?.type?.takeIf { it.isNotBlank() },
                        formatSize(post?.size),
                    ).joinToString(" · ").ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The bar's circular controls, on a scrim so they read over any artwork. */
@Composable
private fun HeroButton(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .playClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, size = 21.dp, tint = Color.White, filled = filled, label = label)
    }
}

/** The catalogue's editorial note: a mark, the claim, the caveat, and the way into the full note. */
@Composable
private fun EditorialCard(
    emblem: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(emblem, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                        GlyphIcon(Glyph.ChevronRight, size = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionPickerDialog(
    artefacts: List<Artefact>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val unlabelled = stringResource(R.string.chooser_unlabelled)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        // Shared with the chevron that opens this dialog, so the label and the sheet cannot drift apart.
        title = { Text(stringResource(R.string.chooser_build_title)) },
        text = {
            // A catalogue post can list a dozen builds, which at a 2.0× text size is taller than the
            // dialog; without the scroll every build below the fold was unselectable.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.chooser_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                artefacts.forEachIndexed { index, artefact ->
                    val picked = index == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                artefact.versionLabel.ifBlank { unlabelled },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                artefact.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (picked) {
                            GlyphIcon(
                                Glyph.Check,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** The button's label and behaviour, derived from the queue and the install record. */
@Composable
private fun actionFor(record: InstallRecord?, item: DownloadItem?): RowAction = when (item?.state) {
    DownloadState.DOWNLOADING -> RowAction(ActionKind.DOWNLOADING, item.progress)
    DownloadState.QUEUED -> RowAction(ActionKind.QUEUED, item.progress)
    DownloadState.VERIFYING -> RowAction(ActionKind.VERIFYING, item.progress)
    DownloadState.PAUSED -> RowAction(ActionKind.PAUSED)
    DownloadState.READY -> RowAction(
        ActionKind.INSTALL,
        label = when {
            item.archive -> stringResource(R.string.row_menu_save)
            item.uninstallFirst -> stringResource(R.string.action_uninstall)
            else -> null
        },
    )
    DownloadState.NEEDS_PERMISSION -> RowAction(ActionKind.PERMISSION)
    DownloadState.AWAITING_CONFIRMATION -> RowAction(ActionKind.INSTALL)
    DownloadState.FAILED -> RowAction(ActionKind.FAILED)
    DownloadState.INSTALLED -> RowAction(ActionKind.OPEN)
    else -> if (record != null) RowAction(ActionKind.OPEN) else RowAction(ActionKind.INSTALL)
}

@Composable
private fun installCaption(action: RowAction, item: DownloadItem?, record: InstallRecord?): String =
    when (action.kind) {
        ActionKind.OPEN -> stringResource(
            R.string.caption_installed,
            record?.packageName ?: stringResource(R.string.caption_this_app),
        )
        ActionKind.DOWNLOADING, ActionKind.QUEUED -> stringResource(R.string.caption_downloading, item?.fileLabel.orEmpty())
        ActionKind.VERIFYING -> stringResource(R.string.caption_verifying)
        ActionKind.PAUSED -> stringResource(R.string.caption_paused)
        ActionKind.PERMISSION -> stringResource(R.string.caption_permission)
        // The engine's own sentence stays: it is persisted with the row, so a locale switch cannot
        // retranslate a failure that happened in another one.
        ActionKind.FAILED -> item?.error ?: stringResource(R.string.caption_failed)
        else -> when {
            item?.state == DownloadState.READY && item.archive ->
                item.notice ?: stringResource(R.string.dl_state_saved)
            item?.state == DownloadState.READY && item.uninstallFirst ->
                item.notice ?: stringResource(R.string.dl_state_waiting_uninstall)
            item?.state == DownloadState.AWAITING_CONFIRMATION -> stringResource(R.string.caption_awaiting)
            else -> stringResource(R.string.caption_default)
        }
    }
