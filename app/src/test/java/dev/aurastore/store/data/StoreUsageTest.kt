package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The two pure rules the screen-time page leans on: the duration wording and the row order. */
class StoreUsageTest {

    @Test
    fun `a duration reads in the unit a person uses`() {
        assertEquals("0m", StoreUsage.formatDuration(0L))
        assertEquals("0m", StoreUsage.formatDuration(-1L))
        assertEquals("<1m", StoreUsage.formatDuration(1L))
        assertEquals("<1m", StoreUsage.formatDuration(59_999L))
        assertEquals("1m", StoreUsage.formatDuration(60_000L))
        assertEquals("59m", StoreUsage.formatDuration(59 * 60_000L))
        assertEquals("1h", StoreUsage.formatDuration(60 * 60_000L))
        assertEquals("1h 5m", StoreUsage.formatDuration(65 * 60_000L))
        assertEquals("12h 30m", StoreUsage.formatDuration(750 * 60_000L))
    }

    @Test
    fun `the most used today leads, then the longest total, then the name`() {
        val rows = listOf(
            entry("zeta", today = 60_000L, total = 120_000L),
            entry("beta", today = 60_000L, total = 300_000L),
            entry("alpha", today = 0L, total = 500_000L),
            entry("Alpha", today = 60_000L, total = 120_000L),
        )

        val sorted = StoreUsage.sortRows(rows)

        assertEquals(listOf("beta", "Alpha", "zeta", "alpha"), sorted.map { it.label })
    }

    @Test
    fun `an app with no stats keeps its zero row`() {
        val sorted = StoreUsage.sortRows(listOf(entry("quiet", today = 0L, total = 0L)))

        assertEquals(1, sorted.size)
        assertEquals(0L, sorted.single().todayMs)
        assertEquals(0L, sorted.single().totalMs)
    }

    private fun entry(label: String, today: Long, total: Long) = StoreUsageEntry(
        packageName = "com.example.${label.lowercase()}",
        label = label,
        todayMs = today,
        totalMs = total,
        lastUsedAt = 0L,
    )
}
