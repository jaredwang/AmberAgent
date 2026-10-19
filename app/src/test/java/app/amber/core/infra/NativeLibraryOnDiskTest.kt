package app.amber.core.infra

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeLibraryOnDiskTest {
    private lateinit var testRoot: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        val base = RuntimeEnvironment.getApplication()
        testRoot = File(base.cacheDir, "native-lib-on-disk-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = File(testRoot, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = File(testRoot, "no_backup").apply { mkdirs() }
        }
    }

    @Test
    fun `prefers a file already extracted into nativeLibraryDir`() {
        val nativeDir = File(testRoot, "native-libs").apply { mkdirs() }
        val extracted = File(nativeDir, "libsimple.so").apply { writeText("from-native-dir") }
        context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath

        val resolved = NativeLibraryOnDisk.resolveOrNull(context, "libsimple.so")

        assertEquals(extracted.absolutePath, resolved?.absolutePath)
        assertEquals("from-native-dir", resolved?.readText())
    }

    @Test
    fun `copies the library out of the APK when nativeLibraryDir is empty`() {
        val nativeDir = File(testRoot, "empty-native").apply { mkdirs() }
        context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath
        val apk = File(testRoot, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libsimple.so"))
            zip.write("from-apk".toByteArray())
            zip.closeEntry()
        }
        context.applicationInfo.sourceDir = apk.absolutePath
        context.applicationInfo.splitSourceDirs = emptyArray()

        val resolved = NativeLibraryOnDisk.resolveOrNull(context, "libsimple.so")

        assertTrue(resolved!!.isFile)
        assertEquals("from-apk", resolved.readText())
        assertTrue(resolved.absolutePath.startsWith(context.noBackupFilesDir.absolutePath))
        val again = NativeLibraryOnDisk.resolveOrNull(context, "libsimple.so")
        assertEquals(resolved.absolutePath, again?.absolutePath)
    }

    @Test
    fun `returns null when the APK does not contain the library`() {
        val nativeDir = File(testRoot, "empty-native").apply { mkdirs() }
        context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath
        val apk = File(testRoot, "empty.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        context.applicationInfo.sourceDir = apk.absolutePath
        context.applicationInfo.splitSourceDirs = emptyArray()

        assertNull(NativeLibraryOnDisk.resolveOrNull(context, "libsimple.so"))
    }
}
