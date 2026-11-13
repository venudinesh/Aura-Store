package dev.aurastore.store.data

import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * How the catalogue's storage hosts authorise a download, verified against the live hosts.
 *
 * A download from `download.liteapks.dev` needs **two** things that the official Android client
 * never sends:
 *
 * 1. the signed query parameter the website's download page attaches —
 *    `?token=` + `btoa(btoa(unixSeconds + 3h))`, which is [signed];
 * 2. a `Referer` from the site itself, which is [refererFor].
 *
 * Both were established by measurement against the live host: token alone → `403 Access is not
 * allowed.`, Referer alone → `403`, token + Referer → `206` with real APK bytes. 9MOD's storage
 * domain (`cloud.9mod.space`) needs the same **pair** and accepts either site's Referer — so a
 * LiteAPKs link that happens to point there has to be signed too, not merely referred.
 *
 * A refusal is still remembered for the session so the storefront can route a file it cannot fetch
 * to another build of the same app ([dev.aurastore.store.install.QuickInstall.alternatives]); a
 * completed transfer clears the mark again.
 */
object Hosts {

    /**
     * Download host → the page a request must appear to come from. Storage hosts that check the
     * referer return `403 Access is not allowed.` without it.
     */
    private val referers = mapOf(
        "liteapks.dev" to "https://liteapks.com/",
        "9mod.space" to "https://9mod.com/",
        "5modapk.com" to "https://getmodsapk.com/",
        "an1.co" to "https://an1.com/",
        "an1.net" to "https://an1.com/",
        "apkvision.org" to "https://apkvision.org/",
    )

