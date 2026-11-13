package dev.aurastore.store.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import coil3.compose.AsyncImage
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.SearchPager
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.sources.SourceAnswer
import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.sources.SourceHub
import dev.aurastore.store.data.sources.Sources
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.theme.ArtShape
import dev.aurastore.store.ui.AppRow
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.footerFailureText
import dev.aurastore.store.ui.playEnter
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.rememberStoreActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val hot: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val results: List<PostSummary>? = null,
    val suggestions: List<PostSummary> = emptyList(),
    /** A search still in flight with nothing on screen yet. */
    val loading: Boolean = false,
    /** Another page is in flight, and the rows already on screen stay where they are. */
    val loadingMore: Boolean = false,
    /** Whether the list can be asked for a page it has not had. */
    val hasMorePages: Boolean = false,
    /** Every match the catalogue has for the term, once it has said how many there are. */
    val total: Int? = null,
    val moreError: String? = null,
    val error: String? = null,
    /**
     * The other catalogues' answers to the same term, one group per source.
     *
     * The catalogue is only one of the places an app is published, so the search is a search across
     * the shop: the catalogue's own rows come first, and each other source's full answer follows
     * under its own name.
     */
    val otherResults: List<SourceAnswer> = emptyList(),
    /** Whether the other catalogues are still answering. */
    val otherSearching: Boolean = false,
    /** Bumped by every load, and used only as the key of the result entrance. */
    val generation: Int = 0,
)

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var moreJob: Job? = null

    /**
     * The pages taken in for the term on screen, and the only place the seen ids live. A new search
     * replaces it, so an in-flight page of the old term has nowhere to land — its job is cancelled, and
     * the rows it fetched go with it.
     */
    private var pager: SearchPager? = null

    fun start() {
        if (_state.value.hot.isNotEmpty()) return
        viewModelScope.launch {
            val hot = runCatching { Api.hotSearch() }.getOrDefault(emptyList())
            // Play fills the idle screen with something to tap; the catalogue's own top charts fit.
            val suggestions = runCatching {
                Api.posts(mapOf("order_by" to "post_views", "limit" to "12", "page" to "1"))
            }.getOrDefault(emptyList())
            _state.update { it.copy(hot = hot, suggestions = suggestions, recent = it.recent, generation = it.generation + 1) }
        }
    }

    fun onQueryChange(value: String) {
        // A term that changes is a new question. Keeping the old rows under the new words would put
        // the wrong query over the right answer, so the answer goes with the edit.
        val hadAnswer = _state.value.results != null || _state.value.loading
        _state.update { it.copy(query = value) }
        if (value.isBlank() || hadAnswer) {
            stop()
            _state.update {
                it.copy(
                    results = null,
                    loading = false,
                    loadingMore = false,
                    hasMorePages = false,
                    total = null,
                    moreError = null,
                    error = null,
                    otherResults = emptyList(),
                    otherSearching = false,
                )
            }
        }
    }

    fun search(term: String = _state.value.query) {
        if (term.isBlank()) return
        stop()
        val p = SearchPager()
        pager = p
        _state.update { current ->
            current.copy(
                query = term,
                loading = true,
                loadingMore = false,
                hasMorePages = true,
                results = null,
                total = null,
                moreError = null,
                error = null,
                otherResults = emptyList(),
                otherSearching = true,
                recent = (listOf(term) + current.recent).distinct().take(6),
                generation = current.generation + 1,
            )
        }
        searchJob = viewModelScope.launch {
            // Both sources answer for a term and neither one's failure is fatal unless the other has
            // nothing to show either. They start together: the ranked list is what fills the screen first.
            val ranked = async { runCatching { Api.search(term) } }
            val first = async { runCatching { Api.searchPage(term, page = 1) } }
            // The other catalogues are asked the same question at the same time. Their answers are a
            // second group under the catalogue's own, not a replacement for it.
            val others = async {
                runCatching { SourceHub.search(term, Settings.sources.value) }
            }
            p.seed(ranked.await().getOrNull().orEmpty())
            // Show the best matches while the paged source is still answering, rather than holding a
            // spinner for the slower of two requests that both have to finish anyway.
            if (p.items.isNotEmpty()) publish(p, loadingMore = true)
            val page = first.await()
            if (page.isSuccess) {
                p.accept(page.getOrThrow())
            } else if (p.items.isEmpty()) {
                // The state keeps the server's sentence, or nothing; the word for "nothing" is the
                // screen's to say, so the fallback lives in resources rather than in a ViewModel that
                // has no Context to read them from.
                _state.update {
                    it.copy(
                        loading = false,
                        hasMorePages = false,
                        error = page.causeOrNull()?.message.orEmpty(),
                    )
                }
                publishOthers(others.await())
                return@launch
            }
            publish(p)
            publishOthers(others.await())
        }
    }

    /** The other catalogues' answers, without the catalogue's own rows. */
    private fun publishOthers(outcome: Result<List<SourceAnswer>>) {
        val found = outcome.getOrNull().orEmpty().filter { it.source != SourceId.LITEAPKS }
        _state.update { it.copy(otherResults = found, otherSearching = false) }
    }

    /** The next page, asked for when the list has run out of screen ahead of the reader. */
    fun loadMore() {
        val p = pager ?: return
        val current = _state.value
        if (current.results == null || current.loading || current.loadingMore || !current.hasMorePages) return
        val term = current.query
        if (term.isBlank()) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            val next = runCatching { Api.searchPage(term, p.page + 1) }
            if (next.isSuccess) {
                p.accept(next.getOrThrow())
            } else {
                // What is on screen stays; the footer is where the failure says so, with a way back.
                _state.update { it.copy(moreError = next.causeOrNull()?.message.orEmpty()) }
            }
            publish(p)
        }
    }

    fun clear() {
        stop()
        _state.update { it.copy(query = "", results = null, error = null) }
    }

    private fun stop() {
        searchJob?.cancel()
        moreJob?.cancel()
        searchJob = null
        moreJob = null
        pager = null
    }

    private fun publish(p: SearchPager, loading: Boolean = false, loadingMore: Boolean = false) {
        _state.update {
            it.copy(
                loading = loading,
                loadingMore = loadingMore,
                hasMorePages = p.hasMore,
                results = p.items,
                total = p.total,
            )
        }
    }

    /** A coroutine cancelled by a newer search ends as a [CancellationException] with no message to show. */
    private fun Result<*>.causeOrNull(): Throwable? = exceptionOrNull()?.takeUnless { it is CancellationException }
}

