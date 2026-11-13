package dev.aurastore.store.install

import dev.aurastore.store.data.DownloadFile
import dev.aurastore.store.data.PostDetail
import dev.aurastore.store.data.PostSummary
import dev.aurastore.store.data.Version
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The two rules that decide whether a game's extra data lands where the game will find it: which
 * artefacts are data, and what a data file has to be *called*. Both were written against the names
 * the catalogue actually publishes, including the ones it publishes wrongly.
 */
class ObbTest {

    private fun file(role: String, kind: String, url: String) =
        DownloadFile(kind = kind, role = role, abi = "universal", sizeText = "147M", url = url, note = null, recommended = false)

    // ---------------------------------------------------------------- what is data

    @Test
    fun `an obb role or extension is data, whatever the label says`() {
        assertTrue(Obb.isDataFile(file("obb", "obb", "https://cdn.example/main.123.com.game.obb")))
        assertTrue(Obb.isDataFile(file("main", "zip", "https://cdn.example/com.game.obb.zip")))
        // The catalogue has shipped an archive tagged as the main artefact; the name is the tell.
        assertTrue(Obb.isDataFile(file("main", "main", "https://cdn.example/data.113974241.com.ea.game.fifa19_row.obb")))
        assertFalse(Obb.isDataFile(file("main", "apk", "https://cdn.example/app-release.apk")))
        assertFalse(Obb.isDataFile(file("", "APK/ARM64", "https://cdn.example/arm64.apk")))
    }

    @Test
    fun `a non-base artefact is data because this client installs one package at a time`() {
        assertTrue(Obb.isDataFile(file("patch", "apk", "https://cdn.example/patch.apk")))
        assertTrue(Obb.isDataFile(file("config", "apk", "https://cdn.example/config.arm64.apk")))
    }

    // ---------------------------------------------------------------- the name rule

    @Test
    fun `an obb name declares its package and version code`() {
        assertTrue(Obb.isObbName("main.113974241.com.ea.game.fifa19_row.obb"))
        assertEquals("com.ea.game.fifa19_row", Obb.packageOf("main.113974241.com.ea.game.fifa19_row.obb"))
        assertEquals(113974241L, Obb.versionCodeOf("main.113974241.com.ea.game.fifa19_row.obb"))
        assertEquals("com.game.pkg", Obb.packageOf("split.42.com.game.pkg.obb"))
        assertEquals("com.game.pkg", Obb.packageOf("patch.42.com.game.pkg.obb"))
    }

    @Test
    fun `a name that ignores the convention still yields its package`() {
        // Renamed archives are the norm in modded builds: no numeric code after the prefix.
        assertEquals("com.game.pkg", Obb.packageOf("main.com.game.pkg.obb"))
        assertNull(Obb.versionCodeOf("main.com.game.pkg.obb"))
    }

    @Test
    fun `anything that is not an obb name is rejected`() {
        assertFalse(Obb.isObbName("main.123.com.game.pkg.zip"))
        assertFalse(Obb.isObbName("obb.123.com.game.pkg.obb"))
        assertFalse(Obb.isObbName("main..obb"))
        assertFalse(Obb.isObbName("data.json"))
        assertNull(Obb.packageOf("main.123.com.game.pkg"))
    }

    @Test
    fun `the canonical name rewrites the code to the package being installed`() {
        val name = "main.113974241.com.ea.game.fifa19_row.obb"
        assertEquals(
            "main.113974242.com.ea.game.fifa19_row.obb",
            Obb.canonicalName(name, "com.ea.game.fifa19_row", 113974242L),
        )
        // Without a code from the APK, the name's own code is kept — but the package is always the
        // one being installed next to it, because that is the directory the file goes into.
        assertEquals("main.113974241.com.other.obb", Obb.canonicalName(name, "com.other", null))
        assertNull(Obb.canonicalName("main.com.x.obb", "com.x", null))
        assertNull(Obb.canonicalName("readme.txt", "com.x", 42L))
    }

