package com.resq.app.map

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Copies only bundled basemap cache files; saved application data and preferences are untouched. */
object PmtilesAssetCopier {
    @Synchronized
    fun ensureCopied(context: Context, mapPackage: BasemapPackage): File {
        val directory = File(context.filesDir, "pmtiles")
        check(directory.isDirectory || directory.mkdirs()) { "Unable to prepare map storage" }
        val destination = File(directory, mapPackage.archive)
        val marker = File(directory, "${mapPackage.archive}.version")
        val expectedMarker = "${mapPackage.version}:${mapPackage.sizeBytes}:${mapPackage.sha256}"
        if (destination.length() == mapPackage.sizeBytes && destination.hasPmtilesHeader() &&
            marker.isFile && marker.readText() == expectedMarker) {
            return destination
        }

        val temporary = File(directory, "${mapPackage.archive}.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("pmtiles/${mapPackage.archive}").use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() == mapPackage.sizeBytes && temporary.hasPmtilesHeader()) {
                "Incomplete bundled map archive"
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            check(hash == mapPackage.sha256) { "Bundled map checksum does not match its metadata" }
            replaceFile(temporary, destination)
            val temporaryMarker = File(directory, "${mapPackage.archive}.version.tmp")
            temporaryMarker.writeText(expectedMarker)
            replaceFile(temporaryMarker, marker)
            return destination
        } finally {
            temporary.delete()
        }
    }

    private fun File.hasPmtilesHeader(): Boolean = isFile && inputStream().use { input ->
        val header = ByteArray(8)
        input.read(header) == 8 && header.copyOfRange(0, 7).toString(Charsets.US_ASCII) == "PMTiles" &&
            header[7].toInt() == 3
    }

    private fun replaceFile(temporary: File, destination: File) {
        // Android's rename is atomic and normally replaces the previous file directly.
        if (temporary.renameTo(destination)) return
        val backup = File(destination.parentFile, "${destination.name}.previous")
        check(!destination.exists() || destination.renameTo(backup)) { "Unable to replace cached map" }
        if (!temporary.renameTo(destination)) {
            backup.renameTo(destination)
            error("Unable to install bundled map")
        }
        backup.delete()
    }
}
