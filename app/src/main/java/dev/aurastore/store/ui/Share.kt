package dev.aurastore.store.ui

import android.content.Context
import android.content.Intent
import dev.aurastore.store.data.sources.SourceApp

/**
 * Sharing an app page.
 *
 * What leaves the phone is the *page*, never the download link: the signed URL a download uses is a
 * credential with this device's referer baked into it, and the storage hosts refuse it anywhere else
 * anyway. The API publishes no permalink for a post, so the link is built from its id in the form
 * WordPress itself resolves (`?p=<id>` redirects to the pretty URL) rather than guessed at from the
 * title. A source app is simpler: it already carries the page it was read from.
 */
object Share {

    private const val SITE = "https://liteapks.com"

    fun pageUrl(postId: Long): String = "$SITE/?p=$postId"

    /** The URL a source app is shared as, or null when the row had no page of its own. */
    fun sourcePageUrl(app: SourceApp): String? =
        app.pageUrl?.takeIf { it.isNotBlank() }
            ?: app.key.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    fun text(title: String, url: String): String = "$title\n$url"

    fun app(context: Context, title: String, postId: Long) {
        link(context, title, pageUrl(postId))
    }

    fun source(context: Context, app: SourceApp) {
        val url = sourcePageUrl(app) ?: return
        link(context, app.title, url)
    }

    fun link(context: Context, title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, text(title, url))
        runCatching {
            context.startActivity(
                Intent.createChooser(send, "Share $title")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
