package dev.aurastore.store.data

/**
 * The display order of the navigation bar's five tabs.
 *
 * A strip is a permutation of `0 until COUNT`, stored as screen indices rather than as positions, so a
 * reorder never changes which screen a tab opens — only where it sits. Everything that reads the list
 * goes through [sanitize] first: a pref written by a crash, a hand-edited file or a future build with
 * six tabs must never leave the bar with a missing or duplicated tab, which would either crash an index
 * or make a screen unreachable.
 */
object NavOrder {

    /** The five tabs this build has. */
    const val COUNT = 5

    /** The order a fresh install draws: the order of the screens themselves. */
    val DEFAULT: List<Int> = (0 until COUNT).toList()

    /** Read a stored value, keeping only known indices, dropping repeats and appending the rest. */
    fun sanitize(stored: String?): List<Int> =
        sanitize(stored?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty())

    fun sanitize(values: List<Int>): List<Int> {
        val seen = LinkedHashSet<Int>(values.size)
        for (value in values) if (value in 0 until COUNT) seen += value
        for (value in 0 until COUNT) seen += value
        return seen.toList()
    }

    fun join(order: List<Int>): String = sanitize(order).joinToString(",")

    /**
     * Move the item at [from] to [to], leaving the rest in their own order.
     *
     * Used by the drag and by its test: an index outside either end is a no-op rather than a clamp,
     * because a drag that ends past the strip has already been resolved to a valid slot by the row.
     */
    fun move(order: List<Int>, from: Int, to: Int): List<Int> {
        val base = sanitize(order)
        if (from !in base.indices || to !in base.indices || from == to) return base
        return base.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Where a screen sits in the strip, or -1 if the strip is somehow missing it. */
    fun positionOf(order: List<Int>, screen: Int): Int = sanitize(order).indexOf(screen)
}
