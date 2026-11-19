package dev.aurastore.store.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The client the engine streams with is part of the stall fix, not an implementation detail: a
 * `readTimeout` of zero is how a half-open host used to keep a row downloading for the life of the
 * process. The transfer tests prove a silent socket raises when the timeout is short; this pins the
 * timeout the app actually ships, so nobody restores the streaming value by accident.
 */
class TransferClientTest {

    @Test
    fun `the engine gives a silent socket a deadline`() {
        val timeout = DownloadEngine.client.readTimeoutMillis
        assertEquals(Transfer.STALL_TIMEOUT_SECONDS * 1000L, timeout.toLong())
        assertEquals(Transfer.STALL_TIMEOUT_SECONDS * 1000L, DownloadEngine.client.writeTimeoutMillis.toLong())
    }
}
