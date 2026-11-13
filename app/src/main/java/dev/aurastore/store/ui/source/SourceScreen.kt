package dev.aurastore.store.ui.source

import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import coil3.compose.AsyncImage
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceCategory
import dev.aurastore.store.data.sources.SourceFile
import dev.aurastore.store.data.sources.SourceHub
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.sources.Sources
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.theme.EmphasizedDecelerateEasing
import dev.aurastore.store.theme.EmphasizedEasing
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardAccelerateEasing
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.playEnter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which source to download from.
 *
 * The catalogue is only one place a build lives, so this screen searches the other catalogues the
 * same way and shows each one's own files. Every file row is already a **direct** URL (each adapter
 * resolves the site's redirects, signed links and AJAX mints for us), so the download goes through
 * the same engine, hash and install pipeline as a catalogue download.
 */
data class SourceState(
    val source: SourceId = SourceId.NINEMOD,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<SourceApp>? = null,
    val error: String? = null,
    val openKey: String? = null,
    val loadingFilesFor: String? = null,
    val files: Map<String, List<SourceFile>> = emptyMap(),
    val fileError: Map<String, String> = emptyMap(),
    /** The source's own newest apps, read from the source rather than from a term. */
    val latest: List<SourceApp> = emptyList(),
    /** The source's own categories, in the source's own order. */
    val categories: List<SourceCategory> = emptyList(),
    /** The category whose apps are on screen, or null when the source's newest apps are. */
    val selectedCategory: SourceCategory? = null,
    /** The apps of the selected category. */
    val categoryApps: List<SourceApp> = emptyList(),
    /** Whether the source's own shelf is still being read. */
    val loadingShelf: Boolean = false,
    /** Whether one category's apps are being read. */
    val loadingCategory: Boolean = false,
    /** Bumped by every finished search, and used only as the key of the result entrance. */
    val generation: Int = 0,
)

class SourceViewModel(initial: SourceId, initialQuery: String) : ViewModel() {

    private val _state = MutableStateFlow(SourceState(source = initial, query = initialQuery))
    val state: StateFlow<SourceState> = _state.asStateFlow()

    /** The term being answered, cancelled when the source or the term changes under it. */
    private var searchJob: Job? = null

    init {
        loadShelf()
        if (initialQuery.isNotBlank()) search(initialQuery)
    }

    fun selectSource(id: SourceId) {
        _state.update {
            it.copy(
                source = id,
                results = null,
                error = null,
                files = emptyMap(),
                openKey = null,
                latest = emptyList(),
                categories = emptyList(),
                selectedCategory = null,
                categoryApps = emptyList(),
            )
        }
        loadShelf()
        if (_state.value.query.isNotBlank()) search(_state.value.query)
    }

    /**
     * The source's own shelf: what it published last and how it groups what it published.
     *
     * One read covers both, because a source answers them from the same page in most cases and the
     * answer is worth keeping for half an hour either way.
     */
    fun loadShelf() {
        _state.update { it.copy(loadingShelf = true) }
        viewModelScope.launch {
            val source = Sources.byId(_state.value.source)
            val section = runCatching {
                withContext(Dispatchers.IO) { SourceHub.sectionFor(source) }
            }.getOrNull()
            _state.update { current ->
                current.copy(
                    loadingShelf = false,
                    latest = section?.apps.orEmpty(),
                    categories = section?.categories.orEmpty(),
                )
            }
        }
    }

    /**
     * Opens one of the source's own categories.
     *
     * The category is the source's own handle, so the apps are read from the source and not from the
     * catalogue's own term tree, which knows nothing about this site's grouping.
     */
    fun selectCategory(category: SourceCategory?) {
        _state.update {
            it.copy(
                selectedCategory = category,
                categoryApps = emptyList(),
                loadingCategory = category != null,
            )
        }
        val chosen = category ?: return
        viewModelScope.launch {
            val apps = runCatching {
                withContext(Dispatchers.IO) { SourceHub.appsIn(chosen.source, chosen) }
            }.getOrDefault(emptyList())
            _state.update { it.copy(categoryApps = apps, loadingCategory = false) }
        }
    }

    /** Opens the category a route named, once the source's own categories are known. */
    fun openCategoryById(id: String) {
        if (_state.value.selectedCategory?.id == id) return
        val category = _state.value.categories.firstOrNull { it.id == id } ?: return
        selectCategory(category)
    }

    fun onQueryChange(value: String) {
        // A term that changes is a new question. The answer to the old one goes, so the field and the
        // rows under it never disagree about what was asked.
        val hadAnswer = _state.value.results != null || _state.value.searching
        _state.update { it.copy(query = value) }
        if (value.isBlank()) {
            // Emptying the field is the way back to the source's own shelf.
            clearSearch()
        } else if (hadAnswer) {
            searchJob?.cancel()
            searchJob = null
            _state.update {
                it.copy(
                    searching = false,
                    results = null,
                    error = null,
                    openKey = null,
                    loadingFilesFor = null,
                    files = emptyMap(),
                    fileError = emptyMap(),
                )
            }
        }
    }