    /** Hosts observed refusing a request even when it is correctly signed. */
    private val blocked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Hosts whose name does not resolve at all.
     *
     * Kept apart from [blocked] only so a DNS failure can be logged as what it is; both are skipped
     * by the same checks, because spending a request on either is spending it on nothing. The
     * catalogue leaves dead storage authorities in its posts for months — `gp.liteapks.com` is
     * NXDOMAIN while its files still sit on `gp4.liteapks.com` under the same path.
     */
    private val dead: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * `host` of [url] in lower case, or null when the URL is not absolute.
     *
     * Parsed by hand rather than with [URI]: the catalogue's links contain literal spaces
     * (`…/A Blade for the Emperor/a-blade-….apk`), and `URI()` throws on those. OkHttp happily
     * percent-encodes them at request time, so a strict parser here silently produced "no host" —
     * which meant no token and no referer, and a 403 that looked like a broken CDN.
     */
    fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return null
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfter('@').substringBefore(':').trim()
        return host.takeIf { it.contains('.') }?.lowercase()
    }

    fun isBlocked(url: String): Boolean = hostOf(url)?.let { it in blocked || it in dead } == true

    fun markBlocked(url: String) {
        hostOf(url)?.let { blocked.add(it) }
    }

    /** The name did not resolve: no request to this authority can succeed this session. */
    fun markDead(url: String) {
        hostOf(url)?.let { dead.add(it) }
    }

    fun isDead(url: String): Boolean = hostOf(url)?.let { it in dead } == true

    /** A transfer that completed proves the host is serving again. */
    fun markServing(url: String) {
        hostOf(url)?.let {
            blocked.remove(it)
            dead.remove(it)
        }
    }

    /** The `Referer` a storage host expects, or null when the host does not check one. */
    fun refererFor(url: String): String? {
        val host = hostOf(url) ?: return null
        return referers.entries.firstOrNull { host == it.key || host.endsWith(".${it.key}") }?.value
    }

    /**
     * Hosts that take the web page's signed link: the catalogues' own storage domains. Measured 22 Oct
     * 2026 — an unsigned `cloud.9mod.space` link from the LiteAPKs catalogue answers 403 even with the
     * 9mod Referer, and 200 once the token is attached.
     */
    fun needsToken(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == "liteapks.dev" || host.endsWith(".liteapks.dev") ||
            host == "9mod.space" || host.endsWith(".9mod.space")
    }

    /**
     * The URL a download would actually use, with the web page's token where one is expected.
     * Existing query parameters are preserved.
     */
    fun signed(url: String, nowMs: Long = System.currentTimeMillis()): String {
        val clean = canonical(url)
        if (clean.isBlank() || !needsToken(clean) || clean.contains("token=")) return clean
        val ttl = nowMs / 1000L + TOKEN_LIFETIME_SECONDS
        val inner = Base64.getEncoder().encodeToString(ttl.toString().toByteArray())
        val token = Base64.getEncoder().encodeToString(inner.toByteArray())
        val encoded = URLEncoder.encode(token, "UTF-8")
        return clean + (if (clean.contains('?')) "&" else "?") + "token=" + encoded
    }

    /**
     * The link with the characters a strict URL parser refuses percent-encoded.
     *
     * The catalogue ships paths with literal spaces (`…/A Blade for the Emperor/a-blade-….apk`), and
     * while OkHttp's canonicaliser would encode them at request time, the token is appended to the
     * *raw* string first — so the URL that was signed and the URL that is requested are not the same
     * text. Encoding here makes them identical, and it is what the hosts were measured to accept: the
     * raw path is not even parseable by a strict client, `%20` returns the whole file.
     *
     * Only characters that are illegal in a URL are touched, and an existing `%XX` escape is copied
     * through so a link is never encoded twice.
     */
    fun canonical(url: String): String {
        if (url.isEmpty()) return url
        val out = StringBuilder(url.length + 24)
        var i = 0
        while (i < url.length) {
            val c = url[i]
            if (c == '%' && i + 2 < url.length && url[i + 1].isHexDigit() && url[i + 2].isHexDigit()) {
                out.append(url, i, i + 3)
                i += 3
                continue
            }
            if (c.code < 0x20 || c == ' ' || c in "\"<>\\^`{|}" || c.code >= 0x7F) {
                val point = if (Character.isHighSurrogate(c) && i + 1 < url.length) {
                    Character.toCodePoint(c, url[i + 1])
                } else {
                    c.code
                }
                val width = if (point > 0xFFFF) 2 else 1
                String(Character.toChars(point)).toByteArray(Charsets.UTF_8).forEach { byte ->
                    out.append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
                }
                i += width
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val HEX = "0123456789ABCDEF"

    /**
     * Storage authorities that answer the paths of a sibling which has stopped resolving.
     *
     * Measured Nov 2026: `gp.liteapks.com` does not resolve, while the identical path on
     * `gp4.liteapks.com` answers 200 — the catalogue keeps publishing links to the dead name. Only
     * the catalogue's own storage family is rewritten: a third-party bucket has no sibling, and
     * inventing one would be manufacturing a URL that was never published.
     */
    private val mirrors = listOf(
        "gp4.liteapks.com",
        "download.liteapks.dev",
        "download-old.liteapks.dev",
    )

    /** [url] on each sibling storage authority not already tried, best first. */
    fun mirrorsFor(url: String, tried: Set<String> = emptySet()): List<String> {
        val host = hostOf(url) ?: return emptyList()
        val ours = host == "liteapks.com" || host.endsWith(".liteapks.com") ||
            host == "liteapks.dev" || host.endsWith(".liteapks.dev")
        if (!ours) return emptyList()
        val spent = tried.mapNotNull { hostOf(it) }.toSet()
        return mirrors
            .filter { it != host && it !in spent && it !in dead }
            .mapNotNull { mirrorOn(url, it) }
            .filter { it !in tried }
            .distinct()
    }

    /** The same path and query, served by [mirrorHost]; null when [url] has no authority to swap. */
    private fun mirrorOn(url: String, mirrorHost: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val afterScheme = url.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null
        return url.substring(0, schemeEnd + 3) + mirrorHost + afterScheme.substring(authority.length)
    }

    /** Seconds of validity the site's download page signs for. */
    const val TOKEN_LIFETIME_SECONDS = 3L * 60 * 60
}
