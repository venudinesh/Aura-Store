package dev.aurastore.store.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import android.content.ClipData
import android.widget.Toast
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
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.Installer
import dev.aurastore.store.install.Obb
import dev.aurastore.store.install.PlayStore
import dev.aurastore.store.install.QuickInstall
import dev.aurastore.store.install.Updates
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.AppCard
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.InfoCard
import dev.aurastore.store.ui.InfoRow
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.NoticeCard
import dev.aurastore.store.ui.PlayBadge
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.PostIcon
import dev.aurastore.store.ui.RatingBlock
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.ScreenshotCard
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.Share
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.VerificationDialog
import dev.aurastore.store.ui.formatCount
import dev.aurastore.store.ui.formatDate
import dev.aurastore.store.ui.formatScore
import dev.aurastore.store.ui.formatSize
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.ui.skeletonPulse
import dev.aurastore.store.ui.sharedIcon
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.theme.StandardEasing
import dev.aurastore.store.theme.RatingStar
import dev.aurastore.store.ui.stripHtml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
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

    LaunchedEffect(postId) { viewModel.load() }

    val detail = state.detail
    // The app's own row. A game's expansion file is queued beside it as a second row, and this page is
    // about the package.
    val item = downloads.firstOrNull { it.postId == postId && !it.dataFile }
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

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current)) {
        // ---------------------------------------------------------------- Play's own top bar
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(44.dp).clip(CircleShape).playClickable(onClick = onBack),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.Back, size = 22.dp, label = stringResource(R.string.a11y_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(44.dp).clip(CircleShape).playClickable(onClick = { menuOpen = true }),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.More, size = 20.dp, label = stringResource(R.string.a11y_more))
                    }
                }
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
                                Toast.makeText(
                                    context,
                                    updateIgnored,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.row_menu_copy_link)) },
                        onClick = {
                            menuOpen = false
                            state.artefacts.getOrNull(state.selected)?.file?.url?.let { url ->
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText(
                                            linkLabel,
                                            url,
                                        )),
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
            }
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
                // The same breathing rail the feeds use, so a page that is still loading looks like it
                // is loading rather than like it has nothing to show.
                val pulse = skeletonPulse()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
                        .graphicsLayer { alpha = pulse },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The icon slot is the hero's landing mark. Without it the tapped card has nowhere
                    // to fly to while the page is still fetching, and the one movement that makes the
                    // storefront read as a single surface only plays on a warm cache.
                    Box(
                        Modifier.size(72.dp).clip(ArtShape).sharedIcon(postId)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth(0.6f).height(28.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(46.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }

            else -> {
                val post = detail.summary

                // ------------------------------------------------------- title block and stats
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            post.publisher ?: stringResource(R.string.detail_unknown_publisher),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            post.title,
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(72.dp)) { PostIcon(post, size = 72.dp) }
                        Spacer(Modifier.width(16.dp))
                        StatBlock(
                            Glyph.Star,
                            if (post.ratingAvg > 0) formatScore(post.ratingAvg) else "—",
                            stringResource(R.string.detail_reviews, formatCount(post.ratingCount.toLong())),
                        )
                        StatBlock(Glyph.Download, formatSize(post.size) ?: "—", stringResource(R.string.detail_fact_size))
                        StatBlock(Glyph.Person, formatCount(post.views), stringResource(R.string.detail_fact_views))
                    }
                }

                // --------------------------------------------------------- install and its caption
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        val action = actionFor(installRecord, item)
                        InstallButton(
                            action = action,
                            onClick = {
                                val artefact = state.artefacts.getOrNull(state.selected)
                                when {
                                    item?.state == DownloadState.READY && item.uninstallFirst ->
                                        actions.uninstallFirst(item)
                                    item?.state == DownloadState.READY ||
                                        item?.state == DownloadState.NEEDS_PERMISSION ||
                                        item?.state == DownloadState.AWAITING_CONFIRMATION ->
                                        actions.install(item)
                                    item?.state == DownloadState.PAUSED -> actions.resume(item)
                                    item?.state == DownloadState.FAILED -> actions.resume(item)
                                    action.kind == ActionKind.OPEN -> {
                                        // The queue's own package name covers an install that finished
                                        // through the system sheet before its record was written.
                                        val pkg = installRecord?.packageName ?: item?.apkPackage
                                        if (pkg != null) Installer.launch(context, pkg)
                                    }
                                    item != null && item.isActive -> DownloadEngine.toggle(context, item.id)
                                    artefact != null && !QuickInstall.runsOn(artefact.file, Api.deviceAbis) ->
                                        Toast.makeText(
                                            context,
                                            R.string.toast_build_not_for_device,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    artefact != null -> actions.enqueue(
                                        detail.summary,
                                        artefact.versionLabel,
                                        artefact.file,
                                        detail,
                                    )
                                    else -> actions.enqueue(detail.summary)
                                }
                            },
                            onChooser = { versionPickerOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            // Only when there is no queued row to describe: a row that is already
                            // downloading has a truer caption than the catalogue's shortfall.
                            if (noBuildForDevice && item == null) {
                                stringResource(R.string.caption_no_build_for_device, Api.deviceAbi)
                            } else {
                                installCaption(action, item, installRecord)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // The catalogue mixes storage domains inside one post, and one of them is
                        // refusing every request. Say so before the download starts rather than
                        // failing with a bare HTTP code.
                        val blockedHost = state.artefacts.getOrNull(state.selected)?.file?.url
                            ?.takeIf { Hosts.isBlocked(it) }
                            ?.let { Hosts.hostOf(it) }
                        if (blockedHost != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.detail_host_blocked, blockedHost),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // A game can ship its data beside its package. The queue gets one row per file
                        // and the user gets told before the download starts, not after a silent one.
                        val chosen = state.artefacts.getOrNull(state.selected)
                        val extras = if (chosen == null) {
                            emptyList()
                        } else {
                            Obb.companions(detail, chosen.versionLabel, chosen.file.url)
                        }
                        if (extras.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
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
                    }
                }

                // --------------------------------------------- the catalogue's own editorial row
                item {
                    Spacer(Modifier.height(8.dp))
                    NoticeCard(
                        emblem = "🧩",
                        title = post.modInfo?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.detail_mod_notes_none),
                        body = stringResource(
                            R.string.detail_mod_notice,
                            post.publisher ?: stringResource(R.string.detail_the_developer),
                        ),
                        actionLabel = stringResource(R.string.detail_read_mod_info),
                        onAction = { modInfoOpen = true },
                    )
                }

                // ------------------------------------------------------------------- screenshots
                val media = detail.gallery.ifEmpty { listOfNotNull(post.bannerUrl ?: post.imageUrl) }
                val galleryIsScreenshots = detail.gallery.isNotEmpty()
                if (media.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            items(media, key = { it }) { url ->
                                ScreenshotCard(url, portrait = galleryIsScreenshots)
                            }
                        }
                    }
                }

                // ------------------------------------------------------------- about this app
                // The catalogue names its own panels (`About`, `Note`, `MOD Info`), so they are
                // offered here as the switchable panels the official client renders.
                val panels = detail.tabs
                val panel = panels.getOrNull(tabIndex) ?: panels.firstOrNull()
                val about = stripHtml(panel?.content?.takeIf { it.isNotBlank() } ?: detail.description)
                if (panels.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                        ) {
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
                    }
                }
                if (about.isNotBlank()) {
                    // Switching panels replaces a whole block of prose, so the block moves rather than
                    // repainting: Play crossfades the panel body and lets the card settle to the new
                    // height instead of snapping the list under the user's thumb.
                    item {
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
                                SectionHeader(title, onMore = { aboutExpanded = true })
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
                                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (!aboutExpanded) {
                        item {
                            TextButton(
                                onClick = { aboutExpanded = true },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Text(stringResource(R.string.a11y_see_more))
                            }
                        }
                    }
                }

                // ------------------------------ verification card, in the Data-safety card shape
                item {
                    Spacer(Modifier.height(8.dp))
                    InfoCard {
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

                // ---------------------------------------- the device chooser, as ABI/artefact chips
                if (state.artefacts.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            itemsIndexed(state.artefacts) { index, artefact ->
                                PlayChip(
                                    label = artefact.label,
                                    selected = state.selected == index,
                                    onClick = { viewModel.select(index) },
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayBadge(
                            (state.artefacts.getOrNull(state.selected)?.versionLabel ?: post.latestVersion ?: "—")
                                .let { if (it.length > 26) it.take(26).trimEnd() + "…" else it },
                        )
                        state.artefacts.getOrNull(state.selected)?.let {
                            PlayBadge(it.file.abi.ifBlank { stringResource(R.string.detail_abi_universal) })
                        }
                    }
                }

                // ------------------------------------------------------- ratings and reviews
                item { SectionHeader(stringResource(R.string.detail_ratings_title)) }
                item {
                    RatingBlock(
                        average = post.ratingAvg,
                        count = post.ratingCount,
                        note = stringResource(R.string.detail_ratings_note),
                    )
                }
                // ------------------------------------------------------------------ what's new
                item { SectionHeader(stringResource(R.string.detail_whats_new)) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(
                                R.string.detail_last_updated,
                                formatDate(post.updatedAt) ?: stringResource(R.string.detail_unknown_date),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            detail.versions.firstOrNull()?.note?.takeIf { it.isNotBlank() }
                                ?: post.modInfo?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.detail_notes_none),
                            style = MaterialTheme.typography.bodyMedium,
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
                item { SectionHeader(stringResource(R.string.detail_app_info)) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailFact(stringResource(R.string.detail_fact_publisher), post.publisher ?: "—")
                        DetailFact(stringResource(R.string.detail_fact_genre), post.genre ?: "—")
                        DetailFact(stringResource(R.string.detail_fact_build), post.type ?: "—")
                        DetailFact(stringResource(R.string.detail_fact_size), formatSize(post.size) ?: "—")
                        DetailFact(stringResource(R.string.detail_fact_version), post.latestVersion ?: "—")
                        DetailFact(stringResource(R.string.detail_fact_updated), formatDate(post.updatedAt) ?: "—")
                        DetailFact(stringResource(R.string.detail_fact_views), formatCount(post.views))
                        val playId = PlayStore.packageId(detail.originalUrl)
                        if (playId != null) {
                            // Play shows the developer's own listing; here the button opens the
                            // real Play Store page for this package instead of printing a URL.
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.detail_play_store),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(110.dp),
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
                            DetailFact(
                                stringResource(R.string.detail_fact_source),
                                detail.originalUrl ?: stringResource(R.string.detail_source_fallback),
                            )
                        }
                    }
                }

                // ------------------------------------------------------------- similar apps
                if (state.similar.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.detail_similar_apps)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(state.similar, key = { it.id }) { similar ->
                                AppCard(post = similar, onClick = { onOpenPost(similar.id) })
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                    Text(
                        stringResource(R.string.detail_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
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
            confirmButton = { TextButton(onClick = { modInfoOpen = false }) { Text(stringResource(R.string.action_close)) } },
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
 * Play's statistic column: the value in bold, an optional glyph on the same line, and the label
 * underneath in grey. The glyph sits beside the value rather than above it, which is what makes
 * Play's header row read as one line of numbers.
 */
@Composable
private fun RowScope.StatBlock(glyph: Glyph, value: String, label: String) {
    Column(
        Modifier.weight(1f).padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(3.dp))
            GlyphIcon(
                glyph,
                size = 15.dp,
                tint = if (glyph == Glyph.Star) RatingStar else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(artefact.versionLabel.ifBlank { unlabelled }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                artefact.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index == selected) {
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
        label = if (item.uninstallFirst) stringResource(R.string.action_uninstall) else null,
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
            item?.state == DownloadState.READY && item.uninstallFirst ->
                item.notice ?: stringResource(R.string.dl_state_waiting_uninstall)
            item?.state == DownloadState.AWAITING_CONFIRMATION -> stringResource(R.string.caption_awaiting)
            else -> stringResource(R.string.caption_default)
        }
    }

