package dev.aurastore.store.ui.feed

import dev.aurastore.store.data.Api
import dev.aurastore.store.data.Category
import dev.aurastore.store.data.Module
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The one place a module tree becomes rails of cards.
 *
 * Home tabs, a category's own page and a sub-category all answer with the same module structure, so
 * the fetch shape lives here once: `tab` wrappers are replaced by the `tab_item` orderings they
 * contain, only `three_up` modules render a rail, and the requests go out in small batches so a
 * page with many rails still paints the first ones quickly.
 */
internal suspend fun railsFromModules(
    modules: List<Module>,
    fallbackTitle: String,
    limit: Int = 8,
    batchSize: Int = 4,
): List<Rail> {
    val wanted = flattenTabs(modules).filter { it.type == "three_up" }.take(limit)
    if (wanted.isEmpty()) return emptyList()
    val rails = mutableListOf<Rail>()
    for (chunk in wanted.chunked(batchSize)) {
        rails += coroutineScope {
            chunk.map { module -> async { railFor(module, fallbackTitle) } }.awaitAll()
        }
    }
    // Two modules in the same tree can carry the same title, and rail titles become list keys.
    return rails.filter { it.posts.isNotEmpty() }.distinctBy { it.title }
}

/**
 * The first page of one module.
 *
 * The query is kept on the rail because a rail that runs out of cards mid-swipe asks for its own next
 * page, and the only thing that says which ordering it is is the parameters it was made from.
 */
internal suspend fun railFor(module: Module, fallbackTitle: String): Rail {
    val query = module.toPostQuery()
    val result = runCatching { Api.postPage(query, 1) }.getOrNull()
    return Rail(
        title = module.title.orEmpty().ifBlank { fallbackTitle },
        posts = result?.items.orEmpty(),
        query = query,
        page = result?.page ?: 1,
        hasMore = result?.hasMore ?: false,
    )
}

/** `tab` wrappers are not rails themselves; their children are the orderings the catalogue offers. */
internal fun flattenTabs(modules: List<Module>): List<Module> = modules.flatMap { module ->
    if (module.type == "tab" && module.items.isNotEmpty()) module.items else listOf(module)
}

/**
 * The categories a thin tab fills with: biggest first, never repeating a rail the tree already showed.
 *
 * The catalogue's `App` tab ships a single `three_up` module and the orderings, so the tab was one row
 * of cards over a long list while `Game` had the promo carousel above it. Its own sub-categories carry
 * the posts and are the same query shape a module is (`taxonomy` / `tax_ids`), so they can be rails —
 * the catalogue's data, not a client-side invention.
 */
internal fun fillCategories(children: List<Category>, taken: List<String>, limit: Int = 4): List<Category> =
    children
        .filter { child -> child.count > 0 && taken.none { title -> title.equals(child.name, true) } }
        .sortedByDescending { child -> child.count }
        // The catalogue lists two `Dating` terms and two `Food & Drink` ones; one rail per name.
        .distinctBy { child -> child.name.lowercase() }
        .take(limit)

/** One category term as a rail, read through the same query a module would have carried. */
internal suspend fun railForCategory(category: Category, limit: Int = 8): Rail = railFor(
    Module(
        title = category.name,
        type = "three_up",
        contentType = null,
        banner = null,
        params = mapOf(
            "taxonomy" to "category",
            "tax_ids" to category.id.toString(),
            "order_by" to "post_views",
            "limit" to limit.toString(),
        ),
        items = emptyList(),
    ),
    category.name,
)
