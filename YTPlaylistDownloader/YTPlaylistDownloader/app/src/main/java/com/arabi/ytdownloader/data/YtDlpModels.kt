package com.arabi.ytdownloader.data

import com.google.gson.annotations.SerializedName

/**
 * Matches yt-dlp's -J (dump-json) output shape.
 * When the URL is a playlist, "entries" is populated (flat, since we pass
 * --flat-playlist) and top-level "id"/"title" describe the playlist itself.
 * When the URL is a single video, "entries" is null.
 */
data class YtDlpDumpJson(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("entries") val entries: List<YtDlpEntry>? = null,
    @SerializedName("webpage_url") val webpageUrl: String? = null
)

data class YtDlpEntry(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("url") val url: String?,
    @SerializedName("duration") val duration: Double? = null
)
