package dev.aurastore.store.data

import dev.aurastore.store.version.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One published build of this app, as GitHub's release API describes it.
 *
 * [apkName] and [apkSize] belong to the asset the running build can actually take: a debug install
 * asks for the debug APK, a release install for the signed one, because Android will not accept the
 * other signature over it. [sha256] is the hash the download must match before it reaches the
 * installer; null means the release published none, and [dev.aurastore.store.update.AppUpdater]
 * refuses to install that.
 */
data class AppRelease(
    val version: AppVersion,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sha256: String?,
    /** The `.sha256` asset beside the APK, when the release carries one. */
    val checksumUrl: String? = null,
)

/**
 * The project's own releases, read from GitHub.
 *
 * This is the store updating itself, not a catalogue: the repository is fixed, the answer is the
 * newest non-draft, non-pre-release release, and the asset is chosen by the signature this build
 * carries. The API is public and answers without a token; a check is cheap and infrequent, so the
 * unauthenticated hourly limit is not something a user can reach by tapping.
 */
object AppReleases {

    const val REPO = "venudinesh/Aura-Store"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val ACCEPT = "application/vnd.github+json"
    private const val UA = "AuraStore/$REPO"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addNetworkInterceptor(SecureTransport.HttpsOnly)
            .build()
    }

    /**
     * The API client, bounded end to end.
     *
     * A phone whose DNS is unreachable does not refuse the lookup, it leaves it hanging; the socket
     * timeouts below never fire because there is no socket yet. The call timeout is the only bound
     * that covers the whole exchange, so the Settings row can say GitHub did not answer instead of
     * sitting on "Checking" until the app is killed.
     */
    private val apiClient: OkHttpClient by lazy {
        client.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
    }

    /** The newest release this build can install, or null when there is no version to offer. */
    suspend fun latest(debuggable: Boolean): AppRelease? = withContext(Dispatchers.IO) {
        val release = parse(get(LATEST), debuggable) ?: return@withContext null
        if (release.sha256 != null) return@withContext release
        // Older releases carry the hash only in the sibling file.
        val checksum = release.checksumUrl?.let { url -> runCatching { checksum(get(url)) }.getOrNull() }
        release.copy(sha256 = checksum)
    }

    /**
     * Pure: one release object into the one build this installation may take.
     *
     * The release is skipped rather than guessed at when it is a draft, a pre-release, carries no
     * version tag, or has no APK with this build's signature. Returning null is "nothing to offer",
     * which is the same answer as being current.
     */
    fun parse(json: String, debuggable: Boolean): AppRelease? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name").trim().takeIf { it.isNotEmpty() } ?: return null
        val version = AppVersion.parse(tag) ?: return null
        val assets = root.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { asset ->
                val name = asset.optString("name")
                name.endsWith(".apk", ignoreCase = true) && !name.endsWith(".sha256", ignoreCase = true)
            }
            .firstOrNull { isDebugAsset(it.optString("name")) == debuggable }
            ?: return null
        val apkName = apk.optString("name")
        val apkUrl = apk.optString("browser_download_url").trim().takeIf { it.isNotEmpty() } ?: return null
        val checksumUrl = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name") == "$apkName.sha256" }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }
        return AppRelease(
            version = version,
            tag = tag,
            notes = root.optString("body").trim(),
            apkUrl = apkUrl,
            apkName = apkName,
            apkSize = apk.optLong("size", 0L),
            sha256 = sha256Of(apk.optString("digest")),
            checksumUrl = checksumUrl,
        )
    }

    /** Pure: the hex digest out of GitHub's `sha256:<hex>` asset digest, or null. */
    fun sha256Of(digest: String?): String? {
        val text = digest?.trim().orEmpty()
        val hex = when {
            text.contains(':') -> {
                val (algorithm, value) = text.split(':', limit = 2)
                if (!algorithm.equals("sha256", ignoreCase = true)) return null
                value
            }
            // A digest without its prefix is still a digest; anything else is not.
            else -> text
        }
        return hex
            .takeIf { it.length == 64 && it.all { char -> char.isDigit() || char in 'a'..'f' || char in 'A'..'F' } }
            ?.lowercase()
    }

    /** Pure: `Aura-Store-v1.1.0-debug.apk` is the debug-signed build. */
    fun isDebugAsset(name: String): Boolean = name.contains("-debug", ignoreCase = true)

    /** Pure: the first 64-hex token of a `sha256sum`-shaped file. */
    fun checksum(text: String): String? = Regex("[0-9a-fA-F]{64}").find(text)?.value?.lowercase()

    /** A response whose body the caller must close. Used by the downloader to stream. */
    internal fun open(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} on ${request.url.host}")
        }
        return response
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", ACCEPT)
            .build()
        val response = apiClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} on ${request.url.host}")
        }
        return response.use { it.body.string() }
    }
}
