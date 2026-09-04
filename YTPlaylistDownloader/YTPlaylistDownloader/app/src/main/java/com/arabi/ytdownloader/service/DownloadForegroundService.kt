package com.arabi.ytdownloader.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arabi.ytdownloader.R
import com.arabi.ytdownloader.YtDlpApplication
import com.arabi.ytdownloader.data.AppDatabase
import com.arabi.ytdownloader.data.DownloadStatus
import com.arabi.ytdownloader.data.PlaylistRepository
import com.arabi.ytdownloader.data.VideoEntity
import com.arabi.ytdownloader.ui.main.MainActivity
import com.arabi.ytdownloader.util.MediaStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Long-running sequential downloader. Deliberately NOT WorkManager:
 * yt-dlp's execute() call is a single long synchronous operation per video
 * (minutes, not the short deferrable units WorkManager expects), and several
 * OEMs aggressively kill WorkManager's background workers. A Foreground
 * Service with a persistent notification is the reliable choice here.
 */
class DownloadForegroundService : Service() {

    companion object {
        const val ACTION_ENQUEUE = "com.arabi.ytdownloader.ACTION_ENQUEUE"
        const val ACTION_CANCEL_CURRENT = "com.arabi.ytdownloader.ACTION_CANCEL_CURRENT"
        const val ACTION_CANCEL_ALL = "com.arabi.ytdownloader.ACTION_CANCEL_ALL"

        const val EXTRA_IDS = "ids"
        const val EXTRA_TITLES = "titles"
        const val EXTRA_URLS = "urls"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_PLAYLIST_ID = "playlist_id"
        const val EXTRA_PLAYLIST_TITLE = "playlist_title"

        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val repository = PlaylistRepository()
    private lateinit var dao: com.arabi.ytdownloader.data.VideoDao

    private val isProcessing = AtomicBoolean(false)
    private var currentProcessId: String? = null
    private val cancelledAll = AtomicBoolean(false)

    private var totalInBatch = 0
    private var completedInBatch = 0

    override fun onCreate() {
        super.onCreate()
        dao = AppDatabase.getInstance(applicationContext).videoDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENQUEUE -> handleEnqueue(intent)
            ACTION_CANCEL_CURRENT -> currentProcessId?.let { repository.cancel(it) }
            ACTION_CANCEL_ALL -> {
                cancelledAll.set(true)
                currentProcessId?.let { repository.cancel(it) }
            }
        }
        return START_NOT_STICKY
    }

    private fun handleEnqueue(intent: Intent) {
        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "best"
        val playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
        val playlistTitle = intent.getStringExtra(EXTRA_PLAYLIST_TITLE)

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_downloading_title), 0))

        serviceScope.launch {
            val entities = ids.indices.map { i ->
                VideoEntity(
                    videoId = ids[i],
                    playlistId = playlistId,
                    playlistTitle = playlistTitle,
                    playlistIndex = if (playlistId != null) i + 1 else null,
                    title = titles.getOrElse(i) { ids[i] },
                    url = urls.getOrElse(i) { "" }
                )
            }
            dao.insertAll(entities) // duplicates silently ignored (dedup)

            if (isProcessing.compareAndSet(false, true)) {
                cancelledAll.set(false)
                processQueue(quality, playlistTitle)
            }
        }
    }

    private suspend fun processQueue(quality: String, playlistSubfolder: String?) {
        val pending = dao.getByStatus(DownloadStatus.PENDING)
        totalInBatch = pending.size
        completedInBatch = 0

        val tempDir = File(filesDir, "downloads").apply { mkdirs() }

        for (video in pending) {
            if (cancelledAll.get()) {
                video.status = DownloadStatus.CANCELLED
                dao.update(video)
                continue
            }

            video.status = DownloadStatus.DOWNLOADING
            dao.update(video)
            currentProcessId = "dl_${video.videoId}_${System.currentTimeMillis()}"

            updateNotification(video.title, 0)

            try {
                repository.downloadVideo(
                    videoUrl = video.url,
                    quality = quality,
                    outputDir = tempDir,
                    processId = currentProcessId!!
                ) { percent, _ ->
                    video.progressPercent = percent
                    updateNotification(video.title, percent.toInt())
                }

                // Find the file yt-dlp just wrote (title-based name, extension varies)
                val downloadedFile = tempDir.listFiles()
                    ?.filter { it.isFile }
                    ?.maxByOrNull { it.lastModified() }

                if (downloadedFile != null) {
                    val uri = MediaStoreHelper.copyToMovies(applicationContext, downloadedFile, playlistSubfolder)
                    video.mediaStoreUri = uri?.toString()
                    video.status = DownloadStatus.DONE
                } else {
                    video.status = DownloadStatus.FAILED
                    video.failureReason = "لم يتم العثور على الملف بعد التحميل"
                }
            } catch (e: Exception) {
                video.status = if (cancelledAll.get()) DownloadStatus.CANCELLED else DownloadStatus.FAILED
                video.failureReason = e.message ?: "خطأ غير معروف"
            }

            dao.update(video)
            completedInBatch++
        }

        isProcessing.set(false)
        currentProcessId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(currentTitle: String, currentPercent: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val text = "($completedInBatch/$totalInBatch) $currentTitle — $currentPercent%"
        manager?.notify(NOTIFICATION_ID, buildNotification(text, currentPercent))
    }

    private fun buildNotification(contentText: String, progress: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, YtDlpApplication.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_downloading_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(openAppIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[Job]?.cancel()
    }
}
