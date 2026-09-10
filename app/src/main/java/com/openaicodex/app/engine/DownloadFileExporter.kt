package com.openaicodex.app.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Copies files created by Codex from the private conversation workspace to the
 * user's visible Download/Codex folder.
 *
 * Android 10+ uses MediaStore, so no broad storage permission is required and
 * the result is immediately visible in the system Downloads provider.
 */
class DownloadFileExporter(private val context: Context) {
    private val tag = "DownloadFileExporter"

    fun export(workspace: File, changedPath: String): File? {
        val source = resolveInsideWorkspace(workspace, changedPath) ?: return null
        if (!source.isFile || !source.canRead()) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportWithMediaStore(source)
            } else {
                exportLegacy(source)
            }
        } catch (error: Exception) {
            Log.w(tag, "Dosya Download klasörüne aktarılamadı: ${error.message}")
            null
        }
    }

    private fun resolveInsideWorkspace(workspace: File, rawPath: String): File? {
        val root = workspace.canonicalFile
        val candidate = if (File(rawPath).isAbsolute) File(rawPath) else File(root, rawPath)
        val canonical = candidate.canonicalFile
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return if (canonical.path.startsWith(rootPath) && canonical != root) canonical else null
    }

    private fun exportWithMediaStore(source: File): File? {
        val resolver = context.contentResolver
        val displayName = uniqueDisplayName(source.name)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(source.name))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Codex")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Download akışı açılamadı")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            File(Environment.getExternalStorageDirectory(), "${Environment.DIRECTORY_DOWNLOADS}/Codex/$displayName")
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(source: File): File {
        val downloads = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Codex"
        ).apply { mkdirs() }
        val target = uniqueLegacyTarget(downloads, source.name)
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun uniqueDisplayName(original: String): String {
        val downloads = "${Environment.DIRECTORY_DOWNLOADS}/Codex"
        val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val args = arrayOf("$downloads/")
        val existing = mutableSetOf<String>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
            while (cursor.moveToNext() && index >= 0) existing += cursor.getString(index)
        }
        return uniqueName(existing, original)
    }

    private fun uniqueLegacyTarget(directory: File, original: String): File {
        val existing = directory.list()?.toSet().orEmpty()
        return File(directory, uniqueName(existing, original))
    }

    private fun uniqueName(existing: Set<String>, original: String): String {
        if (original !in existing) return original
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""
        var index = 1
        while ("$stem ($index)$extension" in existing) index++
        return "$stem ($index)$extension"
    }

    private fun mimeTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "txt", "md", "csv" -> "text/plain"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}