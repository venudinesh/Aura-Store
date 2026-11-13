package dev.aurastore.store.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

/**
 * The envelope every catalogue response arrives in.
 *
 * A `posts/{unknown}` answer is a well-formed `200` whose `data` is null, so the status alone cannot
 * tell a listing from a missing one — and a detail page that fails to say which is what these pin.
 */
class ApiPayloadTest {

    private fun envelope(status: Int, data: Any?) = JSONObject()
        .put("status", status)
        .apply { if (data == null) put("data", JSONObject.NULL) else put("data", data) }

    /** The [IOException] [body] raises, or null when it returns instead of throwing. */
    private fun failure(body: () -> Unit): IOException? =
        try {
            body(); null
        } catch (e: IOException) {
            e
        }

    @Test
    fun `a listing the site does not have is refused by its own id`() {
        val error = failure { Api.payloadPost(envelope(200, JSONObject.NULL), 999_999_999L) }
        assertNotNull("an empty listing must not parse", error)
        assertEquals("Post 999999999 is not in the catalogue.", error!!.message)
    }

    @Test
    fun `an empty object is refused the same way as a null one`() {
        val error = failure { Api.payloadPost(envelope(200, JSONObject()), 42L) }
        assertNotNull("an empty listing must not parse", error)
        assertEquals("Post 42 is not in the catalogue.", error!!.message)
    }

    @Test
    fun `a listing that is there comes back untouched`() {
        val post = JSONObject().put("id", 8333).put("title", "Auto Responder")
        assertEquals(8333, Api.payloadPost(envelope(200, post), 8333L).optInt("id"))
    }

    @Test
    fun `an error status raises the server's own words`() {
        val json = envelope(404, JSONObject.NULL).put("message", "No such post")
        val error = failure { Api.payload(json) }
        assertNotNull("a 404 envelope must not parse", error)
        assertEquals("No such post", error!!.message)
    }

    @Test
    fun `a response with no status key is taken as success`() {
        // The endpoint omits `status` on some routes; treating a missing key as an error would reject
        // every one of them.
        val json = JSONObject().put("data", JSONObject().put("id", 7))
        assertEquals(7, Api.payload(json).optInt("id"))
    }
}
