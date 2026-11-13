package dev.aurastore.store.install

import dev.aurastore.store.download.DownloadItem
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * A catalogue bundle: one zip holding the several packages that together make one app.
 *
 * `.xapk` is this catalogue's answer to "game with data". A Play delivery splits an app into a base plus
 * per-ABI, per-language and per-density config APKs; a bundle ships that same set — sometimes with the
 * expansion files inside it too — as one archive. The distinction matters to this app because the two
 * install paths are not interchangeable: an intent hands the system installer *one* package, while the
 * whole set goes in through a `PackageInstaller` session, which is why the first job here is working out
 * which entry is the base and which are its pieces.
 *
 * Nothing below touches Android. The rules that can be wrong — what counts as a bundle, which entry is
 * the app, what a manifest is allowed to say — are therefore testable on the JVM against archives built
 * in the test.
 */
object Xapk {

    /** The descriptor the format writes next to the packages. */
    const val MANIFEST = "manifest.json"

    /**
     * Whether an artefact name means a bundle rather than a single package.
     *
     * A queue row's own name, so the query string a signed link carries is already gone — but it is cut
     * the same way [DownloadItem] cuts it, because a name that reached here straight from a URL still
     * would. A `.zip` is deliberately not a bundle: the catalogue's game-data archives are zips too, and
     * those rows are marked [DownloadItem.dataFile] and never go near an installer.
     */
    fun isBundle(name: String): Boolean {
        val lower = name.substringBefore('?').substringBefore('#').lowercase()
        return lower.endsWith(".xapk") || lower.endsWith(".apks")
    }

    /** The packages inside an archive, [base] first, plus the expansion files named beside them. */
    data class Contents(val base: String, val splits: List<String>, val expansions: List<String>) {
        val packages: List<String> get() = listOf(base) + splits
    }

    /**
     * Which entry is the app and which are its pieces.
     *
     * [manifestJson], when the packager wrote one, decides the base: a bundle may hold more than one
     * package that is not a `config.`/`split.` piece, and only the manifest says which one the others
     * attach to. A manifest naming a package the archive does not contain is not a description of *this*
     * file, so it is ignored rather than half-believed. Without one the naming convention has to be
     * enough, and exactly one non-piece is the only answer that is not a guess — an archive with two
     * candidate bases gets no answer at all, because installing the wrong one is worse.
     */
    internal fun contentsOf(entries: List<String>, manifestJson: String?): Contents? {
        val names = entries.mapNotNull(::entryName)
        val packages = names.filter { it.endsWith(".apk", ignoreCase = true) }.distinct()
        if (packages.isEmpty()) return null
        val declared = declaredBase(manifestJson, packages)
        val base = declared ?: packages.filterNot(::isPiece).singleOrNull() ?: return null
        return Contents(
            base = base,
            splits = packages.filterNot { it == base },
            expansions = names.filter { it.endsWith(".obb", ignoreCase = true) }.distinct(),
        )
    }

    /** `config.arm64_v8a.apk`, `split_config.en.apk` — a piece of an app rather than the app. */
    internal fun isPiece(name: String): Boolean = PIECE.matches(name)

    private val PIECE = Regex("^(config|split)[._].*\\.apk$", RegexOption.IGNORE_CASE)

    /**
     * The archive's own entry names, read without unpacking anything.
     *
     * Null means the file is not a readable zip, which is a different answer from "a zip with no
     * package in it" — the caller says those apart, and the user deserves to know which one happened.
     */
    fun entriesIn(source: File): List<String>? = runCatching {
        ZipFile(source).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
    }.getOrNull()

    /** What an archive holds, or null when it holds no package this app can install. */
    fun read(source: File): Contents? = runCatching {
        ZipFile(source).use { zip ->
            val manifest = zip.getEntry(MANIFEST)?.let { zip.getInputStream(it).readBytes().decodeToString() }
            contentsOf(zip.entries().asSequence().map { it.name }.toList(), manifest)
        }
    }.getOrNull()

    /**
     * Writes the packages inside [source] into [dir], each named [prefix] + its entry name.
     *
     * The prefix is the queue row's own (`postId-<first 8 of the id>-`), which is what makes the unpacked
     * packages that row's bytes to every other rule in the engine — including the sweep that deletes
     * cached APKs no row owns. Only packages come out: an expansion file stays inside the archive, where
     * [ObbInstaller] already reads them from, because copying a gigabyte out of a zip in order to copy it
     * again somewhere else spends space the user paid for on something else.
     *
     * The archive is read through its central directory rather than streamed, because the packs that put
     * these bundles together store the packages uncompressed and leave each entry's size to the data
     * descriptor that follows it. A streamer that has to know how many bytes an entry is before it will
     * hand any over stops at zero — which is how a whole catalogue build reads as a damaged file.
     *
     * @return the files in [Contents.packages] order — base first — or null when nothing usable came out,
     *   in which case no partial set is left behind.
     */
    fun extract(source: File, dir: File, prefix: String, contents: Contents): List<File>? {
        val written = LinkedHashMap<String, File>()
        val ok = runCatching {
            dir.mkdirs()
            ZipFile(source).use { zip ->
                val entries = LinkedHashMap<String, ZipEntry>()
                val all = zip.entries()
                while (all.hasMoreElements()) {
                    val entry = all.nextElement()
                    if (!entry.isDirectory) entryName(entry.name)?.let { entries.putIfAbsent(it, entry) }
                }
                contents.packages.forEach { name ->
                    val entry = entries[name] ?: return@forEach
                    val target = File(dir, "$prefix$name")
                    // A piece the row already holds is kept as it is: unpacking is a recovery path, not a
                    // re-download. One that came out empty is written again, because it is no piece.
                    if (!target.exists() || target.length() == 0L) {
                        zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                    }
                    written[name] = target
                }
            }
        }.isSuccess
        val files = contents.packages.mapNotNull { written[it] }
        if (!ok || files.size != contents.packages.size || files.any { it.length() == 0L }) {
            written.values.forEach { runCatching { it.delete() } }
            return null
        }
        return files
    }

    /**
     * The base the manifest declares, if it names one that is actually in the archive.
     *
     * `{"xapk_version":2,"package_name":"…","split_apks":[{"file":"app.apk","id":"base"},…]}` is the
     * shape on the catalogue's bundles. The `file` may carry a path, so it goes through the same
     * basename rule as the entry list; anything malformed answers null and the naming convention is
     * tried instead.
     */
    private fun declaredBase(manifestJson: String?, packages: List<String>): String? {
        val json = manifestJson ?: return null
        val declared = runCatching {
            val list = JSONObject(json).optJSONArray("split_apks") ?: return@runCatching null
            (0 until list.length()).mapNotNull { i ->
                val entry = list.optJSONObject(i) ?: return@mapNotNull null
                entryName(entry.optString("file")).takeIf { entry.optString("id").equals("base", true) }
            }.firstOrNull()
        }.getOrNull()
        return declared?.takeIf { it in packages }
    }

    /**
     * An entry name reduced to a file name this app is willing to write.
     *
     * The path an archive puts a file behind is the packager's business; dropping it is also what stops
     * a `../../` entry writing outside the directory it was given. [Obb.safeSegment] is the same gate the
     * expansion path uses, so a name that cannot survive a shell argument cannot survive here either.
     */
    private fun entryName(entry: String): String? {
        val leaf = entry.substringAfterLast('/').substringAfterLast('\\')
        return leaf.takeIf { it.isNotBlank() && Obb.safeSegment(it) != null }
    }
}
