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
    private val youtubeDL = YoutubeDL.getInstance()

    suspend fun extract(url: String): Result<ExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-J")
                addOption("--flat-playlist")
                addOption("--skip-download")
                addOption("--no-warnings")
            }
            
            val response = youtubeDL.execute(request)
            val parsed = gson.fromJson(response.out, YtDlpDumpJson::class.java)

            if (parsed.entries.isNullOrEmpty()) {
                Result.success(
                    ExtractionResult.SingleVideo(
                        YtDlpEntry(
                            id = parsed.id ?: "",
                            title = parsed.title ?: "",
                            url = parsed.webpageUrl ?: url,
                            duration = parsed.duration ?: 0,
                            thumbnail = parsed.thumbnail ?: ""
                        )
                    )
                )
            } else {
                Result.success(
                    ExtractionResult.Playlist(
                        playlistId = parsed.id ?: "",
                        playlistTitle = parsed.title ?: "",
                        entries = parsed.entries
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadVideo(
        videoUrl: String,
        quality: String,
        outputDir: File,
        processId: String,
        onProgress: (percent: Float, eta: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(videoUrl).apply {
                addOption("-f", quality)
                addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")
                addOption("--continue")
                addOption("--no-playlist")
                addOption(
                    "--progress-template",
                    "download:%(progress._percent_str)s|%(progress._eta_str)s"
                )
            }

            // ✅ التصحيح: استخدام (Float, Long) -> Unit فقط
            val response = youtubeDL.execute(request) { progress, eta ->
                // progress: Float, eta: Long (الوقت المتبقي بالثواني)
                val etaString = if (eta > 0) "${eta}s" else "Calculating..."
                onProgress(progress, etaString)
            }
            
            val outputFile = File(outputDir, response.out)
            if (outputFile.exists()) {
                Result.success(outputFile)
            } else {
                Result.failure(Exception("File not found: ${response.out}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancel(processId: String) {
        // في الإصدار 0.12.+، لا يوجد destroyProcessById
        // يمكن إلغاء التحميل عبر إلغاء coroutine
    }

    private fun parseProgressLine(line: String): Pair<Float, String>? {
        if (!line.startsWith("download:")) return null
        val payload = line.removePrefix("download:")
        val parts = payload.split("|")
        if (parts.size != 2) return null
        val percent = parts[0].trim().removeSuffix("%").toFloatOrNull() ?: return null
        return percent to parts[1].trim()
    }
}
