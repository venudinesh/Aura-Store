package dev.aurastore.store.data.sources

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceHubTest {

    private fun app(source: SourceId, title: String, key: String = title) = SourceApp(
        source = source,
        key = key,
        title = title,
    )

    @Test
    fun `takes one row from each source before a second`() {
        val rows = interleaveNewest(
            listOf(
                listOf(app(SourceId.LITEAPKS, "A"), app(SourceId.LITEAPKS, "B")),
                listOf(app(SourceId.NINEMOD, "C"), app(SourceId.NINEMOD, "D")),
            ),
        )

        assertEquals(listOf("A", "C", "B", "D"), rows.map { it.title })
    }

    @Test
    fun `keeps a title once, from the earlier source`() {
        val rows = interleaveNewest(
            listOf(
                listOf(app(SourceId.LITEAPKS, "Roblox")),
                listOf(app(SourceId.FIVEPLAY, "Roblox"), app(SourceId.FIVEPLAY, "Minecraft")),
            ),
        )

        assertEquals(listOf("Roblox", "Minecraft"), rows.map { it.title })
        assertEquals(SourceId.LITEAPKS, rows.first().source)
    }

    @Test
    fun `stops at the per-source cap`() {
        val rows = interleaveNewest(
            listOf((1..10).map { app(SourceId.LITEAPKS, "App $it") }),
            perSource = 3,
        )

        assertEquals(3, rows.size)
    }

    @Test
    fun `an empty source does not stall the rest`() {
        val rows = interleaveNewest(
            listOf(emptyList(), listOf(app(SourceId.APKPORTS, "Shrek"))),
        )

        assertEquals(listOf("Shrek"), rows.map { it.title })
    }
}
