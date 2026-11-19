package dev.aurastore.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The name and type a saved download carries.
 *
 * A package is filed under its title and version; an archive keeps the name it was quoted, because
 * a `.zip`, `.rar` or `.iso` is not an installer and its extension is the only thing that says what
 * the bytes are. The same rule decides the mime MediaStore files it under, which is what makes the
 * saved archive open as an archive instead of as a package.
 */
class ApkExportTest {

    @Test
    fun `an archive keeps its own name`() {
        assertEquals("Game data.zip", ApkExport.savedName("Game data.zip", "Some Game", "1.2"))
        assertEquals("pack.rar", ApkExport.savedName("pack.rar", "Some Game", "1.2"))
        assertEquals("disc.iso", ApkExport.savedName("disc.iso", "Some Game", "1.2"))
        assertEquals("main.42.com.game.obb", ApkExport.savedName("main.42.com.game.obb", "Some Game", "1.2"))
    }

    @Test
    fun `an extensionless archive keeps its name`() {
        assertEquals("download", ApkExport.savedName("download", "Some Game", "1.2"))
    }

    @Test
    fun `a package is renamed to its title and version`() {
        assertEquals("Some Game-1.2.apk", ApkExport.savedName("base.apk", "Some Game", "1.2"))
        assertEquals("Some Game-1.2.apks", ApkExport.savedName("bundle.apks", "Some Game", "1.2"))
        assertEquals("Some Game-1.2.xapk", ApkExport.savedName("bundle.xapk", "Some Game", "1.2"))
    }

    @Test
    fun `a package title cannot escape the folder or the name limit`() {
        val name = ApkExport.savedName("base.apk", "A/B:C", "1.0")
        assertEquals("A_B_C-1.0.apk", name)
        val long = ApkExport.savedName("base.apk", "x".repeat(200), "1.0")
        assertEquals(84, long.length)
    }

    @Test
    fun `an archive path is stripped of its separators`() {
        assertEquals("game.zip", ApkExport.savedName("a/b/game.zip", "Some Game", "1.2"))
        assertEquals("game.zip", ApkExport.savedName("a\\b\\game.zip", "Some Game", "1.2"))
    }

    @Test
    fun `the installer owns packages and nothing else`() {
        assertTrue(ApkExport.isPackage("Game.apk"))
        assertTrue(ApkExport.isPackage("bundle.apks"))
        assertTrue(ApkExport.isPackage("bundle.xapk"))
        assertTrue(ApkExport.isPackage("GAME.APK"))
        assertFalse(ApkExport.isPackage("game.zip"))
        assertFalse(ApkExport.isPackage("game.rar"))
        assertFalse(ApkExport.isPackage("game.iso"))
        assertFalse(ApkExport.isPackage("download"))
    }

    @Test
    fun `every saved name gets the type that opens it`() {
        assertEquals("application/vnd.android.package-archive", ApkExport.mimeFor("Game.apk"))
        assertEquals("application/zip", ApkExport.mimeFor("Game.apks"))
        assertEquals("application/zip", ApkExport.mimeFor("Game.xapk"))
        assertEquals("application/zip", ApkExport.mimeFor("Game.zip"))
        assertEquals("application/vnd.rar", ApkExport.mimeFor("Game.rar"))
        assertEquals("application/x-7z-compressed", ApkExport.mimeFor("Game.7z"))
        assertEquals("application/x-iso9660-image", ApkExport.mimeFor("Game.iso"))
        assertEquals("application/octet-stream", ApkExport.mimeFor("Game.bin"))
    }
}
