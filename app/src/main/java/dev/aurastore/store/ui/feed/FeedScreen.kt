package dev.aurastore.store.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurastore.store.R
import dev.aurastore.store.data.Api
import dev.aurastore.store.data.AppState
import dev.aurastore.store.data.Category
import dev.aurastore.store.data.ListPager
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.Settings
import dev.aurastore.store.data.sources.SourceHub
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.data.sources.SourceSection
import dev.aurastore.store.download.DownloadEngine
import dev.aurastore.store.ui.AppRow
import dev.aurastore.store.ui.LocalNavBarInset
import dev.aurastore.store.ui.HeroCard
import dev.aurastore.store.ui.LoadingRail
import dev.aurastore.store.ui.PagingFooter
import dev.aurastore.store.ui.PlayChip
import dev.aurastore.store.ui.ScrollToTopOnRetap
import dev.aurastore.store.ui.SectionHeader
import dev.aurastore.store.ui.StateMessage
import dev.aurastore.store.ui.StatusLine
import dev.aurastore.store.ui.statusText
import dev.aurastore.store.ui.playEnter
import dev.aurastore.store.ui.rememberStoreActions
import dev.aurastore.store.theme.PlayMotion
import dev.aurastore.store.theme.StandardEasing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The three content tabs. Each is the same surface with a different preset, and each follows the
 * catalogue's own tab of the matching name (`v2/home` ships `Featured`, `Game`, `App`, `Categories`).
 */
enum class FeedKind(val title: String, val categoryName: String?, val homeTab: String) {
    GAMES("Games", "Games", "Game"),
    APPS("Apps", "Apps", "App"),
    BROWSE("Browse", null, "Featured"),
}

/**
 * One ordering chip. The catalogue names these itself (`tab_item` modules: Latest Updates, Popular,
 * New, Trending), so the labels below are only used for orderings the server does not describe.
 */
data class Sort(val orderBy: String, val label: String)

internal val SORTS = listOf(
    Sort("post_views", "Top charts"),
    Sort("modified", "New & updated"),
    Sort("post_views_week", "Trending"),
)

/**
 * One horizontal rail of cards. `banner` is the catalogue's own promo art when there is one.
 *
 * `query`, `page` and `hasMore` are the rail's place in its own ordering: swiping a rail to its last
 * cards asks the module for the next page of the same parameters, so a rail never has to be re-read to
 * grow.
 */
data class Rail(
    val title: String,
    val posts: List<PostSummary>,
    val query: Map<String, String>,
    val subtitle: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val capped: Boolean = false,
) {

    /**
     * How many cards this rail can reach before the route's page window closes on it.
     *
     * A rail pages at its own module's `limit`, so its wall is not the flat lists' [Api.LIST_REACH] but
     * `50 × limit` — 600 cards for a 12-row module. The tail sentence needs the number that belongs to the
     * rail it is under, not the one that belongs to the screen.
     */
    val reach: Int
        get() = Api.LIST_MAX_PAGES * (query["limit"]?.toIntOrNull() ?: Api.LIST_PAGE_SIZE)
}

data class Hero(val id: Long, val title: String, val subtitle: String?, val image: String?)

data class FeedState(
    val loading: Boolean = true,
    // Either a sentence this app owns, as a resource, or the catalogue's own text.
    val error: StatusLine? = null,
    val channels: List<String> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val selectedChannel: Int = 0,
    val sorts: List<Sort> = SORTS,
    val sort: String = SORTS.first().orderBy,
    val hero: List<Hero> = emptyList(),
    val rails: List<Rail> = emptyList(),
    /** The other catalogues' own shelves, read for the Browse tab. */
    val sourceSections: List<SourceSection> = emptyList(),
    val topCharts: List<PostSummary> = emptyList(),
    /** Another page of the flat list is in flight; the rows on screen stay where they are. */
    val loadingMore: Boolean = false,
    /** Whether the flat list can be asked for a page it has not had. */
    val hasMorePages: Boolean = false,
    /** Whether the walk stopped at the page the route refuses rather than at the end of the ordering. */
    val listCapped: Boolean = false,
    val moreError: StatusLine? = null,
    /**
     * Bumped by every successful load, and used only as the key of the entrance stagger.
     *
     * Rows must animate when a list arrives, not every time the lazy list recycles their slot while the
     * user scrolls — so the modifier is keyed on this rather than on the posts themselves. An appended
     * page does not bump it, because the rows already on screen have nothing to replay.
     */
    val generation: Int = 0,
)

