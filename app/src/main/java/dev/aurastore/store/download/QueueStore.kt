package dev.aurastore.store.download

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The download queue, kept on disk.
 *
 * `DownloadEngine._items` was the only state in this app that died with the process while the files it
 * describes outlived it: a restart emptied the You tab's *Downloads* and left every verified APK sitting
 * in `cache/apk` with no row pointing at it, no digest and no install history. This keeps one JSON record
 * per row — the whole row, not a pointer to it — so the queue can be rebuilt before the first screen
 * reads it.
 *
 * JSON rather than a column per field because `DownloadItem` is display state, not a table: it changes
 * shape with the UI, and a row written by an older build simply decodes with whatever fields it has. The
 * two derived rates ([DownloadItem.bytesPerSecond], [DownloadItem.etaSeconds]) are deliberately not
 * stored — a speed measured before the restart would be a lie afterwards.
 */
object QueueStore {

    private const val PREFS = "aurastore_queue"
    private const val KEY_ROWS = "rows"

    fun load(context: Context): List<DownloadItem> =
        parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROWS, null))

    fun save(context: Context, items: List<DownloadItem>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_ROWS, render(items))
        }
    }

    /** A row written by an older build, or nothing at all, decodes to an empty queue. */
    internal fun parse(raw: String?): List<DownloadItem> {
        if (raw == null) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { runCatching { decode(it) }.getOrNull() }
        }
    }

    internal fun render(items: List<DownloadItem>): String {
        val array = JSONArray()
        items.forEach { array.put(encode(it)) }
        return array.toString()
    }

    private fun encode(item: DownloadItem) = JSONObject().apply {
        put("id", item.id)
        put("postId", item.postId)
        put("title", item.title)
        putStr("iconUrl", item.iconUrl)
        put("versionLabel", item.versionLabel)
        put("fileLabel", item.fileLabel)
        put("url", item.url)
        put("fileName", item.fileName)
        put("state", item.state.name)
        put("bytesDownloaded", item.bytesDownloaded)
        put("totalBytes", item.totalBytes)
        putStr("error", item.error)
        putStr("sha256", item.sha256)
        putStr("expectedSha256", item.expectedSha256)
        putStr("apkPackage", item.apkPackage)
        putLng("apkVersionCode", item.apkVersionCode)
        putStr("apkSignerSha256", item.apkSignerSha256)
        putStr("apkVersionName", item.apkVersionName)
        putStr("localPath", item.localPath)
        put("autoInstall", item.autoInstall)
        put("uninstallFirst", item.uninstallFirst)
        putStr("notice", item.notice)
        put("dataFile", item.dataFile)
        put("bundleFiles", JSONArray().apply { item.bundleFiles.forEach { put(it) } })
        put("dataInside", item.dataInside)
        put("transitions", JSONArray().apply {
            item.transitions.forEach { put(JSONObject().put("state", it.state.name).put("at", it.at)) }
        })
        putObj("failure", item.failure?.let {
            JSONObject().put("kind", it.kind.name).putStr("message", it.message).putStr("hint", it.hint)
        })
    }

    private fun decode(obj: JSONObject): DownloadItem {
        val transitions = obj.optJSONArray("transitions") ?: JSONArray()
        val history = (0 until transitions.length()).mapNotNull { i ->
            val entry = transitions.optJSONObject(i) ?: return@mapNotNull null
            val state = enumValueOrNull<DownloadState>(entry.optString("state", "")) ?: return@mapNotNull null
            StateChange(state, entry.optLong("at"))
        }
        val failure = obj.optJSONObject("failure")?.let {
            Failure(
                kind = enumValueOrNull<FailureKind>(it.optString("kind", "")) ?: FailureKind.UNKNOWN,
                message = it.str("message") ?: "download failed",
                hint = it.str("hint"),
            )
        }
        return DownloadItem(
            id = obj.getString("id"),
            postId = obj.optLong("postId"),
            title = obj.optString("title"),
            iconUrl = obj.str("iconUrl"),
            versionLabel = obj.optString("versionLabel"),
            fileLabel = obj.optString("fileLabel"),
            url = obj.optString("url"),
            fileName = obj.optString("fileName"),
            state = enumValueOrNull<DownloadState>(obj.optString("state", "")) ?: DownloadState.FAILED,
            bytesDownloaded = obj.optLong("bytesDownloaded"),
            totalBytes = obj.optLong("totalBytes", -1L),
            error = obj.str("error"),
            sha256 = obj.str("sha256"),
            expectedSha256 = obj.str("expectedSha256"),
            apkPackage = obj.str("apkPackage"),
            apkVersionCode = obj.lng("apkVersionCode"),
            apkSignerSha256 = obj.str("apkSignerSha256"),
            apkVersionName = obj.str("apkVersionName"),
            localPath = obj.str("localPath"),
            autoInstall = obj.optBoolean("autoInstall"),
            uninstallFirst = obj.optBoolean("uninstallFirst"),
            notice = obj.str("notice"),
            dataFile = obj.optBoolean("dataFile"),
            bundleFiles = names(obj.optJSONArray("bundleFiles")),
            dataInside = obj.optBoolean("dataInside"),
            transitions = history,
            failure = failure,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        if (name.isEmpty()) null else runCatching { enumValueOf<T>(name) }.getOrNull()

    /**
     * A stored list of names.
     *
     * Absent is the ordinary case and stays empty: a queue written before this app could unpack a bundle
     * names no pieces, and one that names an empty string names nothing either.
     */
    private fun names(array: JSONArray?): List<String> =
        array?.let { (0 until it.length()).mapNotNull { i -> it.optString(i).takeIf { name -> name.isNotBlank() } } }
            ?: emptyList()

    private fun JSONObject.putStr(key: String, value: String?) = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.putLng(key: String, value: Long?) = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.putObj(key: String, value: JSONObject?) = put(key, value ?: JSONObject.NULL)

    /** `optString` answers the literal `"null"` for a JSON null, which is not what a nullable field means. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.lng(key: String): Long? = if (isNull(key)) null else optLong(key)
}