@Composable
fun SearchScreen(
    onOpenPost: (Long) -> Unit,
    /** Opens the source picker with this source selected and the current query carried over. */
    onOpenSource: (SourceId, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val enabledSources by Settings.sources.collectAsStateWithLifecycle()
    val otherSources = Sources.enabled(enabledSources).filter { it.id != SourceId.LITEAPKS }
    val actions = rememberStoreActions()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // The site whose answer is on screen. Null means the first site that answered, which is the
    // catalogue while it has rows. Keyed to the generation so a new term starts on the catalogue.
    var selectedSite by rememberSaveable(state.generation) { mutableStateOf<String?>(null) }
    val siteTabs = buildList {
        state.results?.takeIf { it.isNotEmpty() }?.let { add(SearchSiteTab(SourceId.LITEAPKS, it.size)) }
        state.otherResults.forEach { add(SearchSiteTab(it.source, it.apps.size)) }
    }
    val activeSite = siteTabs.firstOrNull { it.id.name == selectedSite } ?: siteTabs.firstOrNull()
    val activeAnswer = state.otherResults.firstOrNull { it.source == activeSite?.id }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.start() }

    // Play focuses the field *and* raises the keyboard when you land on the Search tab. Focus alone
    // was not enough on this handset: the bar is a non-focusable popup, so the field could hold focus
    // with no IME attached — the field looked ready and typing went nowhere.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    // A new term starts the list at the top; a new page does not, which is the whole point of paging.
    androidx.compose.runtime.LaunchedEffect(state.generation) { listState.scrollToItem(0) }

    // Switching sites is a new list: it starts at its first row, with the strip in place above it.
    androidx.compose.runtime.LaunchedEffect(activeSite?.id) {
        if (activeSite != null) listState.scrollToItem(0)
    }

    // The next page is asked for while there is still screen left to read, so it arrives before the
    // finger does. Re-keyed on the row count because a short list that ends inside the first screen has
    // to keep asking until either the screen is full or the catalogue says it is out of rows.
    val nearTheEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    androidx.compose.runtime.LaunchedEffect(nearTheEnd, state.results?.size, activeSite?.id) {
        // Only the catalogue pages. A site's answer arrives whole, and asking for the catalogue's
        // next page while another site's tab is open would spend a request nobody asked for.
        if (nearTheEnd && activeSite?.id == SourceId.LITEAPKS) viewModel.loadMore()
    }

    LazyColumn(modifier = modifier, state = listState, contentPadding = PaddingValues(bottom = 28.dp + LocalNavBarInset.current)) {
        // No page title: the tab is Search, the field is the first thing on it, and a 36 sp "Search"
        // above a field that already says "Search for apps & games" was a heading explaining itself.
        // The field starts where the title used to, so the screen opens on the thing you came to use.

        // Play's search field: a filled pill with a leading magnifier and a clear button.
        item {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .height(52.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.Search, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            // The pill is 52 dp and the hint is one line of it. At a 2.0× text size the
                            // hint wrapped to a second line that the pill clipped, so the field read
                            // "Search for apps &" — clipped where it looked like a typo. One line with
                            // an ellipsis says it is truncated.
                            Text(
                                stringResource(R.string.search_hint),
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
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboard?.hide()
                                    viewModel.search()
                                },
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                    if (state.query.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(onClick = viewModel::clear),
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

        // A search replaces the idle screen: while a term is being answered the chips, the recent
        // terms and the charts would all be ways out of the answer the reader is waiting for.
        val active = state.results != null || state.loading || state.error != null

        // The catalogue is not the only place a build lives: the same query can be sent to the
        // other catalogues, each of which resolves its own direct file links.
        if (!active) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    items(otherSources, key = { it.id.name }) { source ->
                        PlayChip(
                            label = source.id.label,
                            selected = false,
                            onClick = { onOpenSource(source.id, state.query) },
                        )
                    }
                }
            }
        }

        if (!active && state.recent.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.search_recent), onMore = null)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.recent, key = { it }) { term ->
                        PlayChip(label = term, selected = false, onClick = { viewModel.search(term) })
                    }
                }
            }
        }

        if (!active && state.hot.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.search_trending)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.hot, key = { it }) { term ->
                        PlayChip(label = term, selected = false, onClick = { viewModel.search(term) })
                    }
                }
            }
        }

        val results = state.results
        when {
            results != null -> {
                val total = state.total
                val moreError = state.moreError
                // One site at a time, chosen from the strip: the catalogue's own rows and every
                // other source's answer are tabs, not blocks stacked under each other. A site with
                // twenty matches still shows twenty.
                if (siteTabs.isNotEmpty()) {
                    item {
                        SectionHeader(
                            stringResource(R.string.search_results),
                            subtitle = when {
                                activeSite == null -> null
                                activeSite.id == SourceId.LITEAPKS && total != null && total > results.size ->
                                    pluralStringResource(
                                        R.plurals.search_count_partial,
                                        results.size,
                                        results.size,
                                        total,
                                        state.query,
                                    )
                                else -> pluralStringResource(
                                    R.plurals.search_count,
                                    activeSite.count,
                                    activeSite.count,
                                    state.query,
                                )
                            },
                        )
                    }
                    item(key = "search-sites") {
                        SearchSiteTabs(
                            tabs = siteTabs,
                            selected = activeSite?.id,
                            onSelect = { selectedSite = it.name },
                        )
                    }
                    when (activeSite?.id) {
                        SourceId.LITEAPKS -> itemsIndexed(results) { position, post ->
                            AppRow(
                                post = post,
                                modifier = Modifier.playEnter(state.generation, position),
                                onClick = { onOpenPost(post.id) },
                                action = actions.actionFor(post, installs, downloads),
                                onAction = { actions.runPrimary(post, askForSource = false) },
                            )
                        }

                        null -> Unit

                        else -> itemsIndexed(
                            activeAnswer?.apps.orEmpty(),
                            key = { _, app -> "src-${activeSite.id.name}-${app.key}" },
                        ) { position, app ->
                            SourceResultRow(
                                app = app,
                                modifier = Modifier.playEnter(state.generation, position),
                                onPick = { actions.download(app) },
                            )
                        }
                    }

                    // The end of the list is a place the reader arrives at, so it has to say what it
                    // is. Only the catalogue pages; a site's answer arrives whole.
                    if (activeSite?.id == SourceId.LITEAPKS) {
                        when {
                            state.loading || state.loadingMore -> item { FindingRow() }
                            moreError != null -> item {
                                RetryRow(
                                    message = moreError,
                                    fallback = stringResource(R.string.search_load_more_failed),
                                    onRetry = viewModel::loadMore,
                                )
                            }
                            state.otherSearching -> item { FindingRow() }
                            !state.hasMorePages -> item { EndRow(state.query) }
                        }
                    }
                } else {
                    // No site has a row yet: the others may still be answering, and when none does,
                    // the term found nothing anywhere.
                    when {
                        state.loading || state.loadingMore || state.otherSearching -> item { FindingRow() }
                        else -> item {
                            StateMessage(
                                title = stringResource(R.string.search_none),
                                body = stringResource(R.string.search_none_body, state.query),
                            )
                        }
                    }
                }
            }

            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                // The catalogue failed but the other sources answered: their rows are the useful
                // part, so they become the tabs and the failure is a line above them.
                if (siteTabs.isEmpty()) {
                    item {
                        StateMessage(
                            title = stringResource(R.string.search_failed),
                            body = state.error?.takeIf { it.isNotBlank() },
                            onRetry = { viewModel.search() },
                        )
                    }
                } else {
                    item { SectionHeader(stringResource(R.string.search_results)) }
                    item {
                        RetryRow(
                            message = state.error.orEmpty(),
                            fallback = stringResource(R.string.search_failed),
                            onRetry = { viewModel.search() },
                        )
                    }
                    item(key = "search-sites") {
                        SearchSiteTabs(
                            tabs = siteTabs,
                            selected = activeSite?.id,
                            onSelect = { selectedSite = it.name },
                        )
                    }
                    itemsIndexed(
                        activeAnswer?.apps.orEmpty(),
                        key = { _, app -> "src-${activeSite?.id?.name}-${app.key}" },
                    ) { position, app ->
                        SourceResultRow(
                            app = app,
                            modifier = Modifier.playEnter(state.generation, position),
                            onPick = { actions.download(app) },
                        )
                    }
                }
            }

            else -> {
                item {
                    SectionHeader(
                        stringResource(R.string.search_suggest),
                        subtitle = stringResource(R.string.search_suggest_subtitle),
                    )
                }
                if (state.suggestions.isEmpty()) item { LoadingRail(3) }
                itemsIndexed(state.suggestions) { position, post ->
                    AppRow(
                        post = post,
                        modifier = Modifier.playEnter(state.generation, position),
                        onClick = { onOpenPost(post.id) },
                        action = actions.actionFor(post, installs, downloads),
                        onAction = { actions.runPrimary(post) },
                    )
                }
            }
        }
    }
}