class FeedViewModel(private val kind: FeedKind) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    /** Resolved once from the category tree: what this tab filters by. */
    private var presetCategory: Category? = null
    private var activeCategory: Category? = null
    /** The whole term tree, kept because the Apps tab fills from the children of its own term. */
    private var tree: List<Category> = emptyList()
    private var started = false

    /** The flat list's pages, and the rails' — keyed by title, which is what the list keys are made of. */
    private var topPager: ListPager? = null
    private val railPagers = mutableMapOf<String, ListPager>()
    private var moreJob: Job? = null

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val categories = Api.categories()
                tree = categories
                presetCategory = kind.categoryName?.let { findByName(categories, it) }
                activeCategory = presetCategory
                val channels = channelsFor(categories)
                _state.update {
                    it.copy(
                        allCategories = flatten(categories).take(14),
                        channels = channels,
                        // Browse is not a category, so nothing in its row starts selected.
                        selectedChannel = if (kind == FeedKind.BROWSE) {
                            -1
                        } else {
                            channels.indexOfFirst { name -> name.equals(kind.categoryName, true) }
                        },
                    )
                }
                loadPosts()
                loadServerLayout()
                loadSources()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message?.let { text -> StatusLine(text = text) }
                            ?: StatusLine(R.string.feed_unreachable),
                    )
                }
            }
        }
    }

    fun reload() {
        started = false
        start()
    }

    /**
     * The other catalogues' own shelves, for the Browse tab.
     *
     * The Games and Apps tabs are the catalogue's own, so they do not carry the other sources; the
     * Browse tab is the one that shows the whole shop, so it is the one that reads them.
     */
    private suspend fun loadSources() {
        if (kind != FeedKind.BROWSE) return
        val sections = runCatching { SourceHub.sections(Settings.sources.value) }
            .getOrDefault(emptyList())
        _state.update { it.copy(sourceSections = sections) }
    }

    /** Pull-to-refresh: re-reads the catalogue so new apps and new versions appear immediately. */
    fun refresh() {
        if (_state.value.channels.isEmpty()) {
            reload()
            return
        }
        loadPosts()
        viewModelScope.launch { loadServerLayout() }
        viewModelScope.launch { loadSources() }
    }

    fun setSort(orderBy: String) {
        _state.update { it.copy(sort = orderBy) }
        loadPosts()
    }

    fun selectChannel(index: Int) {
        val name = _state.value.channels.getOrNull(index) ?: return
        // A channel reads the category of the same name; an empty channel keeps the tab's preset.
        activeCategory = findByName(_state.value.allCategories, name) ?: presetCategory
        _state.update { it.copy(selectedChannel = index) }
        loadPosts()
    }

    private fun loadPosts() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    loadingMore = false,
                    moreError = null,
                    listCapped = false,
                )
            }
            topPager = null
            try {
                val page = Api.postPage(chartsQuery(), 1)
                val pager = ListPager()
                pager.accept(page)
                topPager = pager
                _state.update { current ->
                    current.copy(
                        loading = false,
                        topCharts = pager.items,
                        hasMorePages = pager.hasMore,
                        listCapped = pager.stoppedAtCeiling,
                        generation = current.generation + 1,
                    )
                }
                // A chosen category gets the catalogue's own module tree for that term, which is what
                // the official client's category screen renders.
                activeCategory?.let { category -> loadCategoryRails(category.id) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message?.let { text -> StatusLine(text = text) }
                            ?: StatusLine(R.string.feed_list_failed),
                    )
                }
            }
        }
    }

    /** The ordering and the category the flat list is reading, which is what its next page continues. */
    private fun chartsQuery(): Map<String, String> = buildMap {
        put("order_by", _state.value.sort)
        activeCategory?.let {
            put("taxonomy", "category")
            put("tax_ids", it.id.toString())
        }
    }

    /** The next page of the flat list, asked for while there is still screen left to read. */
    fun loadMore() {
        val pager = topPager ?: return
        val current = _state.value
        if (current.topCharts.isEmpty() || current.loading || current.loadingMore || !pager.hasMore) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            val query = chartsQuery()
            val next = runCatching { Api.postPage(query, pager.page + 1) }
            if (next.isSuccess) {
                pager.accept(next.getOrThrow())
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
                    topCharts = pager.items,
                    hasMorePages = pager.hasMore,
                    listCapped = pager.stoppedAtCeiling,
                )
            }
        }
    }

    /**
     * The next page of one rail, asked for when the swipe has run out of cards.
     *
     * A rail that fails to grow simply stays the length it was: nothing on a horizontal row says
     * "the end", so a card that stopped arriving and a card that never existed look the same, and the
     * next swipe to the end asks again. The one ending a rail *can* claim is the route's window closing on
     * it, and [Rail.capped] carries that to the tail so the row can say it out loud.
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

    /**
     * The server-driven screen: everything except the hero carousel comes from the module tree of
     * this tab's own `v2/home` entry, and every module carries the query parameters for its rail.
     * Modules are fetched in small batches so a page with many rails still opens quickly.
     */
    private suspend fun loadServerLayout() {
        val home = runCatching { Api.home() }.getOrNull() ?: return
        val tab = home.tabs.firstOrNull { it.title.equals(kind.homeTab, true) }
            ?: home.tabs.firstOrNull { it.title.equals(kind.title, true) }
            ?: home.tabs.firstOrNull()
        val modules = tab?.modules.orEmpty()

        val rails = withAppFill(railsFromModules(modules, kind.title))
        val serverSorts = modules
            .firstOrNull { it.type == "tab" && it.items.isNotEmpty() }
            ?.items
            ?.mapNotNull { item -> item.orderBy?.let { Sort(it, item.title.orEmpty().ifBlank { it }) } }
            .orEmpty()

        _state.update { current ->
            current.copy(
                // Play opens every tab with a promo banner; the catalogue ships the same carousel for
                // each of them, so the tab that had none was the odd one out.
                hero = home.sliders.map { slider ->
                    Hero(
                        id = slider.id,
                        title = slider.title.orEmpty(),
                        subtitle = slider.subtitle,
                        image = slider.thumbnail ?: slider.banner,
                    )
                },
                sorts = mergeSorts(serverSorts),
                // The picker's own module lives in the catalogue's `Categories` tab, not in this one.
                allCategories = orderCategories(
                    current.allCategories,
                    home.tabs.flatMap { it.modules }.flatMap { it.categoryIds },
                ),
                rails = rails.ifEmpty { current.rails },
                generation = if (rails.isEmpty()) current.generation else current.generation + 1,
            )
        }
        if (rails.isNotEmpty()) {
            railPagers.clear()
        }
    }

    /** Keeps this client's chip order but takes the catalogue's name for each ordering it defines. */
    private fun mergeSorts(server: List<Sort>): List<Sort> {
        if (server.isEmpty()) return SORTS
        val byOrder = server.associateBy { it.orderBy }
        return SORTS.map { local -> byOrder[local.orderBy] ?: local } +
            server.filterNot { candidate -> SORTS.any { it.orderBy == candidate.orderBy } }
    }

    /** Puts the category picker in the order the catalogue lists (`cat_ids`), unknown ids last. */
    private fun orderCategories(categories: List<Category>, order: List<Long>): List<Category> {
        if (order.isEmpty()) return categories
        val ranked = order.withIndex().associate { (position, id) -> id to position }
        return categories.sortedBy { ranked[it.id] ?: Int.MAX_VALUE }
    }

    /** `GET v2/categories/{id}` -> one rail per module, in the order the catalogue defines. */
    private suspend fun loadCategoryRails(categoryId: Long) {
        val page = runCatching { Api.category(categoryId) }.getOrNull() ?: return
        val rails = withAppFill(railsFromModules(page.modules, page.name, limit = 6, batchSize = 3))
        // Even an empty tree replaces the tab's own rails: a channel shows what its term lists.
        railPagers.clear()
        _state.update { current ->
            current.copy(rails = rails, generation = current.generation + 1)
        }
    }

    private fun channelsFor(categories: List<Category>): List<String> {
        val names = flatten(categories, depth = 1).map { it.name }.distinct()
        // Games and Apps first when the catalogue offers them, the way Play orders its tabs.
        val preferred = listOf("Games", "Apps").filter { wanted -> names.any { it.equals(wanted, true) } }
        return (preferred + names.filterNot { name -> preferred.any { it.equals(name, true) } }).take(8)
    }

    private fun flatten(categories: List<Category>, depth: Int = 2): List<Category> = buildList {
        fun walk(list: List<Category>, level: Int) {
            list.forEach { category ->
                if (category.count > 0) add(category)
                if (level < depth) walk(category.children, level + 1)
            }
        }
        walk(categories, 0)
    }

    private fun findByName(categories: List<Category>, name: String): Category? {
        categories.forEach { category ->
            if (category.name.equals(name, ignoreCase = true)) return category
            findByName(category.children, name)?.let { return it }
        }
        return null
    }

    /** The app categories the Apps tab fills with: the children of the tab's own term. */
    private fun appCategories(): List<Category> =
        kind.categoryName?.let { findByName(tree, it) }?.children.orEmpty()

    /**
     * The Apps tab's rails, filled with its own categories when the catalogue's own tree is just one module.
     *
     * Both routes into this tab land here — `v2/home` and the term's own `v2/categories/{id}` — because the
     * second one used to overwrite the first a moment after the tab painted. A channel the user picked is
     * left alone: a term's screen shows that term, not its siblings.
     */
    private suspend fun withAppFill(base: List<Rail>): List<Rail> {
        if (kind != FeedKind.APPS || activeCategory?.id != presetCategory?.id) return base
        val fills = fillCategories(appCategories(), base.map { rail -> rail.title })
        if (fills.isEmpty()) return base
        return base + coroutineScope { fills.map { category -> async { railForCategory(category) } }.awaitAll() }
    }

}

