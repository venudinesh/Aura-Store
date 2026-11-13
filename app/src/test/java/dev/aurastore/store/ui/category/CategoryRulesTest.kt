package dev.aurastore.store.ui.category

import dev.aurastore.store.data.Module
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a category screen is built on: the query that lists one term, the orderings the term's own
 * module tree offers, and the page a module's rail asks for.
 *
 * The tree below is the shape `v2/categories/9` ("Casual") answers with on the live catalogue —
 * including the `tab_item` that carries no `order_by`, which is the site's default listing.
 */
class CategoryRulesTest {

    private fun module(json: String) = Module.parse(JSONObject(json))

    @Test
    fun `a module's query carries the page it is asked for`() {
        val rail = module(
            """{"title":"Popular","module_type":"three_up",
                "params":{"taxonomy":"category","limit":6,"tax_ids":9,"order_by":"post_views"}}"""
        )
        assertEquals("1", rail.toPostQuery()["page"])
        // A rail's next page keeps the module's own size, which is what made the first page that length.
        val second = rail.toPostQuery(page = 2)
        assertEquals("2", second["page"])
        assertEquals("6", second["limit"])
        assertEquals("9", second["tax_ids"])
    }

    @Test
    fun `the list query names the term and nothing else`() {
        val query = listQuery("post_views", 9L)
        assertEquals("category", query["taxonomy"])
        assertEquals("9", query["tax_ids"])
        assertEquals("post_views", query["order_by"])
        // A page and its size are put on by Api.postPage, which is what asks for the next one.
        assertTrue(query["limit"].isNullOrBlank())
        assertTrue(query["page"].isNullOrBlank())
    }

    @Test
    fun `an ordering the catalogue left blank is sent as blank`() {
        // Api drops blank parameters, so "New" becomes the server's own default rather than a guess.
        assertTrue(listQuery("", 9L)["order_by"].isNullOrBlank())
    }

    @Test
    fun `the catalogue's own ordering labels are kept`() {
        val tree = listOf(
            module("""{"title":"Popular","module_type":"three_up",
                "params":{"taxonomy":"category","limit":6,"tax_ids":9,"order_by":"post_views"}}"""),
            module("""{"title":"","module_type":"tab","content":[
                {"title":"Latest Updates","module_type":"tab_item",
                 "params":{"taxonomy":"category","tax_ids":9,"order_by":"modified"}},
                {"title":"Popular","module_type":"tab_item",
                 "params":{"taxonomy":"category","tax_ids":9,"order_by":"post_views"}},
                {"title":"New","module_type":"tab_item","params":{"taxonomy":"category","tax_ids":9}},
                {"title":"Popular","module_type":"tab_item",
                 "params":{"taxonomy":"category","tax_ids":9,"order_by":"post_views"}}
            ]}"""),
        )

        val orderings = orderingsFrom(tree)
        assertEquals(listOf("modified", "post_views", ""), orderings.map { it.orderBy })
        assertEquals(listOf("Latest Updates", "Popular", "New"), orderings.map { it.label })
    }

    @Test
    fun `a tree without orderings leaves the list of orderings empty`() {
        val tree = listOf(module("""{"title":"Popular","module_type":"three_up","params":{"order_by":"post_views"}}"""))
        assertTrue(orderingsFrom(tree).isEmpty())
    }
}
