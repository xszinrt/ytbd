package com.arabi.ytdownloader.util

object UrlUtils {

    fun isYoutubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    fun isPlaylistUrl(url: String): Boolean {
        return url.contains("list=") || url.contains("/playlist?")
    }

    /** Strips a lone video's "list=" / "index=" params so a video-inside-playlist
     * link doesn't get treated as the whole playlist unless the user meant to. */
    fun stripPlaylistParams(url: String): String {
        return url.replace(Regex("[&?]list=[^&]*"), "")
            .replace(Regex("[&?]index=[^&]*"), "")
    }
}
