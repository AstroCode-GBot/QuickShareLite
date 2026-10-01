package com.novacode.quicksharelite.transfer

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

class FileStore(private val context: Context) {
    private val stagingDir: File get() = File(context.cacheDir, "staging").apply { mkdirs() }
    private val receiveDir: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "Received").apply { mkdirs() }

    fun stageSource(uri: Uri, sessionId: String): File {
        val out = File(stagingDir, "$sessionId.source.partial")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file." }
            out.outputStream().buffered(128 * 1024).use { output -> input.copyTo(output, 128 * 1024) }
        }
        return out
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun destination(name: String): File {
        val safe = sanitize(name)
        val base = File(receiveDir, safe)
        if (!base.exists()) return base
        val stem = base.nameWithoutExtension
        val ext = base.extension.takeIf { it.isNotEmpty() }?.let { ".${it}" }.orEmpty()
        var i = 1
        while (true) {
            val candidate = File(receiveDir, "$stem ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace("/", "_").replace("\\", "_").replace("..", "_").trim().trimStart('.')
        return cleaned.take(180).ifBlank { "received-file" }
    }

    fun completePartial(partial: File, final: File) {
        require(partial.exists()) { "Partial file missing" }
        if (!partial.renameTo(final)) {
            partial.copyTo(final, overwrite = false)
            partial.delete()
        }
    }

    fun deleteQuietly(file: File) { runCatching { file.delete() } }
}
