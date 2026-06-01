package com.llucs.nexusai.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

data class GeneratedFile(
    val name: String,
    val content: String,
    val mimeType: String = "text/plain",
    val path: String? = null
)

class FileTransfer(private val context: Context) {

    private val filesDir: File get() = File(context.filesDir, "nexus-files")

    init {
        filesDir.mkdirs()
    }

    fun saveGeneratedFile(name: String, content: String, mimeType: String = "text/plain"): File {
        filesDir.mkdirs()
        val file = File(filesDir, name)
        var counter = 1
        val baseName = name.substringBeforeLast(".")
        val ext = name.substringAfterLast(".", "")
        while (file.exists()) {
            val newName = if (ext.isNotEmpty() && ext != name) "$baseName($counter).$ext" else "$baseName($counter)"
            counter++
        }
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun saveBinaryFile(name: String, data: ByteArray): File {
        filesDir.mkdirs()
        val file = File(filesDir, name)
        file.writeBytes(data)
        return file
    }

    fun shareFile(file: File, mimeType: String? = null) {
        val type = mimeType ?: getMimeType(file.name)
        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            setType(type)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Share ${file.name}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareText(text: String, title: String = "Shared text") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setType("text/plain")
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun listGeneratedFiles(): List<File> {
        return filesDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteFile(name: String): Boolean {
        return File(filesDir, name).delete()
    }

    fun clearAllFiles() {
        filesDir.listFiles()?.forEach { it.delete() }
    }

    fun getFileSize(file: File): String {
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "kt" -> "text/plain"
            "java" -> "text/plain"
            "py" -> "text/plain"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "yaml", "yml" -> "application/x-yaml"
            "sh" -> "application/x-sh"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
