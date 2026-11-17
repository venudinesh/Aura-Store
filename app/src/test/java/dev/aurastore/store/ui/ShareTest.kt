package dev.aurastore.store.ui

import dev.aurastore.store.data.sources.SourceApp
import dev.aurastore.store.data.sources.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a share leaves the phone as.
 *
 * The rule that matters is the one this pins: the page, not the signed download URL. A download URL
 * is refused by the host on another device anyway, and it carries this install's referer with it.
 */
class ShareTest {

    @Test
    fun `a catalogue post is shared by its own page`() {
        assertEquals("https://liteapks.com/?p=519765", Share.pageUrl(519765L))
    }

    @Test
    fun `the shared body is the title over the link`() {
        assertEquals(
            "YouTube Premium\nhttps://liteapks.com/?p=519765",
            Share.text("YouTube Premium", "https://liteapks.com/?p=519765"),
        )
    }

    @Test
    fun `a source app is shared as the page it was read from`() {
        val app = SourceApp(
            source = SourceId.FIVEPLAY,
            key = "https://5play.org/en/1234-youtube.html",
            title = "YouTube",
            pageUrl = "https://5play.org/en/1234-youtube.html",
        )
        assertEquals("https://5play.org/en/1234-youtube.html", Share.sourcePageUrl(app))
    }

    @Test
    fun `a source key that is a URL is the page when no page field was set`() {
        val app = SourceApp(
            source = SourceId.AN1,
            key = "https://an1.com/1234-youtube.html",
            title = "YouTube",
        )
        assertEquals("https://an1.com/1234-youtube.html", Share.sourcePageUrl(app))
    }

    @Test
    fun `a row with no page cannot be shared as one`() {
        val app = SourceApp(source = SourceId.LITEAPKS, key = "1234", title = "YouTube")
        assertNull(Share.sourcePageUrl(app))
    }
}
