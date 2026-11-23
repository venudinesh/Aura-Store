package dev.aurastore.store.ui.source

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.aurastore.store.R
import dev.aurastore.store.data.Wishlist
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceDetail
import dev.aurastore.store.data.sources.SourceFile
import dev.aurastore.store.data.sources.SourceHub
import dev.aurastore.store.data.sources.Sources
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.download.DownloadItem
import dev.aurastore.store.download.DownloadState
import dev.aurastore.store.install.ApkExport
import dev.aurastore.store.ui.ActionKind
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.FactTable
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.Hairline
import dev.aurastore.store.ui.InstallButton
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.Metric
import dev.aurastore.store.ui.RowAction
import dev.aurastore.store.ui.ScreenshotCard
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.Share
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StoreCard
import dev.aurastore.store.ui.TransferProgress
import dev.aurastore.store.ui.downloadRowStatus
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.ui.statusText
import dev.aurastore.store.ui.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One source app's own page.
 *
 * The same page the catalogue draws for its posts, in the shape the sites can fill: a header, the
 * facts the site published, one obvious action, the files behind it, then its pictures and prose.
 * Description and screenshots are read from the app's page; the file list beside them is the same
 * resolution the download sheet uses, so every control here queues a direct URL.
 */
data class SourceAppState(
    val detail: SourceDetail? = null,
    val detailLoading: Boolean = true,
    val files: List<SourceFile> = emptyList(),
    val filesLoading: Boolean = true,
    val filesError: String? = null,
)

class SourceAppViewModel(private val app: SourceApp) : ViewModel() {

    private val _state = MutableStateFlow(SourceAppState())
    val state: StateFlow<SourceAppState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reads the page and its files together: neither waits for the other on screen. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(detailLoading = true, filesLoading = true, filesError = null) }
            val source = Sources.byId(app.source)
            val detail = async { runCatching { SourceHub.detail(app) } }
            val files = async {
                runCatching { withContext(Dispatchers.IO) { source.files(app) } }
            }
            val detailResult = detail.await()
            val filesResult = files.await()
            _state.update {
                it.copy(
                    detailLoading = false,
                    detail = detailResult.getOrNull(),
                    filesLoading = false,
                    files = filesResult.getOrDefault(emptyList()),
                    filesError = filesResult.exceptionOrNull()?.let(::describe),
                )
            }
        }
    }
}

