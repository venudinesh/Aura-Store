package dev.aurastore.store.install

import dev.aurastore.store.data.LibraryEntry
import dev.aurastore.store.data.PostSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The package→listing lookup is a search dressed as a lookup, so its two rules matter: only the
 * listing that names this exact package may answer, and an answer has to expire. Both are pinned here
 * against the shapes the live `search` endpoint returns.
 */
class PackageLookupTest {

    private fun post(id: Long, title: String, version: String?, playPackage: String?) = PostSummary.parse(
        JSONObject()
            .put("id", id)
            .put("title", title)
            .put("lastest_version", version ?: JSONObject.NULL)
            .put(
                "original_download_url",
                playPackage?.let { "https://play.google.com/store/apps/details?id=$it" } ?: "",
            ),
    )

    @Test
    fun `only the listing that names this package answers`() {
        // What a search for "Spotify" really comes back with: the app, a lite build of it, and
        // unrelated tools. Only one of them *is* com.spotify.music.
        val posts = listOf(
            post(98, "Spotify", "9.1.94.1912", "com.spotify.music"),
            post(80174, "Spotify Lite", "2.0", "com.spotify.lite"),
            post(810959, "Spotify Playlist Downloader", "1.10.3", "com.spot.playlist"),
            post(700, "Something with no listing", "1.0", null),
        )
        val found = PackageLookup.pick(posts, "com.spotify.music", now = 1_700_000_000_000L)
        assertEquals(98L, found?.postId)
        assertEquals("9.1.94.1912", found?.originalVersion)
        assertEquals(1_700_000_000_000L, found?.checkedAt)
        assertNull(PackageLookup.pick(posts, "com.spotify.premium", now = 0L))
    }

    @Test
    fun `the newest listing for one package wins, then the newer post`() {
        val posts = listOf(
            post(519765, "Spotify MOD", "9.1.90", "com.spotify.music"),
            post(98, "Spotify", "9.1.94.1912", "com.spotify.music"),
        )
        assertEquals(98L, PackageLookup.pick(posts, "com.spotify.music", now = 0L)?.postId)
        // Same version on both: the later post is the one that was published against it.
        val tied = listOf(
            post(98, "Spotify", "9.1.94", "com.spotify.music"),
            post(519765, "Spotify MOD", "9.1.94", "com.spotify.music"),
        )
        assertEquals(519765L, PackageLookup.pick(tied, "com.spotify.music", now = 0L)?.postId)
    }

    @Test
    fun `a listing with no version still matches but publishes nothing`() {
        val found = PackageLookup.pick(
            listOf(post(98, "Spotify", null, "com.spotify.music")),
            "com.spotify.music",
            now = 5L,
        )
        assertEquals(98L, found?.postId)
        assertNull(found?.originalVersion)
        // which is the pair AutoUpdater refuses to call an update
        assertFalse(Updates.isNewer("9.1.94", found?.originalVersion))
    }

    @Test
    fun `a verdict lasts a day and then asks again`() {
        val day = PackageLookup.TTL_MS
        assertFalse(PackageLookup.isStale(Lookup(98L, "9.1.94", 1_000L), now = 1_000L + day - 1))
        assertTrue(PackageLookup.isStale(Lookup(98L, "9.1.94", 1_000L), now = 1_000L + day))
        assertTrue(PackageLookup.isStale(null, now = 1_000L))
    }

    @Test
    fun `a searched miss is an answer and is not re-asked until it ages out`() {
        val miss = Lookup(postId = null, originalVersion = null, checkedAt = 1_000L)
        assertFalse(PackageLookup.isStale(miss, now = 1_000L + PackageLookup.TTL_MS / 2))
        assertTrue(PackageLookup.isStale(miss, now = 1_000L + PackageLookup.TTL_MS))
        // A record written before any check ran carries no timestamp, so it is never usable.
        assertTrue(PackageLookup.isStale(Lookup(null, null, 0L), now = Long.MAX_VALUE / 2))
    }

    @Test
    fun `one pass asks for the stale apps and stops at the bound`() {
        val now = 1_700_000_000_000L
        val apps = (0 until 20).map { app(it) }
        // Two of them were answered inside the day, so they drop out; the rest queue in order.
        val cache = mapOf(
            "com.app0" to Lookup(1L, "1.0", now - 60_000L),
            "com.app1" to Lookup(null, null, now - 60_000L),
        )
        val needing = PackageLookup.needing(apps, cache, now)
        assertEquals(PackageLookup.PER_PASS, needing.size)
        assertEquals(listOf("com.app2", "com.app3"), needing.take(2).map { it.packageName })
        assertTrue(needing.none { it.packageName in cache })
        // A manual refresh ignores both the cache and the order, but not the bound.
        assertEquals(PackageLookup.PER_PASS, PackageLookup.needing(apps, cache, now, force = true).size)
    }

