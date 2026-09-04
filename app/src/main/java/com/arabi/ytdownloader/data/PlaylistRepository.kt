package com.arabi.ytdownloader.data

import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class ExtractionResult {
    data class SingleVideo(val entry: YtDlpEntry) : ExtractionResult()
    data class Playlist(
        val playlistId: String,
        val playlistTitle: String,
        val entries: List<YtDlpEntry>
    ) : ExtractionResult()
}

class PlaylistRepository {

    private val gson = Gson()

    /**
     * Stage 1: extraction only. Uses --flat-playlist so a large playlist
     * returns quickly (id/title/url per entry, no per-video format probing).
     */
    suspend fun extract(url: String): Result<ExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-J")
                addOption("--flat-playlist")
                addOption("--skip-download")
                addOption("--no-warnings")
            }
            val response = YoutubeDL.getInstance().execute(request, null) { _, _, _ -> }
            val parsed = gson.fromJson(response.out, YtDlpDumpJson::class.java)

            if (parsed.entries.isNullOrEmpty()) {
                // Single video: synthesize an entry from the top-level fields.
                Result.success(
                    ExtractionResult.SingleVideo(
                        YtDlpEntry(id = parsed.id, title = parsed.title, url = parsed.webpageUrl ?: url)
                    )
                )
            } else {
                Result.success(
                    ExtractionResult.Playlist(
                        playlistId = parsed.id,
                        playlistTitle = parsed.title,
                        entries = parsed.entries
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stage 2: actual download of one video to app-specific (permission-less)
     * storage. Caller is responsible for copying the finished file into
     * MediaStore afterward (needed for reliable --continue/resume behavior,
     * since MediaStore URIs aren't stable file paths yt-dlp can seek into).
     */
    suspend fun downloadVideo(
        videoUrl: String,
        quality: String,
        outputDir: File,
        processId: String,
        onProgress: (percent: Float, eta: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(videoUrl).apply {
            addOption("-f", quality)
            addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--continue")               // resume partial downloads
            addOption("--no-playlist")             // this call always targets ONE video
            addOption(
                "--progress-template",
                "download:%(progress._percent_str)s|%(progress._eta_str)s"
            )
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
            val parsed = parseProgressLine(line)
            onProgress(parsed?.first ?: progress, parsed?.second ?: "")
        }
    }

    fun cancel(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    /** Parses our custom "--progress-template" output instead of guessing
     * at yt-dlp's free-text log lines, which change between versions. */
    private fun parseProgressLine(line: String): Pair<Float, String>? {
        if (!line.startsWith("download:")) return null
        val payload = line.removePrefix("download:")
        val parts = payload.split("|")
        if (parts.size != 2) return null
        val percent = parts[0].trim().removeSuffix("%").toFloatOrNull() ?: return null
        return percent to parts[1].trim()
    }
}