@Composable
fun FeedScreen(
    kind: FeedKind,
    onOpenPost: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the category screen (`0` for the picker of every category). */
    onOpenCategory: (Long) -> Unit = {},
    /** Opens one of the other catalogues' own pages, on a category of theirs when one is named. */
    onOpenSource: (SourceId, String?) -> Unit = { _, _ -> },
    refreshKey: Int = 0,
    /** Bumped by the shell when the tab already showing is tapped again. */
    scrollTopKey: Int = 0,
    viewModel: FeedViewModel = viewModel(key = "feed-${kind.name}") { FeedViewModel(kind) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installs by AppState.installs.collectAsStateWithLifecycle()
    val downloads by DownloadEngine.items.collectAsStateWithLifecycle()
    val actions = rememberStoreActions()
    val listState = rememberLazyListState()

    LaunchedEffect(kind) { viewModel.start() }
    // A catalogue sync from Settings or the account sheet refreshes whatever tab is showing.
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.refresh() }
    ScrollToTopOnRetap(scrollTopKey, listState)

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = modifier,
    ) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp + LocalNavBarInset.current)) {
        item {
            Text(
                kind.title,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            )
        }

        if (state.hero.isNotEmpty()) {
            item {
                HeroCarousel(state.hero, onOpenPost)
                Box(Modifier.height(8.dp))
            }
        }

        // Play's filter row: which list, how it is ranked, and the category chooser.
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.channels) { index, name ->
                    PlayChip(
                        label = name,
                        selected = state.selectedChannel == index,
                        onClick = { viewModel.selectChannel(index) },
                    )
                }
                if (kind == FeedKind.BROWSE) {
                    item {
                        PlayChip(
                            label = stringResource(R.string.feed_categories),
                            selected = false,
                            expandable = true,
                            onClick = { onOpenCategory(0L) },
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
                    title = stringResource(R.string.feed_unavailable),
                    body = statusText(message),
                    onRetry = { viewModel.reload() },
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

        // The other catalogues: their own shelves, between the catalogue's rails and its chart list.
        sourceSections(
            sections = state.sourceSections,
            onOpenSource = onOpenSource,
            onPick = { app -> actions.download(app) },
        )

        if (state.topCharts.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.feed_top_charts, kind.title),
                    subtitle = when (state.sort) {
                        "modified" -> stringResource(R.string.feed_recently_updated, kind.title)
                        "post_views_week" -> stringResource(R.string.feed_trending, kind.title)
                        else -> stringResource(R.string.feed_most_viewed, kind.title)
                    },
                )
            }
            itemsIndexed(state.topCharts, key = { _, post -> post.id }) { position, post ->
                AppRow(
                    post = post,
                    modifier = Modifier.playEnter(state.generation, position),
                    rank = position + 1,
                    onClick = { onOpenPost(post.id) },
                    action = actions.actionFor(post, installs, downloads),
                    onAction = { actions.runPrimary(post) },
                )
            }
            item(key = "top-charts-footer") {
                PagingFooter(
                    loading = state.loadingMore,
                    hasMore = state.hasMorePages,
                    capped = state.listCapped,
                    error = state.moreError,
                    name = kind.title,
                    onRetry = viewModel::loadMore,
                )
            }
        }
    }
    }

    // The next page is asked for while there is still screen left to read, so it arrives before the
    // finger does. Re-keyed on the row count because a list that ends inside the first screen has to
    // keep asking until either the screen is full or the catalogue runs out of rows.
    val nearTheEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearTheEnd, state.topCharts.size) {
        if (nearTheEnd) viewModel.loadMore()
    }
}

@Composable
private fun HeroCarousel(hero: List<Hero>, onOpenPost: (Long) -> Unit) {
    val pager = rememberPagerState(pageCount = { hero.size })
    Column {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val item = hero[page]
            val featured = stringResource(R.string.feed_featured)
            HeroCard(
                imageUrl = item.image,
                title = item.title.ifBlank { featured },
                subtitle = item.subtitle,
                badge = featured,
                onClick = { if (item.id > 0) onOpenPost(item.id) },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hero.indices.forEach { index ->
                // Play's carousel marker grows and takes the accent colour on the same short spline the
                // page settles on, so the dots follow the swipe instead of blinking after it.
                val selected = index == pager.currentPage
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 18.dp else 6.dp,
                    animationSpec = PlayMotion.spec(PlayMotion.MEDIUM, StandardEasing),
                    label = "hero-dot-width",
                )
                val dotColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    animationSpec = PlayMotion.spec(PlayMotion.MEDIUM, StandardEasing),
                    label = "hero-dot-color",
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(dotWidth)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}
