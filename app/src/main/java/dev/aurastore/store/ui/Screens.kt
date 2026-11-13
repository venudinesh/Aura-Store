package dev.aurastore.store.ui

import dev.aurastore.store.data.sources.SourceId

/**
 * The page the shell is showing.
 *
 * The shell has always been state-driven rather than a navigation graph, so the route had to be read
 * back out of six independent variables before anything could animate between two of them. This is
 * that reading, kept free of Compose so the rules below — which way a transition runs, how far a
 * stagger reaches — are testable without a device.
 */
sealed interface Screen {
    /** One of the five bottom tabs. `index` is the tab's position in the bar. */
    data class Feed(val index: Int) : Screen

    /** A category term, or the picker of every term when the id is `0`. */
    data class Category(val id: Long) : Screen

    data object Settings : Screen

    /**
     * Another catalogue's page, opened from search or from the Browse tab.
     *
     * The category is the source's own handle for a term of its own grouping, and it is part of the
     * page because the page draws that term's apps rather than the source's newest ones.
     */
    data class Source(val source: SourceId, val query: String, val category: String? = null) : Screen

    /** One download row's own page. */
    data class Download(val id: String) : Screen

    /** An app's page — the deepest route, and the one everything else opens. */
    data class Detail(val postId: Long) : Screen
}

/**
 * How deep a page sits, which is what decides a push from a pop.
 *
 * Only the tabs are depth `0`: everything else was reached from one. An app's page is deeper than the
 * list it can be opened from (`1`), so moving between a category and an app reads as a push the same
 * way two tabs sliding past each other does not.
 */
fun Screen.depth(): Int = when (this) {
    is Screen.Feed -> 0
    is Screen.Category, Screen.Settings, is Screen.Source, is Screen.Download -> 1
    is Screen.Detail -> 2
}

/** The direction a page transition runs in, or `STAY` when the same page simply re-renders. */
enum class Move { STAY, SLIDE_FORWARD, SLIDE_BACK, PUSH, POP }

fun moveOf(from: Screen, to: Screen): Move = when {
    from == to -> Move.STAY
    from is Screen.Feed && to is Screen.Feed ->
        if (to.index > from.index) Move.SLIDE_FORWARD else Move.SLIDE_BACK
    to.depth() >= from.depth() -> Move.PUSH
    else -> Move.POP
}

/**
 * The stagger for the nth item arriving.
 *
 * Play staggers its rails by a few frames, and stops: a list of thirty rows cannot wait thirty
 * intervals before the last one appears. So the delay saturates after [STAGGER_CAP] items, and the
 * rows below it arrive together with the six above.
 */
fun staggerDelay(index: Int, stepMs: Int = STAGGER_STEP_MS, cap: Int = STAGGER_CAP): Int =
    if (stepMs <= 0) 0 else index.coerceIn(0, cap) * stepMs

const val STAGGER_STEP_MS = 40
const val STAGGER_CAP = 5
