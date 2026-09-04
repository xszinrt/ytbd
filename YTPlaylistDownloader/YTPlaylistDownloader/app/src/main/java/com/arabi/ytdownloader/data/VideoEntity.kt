package com.arabi.ytdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    PENDING, DOWNLOADING, DONE, FAILED, SKIPPED, CANCELLED
}

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val videoId: String,       // yt-dlp "id" field, used for dedup
    val playlistId: String?,               // null if single video (not a playlist)
    val playlistTitle: String?,
    val playlistIndex: Int?,
    val title: String,
    val url: String,
    var status: DownloadStatus = DownloadStatus.PENDING,
    var progressPercent: Float = 0f,
    var failureReason: String? = null,
    var localFilePath: String? = null,     // temp app-specific storage path
    var mediaStoreUri: String? = null,     // final URI after MediaStore copy
    val addedAt: Long = System.currentTimeMillis()
)