    fun search(term: String = _state.value.query) {
        if (term.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(query = term, searching = true, error = null, results = null, files = emptyMap()) }
        searchJob = viewModelScope.launch {
            val source = Sources.byId(_state.value.source)
            // The adapters parse HTML over a blocking socket, so they must not run on the main thread.
            // The term is asked the way an index reads it, so a run-together word still finds its app.
            val outcome = runCatching { withContext(Dispatchers.IO) { source.searchRelaxed(term) } }
            _state.update { current ->
                outcome.fold(
                    onSuccess = { apps ->
                        current.copy(searching = false, results = apps, generation = current.generation + 1)
                    },
                    onFailure = { e ->
                        android.util.Log.w("Aura Store", "search failed on ${source.id.label}", e)
                        current.copy(
                            searching = false,
                            results = emptyList(),
                            // An exception with no message still failed; the screen names the step
                            // that broke when there is nothing to quote.
                            error = describe(e),
                        )
                    },
                )
            }
        }
    }

    /** Back to the source's own shelf: the field empties and the answer to the old term goes. */
    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _state.update { it.copy(query = "", searching = false, results = null, error = null) }
    }

    /** Loads one app's files the first time it is opened, and remembers them. */
    fun toggleFiles(app: SourceApp) {
        val key = app.key
        if (_state.value.openKey == key) {
            _state.update { it.copy(openKey = null) }
            return
        }
        _state.update { it.copy(openKey = key) }
        if (_state.value.files.containsKey(key)) return
        _state.update { it.copy(loadingFilesFor = key) }
        viewModelScope.launch {
            val source = Sources.byId(app.source)
            val outcome = runCatching { withContext(Dispatchers.IO) { source.files(app) } }
            _state.update { current ->
                outcome.fold(
                    onSuccess = { list ->
                        current.copy(
                            loadingFilesFor = null,
                            files = current.files + (key to list),
                            fileError = current.fileError - key,
                        )
                    },
                    onFailure = { e ->
                        current.copy(
                            loadingFilesFor = null,
                            fileError = current.fileError + (key to describe(e).orEmpty()),
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun SourceScreen(
    initialSource: SourceId,
    initialQuery: String,
    /** The category the route named, when the page was opened on one. */
    initialCategory: String? = null,
    onBack: () -> Unit,
    onOpenCataloguePost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceViewModel = viewModel(key = "source-${initialSource.name}") {
        SourceViewModel(initialSource, initialQuery)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Settings.load(context)
    val enabled by Settings.sources.collectAsStateWithLifecycle()
    val sources = Sources.enabled(enabled)

    // A source switched off in Settings must not be searched, so move to one that is still allowed.
    LaunchedEffect(sources) {
        if (sources.isNotEmpty() && sources.none { it.id == state.source }) {
            viewModel.selectSource(sources.first().id)
        }
    }

    // The route may name a category of the source's own grouping; it can only be opened once the
    // source has said which categories it publishes.
    LaunchedEffect(state.categories, initialCategory) {
        if (!initialCategory.isNullOrBlank()) viewModel.openCategoryById(initialCategory)
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current)) {
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
                Text(stringResource(R.string.src_title), style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Text(
                stringResource(R.string.src_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                items(sources, key = { it.id.name }) { source ->
                    PlayChip(
                        label = source.id.label,
                        selected = state.source == source.id,
                        onClick = { viewModel.selectSource(source.id) },
                    )
                }
            }
        }

        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.Search, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                stringResource(Sources.byId(state.source).id.note),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        // Both controls stay while there is a term: Search runs it again, and the
                        // circle is the way back to the source's own shelf.
                        if (state.searching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { viewModel.search() }) {
                                Text(stringResource(R.string.search_title))
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .playClickable(onClick = viewModel::clearSearch),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                GlyphIcon(
                                    Glyph.Close,
                                    size = 15.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = stringResource(R.string.a11y_clear_search),
                                )
                            }
                        }
                    }
                }
            }
        }

        val results = state.results
        val asked = state.searching || results != null || state.error != null

        // An answer sits where the shelf was, directly under the field. A search replaces the page
        // rather than being appended under a rail the reader has already scrolled past.
        if (asked) {
            when {
                state.searching -> item { LoadingRail(3) }

                state.error != null -> item {
                    StateMessage(
                        title = stringResource(R.string.src_failed_title, state.source.label),
                        body = state.error ?: stringResource(R.string.search_failed),
                        onRetry = { viewModel.search() },
                    )
                }

                results != null && results.isEmpty() -> item {
                    StateMessage(
                        title = stringResource(R.string.src_none_title, state.source.label),
                        body = stringResource(R.string.src_none_body, state.query),
                    )
                }

                results != null -> {
                    item {
                        SectionHeader(
                            stringResource(R.string.src_results_title, state.source.label),
                            subtitle = pluralStringResource(
                                R.plurals.search_count,
                                results.size,
                                results.size,
                                state.query,
                            ),
                        )
                    }
                    itemsIndexed(results, key = { _, app -> app.key }) { position, app ->
                        // Resolved in composition rather than in the click callback, so a configuration
                        // change redraws the line instead of toasting the previous locale's words.
                        val queued = stringResource(R.string.src_downloading, app.title, app.source.label)
                        SourceAppRow(
                            app = app,
                            modifier = Modifier.playEnter(state.generation, position),
                            open = state.openKey == app.key,
                            loadingFiles = state.loadingFilesFor == app.key,
                            files = state.files[app.key],
                            fileError = state.fileError[app.key],
                            onToggle = { viewModel.toggleFiles(app) },
                            onOpenPage = {
                                app.pageUrl?.let { url ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, url.toUri())
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                            onOpenInApp = app.cataloguePostId?.let { postId -> { onOpenCataloguePost(postId) } },
                            onDownload = { file ->
                                enqueueSourceFile(context, app, file)
                                Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        } else {
            // The source's own grouping. A site that publishes categories of its own gets a chip row
            // for them, so the page can be read as that site's catalogue rather than as the store's.
            if (state.categories.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        item {
                            PlayChip(
                                label = stringResource(R.string.src_newest),
                                selected = state.selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        items(state.categories, key = { it.id }) { category ->
                            PlayChip(
                                label = category.name,
                                selected = state.selectedCategory?.id == category.id,
                                onClick = { viewModel.selectCategory(category) },
                            )
                        }
                    }
                }
            }

            // The source's newest apps, or the apps of the category that was opened.
            val shelf = state.selectedCategory?.let { category -> state.categoryApps } ?: state.latest
            if (state.loadingShelf || state.loadingCategory) {
                item { LoadingRail(2) }
            } else if (shelf.isNotEmpty()) {
                item {
                    SectionHeader(
                        state.selectedCategory?.name
                            ?: stringResource(R.string.src_newest_on, state.source.label),
                        subtitle = stringResource(R.string.src_from_source, state.source.label),
                    )
                }
                itemsIndexed(shelf) { position, app ->
                    val queued = stringResource(R.string.src_downloading, app.title, app.source.label)
                    SourceAppRow(
                        app = app,
                        modifier = Modifier.playEnter(state.generation, position),
                        open = state.openKey == app.key,
                        loadingFiles = state.loadingFilesFor == app.key,
                        files = state.files[app.key],
                        fileError = state.fileError[app.key],
                        onToggle = { viewModel.toggleFiles(app) },
                        onOpenPage = {
                            app.pageUrl?.let { url ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, url.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        },
                        onOpenInApp = app.cataloguePostId?.let { postId -> { onOpenCataloguePost(postId) } },
                        onDownload = { file ->
                            enqueueSourceFile(context, app, file)
                            Toast.makeText(context, queued, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceAppRow(
    app: SourceApp,
    modifier: Modifier = Modifier,
    open: Boolean,
    loadingFiles: Boolean,
    files: List<SourceFile>?,
    fileError: String?,
    onToggle: () -> Unit,
    onOpenPage: () -> Unit,
    onOpenInApp: (() -> Unit)?,
    onDownload: (SourceFile) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().playClickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(ArtShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = app.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(app.version, app.size, app.subtitle).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    app.source.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            // The word on the control is the state, so it morphs rather than being repainted.
            AnimatedContent(
                targetState = open,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(PlayMotion.spec(PlayMotion.SHORT, EmphasizedEasing)),
                        initialContentExit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)),
                    )
                },
                label = "files-toggle-label",
            ) { expanded ->
                TextButton(onClick = onToggle) {
                    Text(stringResource(if (expanded) R.string.src_hide_files else R.string.src_files))
                }
            }
        }

        // The disclosure opens rather than appearing: Play slides a row's files out from under its
        // title, and closing it takes them back up rather than deleting them.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) +
                slideInVertically(PlayMotion.spec(PlayMotion.MEDIUM, EmphasizedDecelerateEasing)) { it / 4 },
            exit = fadeOut(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) +
                slideOutVertically(PlayMotion.spec(PlayMotion.SHORT, StandardAccelerateEasing)) { it / 4 },
        ) {
            when {
                loadingFiles -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.src_resolving),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                fileError != null -> Text(
                    fileError.ifBlank { stringResource(R.string.src_file_list_failed) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )

                files != null && files.isEmpty() -> Text(
                    stringResource(R.string.src_no_file),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )

                files != null -> files.forEach { file ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        TextButton(onClick = { onDownload(file) }) {
                            Text(stringResource(R.string.action_download))
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onOpenPage) { Text(stringResource(R.string.src_open_page)) }
                onOpenInApp?.let { open ->
                    TextButton(onClick = open) { Text(stringResource(R.string.src_app_page)) }
                }
            }
        }
    }
}

/** A failure a person can read: the message when there is one, else the exception's own name. */
private fun describe(e: Throwable): String? =
    e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
