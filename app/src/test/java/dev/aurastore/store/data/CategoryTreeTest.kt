package dev.aurastore.store.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `v2/categories` answers a term tree, measured 22 Oct 2026: labels are WordPress HTML
 * (`Art &amp; Design`) and `count` only covers posts tagged with the term itself, so a parent looks
 * nearly empty — `Games` reports 5 while its 20 children report 13 008 between them.
 */
class CategoryTreeTest {

    private val tree = JSONObject(
        """{"count":100,"list":[{"term_id":2,"name":"Games","count":5,"parent":0,"children":[
             {"term_id":22,"name":"Art &amp; Design","count":160,"parent":2,"children":[]},
             {"term_id":4,"name":"Action","count":1846,"parent":2,"children":[]},
             {"term_id":8617,"name":"Visual Novel","count":0,"parent":2,"children":[]}
           ]},
           {"term_id":9569,"name":"Desporto","count":0,"parent":0,"children":[]}]}""",
    )

    @Test
    fun `decodes the wordpress entities in term names`() {
        val games = Category.parseList(tree).first()
        assertEquals("Art & Design", games.children.first().name)
    }

    @Test
    fun `counts a parent term across its whole subtree`() {
        val games = Category.parseList(tree).first()
        assertEquals(5, games.count)
        assertEquals(2011, games.total)
        // A leaf's total is its own count; the empty term stays empty.
        assertEquals(1846, games.children[1].total)
        assertEquals(0, Category.parseList(tree)[1].total)
    }
}
