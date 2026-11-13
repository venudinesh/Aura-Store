package dev.aurastore.store.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transport rule, read without a socket.
 *
 * The interceptor itself is four lines around [SecureTransport.permitsTransport]; what is worth
 * pinning is that a signed download URL is allowed and a plain one is not, because a redirect to
 * `http://` is the shape a downgrade takes when the first request went out over TLS.
 */
class SecureTransportTest {

    @Test
    fun anHttpsUrlIsPermitted() {
        assertTrue(SecureTransport.permitsTransport("https://liteapks.com/app/1?token=abc".toHttpUrl()))
    }

    @Test
    fun anHttpUrlIsRefused() {
        assertFalse(SecureTransport.permitsTransport("http://liteapks.com/app/1?token=abc".toHttpUrl()))
    }
}
