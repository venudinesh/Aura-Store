package dev.aurastore.store.download

import java.io.IOException
import java.net.SocketTimeoutException

/** What went wrong with a download, in the terms a user can act on. */
enum class FailureKind {
    /** The host answered 403: the link or the storage domain is not serving this file. */
    REFUSED,

    /** 404/410: the catalogue does not have this file any more. */
    MISSING,

    /** The connection died, reset or timed out. The bytes on disk are still usable. */
    NETWORK,

    /** The device could not take the file (disk full, cache cleared underneath us). */
    STORAGE,

    /** The bytes arrived but are not the package this app can install. */
    PACKAGE,

    /** Android itself refused the install. */
    INSTALL,

    /** The bytes arrived, but they are not the file the source published its digest for. */
    INTEGRITY,

    UNKNOWN,
}

/**
 * A failure the user can read.
 *
 * A bare `HTTP 403` tells someone nothing about what to do, and the queue used to show exactly that
 * plus the exception message. The kind is what the UI keys its advice and its Retry button on; the
 * message stays the sentence, so an older row and a new one read the same.
 */
data class Failure(
    val kind: FailureKind,
    val message: String,
    /** What to do about it, when there is anything to do. */
    val hint: String?,
) {
    /** Tap-to-retry is only honest when another attempt can plausibly end differently. */
    val worthRetrying: Boolean
        get() = kind != FailureKind.MISSING && kind != FailureKind.PACKAGE
}

object Failures {

    /** Classifies a transfer error into a kind, a sentence and the advice that goes with it. */
    fun of(e: Throwable, describeHost: (String) -> String, url: String): Failure = when {
        e is HttpStatusException && e.code == 403 -> Failure(
            kind = FailureKind.REFUSED,
            message = "${describeHost(url)} refused this file (HTTP 403).",
            hint = "This app has no other build of it on a host that is serving. The catalogue mixes " +
                "storage domains inside one post, so another version usually downloads fine.",
        )
        e is HttpStatusException && (e.code == 404 || e.code == 410) -> Failure(
            kind = FailureKind.MISSING,
            message = "The catalogue no longer serves this file (HTTP ${e.code}).",
            hint = "Pick another version of this app, or open its page and try the other artefact listed there.",
        )
        e is HttpStatusException -> Failure(
            kind = if (e.code in 500..599) FailureKind.NETWORK else FailureKind.UNKNOWN,
            message = "${describeHost(url)} answered HTTP ${e.code}.",
            hint = if (e.code in 500..599) "The host is having trouble; retry in a few minutes." else null,
        )
        e is SocketTimeoutException -> Failure(
            kind = FailureKind.NETWORK,
            message = "The host stopped answering.",
            hint = "Retry continues from the bytes already on disk.",
        )
        isNoSpace(e) -> Failure(
            kind = FailureKind.STORAGE,
            message = "There is no room left on this device for this file.",
            hint = "Free space, or delete a finished download from the queue, then retry.",
        )
        e is IOException -> Failure(
            kind = FailureKind.NETWORK,
            message = e.message ?: "The connection failed.",
            hint = "Retry continues from the bytes already on disk.",
        )
        else -> Failure(
            kind = FailureKind.UNKNOWN,
            message = e.message ?: "download failed",
            hint = null,
        )
    }

    /** A file that arrived but is not installable. */
    fun packageFailure(message: String?): Failure = Failure(
        kind = FailureKind.PACKAGE,
        message = message ?: "This file is not a package Android can install.",
        hint = "The download itself is complete and hashed; the artefact is the wrong shape. " +
            "Choose another build of this version.",
    )

    fun installFailure(message: String?): Failure = Failure(
        kind = FailureKind.INSTALL,
        message = message ?: "The system rejected this package.",
        hint = "Android refuses an update signed with a different key than the app already installed. " +
            "Uninstall the app, then install this build.",
    )

    /**
     * A bundle's packages went to the installer as one session and the device would not commit it.
     *
     * Kept apart from [installFailure] on the advice, not the kind: a single APK cannot be refused for
     * being a set, so this is about how the artefact is shaped rather than about who signed it.
     */
    fun bundleFailure(): Failure = Failure(
        kind = FailureKind.INSTALL,
        message = "This device would not install the bundle as one set of packages.",
        hint = "A split set has to go through a single installer session, and this build of Android " +
            "refused it. Another build of this version may ship one APK instead.",
    )

    /**
     * A row that still claims a file the device no longer has.
     *
     * The queue is persisted but the APKs live in `cache/apk`, which Android is free to empty — and on
     * these ROMs does when storage runs low. Rather than let the row hand a missing path to an installer,
     * the queue says so on the first tap or the next start. Retry re-fetches, so this is worth retrying.
     */
    fun lostFileFailure(): Failure = Failure(
        kind = FailureKind.STORAGE,
        message = "The downloaded file is no longer on this device.",
        hint = "Android cleared the app's cache, so the queue kept the row but the bytes are gone. " +
            "Retry fetches the file again.",
    )

    /**
     * The published digest against the digest the transfer produced.
     *
     * `null` means *nothing to compare*: the catalogue publishes no hashes at all, so most rows never
     * reach this check, and a source that wrote something un-parseable in the field does not get to
     * fail a good download. A real mismatch is the worst case the pipeline can meet — complete,
     * installable bytes that are not the artefact the source said it was — so it is the one failure
     * that must never be waved through to an installer.
     */
    fun integrityFailure(published: String?, computed: String?): Failure? {
        val want = normalizeDigest(published) ?: return null
        val have = normalizeDigest(computed)
        if (have == want) return null
        return Failure(
            kind = FailureKind.INTEGRITY,
            message = "This file does not match the digest the source published.",
            hint = "Expected sha-256 $want, this file hashed to ${have ?: "nothing"}." +
                " Retry for a fresh copy; if it still differs, use another build.",
        )
    }

    /** A sha-256 as a source writes it — lower case, and neither bare nor prefixed with `sha256:`. */
    fun normalizeDigest(value: String?): String? = value
        ?.trim()
        ?.removePrefix("sha256:")
        ?.removePrefix("sha256=")
        ?.removePrefix("sha256-")
        ?.trim()
        ?.lowercase()
        ?.takeIf { DIGEST.matches(it) }

    private val DIGEST = Regex("^[0-9a-f]{64}$")

    /** Walks the cause chain: ENOSPC surfaces wrapped in a plain IOException. */
    private fun isNoSpace(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val message = cause.message?.lowercase()
            if (message != null && (message.contains("no space") || message.contains("ENOSPC") ||
                    message.contains("not enough space"))
            ) {
                return true
            }
            cause = cause.cause?.takeIf { it !== cause }
        }
        return false
    }
}
