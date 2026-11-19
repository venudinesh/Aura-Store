package dev.aurastore.store.download

import dev.aurastore.store.data.Hosts
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** What a finished transfer produced: its byte count and the digest of the whole file. */
data class TransferResult(val bytes: Long, val sha256: String)

/**
 * A non-2xx answer, with the status kept as a number.
 *
 * The engine distinguishes refusals (403/404) from transient failures: a refusal means the whole
 * host is not serving this catalogue any more, so the next artefact is tried instead of retrying
 * the same URL.
 */
class HttpStatusException(val code: Int, val link: String) : IOException("HTTP $code")

/**
 * The transfer core.
 *
 * Deliberately free of Android types: the interesting behaviour here is pausing and resuming, and
 * that can only be trusted if it is exercised against a server that speaks byte ranges — which is
 * a JVM test (`TransferTest`), not something a real CDN lets you do on demand.
 *
 * Resuming re-reads the bytes already on disk *before* appending, so the digest always covers the
 * whole file. If the server answers a range request with 200 instead of 206 the file is truncated
 * and the transfer restarts from zero, because a half-written file plus a fresh stream would
 * otherwise hash to something that never existed.
 */
object Transfer {

    private const val UA = "okhttp/3.12.13"
    private const val BUFFER_BYTES = 64 * 1024

    /**
     * How long a socket may stay silent before the transfer gives up on it.
     *
     * A download is a stream, not a single request, so the value has to be long enough for a slow
     * mobile handover and short enough that a host which stopped sending is a failure the queue can
     * retry rather than a row that waits forever. `0` — OkHttp's "no timeout" — is the one setting a
     * streaming download must not use: a CDN that closes its end without a FIN leaves `read()` blocked
     * with nothing to wake it, and no amount of retry logic ever runs.
     */
    const val STALL_TIMEOUT_SECONDS = 30L

    /** The HEAD that asks for a size may not hold the transfer it is describing. */
    const val PROBE_TIMEOUT_MS = 10_000L

    fun stream(
        client: OkHttpClient,
        url: String,
        target: File,
        resumeFrom: Long = 0L,
        /** Streamed to the host, which some storage domains require; see [Hosts.refererFor]. */
        referer: String? = null,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        onCancelled: () -> Boolean = { false },
        /**
         * Handed the live [Call] the moment it is built, so a caller can cancel it.
         *
         * This is the only way to stop a transfer that is already inside `execute()`: the read blocks
         * on the socket, and a coroutine's cancellation flag is never consulted there. Closing the call
         * is what actually stops the bytes, and [onCancelled] then keeps the loop from writing another
         * buffer on the way out.
         */
        onCall: (Call) -> Unit = {},
        /**
         * Handed the live response once its status line has arrived.
         *
         * [onCall] cancels the call, which is the documented stop, but a pause that only cancelled
         * still left this thread blocked in `InputStream.read` on a real device: the socket close is
         * asynchronous, and the row looked like it had ignored the tap until the next buffer landed.
         * Closing the body is the direct way to unblock the read — it is the stream the thread is
         * sitting in — so a caller keeps this handle as well and closes it to stop the bytes now.
         */
        onResponse: (okhttp3.Response) -> Unit = {},
        /**
         * How long the size probe may wait for its own answer. A parameter rather than the constant
         * so a test can make the wait shorter than the suite.
         */
        probeTimeoutMs: Long = PROBE_TIMEOUT_MS,
    ): TransferResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = 0L

        if (resumeFrom > 0 && target.isFile && target.length() == resumeFrom) {
            target.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    offset += read
                }
            }
        } else if (target.exists()) {
            target.delete()
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            // No compression: the digest and the byte ranges must match what the host stores.
            .header("Accept-Encoding", "identity")
            .apply {
                if (referer != null) header("Referer", referer)
                if (offset > 0) header("Range", "bytes=$offset-")
            }
            .build()

        val call = client.newCall(request)
        // The caller keeps the handle so a pause can close the socket under a blocking read.
        onCall(call)
        try {
            val response = call.execute()
            // The caller keeps this so a pause can close the body under a blocking read.
            onResponse(response)
            if (response.code == 416 && offset > 0) {
                return TransferResult(offset, digest.digest().joinToString("") { "%02x".format(it) })
            }
            if (!response.isSuccessful) {
                // A refusal leaves a body behind; closing it hands the connection back instead of
                // leaving it counted against the host for the life of the process.
                response.close()
                throw HttpStatusException(response.code, request.url.toString())
            }
            val body = response.body
            val partial = offset > 0 && response.code == 206
            if (!partial) {
                // Fresh start, or the server ignored the range: begin again from zero.
                if (target.exists()) target.delete()
                offset = 0L
                digest.reset()
            }
            val remaining = body.contentLength()
            // A chunked or compressed answer carries no usable length, so progress would be a bare
            // spinner for the whole transfer. One HEAD costs a round trip and gives the real size —
            // and the size of the file on the host is the size of the file either way: the range
            // response is the same bytes minus what is already on disk. It is bounded: a host that
            // never answers the HEAD must not hold the GET's bytes hostage.
            val total = if (remaining > 0) offset + remaining else probeSize(client, url, referer, probeTimeoutMs)
            onProgress(offset, total)

            body.byteStream().use { input ->
                FileOutputStream(target, partial).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var done = offset
                    while (true) {
                        if (onCancelled()) throw IOException(PAUSED)
                        // Block on the socket read; the caller will unblock us by closing
                        // the response body when pause() is called.
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                        
                        // Additional cancellation check after writing each buffer,
                        // because the user might have paused between progress ticks.
                        if (onCancelled()) throw IOException(PAUSED)
                    }
                    onProgress(done, total)
                    return TransferResult(done, digest.digest().joinToString("") { "%02x".format(it) })
                }
            }
        } catch (e: IOException) {
            // A paused call is closed rather than merely flagged, so the read throws instead of
            // returning. That is the pause working, not a network failure: report it as one thing so
            // the engine keeps the partial file and does not spend a retry on it.
            if (onCancelled()) throw IOException(PAUSED, e)
            throw e
        }
    }

    /** Thrown (and recognised) when a transfer stops because the user paused it. */
    const val PAUSED = "paused"

    /**
     * The file's full size from a HEAD, or -1 when the host will not say. Asked with the same
     * headers as the GET it stands in for, because a storage domain that wants a token and a
     * referer wants them here too.
     *
     * The call gets its own deadline. With a streaming client a HEAD that is accepted but never
     * answered would otherwise block until the read timeout, and this happens before the first body
     * byte is read — a transfer stuck at 0 B because the size question was asked.
     */
    private fun probeSize(client: OkHttpClient, url: String, referer: String?, timeoutMs: Long): Long = runCatching {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", UA)
            .header("Accept-Encoding", "identity")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                -1L
            } else {
                // A HEAD answered with `Content-Length: 0` is a host declining to say, not an empty
                // file: the transfer already has bytes to read, so zero is not a usable total.
                response.body.contentLength().takeIf { it > 0 } ?: -1L
            }
        }
    }.getOrDefault(-1L)
}