    @Test
    fun `a path segment is only accepted when it cannot escape or reach a shell`() {
        assertEquals("com.game.pkg", Obb.safeSegment("com.game.pkg"))
        assertEquals("main.42.com.game.pkg.obb", Obb.safeSegment("main.42.com.game.pkg.obb"))
        assertNull(Obb.safeSegment(""))
        assertNull(Obb.safeSegment("../evil"))
        assertNull(Obb.safeSegment("com/game\\pkg"))
        assertNull(Obb.safeSegment("a; rm -rf /"))
        assertNull(Obb.safeSegment("quoted'apostrophe"))
        assertNull(Obb.safeSegment("x".repeat(201)))
    }

    @Test
    fun `the target directory is the one Android looks in`() {
        // The caller passes the root already trimmed, which is what Environment reports.
        assertEquals(
            "/storage/emulated/0/Android/obb/com.game.pkg",
            Obb.obbDir("/storage/emulated/0", "com.game.pkg"),
        )
    }

    // ---------------------------------------------------------------- pairing

    private fun detail(vararg versions: Version) = PostDetail(
        summary = PostSummary.parse(JSONObject("""{"id":7,"title":"Game"}""")),
        description = "",
        tabs = emptyList(),
        gallery = emptyList(),
        versions = versions.toList(),
        originalUrl = null,
    )

    @Test
    fun `companions are the chosen build's own data files`() {
        val apk = "https://cdn.example/app.apk"
        val obb = "https://liteapks.dev/dl/main.42.com.game.pkg.obb"
        val detail = detail(
            Version(
                label = "1.2.3",
                note = null,
                files = listOf(
                    file("main", "APK", apk),
                    file("obb", "obb", obb),
                    file("obb", "obb", obb), // published twice by the same version entry
                    file("obb", "obb", " "),
                ),
            ),
            Version(label = "1.2.2", note = null, files = listOf(file("obb", "obb", "https://old/x.obb"))),
        )

        assertEquals(listOf(obb), Obb.companions(detail, "1.2.3", apk).map { it.url })
        // An unknown label must not fall back to another build's data.
        assertEquals(emptyList<String>(), Obb.companions(detail, "9.9.9", apk).map { it.url })
    }

    @Test
    fun `a version with only the package has no companions`() {
        val detail = detail(Version(label = "1.0", note = null, files = listOf(file("main", "APK", "https://c/a.apk"))))
        assertTrue(Obb.companions(detail, "1.0", "https://c/a.apk").isEmpty())
    }

    // ---------------------------------------------------------------- archives

    @Test
    fun `expansions are read from a bare obb and from a zip that holds them`() {
        val dir = java.nio.file.Files.createTempDirectory("obb").toFile()
        val bare = File(dir, "main.42.com.game.pkg.obb")
        val zip = File(dir, "com.game.pkg-data.zip")
        try {
            bare.writeBytes(byteArrayOf(1, 2, 3))
            // A loose .obb is already named the way Android wants it, so its own name is the answer.
            assertEquals(listOf("main.42.com.game.pkg.obb"), ObbInstaller.expansionsIn(bare))

            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("payload/main.42.com.game.pkg.obb"))
                out.write(byteArrayOf(7))
                out.closeEntry()
                out.putNextEntry(ZipEntry("payload/icon.png"))
                out.write(byteArrayOf(8))
                out.closeEntry()
            }
            // Inside an archive only the basename survives; the path inside is the packager's business.
            assertEquals(listOf("main.42.com.game.pkg.obb"), ObbInstaller.expansionsIn(zip))
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `a zip with no expansion inside says so instead of pretending`() {
        val dir = java.nio.file.Files.createTempDirectory("obb").toFile()
        val zip = File(dir, "bundle.zip")
        try {
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("classes.dex"))
                out.write(byteArrayOf(1))
                out.closeEntry()
            }
            assertTrue(ObbInstaller.expansionsIn(zip).isEmpty())
        } finally {
            zip.delete()
            dir.delete()
        }
    }
}
