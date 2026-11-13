package dev.aurastore.store.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one slot at the bottom of a paged list, and what it is allowed to say there.
 *
 * [PagingFooter] draws three different things and only ever one of them, which is a decision a composable
 * cannot be tested through: the JVM has no resource table for [statusText], and the arm round nineteen could
 * not draw on a phone — a refusal whose exception carries no message at all — is precisely the one where the
 * words come from the app rather than from the wire. Both halves are pure functions of the state now, so
 * both are pinned here: which arm wins, and which sentence the winning arm prints.
 */
class PagingFooterTest {

    @Test
    fun `a refusal outranks the spinner that has not been cleared yet`() {
        // What the screen's state looks like for one moment after a page fails: `moreError` is set and
        // `loadingMore` is still true. The retry button is the only control the reader has left, and a
        // spinner drawn here hides it.
        val failed = StatusLine(text = "Unable to resolve host \"liteapks.com\"")
        assertEquals(FooterArm.Failed, footerArm(loading = true, hasMore = true, error = failed))
    }

    @Test
    fun `a page still in flight does not claim the list has ended`() {
        // `hasMore` is the previous page's opinion. Until this one arrives, the sentence saying the list is
        // finished would be a guess — and one the reader can disprove by scrolling.
        assertEquals(FooterArm.Loading, footerArm(loading = true, hasMore = false, error = null))
    }

    @Test
    fun `a walk that ran out says so in the list's own name`() {
        assertEquals(FooterArm.End, footerArm(loading = false, hasMore = false, error = null))
    }

    @Test
    fun `an idle list with another page to fetch says nothing at all`() {
        // The arm that keeps the footer from becoming a permanent widget: no sentence, no spinner, no
        // button — the next page is asked for by the scroll, not by the reader.
        assertEquals(FooterArm.None, footerArm(loading = false, hasMore = true, error = null))
    }

    @Test
    fun `a refusal with nothing to say still offers a way back`() {
        // `runCatching` hands back an exception whose `message` is null or empty often enough that this is
        // the common case, not the odd one. An empty sentence still selects the failed arm, so the retry
        // control survives; only the words come from the app.
        assertEquals(FooterArm.Failed, footerArm(loading = false, hasMore = true, error = StatusLine(text = "")))
    }

    @Test
    fun `the words that came with the failure are the words that are printed`() {
        // Both screens fall back to the same sentence, `R.string.list_load_more_failed` and
        // `R.string.search_load_more_failed`, which read alike today: "Could not load more".
        val fallback = "Could not load more"
        assertEquals(fallback, footerFailureText("", fallback))
        assertEquals(fallback, footerFailureText("   ", fallback))
        assertEquals(
            "the server's own sentence is not overwritten",
            "HTTP 503 Service Unavailable",
            footerFailureText("HTTP 503 Service Unavailable", fallback),
        )
    }

    @Test
    fun `a walk cut off by the route does not claim the list is finished`() {
        // `v2/posts` answers nothing past its fiftieth page, so a flat list can stop at 1 000 rows in the
        // middle of an ordering — `Tools` is published with 1 257 — and the two ends read differently:
        // "That's everything in Tools." against a list that has more, and a sentence saying the catalogue,
        // not the term, is what stopped.
        val everything = "That's everything in Tools."
        val noFurther = "Tools has more than this — the catalogue pages no further."
        assertEquals(everything, footerEndText(capped = false, everything, noFurther))
        assertEquals(noFurther, footerEndText(capped = true, everything, noFurther))
    }
}
