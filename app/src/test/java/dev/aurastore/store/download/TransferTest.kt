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

    /** The engine's stream is long-lived, so a stall is proved with a client that notices one fast. */
    private val stallClient = OkHttpClient.Builder()
        .readTimeout(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    private val port get() = server.address.port
    private fun url() = "http://127.0.0.1:$port/payload.bin"

    @Before
    fun setUp() {
        payload = ByteArray(512 * 1024).also { Random(7).nextBytes(it) }
        payloadSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/payload.bin") { exchange -> serve(exchange) }
        server.createContext("/stall.bin") { exchange ->
            // A body with no length, then silence: the shape that used to hang a streaming read
            // forever. The connection is closed only after the client must have given up on it.
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                out.write(ByteArray(64 * 1024) { 1 })
                out.flush()
                Thread.sleep(2_000)
            }
        }
        server.createContext("/chunked.bin") { exchange ->
            if (exchange.requestMethod == "HEAD") {
                // Accepts the size question and never answers it: the probe has its own deadline.
                Thread.sleep(2_000)
                runCatching { exchange.sendResponseHeaders(500, -1) }
                exchange.close()
                return@createContext
            }
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(payload) }
        }
        // A cached pool because the probe and the body are two connections at once.
        server.executor = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
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

    @Test
    fun `a host that stops mid-body fails and keeps its bytes instead of hanging`() {
        val target = tempFile()
        val started = System.currentTimeMillis()
        val failure = runCatching { Transfer.stream(stallClient, "http://127.0.0.1:$port/stall.bin", target) }
        val elapsed = System.currentTimeMillis() - started

        assertTrue("a silent socket must raise, not block", failure.exceptionOrNull() is IOException)
        assertTrue("it must give up near the stall timeout, not the host's lifetime: ${elapsed}ms", elapsed < 5_000)
        assertTrue(
            "the bytes already written are what a resume continues from, found ${target.length()}",
            target.length() in 1..(64L * 1024),
        )
    }

    @Test
    fun `a size probe that never answers cannot hold the body`() {
        val target = tempFile()
        var lastTotal = -2L
        val started = System.currentTimeMillis()
        val result = Transfer.stream(
            client = client,
            url = "http://127.0.0.1:$port/chunked.bin",
            target = target,
            // Short enough that a waiting probe would still be sleeping when the assertion runs.
            probeTimeoutMs = 300,
            onProgress = { _, total -> lastTotal = total },
        )
        val elapsed = System.currentTimeMillis() - started

        assertEquals(payload.size.toLong(), result.bytes)
        assertEquals(payloadSha, result.sha256)
        assertEquals("an unanswered HEAD means an unknown size, not a stuck transfer", -1L, lastTotal)
        assertTrue("the HEAD deadline must not gate the download: ${elapsed}ms", elapsed < 5_000)
    }

    private fun sha256Of(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
