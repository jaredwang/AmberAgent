package app.amber.core.infra

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

/**
 * Resolves a JNI `.so` to a real filesystem path.
 *
 * With `extractNativeLibs=false` (AGP default, required for 16 KB zip
 * alignment) `ApplicationInfo.nativeLibraryDir` is often empty. Callers that
 * `dlopen` a path — SQLite extensions, proot — need a file on disk, so this
 * copies the library out of the APK (or split APK) into no-backup storage.
 */
object NativeLibraryOnDisk {
    fun resolveOrNull(context: Context, fileName: String): File? {
        require(fileName.endsWith(".so")) { "Expected a .so file name, got $fileName" }
        val extracted = File(context.applicationInfo.nativeLibraryDir, fileName)
        if (extracted.isFile && extracted.length() > 0L) return extracted

        val apkPaths = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.forEach { add(it) }
        }.map(::File).filter { it.isFile }
        if (apkPaths.isEmpty()) return null

        val dest = File(context.noBackupFilesDir, "native-libs/$fileName")
        val stamp = File(dest.parentFile, "$fileName.stamp")
        val token = apkPaths.joinToString(separator = "|") { "${it.absolutePath}:${it.length()}:${it.lastModified()}" }
        if (dest.isFile && dest.length() > 0L && stamp.takeIf { it.isFile }?.readText() == token) {
            return dest
        }

        val abiCandidates = (Build.SUPPORTED_ABIS.toList() + "arm64-v8a" + "x86_64")
            .distinct()
            .map { abi -> "lib/$abi/$fileName" }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "$fileName.part")
        for (apk in apkPaths) {
            ZipFile(apk).use { zip ->
                val entry = abiCandidates.firstNotNullOfOrNull { name -> zip.getEntry(name) } ?: return@use
                tmp.outputStream().use { output ->
                    zip.getInputStream(entry).use { input -> input.copyTo(output) }
                }
            }
            if (tmp.isFile && tmp.length() > 0L) {
                if (dest.exists()) dest.delete()
                require(tmp.renameTo(dest)) { "Unable to place $fileName at ${dest.absolutePath}" }
                dest.setReadable(true, false)
                dest.setExecutable(true, false)
                stamp.writeText(token)
                return dest
            }
        }
        tmp.delete()
        return null
    }
}
