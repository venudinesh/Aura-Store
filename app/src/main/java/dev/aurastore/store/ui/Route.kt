package dev.aurastore.store.ui

import dev.aurastore.store.data.sources.SourceId

/**
 * A page the shell has pushed on top of a tab.
 *
 * The shell used to route with six independent flags — `detailPost`, `categoryOpen`, `settingsOpen`,
 * `sourceOpen`, `downloadDetail` — and each one was cleared by whoever opened the next. That is what
 * made back feel broken: opening an app from a category cleared the category, so the first back went
 * to the tab instead of the list, and the second back left the app. A stack is the only shape that
 * answers "where was I" correctly, because the answer is the whole list, not one flag.
 *
 * Tabs are deliberately *not* routes. They are the floor the stack sits on, and a tab is switched by
 * the bar rather than pushed, which is why back from a non-first tab returns to the first tab instead
 * of unwinding a history nobody asked for.
 */
sealed interface Route {
    data class Detail(val postId: Long) : Route
    data class Download(val id: String) : Route
    data class Category(val id: Long) : Route
    data object Settings : Route

    /**
     * One source's own page.
     *
     * The category is the source's own handle for a term, so it is carried as a string: the id means
     * something different on every site (a term id on one, a slug on another), and the page resolves
     * it against the categories that source publishes.
     */
    data class Source(val source: SourceId, val category: String? = null) : Route
}

/**
 * The page a route draws, which is what the shell's transition animates between.
 *
 * [Screen.Source] carries the query because the source screen needs it; the stack does not, because a
 * query is a property of the visit rather than of the position in history.
 */
fun Route.toScreen(query: String = ""): Screen = when (this) {
    is Route.Detail -> Screen.Detail(postId)
    is Route.Download -> Screen.Download(id)
    is Route.Category -> Screen.Category(id)
    Route.Settings -> Screen.Settings
    is Route.Source -> Screen.Source(source, query, category)
}

/**
 * A route as one string, so the stack survives process death through `rememberSaveable`.
 *
 * The separator is a character no id can contain: post ids and category ids are numbers, a download
 * id is a UUID, and a source is an enum name. Encoding rather than a custom `Saver` keeps the stack a
 * plain `List<String>` in the bundle, which is the one shape that always restores.
 */
private const val SEP = "\u0001"

fun encodeRoute(route: Route): String = when (route) {
    is Route.Detail -> "detail$SEP${route.postId}"
    is Route.Download -> "download$SEP${route.id}"
    is Route.Category -> "category$SEP${route.id}"
    Route.Settings -> "settings"
    is Route.Source -> "source$SEP${route.source.name}$SEP${route.category.orEmpty()}"
}

/**
 * The route [encoded] names, or null when it is unreadable.
 *
 * Null rather than a fallback: a stack entry this build cannot draw is dropped, which lands the user on
 * the page below it. Inventing a route would open something they never asked for.
 */
fun decodeRoute(encoded: String): Route? {
    val parts = encoded.split(SEP)
    return when (parts.firstOrNull()) {
        "detail" -> parts.getOrNull(1)?.toLongOrNull()?.let(Route::Detail)
        "download" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(Route::Download)
        "category" -> parts.getOrNull(1)?.toLongOrNull()?.let(Route::Category)
        "settings" -> Route.Settings
        "source" -> parts.getOrNull(1)
            ?.let { name -> runCatching { SourceId.valueOf(name) }.getOrNull() }
            ?.let { source ->
                Route.Source(source, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
        else -> null
    }
}

/**
 * Whether a push of [next] on top of [current] should replace it instead of stacking.
 *
 * Two app pages in a row is a walk through related apps, and it is a real history — back must return to
 * the previous app, so it stacks. Settings is the one route that never stacks on itself, because
 * opening it twice is not a place you can be.
 */
fun stacksOn(current: Route?, next: Route): Boolean = current != next