    @Test
    fun `coverage separates a searched miss from an app not asked yet`() {
        val now = 1_700_000_000_000L
        val apps = listOf(app(0), app(1), app(2), app(3))
        val cache = mapOf(
            "com.app0" to Lookup(98L, "9.1.94", now - 60_000L),
            "com.app1" to Lookup(null, null, now - 60_000L),
            // An answer left behind by an app that is long gone says nothing about this device.
            "com.uninstalled" to Lookup(5L, "1.0", now - 60_000L),
        )
        val coverage = PackageLookup.coverage(apps, cache)
        assertEquals(4, coverage.total)
        assertEquals(2, coverage.answered)
        assertEquals(1, coverage.matched)
        assertEquals(2, coverage.pending)
        assertEquals(1, coverage.missed)
        // Nothing answered yet, and nothing over-answered.
        val none = PackageLookup.coverage(apps, emptyMap())
        assertEquals(0, none.answered)
        assertEquals(4, none.pending)
        assertEquals(0, none.missed)
        assertEquals(0, PackageLookup.coverage(emptyList(), cache).total)
    }

    @Test
    fun `a pass that fetched nothing still answers from the cache`() {
        val now = 1_700_000_000_000L
        val apps = listOf(app(0), app(1), app(2))
        // The second visit of the day asks for nothing: every app was answered inside the TTL. If the
        // pass returned only what it fetched, the matched updates would render once and then vanish.
        val cache = mapOf(
            "com.app0" to Lookup(98L, "9.1.94", now - 60_000L),
            "com.app1" to Lookup(null, null, now - 60_000L),
        )
        val answers = PackageLookup.answers(apps, cache, emptyMap())
        assertEquals(setOf("com.app0", "com.app1"), answers.keys)
        assertEquals(98L, answers["com.app0"]?.postId)
        // An app the cache never heard of stays absent — the caller has no verdict to draw, so it
        // cannot be compared and does not reach the screen.
        assertNull(answers["com.app2"])
    }

    @Test
    fun `what this pass fetched beats the older cached answer`() {
        val now = 1_700_000_000_000L
        val apps = listOf(app(0), app(1))
        val cache = mapOf(
            "com.app0" to Lookup(98L, "9.1.94", now - 2 * PackageLookup.TTL_MS),
            "com.app1" to Lookup(12L, "3.0", now - 2 * PackageLookup.TTL_MS),
        )
        val answers = PackageLookup.answers(
            apps,
            cache,
            mapOf(
                // Re-asked and now a searched miss: the new answer wins, the old listing does not linger.
                "com.app0" to Lookup(null, null, now),
                // Re-asked and the request failed with nothing newer to fall back on: keep the cache.
                "com.app1" to null,
            ),
        )
        assertNull(answers["com.app0"]?.postId)
        assertEquals(12L, answers["com.app1"]?.postId)
    }

    private fun app(index: Int) = LibraryEntry(
        packageName = "com.app$index",
        label = "App $index",
        versionName = "1.0",
        versionCode = 1L,
        isSystem = false,
        hasLaunchIntent = true,
        postId = null,
        installedLabel = null,
        knownSigner = null,
        lastUpdateAt = 0L,
    )

    @Test
    fun `a phone label is asked whole, then by its head`() {
        assertEquals(
            listOf("Truecaller: Caller ID & Block", "Truecaller"),
            PackageLookup.searchTerms("Truecaller: Caller ID & Block"),
        )
        assertEquals(
            listOf("Amazon Prime Video (Beta)", "Amazon Prime Video"),
            PackageLookup.searchTerms("Amazon Prime Video (Beta)"),
        )
        assertEquals(
            listOf("Spotify - Music and Podcasts", "Spotify"),
            PackageLookup.searchTerms("Spotify - Music and Podcasts"),
        )
    }

    @Test
    fun `a label with nothing to trim is asked once`() {
        assertEquals(listOf("1.1.1.1"), PackageLookup.searchTerms("1.1.1.1"))
        // A head of one character would match half the catalogue; the full label is the safer question.
        assertEquals(listOf("W: Widgets"), PackageLookup.searchTerms("W: Widgets"))
        assertEquals(emptyList<String>(), PackageLookup.searchTerms("   "))
    }
}
