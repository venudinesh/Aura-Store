package dev.aurastore.store.download

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Random

/**
 * The riskiest thing in the download engine is resuming: bytes written before a pause must still
 * be part of the final file, and the digest must cover the whole of it. A real CDN gives you no way
 * to pause at will, so these tests run against a local server that speaks byte ranges, and compare
 * the resumed file against a single uninterrupted download of the same payload.
 */
class TransferTest {

    private lateinit var server: HttpServer
    private lateinit var payload: ByteArray
    private lateinit var payloadSha: String
    private var honourRanges = true
    private val client = OkHttpClient()

    private val port get() = server.address.port
    private fun url() = "http://127.0.0.1:$port/payload.bin"

    @Before
    fun setUp() {
        payload = ByteArray(512 * 1024).also { Random(7).nextBytes(it) }
        payloadSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/payload.bin") { exchange -> serve(exchange) }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    /** Serves the payload, honouring `Range: bytes=N-` unless a test turns that off. */
    private fun serve(exchange: HttpExchange) {
        val range = exchange.requestHeaders.getFirst("Range")
        val start = if (honourRanges && range != null && range.startsWith("bytes=")) {
            range.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
        } else {
            0
        }
        if (start >= payload.size) {
            // What a real host answers for a range that begins at or past the end of the file.
            exchange.sendResponseHeaders(416, -1)
            exchange.close()
            return
        }
        val slice = payload.copyOfRange(start, payload.size)
        if (start > 0) {
            exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
            exchange.sendResponseHeaders(206, slice.size.toLong())
        } else {
            exchange.sendResponseHeaders(200, slice.size.toLong())
        }
        exchange.responseBody.use { it.write(slice) }
    }

    private fun tempFile(): File =
        File.createTempFile("transfer", ".bin").also { it.delete() }

    @Test
    fun `a fresh download matches the payload byte for byte`() {
        val target = tempFile()
        val result = Transfer.stream(client, url(), target)

        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals(payloadSha, result.sha256)
        assertEquals(payloadSha, sha256Of(target))
    }

    @Test
    fun `resuming after a pause produces the same file as one uninterrupted download`() {
        val target = tempFile()

        // 1. Start a download and stop it part way through, the way a pause does.
        var seen = 0L
        val paused = runCatching {
            Transfer.stream(
                client,
                url(),
                target,
                onProgress = { done, _ -> seen = done },
                onCancelled = { seen > 150_000 },
            )
        }
        assertTrue("expected the transfer to be interrupted, not completed", paused.isFailure)
        val partial = target.length()
        assertTrue("expected a partial file, found $partial bytes", partial in 1 until payload.size.toLong())

        // 2. Resume from the partial file.
        val result = Transfer.stream(client, url(), target, resumeFrom = partial)

        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals("the digest must cover the bytes written before the pause too", payloadSha, result.sha256)
        assertEquals(payloadSha, sha256Of(target))
    }

    @Test
    fun `a server that ignores range requests forces a clean restart`() {
        val target = tempFile()
        var seen = 0L
        runCatching {
            Transfer.stream(
                client,
                url(),
                target,
                onProgress = { done, _ -> seen = done },
                onCancelled = { seen > 150_000 },
            )
        }
        val partial = target.length()

        honourRanges = false
        val result = Transfer.stream(client, url(), target, resumeFrom = partial)

        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals("a restarted transfer must still hash to the payload", payloadSha, result.sha256)
        assertEquals(payloadSha, sha256Of(target))
    }

    @Test
    fun `a stale partial file is discarded rather than appended to`() {
        val target = tempFile()
        target.writeBytes(ByteArray(4096) { 7 })

        // resumeFrom does not match the file on disk, so this must behave as a fresh download.
        val result = Transfer.stream(client, url(), target, resumeFrom = 99)

        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals(payloadSha, result.sha256)
    }

    @Test
    fun `resuming a file that is already complete keeps it instead of failing`() {
        // A finished download is verified and ready; only a later failure (an install that the
        // system refused) can send it back through the engine. Asking for bytes past the end is
        // answered 416, which used to be reported as a host error and delete the good file.
        val target = tempFile()
        target.writeBytes(payload)

        val result = Transfer.stream(client, url(), target, resumeFrom = payload.size.toLong())

        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals(payloadSha, result.sha256)
        assertEquals("a complete file must survive the retry", payloadSha, sha256Of(target))
    }

    @Test
    fun `an http failure surfaces as an IOException`() {
        val target = tempFile()
        val failure = runCatching { Transfer.stream(client, "http://127.0.0.1:$port/missing.bin", target) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is IOException)
    }

    private fun sha256Of(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