/** One site's answer, as the strip above the results counts it. */
private data class SearchSiteTab(val id: SourceId, val count: Int)

/**
 * The sites that answered, as tabs.
 *
 * The strip is the only place the site is named now: every row under it comes from that site, so
 * the row no longer repeats the name in a pill. The count is the number of rows that site returned,
 * which is what tells a thin answer from a full one before the reader taps it.
 */
@Composable
private fun SearchSiteTabs(
    tabs: List<SearchSiteTab>,
    selected: SourceId?,
    onSelect: (SourceId) -> Unit,
) {
    // The strip sits on the page rather than on a card, so it carries the page's own colour and
    // the rows below start clean.
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = { it.id.name }) { tab ->
                PlayChip(
                    label = tab.id.label,
                    count = tab.count,
                    selected = tab.id == selected,
                    onClick = { onSelect(tab.id) },
                )
            }
        }
    }
}

/** The spinner that says another page, or another site, is still on its way. */
@Composable
private fun FindingRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.search_finding),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A failure line with the one control that answers it. */
@Composable
private fun RetryRow(message: String, fallback: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // The state carries the server's sentence when it gave one; the screen owns the words
            // for the case where it gave nothing.
            footerFailureText(message, fallback),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.try_again))
        }
    }
}

/** The line under the last row, when there is nothing more to ask for. */
@Composable
private fun EndRow(query: String) {
    Text(
        stringResource(R.string.search_end, query),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/**
 * One app from another catalogue, in the search results.
 *
 * The tab carries the source's name, so the row carries the app and nothing else.
 */
@Composable
private fun SourceResultRow(
    app: SourceApp,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().playClickable(onClick = onPick)
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
            Text(
                app.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(app.version, app.size).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onPick) {
            Text(stringResource(R.string.action_download))
        }
    }
}
