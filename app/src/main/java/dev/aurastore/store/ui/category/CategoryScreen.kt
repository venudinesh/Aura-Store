package dev.aurastore.store.ui.category

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.Category
import dev.aurastore.store.data.ListPager
import dev.aurastore.store.data.Module
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.ui.AppRow
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.Glyph
import dev.aurastore.store.ui.GlyphIcon
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PagingFooter
import dev.aurastore.store.ui.PlayBadge
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StatusLine
import dev.aurastore.store.ui.statusText
import dev.aurastore.store.ui.playClickable
import dev.aurastore.store.ui.playEnter
import dev.aurastore.store.ui.feed.Rail
import dev.aurastore.store.ui.feed.RailRow
import dev.aurastore.store.ui.feed.Sort
import dev.aurastore.store.ui.feed.SORTS
import dev.aurastore.store.ui.feed.flattenTabs
import dev.aurastore.store.ui.feed.railsFromModules
import dev.aurastore.store.ui.rememberStoreActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A category as its own screen, the way the catalogue's CategoryActivity works.
 *
 * The Browse tab can only be a general feed, which is why picking "Racing" there used to re-filter
 * that feed in place: the section rails, the sub-categories and the full list all belong to the term
 * itself (`v2/categories/{id}` ships a module tree, `v2/posts?taxonomy=category&tax_ids=` ships the
 * list), and a sub-category is just another term id, so drilling down needs no new route.
 */
data class CategoryState(
    val loading: Boolean = true,
    // Either a sentence this app owns, as a resource, or the catalogue's own text.
    val error: StatusLine? = null,
    val tree: List<Category> = emptyList(),
    val current: Category? = null,
    /** The term's own text, which only `v2/categories/{id}` carries — the tree has names and counts. */
    val description: String? = null,
    val subcategories: List<Category> = emptyList(),
    val sorts: List<Sort> = SORTS,
    val sort: String = SORTS.first().orderBy,
    val rails: List<Rail> = emptyList(),
    val posts: List<PostSummary> = emptyList(),
    /** Another page of the flat list is in flight; the rows on screen stay where they are. */
    val loadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    /** Whether the walk stopped at the page the route refuses rather than at the end of the ordering. */
    val listCapped: Boolean = false,
    val moreError: StatusLine? = null,
    /** Bumped by every finished load, and used only as the key of the entrance stagger. */
    val generation: Int = 0,
)

/** [categoryId] is the term to open, or `0L` for the picker of every category. */
class CategoryViewModel(private var categoryId: Long) : ViewModel() {

    private val _state = MutableStateFlow(CategoryState())
    val state: StateFlow<CategoryState> = _state.asStateFlow()

    private var started = false
    private var pager: ListPager? = null
    private val railPagers = mutableMapOf<String, ListPager>()
    private var moreJob: Job? = null

    fun start() {
        if (started) return
        started = true
        load()
    }

    /** Sub-categories are ordinary terms, so drilling down replaces this screen's content. */
    fun open(id: Long) {
        if (id == categoryId) return
        categoryId = id
        moreJob?.cancel()
        pager = null
        railPagers.clear()
        _state.update {
            it.copy(
                rails = emptyList(),
                posts = emptyList(),
                description = null,
                loadingMore = false,
                hasMorePages = false,
                listCapped = false,
                moreError = null,
            )
        }
        load()
    }

