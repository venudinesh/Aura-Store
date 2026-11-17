package dev.aurastore.store.ui

import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import dev.aurastore.store.ui.settings.SettingsSection

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
     * One category of Settings, opened from the hub.
     *
     * Settings used to be a single form long enough that the bottom of it was a scroll away from the
     * top. A category is the same rows with a boundary around them, and it is a route rather than a
     * fold so back returns to the hub instead of to whichever tab was underneath.
     */
    data class SettingsGroup(val section: SettingsSection) : Route

    /**
     * The queue, as a page of its own.
     *
     * It used to be the tail of the You tab, which meant the brand bar's download badge answered a
     * tap by switching tabs — a button that said "downloads" took the user to a place called You.
     * The queue is a place; this is its route.
     */
    data object Downloads : Route

    /** The catalogue updates this phone has waiting. */
    data object Manage : Route

    /** The apps installed on this phone, as a page rather than a segment of the You tab. */
    data object Device : Route

    /** What this client verifies, on its own page instead of a dialog taller than the screen. */
    data object About : Route

    /**
     * Everything the heart saved.
     *
     * It used to be the sixth tab. A whole tab for an empty list most days, next to You — where the
     * same account already keeps its downloads and its installed apps — made the bar wider than the
     * store underneath it. The list is a place reached from You, so it is a route.
     */
    data object Wishlist : Route

    /**
     * One source's own page.
     *
     * The category is the source's own handle for a term, so it is carried as a string: the id means
     * something different on every site (a term id on one, a slug on another), and the page resolves
     * it against the categories that source publishes.
     */
    data class Source(val source: SourceId, val category: String? = null) : Route

    /**
     * One app's page inside a source.
     *
     * The row itself travels: its key is the page the source will be asked for, and the title and
     * icon are what the header draws before that page answers. Carrying them keeps the route a plain
     * value rather than a promise that the source's list is still in memory after process death.
     */
    data class SourcePage(val app: SourceApp) : Route
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
    is Route.SettingsGroup -> Screen.SettingsGroup(section)
    Route.Downloads -> Screen.Downloads
    Route.Manage -> Screen.Manage
    Route.Device -> Screen.Device
    Route.About -> Screen.About
    Route.Wishlist -> Screen.Wishlist
    is Route.Source -> Screen.Source(source, query, category)
    is Route.SourcePage -> Screen.SourcePage(app)
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
    is Route.SettingsGroup -> "settings-group$SEP${route.section.name}"
    Route.Downloads -> "downloads"
    Route.Manage -> "manage"
    Route.Device -> "device"
    Route.About -> "about"
    Route.Wishlist -> "wishlist"
    is Route.Source -> "source$SEP${route.source.name}$SEP${route.category.orEmpty()}"
    is Route.SourcePage -> listOf(
        "source-page",
        route.app.source.name,
        route.app.key,
        route.app.title,
        route.app.iconUrl.orEmpty(),
        route.app.version.orEmpty(),
        route.app.size.orEmpty(),
        route.app.subtitle.orEmpty(),
        route.app.pageUrl.orEmpty(),
        route.app.cataloguePostId?.toString().orEmpty(),
    ).joinToString(SEP)
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
        "settings-group" -> parts.getOrNull(1)
            ?.let { name -> runCatching { SettingsSection.valueOf(name) }.getOrNull() }
            ?.let(Route::SettingsGroup)
        "downloads" -> Route.Downloads
        "manage" -> Route.Manage
        "device" -> Route.Device
        "about" -> Route.About
        "wishlist" -> Route.Wishlist
        "source" -> parts.getOrNull(1)
            ?.let { name -> runCatching { SourceId.valueOf(name) }.getOrNull() }
            ?.let { source ->
                Route.Source(source, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
            }
        "source-page" -> {
            val source = parts.getOrNull(1)
                ?.let { name -> runCatching { SourceId.valueOf(name) }.getOrNull() }
            val key = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
            if (source == null || key == null) {
                null
            } else {
                Route.SourcePage(
                    SourceApp(
                        source = source,
                        key = key,
                        title = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: key,
                        version = parts.getOrNull(5)?.takeIf { it.isNotBlank() },
                        size = parts.getOrNull(6)?.takeIf { it.isNotBlank() },
                        subtitle = parts.getOrNull(7)?.takeIf { it.isNotBlank() },
                        iconUrl = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                        pageUrl = parts.getOrNull(8)?.takeIf { it.isNotBlank() },
                        cataloguePostId = parts.getOrNull(9)?.toLongOrNull(),
                    ),
                )
            }
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
