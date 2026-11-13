package dev.aurastore.store.ui

import android.content.Context
import android.content.Intent

/**
 * Sharing an app page.
 *
 * What leaves the phone is the *page*, never the download link: the signed URL a download uses is a
 * credential with this device's referer baked into it, and the storage hosts refuse it anywhere else
 * anyway. The API publishes no permalink for a post, so the link is built from its id in the form
 * WordPress itself resolves (`?p=<id>` redirects to the pretty URL) rather than guessed at from the
 * title.
 */
object Share {

    private const val SITE = "https://liteapks.com"

    fun pageUrl(postId: Long): String = "$SITE/?p=$postId"

    fun app(context: Context, title: String, postId: Long) {
        val text = "$title\n${pageUrl(postId)}"
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, text)
        runCatching {
            context.startActivity(
                Intent.createChooser(send, "Share $title")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
