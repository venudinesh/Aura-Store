package dev.aurastore.store.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The name a download writes to is what decides whether the transfer can start at all: a signed
 * storage link carries its whole signature in the query string, and using the last path segment
 * verbatim produced a name of several hundred bytes, so the write failed with `ENAMETOOLONG`
 * before a single byte arrived.
 */
class FileNameTest {

    @Test
    fun `the query string of a signed link never reaches the file name`() {
        val url = "https://abcd.r2.cloudflarestorage.com/5play/files/2026-04/spotify-music-9.0.84.1313-mod-5play.apk" +
            "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=key%2F20261018%2Fauto%2Fs3%2Faws4_request" +
            "&X-Amz-Date=20261018T105017Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host" +
            "&X-Amz-Signature=not-a-real-signature-only-a-long-one-so-the-query-is-long"
        val name = DownloadEngine.fileNameFor(url)

        assertEquals("spotify-music-9.0.84.1313-mod-5play.apk", name)
        assertTrue("a name must stay well under the 255-byte filesystem limit", name.length < 120)
    }

    @Test
    fun `a vague or empty path still yields a usable name`() {
        assertEquals("package.apk", DownloadEngine.fileNameFor("https://cdn.example.com/"))
        assertEquals("package.apk", DownloadEngine.fileNameFor(""))
        // A segment with no extension is still a name; only an empty one needs the placeholder.
        assertEquals("download", DownloadEngine.fileNameFor("https://cdn.example.com/download?id=42"))
        assertEquals("hidden", DownloadEngine.fileNameFor("https://cdn.example.com/.hidden"))
    }

    @Test
    fun `escapes in the path are decoded before the name is built`() {
        // A 9MOD file really is linked this way: .../Spotify%20v9.1.92.3058%20(Premium).apk
        val name = DownloadEngine.fileNameFor(
            "https://cloud.9mod.space/files/Spotify%20v9.1.92.3058%20(Premium).apk?token=abc",
        )
        assertEquals("Spotify_v9.1.92.3058_(Premium).apk", name)
    }

    @Test
    fun `separators and spaces are replaced rather than passed to the filesystem`() {
        val name = DownloadEngine.fileNameFor("https://cdn.example.com/My%20App/v1.2/Spotify Premium (MOD).apk")
        assertFalse(name.contains('/'))
        assertFalse(name.contains(' '))
        assertTrue(name.endsWith(".apk"))
    }

    @Test
    fun `an absurdly long name is truncated but keeps its extension`() {
        val name = DownloadEngine.fileNameFor("https://cdn.example.com/${"a".repeat(400)}.apk")
        assertEquals(80, name.length)
        assertTrue(name.endsWith(".apk"))
    }
}
