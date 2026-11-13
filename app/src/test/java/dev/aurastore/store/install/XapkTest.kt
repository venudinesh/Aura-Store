package dev.aurastore.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The rules a `.xapk` install turns on: which artefact is a bundle, which entry inside it is the app, and
 * what unpacking is allowed to write where.
 *
 * These are the parts of the format that can be quietly wrong. A bundle that picks the wrong base installs
 * a config APK as an application; an unpacking that trusts an archive's paths writes outside the cache
 * directory; and neither failure is visible until the installer refuses or the user finds bytes they never
 * asked for. Everything here runs on archives built in the test, because deciding any of it needs no
 * Android — the catalogue's own manifest, the naming convention, and a zip are enough.
 */
class XapkTest {

    /** The catalogue's shape for post 8333: a base, three configs, and the descriptor beside them. */
    private val catalogueManifest =
        """{"xapk_version":2,"package_name":"tkstudio.autoresponderfortg","split_apks":[""" +
            """{"file":"tkstudio.autoresponderfortg.apk","id":"base"},""" +
            """{"file":"config.arm64_v8a.apk","id":"arm64_v8a"},""" +
            """{"file":"config.en.apk","id":"en"},""" +
            """{"file":"config.xxhdpi.apk","id":"xxhdpi"}]}"""

