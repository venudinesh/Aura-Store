package dev.aurastore.store.install

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Puts a game's expansion files where Android looks for them.
 *
 * There is no API for this. `Android/obb` is write-blocked for third-party apps by scoped storage,
 * even with all-files access, so exactly two outcomes exist and this file promises only those:
 *
 * * **root** — the device grants `su`, the files are copied into `Android/obb/<package>` directly;
 * * **manual** — the files are exported to `Downloads/AuraStore/<package>` with the destination path
 *   spelled out, which is what every other installer on this catalogue ends up doing.
 *
 * Reporting a download as installed would leave the game silently re-downloading its own data on
 * first launch, so the caller has to show whichever of these happened.
 */
object ObbInstaller {

    sealed interface Outcome {
        /** Copied into [dir]; the data is where Android expects it. */
        data class Placed(val names: List<String>, val dir: String) : Outcome

        /** No way to write `Android/obb`: files are in [exportedTo], to be moved to [dir]. */
        data class Manual(val names: List<String>, val exportedTo: String, val dir: String) : Outcome

        /** Nothing could be placed, with a reason about the file rather than a status code. */
        data class Refused(val message: String) : Outcome
    }

    /**
     * @param id the queue row's id, used to keep two placements from sharing a work directory.
     * @param packageHint package of the app this data belongs to, when the archive's own file names do
     *   not declare one — usually the parsed APK sitting next to it in the queue.
     * @param versionCode that app's version code, used to rename `main.<code>.<pkg>.obb` so the game
     *   recognises the file.
     */
    suspend fun place(
        context: Context,
        id: String,
        source: File,
        packageHint: String? = null,
        versionCode: Long? = null,
    ): Outcome = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() == 0L) {
            return@withContext Outcome.Refused("The downloaded file is no longer on this device.")
        }
        val work = File(context.cacheDir, "obb/$id")
        val staged = runCatching { stage(source, work) }.getOrElse { emptyList() }
        if (staged.isEmpty()) {
            return@withContext Outcome.Refused("No expansion file was found inside ${source.name}.")
        }
        val packageName = Obb.safeSegment(staged.mapNotNull { Obb.packageOf(it.name) }.firstOrNull()
            ?: packageHint ?: "")
        if (packageName == null) {
            return@withContext Outcome.Refused(
                "This archive does not name the app it belongs to, and the app itself has not been " +
                    "read yet. Install the package first, then add the data.",
            )
        }
        val dir = Obb.obbDir(storageRoot(), packageName)
        val targets = staged.map { file ->
            File(dir, Obb.safeSegment(Obb.canonicalName(file.name, packageName, versionCode) ?: file.name)
                ?: return@withContext Outcome.Refused("A file inside the archive has an unusable name."))
        }
        if (Root.available() && copyAsRoot(targets zip staged, dir)) {
            runCatching { work.deleteRecursively() }
            return@withContext Outcome.Placed(targets.map { it.name }, dir)
        }
        // Either there is no su, or su was granted and the write still failed. Both end the same way:
        // the files leave the cache for Downloads and the user is told exactly where they must go.
        val exported = staged.indices.mapNotNull { i ->
            ApkExport.save(context, staged[i], targets[i].name, "AuraStore/$packageName", OBB_MIME)
        }
        runCatching { work.deleteRecursively() }
        if (exported.isEmpty()) {
            Outcome.Refused("The expansion file could not be written anywhere on this device.")
        } else {
            Outcome.Manual(targets.map { it.name }, exported.first(), dir)
        }
    }

    private const val OBB_MIME = "application/obb"

    /**
     * The expansion files an artefact carries, by name — without unpacking anything. Used to verify a
     * download before it is called ready, and to answer "which app is this for" from the file itself.
     */
    fun expansionsIn(source: File): List<String> {
        if (!source.isFile) return emptyList()
        if (Obb.isObbName(source.name)) return listOf(source.name)
        return runCatching {
            val names = mutableListOf<String>()
            ZipFile(source).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".obb", ignoreCase = true)) {
                        names += entry.name.substringAfterLast('/')
                    }
                }
            }
            names.distinct()
        }.getOrDefault(emptyList())
    }

    /**
     * Pulls the expansion files out of the archive the catalogue shipped.
     *
     * A `.obb` downloaded bare is used where it lies — copying a gigabyte out of the way first would
     * only waste the space. Inside a zip only `.obb` entries are taken, by their basename: the folder
     * an archive puts them in is the packager's business, and dropping the path is also what keeps a
     * `../../` entry from writing outside the work directory.
     */
    internal fun stage(source: File, work: File): List<File> {
        if (Obb.isObbName(source.name)) return listOf(source)
        work.deleteRecursively()
        work.mkdirs()
        val out = mutableListOf<File>()
        // The central directory, not a stream: packs that store their entries uncompressed leave each
        // entry's size to the data descriptor after the bytes, and a streamer that has to know how much
        // to read before it reads is handed nothing. Same reason Xapk.extract reads this way.
        ZipFile(source).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = Obb.safeSegment(entry.name.substringAfterLast('/')) ?: continue
                if (!name.endsWith(".obb", ignoreCase = true)) continue
                val target = File(work, name)
                // Two entries with the same basename: the first wins, the second would overwrite a
                // file that is already complete.
                if (!target.exists()) {
                    zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                    if (target.length() > 0) out += target
                }
            }
        }
        return out
    }

    private fun storageRoot(): String =
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

    /** Every file copied and readable at its full size; false on the first failure. */
    private fun copyAsRoot(pairs: List<Pair<File, File>>, dir: String): Boolean {
        if (Root.shell("mkdir -p ${shellQuote(dir)}").first != 0) return false
        pairs.forEach { (target, source) ->
            if (source.length() == 0L) return false
            val to = shellQuote(target.absolutePath)
            val from = shellQuote(source.absolutePath)
            if (Root.shell("cp -f $from $to && chmod 660 $to && test -s $to").first != 0) return false
        }
        return true
    }

    /**
     * Quotes one argument. Every value here is a path this object or [Obb] built and validated, so a
     * quote or a newline reaching this point means something upstream broke — refuse loudly instead of
     * handing a shell a command it did not intend.
     */
    private fun shellQuote(value: String): String {
        require(!value.contains('\'') && !value.contains('\n')) { "unsafe path: $value" }
        return "'$value'"
    }

    /**
     * `su` on this device.
     *
     * Probed once per process: the check costs a fork, and the first attempt may raise a root manager's
     * prompt that a second queue row must not raise again.
     */
    private object Root {
        @Volatile private var cached: Boolean? = null

        fun available(): Boolean = cached ?: run {
            val answer = shell("id").let { it.first == 0 && it.second.contains("uid=0") }
            cached = answer
            answer
        }

        /** Runs one command through `su`; returns its exit code with stdout+stderr joined. */
        fun shell(command: String, timeoutMs: Long = 60_000): Pair<Int, String> = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = StringBuilder()
            val reader = Thread {
                runCatching { process.inputStream.bufferedReader().forEachLine { output.append(it).append('\n') } }
            }.apply { isDaemon = true; start() }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return 1 to "timeout"
            }
            reader.join(1_000)
            process.exitValue() to output.toString()
        }.getOrElse { 1 to it.message.orEmpty() }
    }
}
