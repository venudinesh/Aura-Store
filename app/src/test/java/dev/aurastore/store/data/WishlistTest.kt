package dev.aurastore.store.data

import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wishlist's storage rules, without a handset.
 *
 * A wish is the only thing on the phone that has to be rebuilt from its own bytes: the feed it came
 * from is gone after a process death. These tests pin the round trip, the newest-first rule, and the
 * promise that one corrupt row cannot take the rest of the list with it.
 */
class WishlistTest {

    private val post = Wishlist.post(
        postId = 519765L,
        title = "YouTube Premium",
        iconUrl = "https://cdn.example/icon.png",
        subtitle = "Google LLC",
        size = "168 MB",
        now = 1_700_000_000_000L,
    )

    private val sourceApp = SourceApp(
        source = SourceId.GETMODSAPK,
        key = "https://getmodsapk.com/youtube-premium/",
        title = "YouTube Premium",
        version = "v21.46.522",
        size = "37 MB",
        subtitle = "Tools · Premium",
        iconUrl = "https://cdn.example/yt.png",
        pageUrl = "https://getmodsapk.com/youtube-premium/",
        cataloguePostId = 519765L,
    )

    @Test
    fun `a catalogue wish survives the round trip`() {
        val decoded = Wishlist.WishCodec.decode(Wishlist.WishCodec.encode(listOf(post)))

        assertEquals(listOf(post), decoded)
    }

    @Test
    fun `a source wish can rebuild the page it was opened from`() {
        val wish = Wishlist.source(sourceApp, now = 1L)

        val decoded = Wishlist.WishCodec.decode(Wishlist.WishCodec.encode(listOf(wish))).single()

        assertEquals(wish, decoded)
        assertEquals(sourceApp, decoded.toSourceApp())
    }

    @Test
    fun `a catalogue wish does not pretend to be a source page`() {
        assertNull(post.toSourceApp())
    }

    @Test
    fun `the newest wish sits first and the same key toggles off`() {
        val newer = post.copy(key = Wishlist.postKey(2L), postId = 2L, title = "Newer", addedAt = 2L)
        val once = Wishlist.toggled(emptyList(), post)
        val twice = Wishlist.toggled(once, newer)
        assertEquals(listOf(newer, post), twice)

        val removed = Wishlist.toggled(twice, post)
        assertEquals(listOf(newer), removed)
    }

    @Test
    fun `a post and a source app are separate wishes`() {
        // The same app can be saved from both places; each page is its own place to come back to.
        assertTrue(post.key != Wishlist.source(sourceApp).key)
    }

    @Test
    fun `a corrupt row is dropped and its neighbours are kept`() {
        val good = Wishlist.WishCodec.encode(listOf(post))
        val broken = "[42,{\"kind\":\"NOT_A_KIND\",\"key\":\"bad\",\"title\":\"bad\"}," +
            good.removePrefix("[")

        val decoded = Wishlist.WishCodec.decode(broken)

        assertEquals(listOf(post), decoded)
    }

    @Test
    fun `duplicate keys collapse to one row`() {
        val twice = Wishlist.WishCodec.encode(listOf(post, post))
        assertEquals(1, Wishlist.WishCodec.decode(twice).size)
    }

    @Test
    fun `an empty or unreadable file is an empty wishlist`() {
        assertEquals(emptyList<WishEntry>(), Wishlist.WishCodec.decode(null))
        assertEquals(emptyList<WishEntry>(), Wishlist.WishCodec.decode(""))
        assertEquals(emptyList<WishEntry>(), Wishlist.WishCodec.decode("not json"))
    }

    @Test
    fun `the row reads the source name before the app's own lines`() {
        val line = Wishlist.source(sourceApp).metaLine()
        assertEquals("GetModsAPK · Tools · Premium · v21.46.522 · 37 MB", line)
    }
}