    private fun archive(named: List<Pair<String, String>>): File {
        val file = Files.createTempFile("bundle", ".xapk").toFile()
        ZipOutputStream(file.outputStream()).use { out ->
            named.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun tempDir(): File = Files.createTempDirectory("xapk").toFile()

    private fun contentsOf(vararg entries: String, manifest: String? = null) =
        Xapk.contentsOf(entries.toList(), manifest)

    // ---------------------------------------------------------------- what is a bundle

    @Test
    fun `a bundle is the extension that is not a single package`() {
        assertTrue(Xapk.isBundle("Game.xapk"))
        assertTrue(Xapk.isBundle("game.XAPK"))
        assertTrue(Xapk.isBundle("game.apks"))
        assertFalse(Xapk.isBundle("game.apk"))
        // The catalogue's game-data archives are zips, and a zip is not a bundle: those rows are data,
        // and the engine must not send one to an installer.
        assertFalse(Xapk.isBundle("com.game.pkg-data.zip"))
        assertFalse(Xapk.isBundle("game.obb"))
    }

    @Test
    fun `a name still carrying a signed link's query is cut before it is judged`() {
        assertTrue(Xapk.isBundle("game.xapk?token=abc&Expires=1"))
        assertTrue(Xapk.isBundle("game.xapk#download"))
        assertFalse(Xapk.isBundle("game.apk?token=abc"))
    }

    // ---------------------------------------------------------------- which entry is the app

    @Test
    fun `the naming convention is enough when there is one app and some pieces`() {
        val contents = contentsOf("app.apk", "config.arm64_v8a.apk", "split_config.en.apk")
        assertEquals("app.apk", contents?.base)
        assertEquals(listOf("config.arm64_v8a.apk", "split_config.en.apk"), contents?.splits)
    }

    @Test
    fun `a piece is never the base`() {
        assertTrue(Xapk.isPiece("config.arm64_v8a.apk"))
        assertTrue(Xapk.isPiece("split_config.en.apk"))
        assertTrue(Xapk.isPiece("Config.EN.apk"))
        assertFalse(Xapk.isPiece("app.apk"))
        // A dot is not the convention's separator, and `configurator.apk` is an app name, not a piece.
        assertFalse(Xapk.isPiece("config.apk"))
        assertFalse(Xapk.isPiece("configurator.apk"))
    }

    @Test
    fun `the manifest decides when the convention cannot`() {
        // Two packages that are not pieces: only the descriptor says which one the others attach to.
        val base = "tkstudio.autoresponderfortg.apk"
        val contents = contentsOf("other.apk", base, "config.arm64_v8a.apk", manifest = catalogueManifest)
        assertEquals(base, contents?.base)
        assertEquals(listOf("other.apk", "config.arm64_v8a.apk"), contents?.splits)
    }

    @Test
    fun `an archive with two candidate bases and no manifest gets no answer`() {
        // Guessing here installs the wrong app, so the bundle fails instead.
        assertNull(contentsOf("one.apk", "two.apk", "config.en.apk"))
    }

    @Test
    fun `a manifest naming a package that is not inside is ignored, not half-believed`() {
        val contents = contentsOf("app.apk", "config.en.apk", manifest = """{"split_apks":[{"file":"gone.apk","id":"base"}]}""")
        assertEquals("app.apk", contents?.base)
        // And when ignoring it leaves the convention with two candidates, the answer is still no answer.
        assertNull(contentsOf("one.apk", "two.apk", manifest = """{"split_apks":[{"file":"gone.apk","id":"base"}]}"""))
    }

    @Test
    fun `a manifest is read for its base however it spells the file or the id`() {
        val manifest = """{"split_apks":[{"file":"nested/app.apk","id":"BASE"}]}"""
        assertEquals("app.apk", contentsOf("app.apk", "config.en.apk", manifest = manifest)?.base)
    }

    @Test
    fun `a manifest that is not the shape expected falls back to the convention`() {
        listOf(
            "not json at all",
            """{"split_apks":"base"}""",
            """{"split_apks":[]}""",
            """{"split_apks":[{"id":"base"}]}""",
        ).forEach { broken ->
            assertEquals("app.apk", contentsOf("app.apk", "config.en.apk", manifest = broken)?.base)
        }
    }

    @Test
    fun `expansion files are listed but are not packages`() {
        val contents = contentsOf("app.apk", "main.42.com.game.pkg.obb", "readme.txt")
        assertEquals(listOf("main.42.com.game.pkg.obb"), contents?.expansions)
        assertEquals(listOf("app.apk"), contents?.packages)
    }

    @Test
    fun `the paths an archive puts files behind are the packager's business`() {
        val contents = contentsOf("split/app.apk", "splits/config.en.apk", "payload/readme.txt")
        assertEquals("app.apk", contents?.base)
        assertEquals(listOf("config.en.apk"), contents?.splits)
    }

    @Test
    fun `an archive with nothing installable in it is not a bundle`() {
        assertNull(contentsOf("classes.dex", "resources.arsc"))
        assertNull(contentsOf("main.42.com.game.pkg.obb"))
        // The same name published twice inside one archive is one package.
        assertEquals(1, contentsOf("app.apk", "nested/app.apk")?.packages?.size)
    }

    // ---------------------------------------------------------------- unpacking

    @Test
    fun `only the packages come out, named for the row that asked for them`() {
        val source = archive(
            listOf(
                "tkstudio.autoresponderfortg.apk" to "base-bytes",
                "config.arm64_v8a.apk" to "arm64",
                "manifest.json" to catalogueManifest,
                "payload/main.42.com.game.pkg.obb" to "data-bytes",
            ),
        )
        val dir = tempDir()
        try {
            val contents = Xapk.read(source)
            assertEquals("tkstudio.autoresponderfortg.apk", contents?.base)
            assertEquals(listOf("main.42.com.game.pkg.obb"), contents?.expansions)
            val files = Xapk.extract(source, dir, "8333-3f2a1b90-", contents!!)
            assertEquals(
                listOf("8333-3f2a1b90-tkstudio.autoresponderfortg.apk", "8333-3f2a1b90-config.arm64_v8a.apk"),
                files?.map { it.name },
            )
            assertEquals("base-bytes", files?.first()?.readText())
            // The base leads, because that is the package the installer has to see first, and the
            // expansion stays inside the archive — ObbInstaller reads it from there.
            assertEquals(files?.map { it.name }?.sorted(), dir.list()?.sorted())
        } finally {
            source.delete()
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `a pack that leaves its sizes to a data descriptor still unpacks`() {
        // The catalogue stores its packages uncompressed and writes each entry's length only after the
        // bytes, so an unpacker that streams the archive is told nothing about how much to read and hands
        // back an empty file — which is how a whole 20 MB build reported itself as damaged. This fixture
        // is that shape, header for header, because a stream is the only way to meet it.
        val dir = tempDir()
        val source = File(dir, "descriptor-pack.xapk")
        try {
            javaClass.classLoader!!.getResourceAsStream("fixtures/stored-descriptor.xapk")!!
                .use { input -> source.outputStream().use(input::copyTo) }
            assertEquals(4, Xapk.entriesIn(source)?.size)
            val contents = Xapk.read(source)
            assertEquals("tkstudio.autoresponderfortg.apk", contents?.base)
            assertEquals(listOf("main.42.com.game.pkg.obb"), contents?.expansions)
            val files = Xapk.extract(source, File(dir, "out"), "8333-3f2a1b90-", contents!!)
            assertEquals(listOf("8333-3f2a1b90-tkstudio.autoresponderfortg.apk", "8333-3f2a1b90-config.arm64_v8a.apk"), files?.map { it.name })
            assertEquals(listOf(256L, 200L), files?.map { it.length() })
        } finally {
            File(dir, "out").listFiles()?.forEach { it.delete() }
            File(dir, "out").delete()
            source.delete()
            dir.delete()
        }
    }

    @Test
    fun `a piece already on disk is not written again`() {
        val source = archive(listOf("app.apk" to "base-bytes", "config.en.apk" to "en"))
        val dir = tempDir()
        try {
            val contents = Xapk.read(source)!!
            val first = Xapk.extract(source, dir, "7-aaaa-", contents)
            assertEquals(2, first?.size)
            val rewritten = File(dir, "7-aaaa-app.apk")
            rewritten.writeText("kept")
            val second = Xapk.extract(source, dir, "7-aaaa-", contents)
            assertEquals(first?.map { it.absolutePath }, second?.map { it.absolutePath })
            // Unpacking is a recovery path, not a re-download: a file the row still holds stays as it is.
            assertEquals("kept", rewritten.readText())
        } finally {
            source.delete()
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `an archive that cannot yield the whole set yields nothing`() {
        val source = archive(listOf("app.apk" to "base-bytes"))
        val dir = tempDir()
        try {
            // A row restored from disk can name pieces its archive no longer holds. Half a split set
            // cannot install, so the app that did come out is deleted rather than left to be installed.
            val contents = Xapk.Contents(base = "app.apk", splits = listOf("gone.apk"), expansions = emptyList())
            assertNull(Xapk.extract(source, dir, "7-bbbb-", contents))
            assertTrue(dir.list()?.isEmpty() != false)
        } finally {
            source.delete()
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `an entry that tries to escape is reduced to a file inside the directory it was given`() {
        val source = archive(listOf("app.apk" to "base", "../../evil.apk" to "evil"))
        val dir = tempDir()
        try {
            val contents = Xapk.read(source)
            // Two non-piece candidates, so no base is claimed — which is the right answer here too.
            assertNull(contents)
            val files = Xapk.extract(source, dir, "7-cccc-", Xapk.Contents("app.apk", listOf("evil.apk"), emptyList()))
            assertEquals(listOf("7-cccc-app.apk", "7-cccc-evil.apk"), files?.map { it.name })
            assertTrue(files!!.all { it.parentFile == dir })
            assertFalse(File(dir.parentFile, "evil.apk").exists())
        } finally {
            source.delete()
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `a name that cannot be a file name is never unpacked`() {
        val source = archive(listOf("app.apk" to "base", "in jar/apk with space.apk" to "nope"))
        val dir = tempDir()
        try {
            // What is not a writable name is not a package either, so the archive reads as holding one
            // app — and unpacking only that app is refused, because a subset of a split set cannot install.
            val contents = Xapk.read(source)
            assertEquals(listOf("app.apk"), contents?.packages)
            assertNull(Xapk.extract(source, dir, "7-dddd-", Xapk.Contents("apk with space.apk", emptyList(), emptyList())))
            assertTrue(dir.list()?.isEmpty() != false)
        } finally {
            source.delete()
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    // ---------------------------------------------------------------- reading a file

    @Test
    fun `a file that is not an archive says so rather than looking empty`() {
        val dir = tempDir()
        val notAZip = File(dir, "game.xapk")
        try {
            notAZip.writeText("this is a truncated download")
            assertNull(Xapk.entriesIn(notAZip))
            assertNull(Xapk.read(notAZip))
            assertNull(Xapk.read(File(dir, "does-not-exist.xapk")))
        } finally {
            notAZip.delete()
            dir.delete()
        }
    }

    @Test
    fun `an archive read from disk reports its entries before anything is unpacked`() {
        val source = archive(listOf("app.apk" to "base", "manifest.json" to catalogueManifest))
        try {
            assertEquals(listOf("app.apk", "manifest.json"), Xapk.entriesIn(source))
        } finally {
            source.delete()
        }
    }
}
