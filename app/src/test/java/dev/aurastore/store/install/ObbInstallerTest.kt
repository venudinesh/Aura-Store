package dev.aurastore.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What [ObbInstaller] takes out of a data artefact and where it puts it — the half of expansion delivery
 * that needs no Android, and therefore the half with no excuse for being wrong.
 *
 * Staging is the step between a verified download and a directory Android will not let this app write to,
 * so its rules decide whether a game's data has any chance of arriving: which entries count as data, what
 * an archive's own folder structure is allowed to do to a path, and what happens when a pack keeps its
 * sizes somewhere a reader might not look.
 */
class ObbInstallerTest {

    private fun tempDir(): File = Files.createTempDirectory("obb").toFile()

    private fun archive(named: List<Pair<String, String>>): File {
        val file = Files.createTempFile("data", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { out ->
            named.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun wipe(vararg dirs: File?) {
        dirs.filterNotNull().forEach { dir ->
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dir.delete()
        }
    }

    @Test
    fun `only data comes out of an archive, and the archive's folders do not choose where`() {
        val source = archive(
            listOf(
                // The layout a packager writes, the path a hostile archive writes, and two entries that
                // are not data at all.
                "Android/obb/com.game/main.7.com.game.obb" to "main-data",
                "../../escape.obb" to "escape",
                "tkstudio.autoresponderfortg.apk" to "not data",
                "readme.txt" to "not data",
            ),
        )
        val dir = tempDir()
        val work = File(dir, "work")
        try {
            val expected = listOf("escape.obb", "main.7.com.game.obb")
            assertEquals(expected, ObbInstaller.stage(source, work).map { it.name }.sorted())
            // The escape attempt is reduced to its basename and lands inside the directory it was given;
            // nothing at all is written beside it, above it, or in the temp root.
            assertEquals(expected, work.listFiles()?.map { it.name }?.sorted())
            assertFalse(File(dir, "escape.obb").exists())
        } finally {
            wipe(dir, source)
        }
    }

    @Test
    fun `an archive that leaves its sizes to a data descriptor still stages its data`() {
        // These packs store their entries uncompressed and write each length after the bytes, so a reader
        // that streams the archive is told "unknown", copies nothing, and the engine reports "no expansion
        // file was found inside" for a file that has one. Same fixture the bundle unpacker is pinned with,
        // because it is the same pack.
        val dir = tempDir()
        val source = File(dir, "stored-descriptor.xapk")
        try {
            javaClass.classLoader!!.getResourceAsStream("fixtures/stored-descriptor.xapk")!!
                .use { input -> source.outputStream().use(input::copyTo) }
            val staged = ObbInstaller.stage(source, File(dir, "work"))
            assertEquals(listOf("main.42.com.game.pkg.obb"), staged.map { it.name })
            assertEquals(128L, staged.single().length())
        } finally {
            wipe(dir)
        }
    }

    @Test
    fun `a bare obb file is used where it lies`() {
        val dir = tempDir()
        val source = File(dir, "main.9.com.game.pkg.obb").apply { writeText("already the right shape") }
        try {
            val staged = ObbInstaller.stage(source, File(dir, "work"))
            assertEquals(listOf(source.absolutePath), staged.map { it.absolutePath })
            // Copying a gigabyte out of the way to place it again spends space the user paid for twice.
            assertFalse(File(dir, "work").exists())
        } finally {
            wipe(dir)
        }
    }

    @Test
    fun `an archive with nothing to place says so instead of staging an empty directory`() {
        val source = archive(listOf("app.apk" to "base", "manifest.json" to "{}"))
        val dir = tempDir()
        try {
            assertEquals(emptyList<String>(), ObbInstaller.stage(source, File(dir, "work")).map { it.name })
        } finally {
            wipe(dir, source)
        }
    }
}
