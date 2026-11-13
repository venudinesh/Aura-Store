package dev.aurastore.store.ui.feed

import dev.aurastore.store.data.Category
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Apps tab fills from its own term's children, so the pick has to be stable: biggest first, nothing
 * empty, nothing the module tree already showed by that name.
 */
class AppFillTest {

    private fun category(id: Long, name: String, count: Int) = Category(
        id = id,
        name = name,
        slug = null,
        parent = 0L,
        count = count,
        children = emptyList(),
    )

    @Test
    fun `the biggest categories fill first and an empty term never does`() {
        val fills = fillCategories(
            children = listOf(
                category(1, "Tools", 1257),
                category(2, "Social", 98),
                category(3, "Empty", 0),
                category(4, "Photography", 433),
            ),
            taken = emptyList(),
        )
        assertEquals(listOf("Tools", "Photography", "Social"), fills.map { it.name })
    }

    @Test
    fun `a rail the tree already showed is not repeated`() {
        val fills = fillCategories(
            children = listOf(category(1, "Popular", 500), category(2, "Tools", 20)),
            taken = listOf("popular"),
        )
        assertEquals(listOf("Tools"), fills.map { it.name })
    }

    @Test
    fun `a term the catalogue lists twice fills once, with the bigger count`() {
        val fills = fillCategories(
            children = listOf(category(1, "Dating", 1), category(2, "Dating", 40)),
            taken = emptyList(),
        )
        assertEquals(1, fills.size)
        assertEquals(2L, fills.single().id)
    }

    @Test
    fun `the fill stops at its limit`() {
        val children = (1..6).map { category(it.toLong(), "C$it", it * 10) }
        assertEquals(4, fillCategories(children, emptyList()).size)
        assertEquals(listOf("C6", "C5"), fillCategories(children, emptyList(), limit = 2).map { it.name })
    }
}
