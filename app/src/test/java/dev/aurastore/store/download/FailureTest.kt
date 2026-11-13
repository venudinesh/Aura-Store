package dev.aurastore.store.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * What a failed download says to the person who has to act on it.
 *
 * The queue used to print `HTTP 403`, which is a fact about a socket and not about what to do next.
 * The kind is what the details page keys its advice and its Retry button on, so the mapping from an
 * exception to a kind is the part worth pinning.
 */
class FailureTest {

    private val url = "https://liteapks.dev/dl/app.apk"

    private fun classify(e: Throwable) = Failures.of(e, { host -> host.substringAfter("//").substringBefore('/') }, url)

    @Test
    fun `a refusal is not the same as a missing file`() {
        val refused = classify(HttpStatusException(403, url))
        assertEquals(FailureKind.REFUSED, refused.kind)
        assertTrue(refused.message.startsWith("liteapks.dev refused this file"))
        assertTrue(refused.worthRetrying)

        val gone = classify(HttpStatusException(410, url))
        assertEquals(FailureKind.MISSING, gone.kind)
        // Retrying a file the catalogue no longer has can only fail the same way again.
        assertFalse(gone.worthRetrying)
    }

    @Test
    fun `a host having trouble is a network problem`() {
        val failing = classify(HttpStatusException(503, url))
        assertEquals(FailureKind.NETWORK, failing.kind)
        assertEquals("The host is having trouble; retry in a few minutes.", failing.hint)

        // 416 on a resumed range means the file is already whole, so it is never reached as a failure;
        // any other 4xx has no advice to give beyond what the host said.
        assertEquals(FailureKind.UNKNOWN, classify(HttpStatusException(416, url)).kind)
    }

    @Test
    fun `a dead connection keeps its bytes on disk`() {
        assertEquals(FailureKind.NETWORK, classify(SocketTimeoutException()).kind)
        assertEquals(FailureKind.NETWORK, classify(IOException("Connection reset")).kind)
        assertTrue(classify(IOException("Connection reset")).worthRetrying)
    }

    @Test
    fun `a full device is found through the cause chain`() {
        val wrapped = IOException("failed to write", IOException("No space left on device"))
        val failure = classify(wrapped)
        assertEquals(FailureKind.STORAGE, failure.kind)
        assertEquals("Free space, or delete a finished download from the queue, then retry.", failure.hint)
    }

    @Test
    fun `an artefact of the wrong shape is not offered a retry`() {
        val bundle = Failures.packageFailure("This artefact is a bundle, not a single package.")
        assertEquals(FailureKind.PACKAGE, bundle.kind)
        assertFalse(bundle.worthRetrying)
        assertEquals(FailureKind.PACKAGE, Failures.packageFailure(null).kind)

        // Android itself refusing is different: signing conflicts are worth another tap after a fix.
        val install = Failures.installFailure(null)
        assertEquals(FailureKind.INSTALL, install.kind)
        assertTrue(install.worthRetrying)
    }

    @Test
    fun `an unclassified error still says something`() {
        val unknown = classify(IllegalStateException("unexpected"))
        assertEquals(FailureKind.UNKNOWN, unknown.kind)
        assertEquals("unexpected", unknown.message)
        assertNull(unknown.hint)
    }

    /**
     * A published digest is the one external promise the pipeline can keep, so both directions matter:
     * a mismatch must be a failure, and the absence of one must not be.
     */
    @Test
    fun `a published digest is compared, not just displayed`() {
        val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val other = digest.reversed()

        assertNull(Failures.integrityFailure(null, digest))
        assertNull(Failures.integrityFailure(digest, digest))
        // The catalogue publishes nothing, so a row without an expectation is never an integrity case.
        assertNull(Failures.integrityFailure("", digest))

        val mismatch = Failures.integrityFailure(digest, other)
        require(mismatch != null)
        assertEquals(FailureKind.INTEGRITY, mismatch.kind)
        assertEquals("This file does not match the digest the source published.", mismatch.message)
        assertTrue(mismatch.hint!!.contains("Expected sha-256 $digest"))
        // A fresh copy is a plausible way to end differently, unlike a file the host does not have.
        assertTrue(mismatch.worthRetrying)

        // Nothing hashed yet is still a mismatch: an incomplete row must not reach an installer.
        assertTrue(Failures.integrityFailure(digest, null)!!.hint!!.contains("hashed to nothing"))
    }

    @Test
    fun `a digest is read the way sources write it`() {
        val digest = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        assertEquals(digest, Failures.normalizeDigest(digest.uppercase()))
        assertEquals(digest, Failures.normalizeDigest("  sha256:$digest  "))
        assertEquals(digest, Failures.normalizeDigest("sha256=$digest"))
        assertEquals(digest, Failures.normalizeDigest("sha256-$digest"))
        // Garbage in the field is not a promise, so it cannot fail a good download.
        assertNull(Failures.normalizeDigest("not-a-digest"))
        assertNull(Failures.normalizeDigest(digest.take(63)))
        assertNull(Failures.normalizeDigest(null))
    }
}
