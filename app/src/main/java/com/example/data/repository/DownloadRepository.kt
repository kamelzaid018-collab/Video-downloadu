package com.example.data.repository

import android.content.Context
import com.example.core.downloader.DownloadEngine
import com.example.core.sniffer.VideoQualityOption
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val downloadDao: DownloadDao = database.downloadDao()
    private val downloadEngine = DownloadEngine.getInstance(context)

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    fun getDownloadById(id: Long): Flow<DownloadEntity?> = downloadDao.getDownloadById(id)

    fun startDownload(
        title: String,
        sourceUrl: String,
        qualityOption: VideoQualityOption,
        thumbnailUrl: String?
    ) {
        downloadEngine.startDownload(
            title = title,
            sourceUrl = sourceUrl,
            mediaUrl = qualityOption.downloadUrl,
            fileFormat = qualityOption.fileFormat,
            qualityLabel = qualityOption.qualityLabel,
            resolution = qualityOption.resolution,
            estimatedSize = qualityOption.estimatedSizeBytes,
            thumbnailUrl = thumbnailUrl
        )
    }

    fun pauseDownload(id: Long) = downloadEngine.pauseDownload(id)

    fun resumeDownload(id: Long) = downloadEngine.resumeDownload(id)

    fun cancelDownload(id: Long) = downloadEngine.cancelDownload(id)

    fun retryDownload(id: Long) = downloadEngine.retryDownload(id)

    fun deleteDownload(id: Long) = downloadEngine.deleteDownload(id)
}
