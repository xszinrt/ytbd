package com.arabi.ytdownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Single init point for yt-dlp + ffmpeg.
 * Must be initialized here (not in Activity) so the Foreground Service
 * can use it independently of whether any Activity is alive.
 */
class YtDlpApplication : Application() {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@YtDlpApplication)
                FFmpeg.getInstance().init(this@YtDlpApplication)
                // Keep the binary up to date; YouTube changes break old extractors often.
                YoutubeDL.getInstance().updateYoutubeDL(this@YtDlpApplication)
            } catch (e: YoutubeDLException) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "تحميل الفيديوهات",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات تقدم تحميل فيديوهات يوتيوب"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
