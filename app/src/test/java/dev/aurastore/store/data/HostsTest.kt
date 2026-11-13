package dev.aurastore.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64

/**
 * The catalogue signs its own storage links (`?token=` in `site.js`) and its storage domains want a
 * `Referer` as well; without both, `download.liteapks.dev` answers 403 for every path. Both
 * behaviours are reproduced in [Hosts], so both are pinned here.
 */
class HostsTest {

    @Test
    fun `reads the host and ignores malformed urls`() {
        assertEquals("download.liteapks.dev", Hosts.hostOf("https://download.liteapks.dev/a/b.apk"))
        assertEquals("sdk.appsupload.com", Hosts.hostOf("https://SDK.appsupload.com/a.apk"))
        assertEquals("download.liteapks.dev", Hosts.hostOf("https://download.liteapks.dev:443/a/b.apk"))
        assertNull(Hosts.hostOf("not a url"))
        assertNull(Hosts.hostOf("/relative/path.apk"))
    }

    @Test
    fun `reads hosts out of links with literal spaces, which the catalogue ships`() {
        // Real link: spaces in the path made java.net.URI throw, which silently disabled both the
        // token and the referer — the cause of a 403 that looked like a dead CDN.
        val url = "https://download.liteapks.dev/A Blade for the Emperor/a-blade-1.0.3-mod.apk"
        assertEquals("download.liteapks.dev", Hosts.hostOf(url))
        assertEquals("https://liteapks.com/", Hosts.refererFor(url))
        assertTrue(Hosts.signed(url, 1_700_000_000_000L).contains("?token="))
    }

    @Test
    fun `no host is assumed dead — a refusal is learned at runtime`() {
        // The catalogue's own storage domain is *not* offline: it needs a token and a referer.
        assertFalse(Hosts.isBlocked("https://download.liteapks.dev/TikTok/v1/x.apk"))
        assertFalse(Hosts.isBlocked("https://sdk.appsupload.com/bj/TikTok/x.apk"))
    }

    @Test
    fun `a runtime refusal marks a host, and a success clears it`() {
        val url = "https://mirror.example.org/app.apk"
        assertFalse(Hosts.isBlocked(url))
        Hosts.markBlocked(url)
        assertTrue(Hosts.isBlocked(url))
        Hosts.markServing(url)
        assertFalse(Hosts.isBlocked(url))
    }

    @Test
    fun `storage hosts get the referer their site expects`() {
        // Verified against the live hosts: without this header `download.liteapks.dev` answers
        // 403 "Access is not allowed.", with it 206 and real APK bytes.
        assertEquals(
            "https://liteapks.com/",
            Hosts.refererFor("https://download.liteapks.dev/TikTok/v1/x.apk"),
        )
        assertEquals("https://9mod.com/", Hosts.refererFor("https://cloud.9mod.space/a/b.apk?token=x"))
        assertEquals("https://getmodsapk.com/", Hosts.refererFor("https://files.5modapk.com/a/b.apk"))
        assertEquals("https://an1.com/", Hosts.refererFor("https://files.an1.co/game.apk"))
        // 5play serves from a signed R2 bucket that does not check one, as does appsupload.
        assertNull(Hosts.refererFor("https://sdk.appsupload.com/bj/TikTok/x.apk"))
        assertNull(Hosts.refererFor("https://bucket.r2.cloudflarestorage.com/5play/x.apk"))
    }

    @Test
    fun `signs catalogue links the way the download page does`() {
        val now = 1_700_000_000_000L
        val signed = Hosts.signed("https://download.liteapks.dev/TikTok/v1/x.apk", now)
        assertTrue(signed.startsWith("https://download.liteapks.dev/TikTok/v1/x.apk?token="))

        val token = URLDecoder.decode(signed.substringAfter("?token="), "UTF-8")
        // btoa(btoa(seconds)) -> decode twice to get the epoch second the link is valid for.
        val once = String(Base64.getDecoder().decode(token))
        val ttl = String(Base64.getDecoder().decode(once)).toLong()
        assertEquals(now / 1000 + Hosts.TOKEN_LIFETIME_SECONDS, ttl)
    }

    @Test
    fun `keeps existing query parameters and never signs twice`() {
        val withQuery = Hosts.signed("https://download.liteapks.dev/a.apk?x=1", 1_700_000_000_000L)
        assertTrue(withQuery.startsWith("https://download.liteapks.dev/a.apk?x=1&token="))
        assertEquals(withQuery, Hosts.signed(withQuery, 1_700_000_000_000L))
    }