@Composable
fun SourceAppScreen(
    app: SourceApp,
    onBack: () -> Unit,
    onOpenCataloguePost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceAppViewModel = viewModel(key = "source-app-${app.source.name}-${app.key}") {
        SourceAppViewModel(app)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actions = rememberStoreActions()
    // The rows this page's files queue into. A source-only app has a stable pseudo-id built from its
    // own key; a catalogue-linked one uses the post id, which is the same rule enqueueSourceFile uses.
    // A file fetched through the source picker keeps its mirror listing id and records the catalogue
    // page that asked, so both ids have to be read here.
    val queuePostId = app.cataloguePostId ?: app.key.hashCode().toLong()
    val rows by remember(app.cataloguePostId, app.key) {
        DownloadEngine.items.map { list ->
            list.filter { it.postId == queuePostId || it.originPostId == queuePostId }
        }
    }.collectAsStateWithLifecycle(emptyList())
    val queued = stringResource(R.string.src_downloading, app.title, app.source.label)
    var aboutExpanded by remember(app.key) { mutableStateOf(false) }
    val pageUrl = app.pageUrl ?: app.key.takeIf { it.startsWith("http") }
    val wishes by Wishlist.entries.collectAsStateWithLifecycle()
    val wished = wishes.any { it.key == Wishlist.sourceKey(app) }
    val wishAdded = stringResource(R.string.wishlist_added)
    val wishRemoved = stringResource(R.string.wishlist_removed)

    val files = state.files
    // The package is what the big pill fetches; a page that publishes only data still gets a control,
    // and the first file is the only honest choice then.
    val primaryFile = remember(files) {
        files.firstOrNull { ApkExport.isPackage(nameOf(it)) } ?: files.firstOrNull()
    }
    val primaryRow = rows.firstOrNull { it.url == primaryFile?.url }
    val primaryAction = actions.actionFor(
        primaryRow,
        idle = RowAction(ActionKind.INSTALL, label = stringResource(R.string.action_download)),
    )
    val primaryClick: () -> Unit = {
        if (primaryRow != null) {
            actions.primary(primaryRow)
        } else if (primaryFile != null) {
            enqueueSourceFile(context, app, primaryFile)
            Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
        }
    }
    val caption = primaryRow?.let { statusText(downloadRowStatus(it)) }
        ?: stringResource(R.string.src_caption_idle, app.source.label)

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 40.dp + LocalNavBarInset.current),
    ) {
        item(key = "hero") {
            SourceHero(
                app = app,
                wished = wished,
                onBack = onBack,
                onWishlist = {
                    val added = Wishlist.toggle(context, Wishlist.source(app))
                    Toast.makeText(
                        context,
                        if (added) wishAdded else wishRemoved,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onShare = { Share.source(context, app) },
            )
        }

        item(key = "stats") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoreCard(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Metric(
                            Glyph.Grid,
                            if (state.filesLoading) "—" else files.size.toString(),
                            stringResource(R.string.src_fact_files),
                        )
                        Hairline()
                        Metric(
                            Glyph.Download,
                            app.size ?: primaryFile?.size ?: "—",
                            stringResource(R.string.detail_fact_size),
                        )
                        Hairline()
                        Metric(
                            Glyph.Info,
                            app.version ?: "—",
                            stringResource(R.string.detail_fact_version),
                        )
                    }
                }
            }
        }

        when {
            state.filesLoading -> item(key = "loading") { LoadingRail(2) }

            state.filesError != null -> item(key = "files-error") {
                StateMessage(
                    title = stringResource(R.string.src_file_list_failed),
                    body = state.filesError,
                    onRetry = viewModel::load,
                )
            }

            files.isEmpty() -> item(key = "no-files") {
                StateMessage(
                    title = stringResource(R.string.src_no_file),
                    body = stringResource(R.string.src_caption_idle, app.source.label),
                )
            }

            else -> {
                item(key = "install") {
                    StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        if (primaryRow != null &&
                            (primaryRow.isActive || primaryRow.state == DownloadState.PAUSED)
                        ) {
                            TransferProgress(primaryRow)
                        }
                        InstallButton(
                            action = primaryAction,
                            onClick = primaryClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (primaryFile != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                stringResource(R.string.src_file_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                primaryFile.fileName?.takeIf { it.isNotBlank() } ?: primaryFile.label,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                fileFacts(primaryFile).ifBlank { app.source.label },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // The storage host is what tells two mirrors apart, but a signed R2 name
                            // is a paragraph of its own. Its own line, clipped, keeps it from squeezing
                            // the file name it belongs to into a column one letter wide.
                            Text(
                                hostOf(primaryFile.url).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                item(key = "files-title") {
                    SectionHeader(
                        stringResource(R.string.src_files),
                        subtitle = stringResource(R.string.src_files_note, app.source.label),
                    )
                }

                items(files, key = { it.url }) { file ->
                    val item = rows.firstOrNull { it.url == file.url }
                    SourceFileEntry(
                        file = file,
                        item = item,
                        action = actions.actionFor(
                            item,
                            idle = RowAction(
                                ActionKind.INSTALL,
                                label = stringResource(R.string.action_download),
                            ),
                        ),
                        onAction = {
                            if (item == null) {
                                enqueueSourceFile(context, app, file)
                                Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                            } else {
                                actions.primary(item)
                            }
                        },
                    )
                }
            }
        }

        val screenshots = state.detail?.screenshots.orEmpty()
        if (screenshots.isNotEmpty()) {
            item(key = "shots-title") { SectionHeader(stringResource(R.string.src_screenshots)) }
            item(key = "shots") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(screenshots, key = { it }) { url -> ScreenshotCard(url) }
                }
            }
        }

        val about = stripHtml(state.detail?.description.orEmpty())
        if (about.isNotBlank()) {
            item(key = "about") {
                StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.detail_about_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        about,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (aboutExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
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

        item(key = "facts") {
            StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.detail_app_info), style = MaterialTheme.typography.titleMedium)
                FactTable(
                    rows = listOf(
                        stringResource(R.string.src_fact_source) to app.source.label,
                        stringResource(R.string.detail_fact_version) to (app.version ?: "—"),
                        stringResource(R.string.detail_fact_size) to (app.size ?: "—"),
                        stringResource(R.string.src_fact_files) to
                            if (state.filesLoading) "—" else files.size.toString(),
                    ),
                )
                if (pageUrl != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.src_page_fact),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, pageUrl.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.src_open_page), maxLines = 1)
                                Spacer(Modifier.width(4.dp))
                                GlyphIcon(Glyph.ChevronRight, size = 16.dp)
                            }
                        }
                    }
                }
            }
        }

        if (app.cataloguePostId != null) {
            item(key = "catalogue") {
                TextButton(
                    onClick = { onOpenCataloguePost(app.cataloguePostId) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.src_app_page))
                }
            }
        }

        item(key = "disclaimer") {
            HorizontalDivider(Modifier.padding(vertical = 18.dp))
            Text(
                stringResource(R.string.src_disclaimer, app.source.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/**
 * The page's opening: the site's own icon as the colour of the band, the app's mark rising out of it,
 * and the numbers the card carried.
 *
 * A mirror publishes no banner, only the square art the card draws, so the band is that art held
 * back by a scrim rather than a promise of a picture the site never had. The bar controls sit on the
 * art because they have to stay legible over whatever it is.
 */
@Composable
private fun SourceHero(
    app: SourceApp,
    wished: Boolean,
    onBack: () -> Unit,
    onWishlist: () -> Unit,
    onShare: () -> Unit,
) {
    val page = MaterialTheme.colorScheme.background
    val back = stringResource(R.string.a11y_back)
    val wishLabel = stringResource(
        if (wished) R.string.a11y_wishlist_remove else R.string.a11y_wishlist_add,
    )
    Column {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            if (app.iconUrl != null) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
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
                HeroButton(Glyph.Share, stringResource(R.string.action_share), onShare)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .graphicsLayer { translationY = (-30).dp.toPx() },
            verticalAlignment = Alignment.Top,
        ) {
            ArtImage(
                url = app.iconUrl,
                title = app.title,
                modifier = Modifier.size(86.dp),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 14.dp)) {
                Text(
                    app.source.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        app.version?.takeIf { it.isNotBlank() },
                        app.size?.takeIf { it.isNotBlank() },
                        app.subtitle?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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

@Composable
private fun SourceFileEntry(
    file: SourceFile,
    item: DownloadItem?,
    action: RowAction,
    onAction: () -> Unit,
) {
    val extension = nameOf(file).substringAfterLast('.', "").uppercase()
    StoreCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    extension.ifBlank { stringResource(R.string.src_file_badge) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val facts = fileFacts(file)
                if (facts.isNotBlank()) {
                    Text(
                        facts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // A file the queue already holds reports itself here, in the same words the download list
        // uses. Without this the venue that asked for the download was the one place that never said
        // how it was going, while the dock and the queue both did.
        if (item != null) {
            if (item.isActive || item.state == DownloadState.PAUSED) {
                TransferProgress(item)
            } else {
                Text(
                    statusText(downloadRowStatus(item)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hostOf(file.url).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            InstallButton(action, onClick = onAction, compact = true)
        }
    }
}

/** Size and the page's own note, each printed once even when the label already carries it. */
private fun fileFacts(file: SourceFile): String = listOfNotNull(
    file.size?.trim()?.takeIf { it.isNotEmpty() && !file.label.contains(it, true) },
    file.note?.trim()?.takeIf { it.isNotEmpty() && !file.label.contains(it, true) },
).joinToString(" · ")

/** The storage host, which is how two mirrors of the same file are told apart at a glance. */
private fun hostOf(url: String): String? =
    runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() }

/** The page's own name for a file when it published one, else the URL's last segment. */
private fun nameOf(file: SourceFile): String =
    file.fileName?.takeIf { it.isNotBlank() } ?: fileNameOf(file.url)

/** A file's own name from its URL, decoded, so the extension badge and the label agree. */
private fun fileNameOf(url: String): String = runCatching {
    url.toUri().lastPathSegment
}.getOrNull()?.takeIf { it.isNotBlank() } ?: url.substringBefore('?').substringAfterLast('/')

/** A failure a person can read: the message when there is one, else the exception's own name. */
private fun describe(e: Throwable): String? =
    e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