    fun setSort(orderBy: String) {
        if (orderBy == _state.value.sort) return
        _state.update { it.copy(sort = orderBy) }
        loadPosts()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val tree = _state.value.tree.ifEmpty { runCatching { Api.categories() }.getOrDefault(emptyList()) }
            if (tree.isEmpty() && categoryId == 0L) {
                _state.update { it.copy(loading = false, error = StatusLine(R.string.cat_no_categories)) }
                return@launch
            }
            val current = if (categoryId > 0L) find(tree, categoryId) else null
            _state.update {
                it.copy(
                    tree = tree,
                    current = current,
                    subcategories = current?.children?.filter { child -> child.count > 0 }.orEmpty(),
                )
            }
            if (categoryId > 0L) loadCategory() else _state.update { it.copy(loading = false) }
        }
    }

    /** The term's own module tree, then the flat list under the current ordering. */
    private suspend fun loadCategory() {
        val page = runCatching { Api.category(categoryId) }.getOrNull()
        val rails = page?.let { railsFromModules(it.modules, it.name, limit = 6, batchSize = 3) }.orEmpty()
        val sorts = page?.let { orderingsFrom(it.modules) }.orEmpty().ifEmpty { SORTS }
        _state.update { current ->
            current.copy(
                sorts = sorts,
                description = page?.description,
                // The term defines its own orderings, so the one carried over may not exist here.
                sort = if (sorts.any { it.orderBy == current.sort }) current.sort else sorts.first().orderBy,
            )
        }
        loadPostsNow()
        railPagers.clear()
        _state.update { current ->
            current.copy(
                loading = false,
                rails = rails,
                generation = current.generation + 1,
                error = when {
                    rails.isEmpty() && current.posts.isEmpty() ->
                        page?.let { StatusLine(R.string.cat_nothing_listed, listOf(it.name)) }
                            ?: StatusLine(R.string.cat_could_not_read)
                    else -> null
                },
            )
        }
    }

    private fun loadPosts() {
        viewModelScope.launch { loadPostsNow() }
    }

    private suspend fun loadPostsNow() {
        moreJob?.cancel()
        pager = null
        val page = runCatching { Api.postPage(listQuery(_state.value.sort, categoryId), 1) }.getOrNull()
        val p = ListPager()
        if (page != null) p.accept(page)
        pager = p
        _state.update { current ->
            current.copy(
                posts = p.items,
                hasMorePages = p.hasMore,
                listCapped = p.stoppedAtCeiling,
                loadingMore = false,
                moreError = null,
                generation = current.generation + 1,
            )
        }
    }

    /** The next page of the term's own list, asked for while there is still screen left to read. */
    fun loadMore() {
        val p = pager ?: return
        val current = _state.value
        if (current.posts.isEmpty() || current.loading || current.loadingMore || !p.hasMore) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            val query = listQuery(current.sort, categoryId)
            val next = runCatching { Api.postPage(query, p.page + 1) }
            if (next.isSuccess) {
                p.accept(next.getOrThrow())
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
                    posts = p.items,
                    hasMorePages = p.hasMore,
                    listCapped = p.stoppedAtCeiling,
                )
            }
        }
    }

    /**
     * The next page of one rail, asked for when the swipe has run out of cards.
     *
     * A rail that fails to grow stays the length it was: nothing on a horizontal row says "the end", so
     * the next swipe to the last cards asks again. The ending a rail can claim is the route's window
     * closing on it, which [Rail.capped] carries to the tail so the row can say it out loud.
     */
    fun loadMoreRail(title: String) {
        val rail = _state.value.rails.firstOrNull { it.title == title } ?: return
        if (rail.loadingMore || !rail.hasMore) return
        val pager = railPagers.getOrPut(title) { ListPager.of(rail.posts, rail.page, rail.hasMore) }
        updateRail(title) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = runCatching { Api.postPage(rail.query, pager.page + 1) }
            if (next.isSuccess) pager.accept(next.getOrThrow())
            updateRail(title) {
                it.copy(
                    posts = pager.items,
                    page = pager.page,
                    hasMore = pager.hasMore,
                    capped = pager.stoppedAtCeiling,
                    loadingMore = false,
                )
            }
        }
    }

    private fun updateRail(title: String, change: (Rail) -> Rail) {
        _state.update { current ->
            current.copy(rails = current.rails.map { rail -> if (rail.title == title) change(rail) else rail })
        }
    }

    private fun find(categories: List<Category>, id: Long): Category? {
        categories.forEach { category ->
            if (category.id == id) return category
            find(category.children, id)?.let { return it }
        }
        return null
    }
}

/**
 * The query `v2/posts` needs for one category term.
 *
 * No `limit` and no `page`: [Api.postPage] puts both in, so the ordering and the term are all this list
 * has to remember between one page and the next.
 */
internal fun listQuery(orderBy: String, categoryId: Long): Map<String, String> = mapOf(
    "taxonomy" to "category",
    "tax_ids" to categoryId.toString(),
    "order_by" to orderBy,
)

/**
 * The orderings a term's tree offers (`tab_item` modules). Their labels are the catalogue's own —
 * "Modified Game" rather than our "New & updated" — so a category reads as the site presents it.
 * An item without `order_by` is the catalogue's default listing ("New"), which the query leaves out.
 */
