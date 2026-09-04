package com.arabi.ytdownloader.data

import com.google.gson.annotations.SerializedName

data class YtDlpDumpJson(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("webpage_url") val webpageUrl: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("entries") val entries: List<YtDlpEntry>? = null
)

data class YtDlpEntry(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("webpage_url") val url: String = "",
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("thumbnail") val thumbnail: String = ""
)
