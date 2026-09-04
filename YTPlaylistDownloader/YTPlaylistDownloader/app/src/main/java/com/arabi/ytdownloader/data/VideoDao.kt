package com.arabi.ytdownloader.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface VideoDao {

    // IGNORE on conflict = duplicate-check by videoId primary key.
    // Re-adding the same playlist won't re-queue videos already tracked.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(videos: List<VideoEntity>): List<Long>

    @Query("SELECT videoId FROM videos WHERE videoId IN (:ids)")
    suspend fun findExistingIds(ids: List<String>): List<String>

    @Update
    suspend fun update(video: VideoEntity)

    @Query("SELECT * FROM videos WHERE playlistId = :playlistId ORDER BY playlistIndex ASC")
    fun observeByPlaylist(playlistId: String): LiveData<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE status = :status ORDER BY addedAt ASC")
    suspend fun getByStatus(status: DownloadStatus): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE videoId = :id LIMIT 1")
    suspend fun getById(id: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun observeAll(): LiveData<List<VideoEntity>>

    @Query("DELETE FROM videos WHERE status = 'DONE'")
    suspend fun clearCompleted()
}
