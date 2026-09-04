package com.arabi.ytdownloader.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MediaStoreHelper {

    /**
     * Copies a completed file from app-specific (permission-less) storage
     * into the shared Movies collection so it shows up in the user's
     * gallery/file manager. Returns the resulting content URI, or null on failure.
     *
     * We copy only AFTER the download fully completes — yt-dlp's --continue
     * resume logic needs a stable direct file path to seek into, which
     * MediaStore's URI-based access doesn't reliably give while a download
     * is in progress.
     */
    fun copyToMovies(context: Context, sourceFile: File, subfolder: String?): android.net.Uri? {
        if (!sourceFile.exists()) return null

        val relativePath = if (subfolder.isNullOrBlank()) {
            "Movies/YTPlaylistDownloader"
        } else {
            "Movies/YTPlaylistDownloader/${sanitize(subfolder)}"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, guessMimeType(sourceFile.name))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (isAudio(sourceFile.name)) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        sourceFile.delete() // clean up the temp app-specific copy
        return uri
    }

    private fun isAudio(fileName: String) = fileName.endsWith(".mp3") || fileName.endsWith(".m4a")

    private fun guessMimeType(fileName: String): String = when {
        fileName.endsWith(".mp4") -> "video/mp4"
        fileName.endsWith(".mkv") -> "video/x-matroska"
        fileName.endsWith(".webm") -> "video/webm"
        fileName.endsWith(".mp3") -> "audio/mpeg"
        fileName.endsWith(".m4a") -> "audio/mp4"
        else -> "application/octet-stream"
    }

    private fun sanitize(name: String) = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
}