internal fun orderingsFrom(modules: List<Module>): List<Sort> = flattenTabs(modules)
    .filter { it.type == "tab_item" }
    .map { item ->
        val orderBy = item.params["order_by"].orEmpty()
        Sort(orderBy, item.title?.takeIf { title -> title.isNotBlank() } ?: orderBy.ifBlank { "New" })
    }
    .distinctBy { it.orderBy }

@Composable
fun CategoryScreen(
    categoryId: Long,
    onBack: () -> Unit,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = viewModel(key = "category-$categoryId") { CategoryViewModel(categoryId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val actions = rememberStoreActions()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.start() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp + LocalNavBarInset.current),
    ) {
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
                Spacer(Modifier.width(8.dp))
                Text(
                    state.current?.name ?: stringResource(R.string.cat_all),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (state.current == null) {
            if (state.loading) {
                item { LoadingRail(6) }
            }
            state.error?.let { message ->
                item {
                    StateMessage(
                        title = stringResource(R.string.cat_unavailable),
                        body = statusText(message),
                        onRetry = viewModel::start,
                    )
                }
            }
            itemsIndexed(state.tree.filter { it.count > 0 || it.children.isNotEmpty() }) { position, parent ->
                CategoryGroup(
                    parent = parent,
                    modifier = Modifier.playEnter(state.generation, position),
                    onOpen = viewModel::open,
                )
            }
            return@LazyColumn
        }

        state.current?.let { category ->
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        pluralStringResource(R.plurals.cat_app_count, category.total, category.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.subcategories.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.cat_subcategories))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.subcategories, key = { it.id }) { child ->
                        PlayChip(
                            label = child.name,
                            selected = false,
                            onClick = { viewModel.open(child.id) },
                        )
                    }
                }
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(state.sorts, key = { it.orderBy }) { sort ->
                    PlayChip(
                        label = sort.label,
                        selected = state.sort == sort.orderBy,
                        onClick = { viewModel.setSort(sort.orderBy) },
                    )
                }
            }
        }

        if (state.loading) {
            item { LoadingRail() }
        }

        state.error?.let { message ->
            item {
                StateMessage(
                        title = stringResource(R.string.cat_empty),
                        body = statusText(message),
                        onRetry = viewModel::start,
                    )
            }
        }

        state.rails.forEachIndexed { index, rail ->
            item(key = "rail-$index-${rail.title}") {
                RailRow(
                    rail = rail,
                    generation = state.generation,
                    onOpenPost = onOpenPost,
                    onLoadMore = viewModel::loadMoreRail,
                )
            }
        }

        if (state.posts.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.cat_all_in, state.current?.name.orEmpty()),
                    subtitle = state.sorts.firstOrNull { it.orderBy == state.sort }?.label,
                )
            }
            itemsIndexed(state.posts, key = { _, post -> post.id }) { position, post ->
                AppRow(
                    post = post,
                    onClick = { onOpenPost(post.id) },
                    modifier = Modifier.playEnter(state.generation, position),
                    action = actions.actionFor(post, installs, downloads),
                    onAction = { actions.runPrimary(post) },
                )
            }
            item(key = "list-footer") {
                PagingFooter(
                    loading = state.loadingMore,
                    hasMore = state.hasMorePages,
                    capped = state.listCapped,
                    error = state.moreError,
                    name = state.current?.name.orEmpty(),
                    onRetry = viewModel::loadMore,
                )
            }
        }
    }

    // The next page is asked for while there is still screen left to read, so it arrives before the
    // finger does. Re-keyed on the row count because a term whose first page fits on one screen has to
    // keep asking until either the screen is full or the catalogue runs out of rows.
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

/** One top-level category with its own children listed underneath, for the picker. */
@Composable
private fun CategoryGroup(
    parent: Category,
    modifier: Modifier = Modifier,
    onOpen: (Long) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().playClickable(onClick = { onOpen(parent.id) })
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(parent.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            PlayBadge(text = parent.total.toString())
            Spacer(Modifier.width(8.dp))
            GlyphIcon(Glyph.ChevronRight, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        parent.children.filter { it.count > 0 }.forEach { child ->
            Row(
                Modifier.fillMaxWidth().playClickable(onClick = { onOpen(child.id) })
                    .padding(start = 32.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(child.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PlayBadge(text = child.count.toString())
                Spacer(Modifier.width(8.dp))
                GlyphIcon(Glyph.ChevronRight, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
