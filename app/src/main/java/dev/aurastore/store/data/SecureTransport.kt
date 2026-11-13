package dev.aurastore.store.data

import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException

/**
 * The one transport rule every call in this app obeys: bytes leave and arrive over TLS.
 *
 * `usesCleartextTraffic="false"` covers the platform's own stacks, and OkHttp is not one of them:
 * it opens the socket itself, so a source that answered a 302 with an `http://` location would have
 * its body read in the clear while the manifest still claimed otherwise. The check lives in a
 * network interceptor rather than an application one because redirects are followed inside OkHttp,
 * below the application interceptors, and each hop passes through here.
 *
 * Only the host goes into the message. A download URL carries a signed token in its query string,
 * and a failure message is exactly the sort of thing that ends up in a log.
 */
object SecureTransport {

    val HttpsOnly: Interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!permitsTransport(url)) {
            throw IOException("Refused a cleartext request to ${url.host}")
        }
        chain.proceed(chain.request())
    }

    /** The rule on its own, so it can be read and tested without a socket. */
    fun permitsTransport(url: HttpUrl): Boolean = url.isHttps
}
