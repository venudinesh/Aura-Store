package dev.aurastore.store.ui.source

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import dev.aurastore.store.data.Wishlist
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceDetail
import dev.aurastore.store.data.sources.SourceFile
import dev.aurastore.store.data.sources.SourceHub
import dev.aurastore.store.data.sources.Sources
import dev.aurastore.store.ui.ArtImage
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.ScreenshotCard
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.Share
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One source app's own page.
 *
 * The same page the catalogue draws for its posts, in the shape the sites can fill: a header, the
 * files the source publishes, its screenshots and its prose. Description and screenshots are read
 * from the app's page; the file list beside them is the same resolution the download sheet uses, so
 * every button here queues a direct URL.
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
    val queued = stringResource(R.string.src_downloading, app.title, app.source.label)
    var aboutExpanded by remember { mutableStateOf(false) }
    val pageUrl = app.pageUrl ?: app.key.takeIf { it.startsWith("http") }
    val wishes by Wishlist.entries.collectAsStateWithLifecycle()
    val wished = wishes.any { it.key == Wishlist.sourceKey(app) }
    val wishAdded = stringResource(R.string.wishlist_added)
    val wishRemoved = stringResource(R.string.wishlist_removed)

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current)) {
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
                Spacer(Modifier.width(4.dp))
                Text(app.source.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .playClickable(
                            onClick = {
                                val added = Wishlist.toggle(context, Wishlist.source(app))
                                Toast.makeText(
                                    context,
                                    if (added) wishAdded else wishRemoved,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlyphIcon(
                            Glyph.Heart,
                            size = 22.dp,
                            filled = wished,
                            tint = if (wished) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = stringResource(
                                if (wished) R.string.a11y_wishlist_remove else R.string.a11y_wishlist_add,
                            ),
                        )
                    }
                }
                if (pageUrl != null) {
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .playClickable(onClick = { Share.source(context, app) }),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GlyphIcon(
                                Glyph.Share,
                                size = 20.dp,
                                label = stringResource(R.string.action_share),
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    app.source.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    app.title,
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
                ArtImage(
                    url = app.iconUrl,
                    title = app.title,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    listOfNotNull(app.version, app.size, app.subtitle)
                        .filter { it.isNotBlank() }
                        .forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.src_files)) }

        when {
            state.filesLoading -> item { LoadingRail(1) }

            state.filesError != null -> item {
                StateMessage(
                    title = stringResource(R.string.src_file_list_failed),
                    body = state.filesError,
                    onRetry = viewModel::load,
                )
            }

            state.files.isEmpty() -> item {
                Text(
                    stringResource(R.string.src_no_file),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            else -> items(state.files, key = { it.url }) { file ->
                SourceFileRow(
                    file = file,
                    onDownload = {
                        enqueueSourceFile(context, app, file)
                        Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }

        val screenshots = state.detail?.screenshots.orEmpty()
        if (screenshots.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.src_screenshots)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(screenshots, key = { it }) { url ->
                        ScreenshotCard(url)
                    }
                }
            }
        }

        val about = stripHtml(state.detail?.description.orEmpty())
        if (about.isNotBlank()) {
            item { SectionHeader(stringResource(R.string.detail_about_title)) }
            item {
                Text(
                    about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (aboutExpanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
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

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (pageUrl != null) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, pageUrl.toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.src_open_page))
                    }
                }
                app.cataloguePostId?.let { postId ->
                    TextButton(onClick = { onOpenCataloguePost(postId) }) {
                        Text(stringResource(R.string.src_app_page))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFileRow(file: SourceFile, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                file.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(file.size, file.note, file.sha256?.let { "sha-256 ${it.take(12)}…" })
                .joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onDownload) {
            Text(stringResource(R.string.action_download))
        }
    }
}

/** A failure a person can read: the message when there is one, else the exception's own name. */
private fun describe(e: Throwable): String? =
    e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
