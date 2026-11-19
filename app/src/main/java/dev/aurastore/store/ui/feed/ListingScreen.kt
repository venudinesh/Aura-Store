package dev.aurastore.store.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.ListPager
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.ui.AppRow
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.PagingFooter
import dev.aurastore.store.ui.ScrollRevealHost
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StatusLine
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.playEnter
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.ui.statusText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One rail's full list, in the ordering the rail itself used.
 *
 * A rail shows the first screen of a cut of the catalogue — *Editor's Choice*, a modified-games row —
 * and offers *View all* when a person wants the rest. The query that made the cut travels with the
 * route, so this page walks the same rows rather than guessing a term from the heading.
 *
 * The walk is at [Api.LIST_PAGE_SIZE] a page, the same size as every other flat list: the rail's own
 * `limit` is how wide a row looks, not how much of the list the route holds.
 */
data class ListingState(
    val loading: Boolean = true,
    // Either a sentence this app owns, as a resource, or the catalogue's own text.
    val error: StatusLine? = null,
    val posts: List<PostSummary> = emptyList(),
    val loadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    /** Whether the walk stopped at the page the route refuses rather than at the end of the ordering. */
    val listCapped: Boolean = false,
    val moreError: StatusLine? = null,
    /** Bumped by a finished first load, and used only as the key of the entrance stagger. */
    val generation: Int = 0,
)

class ListingViewModel(private val query: Map<String, String>) : ViewModel() {

    private val _state = MutableStateFlow(ListingState())
    val state: StateFlow<ListingState> = _state.asStateFlow()

    private var started = false
    private var pager: ListPager? = null
    private var moreJob: Job? = null

    fun start() {
        if (started) return
        started = true
        load()
    }

    fun retry() {
        started = false
        start()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val page = runCatching { Api.postPage(query, 1, pageSize = Api.LIST_PAGE_SIZE) }
            page.fold(
                onSuccess = { result ->
                    val fresh = ListPager()
                    fresh.accept(result)
                    pager = fresh
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            posts = fresh.items,
                            hasMorePages = fresh.hasMore,
                            listCapped = fresh.stoppedAtCeiling,
                            moreError = null,
                            generation = current.generation + 1,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            error = e.message?.let { text -> StatusLine(text = text) }
                                ?: StatusLine(R.string.feed_list_failed),
                        )
                    }
                },
            )
        }
    }

    /** The next page, asked for while there is still screen left to read. */
    fun loadMore() {
        val walk = pager ?: return
        val current = _state.value
        if (current.loading || current.loadingMore || !walk.hasMore) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            val next = runCatching { Api.postPage(query, walk.page + 1, pageSize = Api.LIST_PAGE_SIZE) }
            if (next.isSuccess) {
                walk.accept(next.getOrThrow())
            } else {
                // What is on screen stays, and the footer is where the failure says so with a way back.
                val cause = next.exceptionOrNull()?.takeUnless { it is CancellationException }
                _state.update {
                    it.copy(moreError = cause?.message?.let { text -> StatusLine(text = text) }
                        ?: StatusLine(R.string.list_load_more_failed))
                }
            }
            _state.update {
                it.copy(
                    loadingMore = false,
                    posts = walk.items,
                    hasMorePages = walk.hasMore,
                    listCapped = walk.stoppedAtCeiling,
                )
            }
        }
    }
}

@Composable
fun ListingScreen(
    title: String,
    query: Map<String, String>,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListingViewModel = viewModel(
        key = "listing-${title.hashCode()}-${query.hashCode()}",
    ) { ListingViewModel(query) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    val actions = rememberStoreActions()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.start() }

    ScrollRevealHost(listState) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
        item(key = "listing-header") {
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
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.loading) {
            item(key = "listing-loading") { LoadingRail(6) }
        }

        state.error?.let { message ->
            item(key = "listing-error") {
                StateMessage(
                    title = stringResource(R.string.feed_unavailable),
                    body = statusText(message),
                    onRetry = viewModel::retry,
                )
            }
        }

        itemsIndexed(state.posts, key = { _, post -> post.id }) { position, post ->
            AppRow(
                post = post,
                rank = position + 1,
                onClick = { onOpenPost(post.id) },
                modifier = Modifier.playEnter(state.generation, position),
                action = actions.liveAction(post, installs),
                onAction = { actions.runPrimary(post) },
            )
        }

        if (state.posts.isNotEmpty()) {
            item(key = "listing-footer") {
                PagingFooter(
                    loading = state.loadingMore,
                    hasMore = state.hasMorePages,
                    capped = state.listCapped,
                    error = state.moreError,
                    name = title,
                    onRetry = viewModel::loadMore,
                )
            }
        }
    }
    }

    // The next page is asked for while there is still screen left to read, so it arrives before the
    // finger does. A list that ends inside the first screen keeps asking until either the screen is
    // full or the ordering runs out.
    val nearTheEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearTheEnd, state.posts.size) {
        if (nearTheEnd) viewModel.loadMore()
    }
}