    @Test
    fun `signs 9mod links that arrive unsigned`() {
        // Measured 22 Oct 2026: the LiteAPKs catalogue ships a bare cloud.9mod.space link. With only
        // the 9mod referer it answers 403 "Access is not allowed."; with token + referer it serves
        // 200 and the whole 21.1 MB. 9MOD's own source mints links that already carry a token.
        val bare = "https://cloud.9mod.space/Socket Tool/socket-tool.apk"
        assertTrue(Hosts.needsToken(bare))
        val signed = Hosts.signed(bare, 1_700_000_000_000L)
        assertTrue(signed.startsWith(Hosts.canonical(bare) + "?token="))
        assertEquals(signed, Hosts.signed(signed, 1_700_000_000_000L))

        val minted = "https://cloud.9mod.space/a/b.apk?token=already"
        assertEquals(minted, Hosts.signed(minted))
    }

    @Test
    fun `leaves other storage domains untouched`() {
        val url = "https://sdk.appsupload.com/bj/TikTok/x.apk"
        assertEquals(url, Hosts.signed(url))
        // Public and range-capable, and they check neither header.
        for (public in listOf("https://gp4.liteapks.com/Sugar Rush/sugar.apk",
                              "https://easyplaytv.sh/easyplay.apk")) {
            assertFalse(Hosts.needsToken(public))
            assertNull(Hosts.refererFor(public))
            // Public links keep their shape; only a character no parser accepts is encoded.
            assertEquals(Hosts.canonical(public), Hosts.signed(public))
        }
    }

    @Test
    fun `encodes the characters a strict parser refuses, once`() {
        // Measured Nov 2026: the catalogue ships game paths with literal spaces, and those links
        // failed while their encoded twins returned the whole file.
        val raw = "https://download.liteapks.dev/Subway Surfers/subway.apk"
        assertEquals(
            "https://download.liteapks.dev/Subway%20Surfers/subway.apk",
            Hosts.canonical(raw),
        )
        // An existing escape is copied through, never doubled.
        val encoded = "https://download.liteapks.dev/Subway%20Surfers/subway.apk"
        assertEquals(encoded, Hosts.canonical(encoded))
        // A non-ASCII name leaves as its UTF-8 bytes instead of reaching the socket unencoded.
        assertEquals(
            "https://gp4.liteapks.com/a/%F0%9F%8E%AE.apk",
            Hosts.canonical("https://gp4.liteapks.com/a/\uD83C\uDFAE.apk"),
        )
    }

    @Test
    fun `a signed link is signed in its final, encoded form`() {
        val signed = Hosts.signed(
            "https://download.liteapks.dev/Subway Surfers/subway.apk",
            1_700_000_000_000L,
        )
        assertTrue(signed.startsWith("https://download.liteapks.dev/Subway%20Surfers/subway.apk?token="))
        assertFalse(signed.contains(' '))
    }

    @Test
    fun `offers sibling storage authorities for the catalogue's own hosts`() {
        val dead = "https://gp.liteapks.com/Subway Surfers/subway.apk"
        assertEquals(
            listOf("https://gp4.liteapks.com/Subway Surfers/subway.apk"),
            Hosts.mirrorsFor(dead).take(1),
        )
        // The authority already in use is not a mirror of itself, and a tried one is not offered again.
        assertFalse(Hosts.mirrorsFor(dead).any { Hosts.hostOf(it) == "gp.liteapks.com" })
        val gp4 = "https://gp4.liteapks.com/Subway Surfers/subway.apk"
        assertTrue(Hosts.mirrorsFor(dead, setOf(gp4)).none { Hosts.hostOf(it) == "gp4.liteapks.com" })
        // A third-party bucket has no sibling, and inventing one would be inventing a URL.
        assertTrue(Hosts.mirrorsFor("https://sdk.appsupload.com/bj/TikTok/x.apk").isEmpty())
    }

    @Test
    fun `a host whose name does not resolve is skipped like a refusing one`() {
        val gone = "https://no-such-authority.example.org/a.apk"
        assertFalse(Hosts.isBlocked(gone))
        Hosts.markDead(gone)
        assertTrue(Hosts.isDead(gone))
        assertTrue(Hosts.isBlocked(gone))
        // Serving again clears both verdicts.
        Hosts.markServing(gone)
        assertFalse(Hosts.isDead(gone))
        assertFalse(Hosts.isBlocked(gone))
    }
}
