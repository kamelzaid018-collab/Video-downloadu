package com.example.core.downloader

import android.content.Context
import android.os.Environment
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadEngine private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getInstance(context)
    private val downloadDao: DownloadDao = database.downloadDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Map of downloadId to active Coroutine Job
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    // Set of paused IDs
    private val pausedIds = ConcurrentHashMap.newKeySet<Long>()

    companion object {
        @Volatile
        private var INSTANCE: DownloadEngine? = null

        fun getInstance(context: Context): DownloadEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = DownloadEngine(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun startDownload(
        title: String,
        sourceUrl: String,
        mediaUrl: String,
        fileFormat: String,
        qualityLabel: String,
        resolution: String,
        estimatedSize: Long,
        thumbnailUrl: String?
    ) {
        scope.launch {
            // Determine storage directory
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val safeName = title.replace(Regex("[^a-zA-Z0-9_\\u0600-\\u06FF.-]"), "_").take(40)
            val fileName = "${safeName}_${qualityLabel.replace(" ", "_")}_${System.currentTimeMillis()}.$fileFormat"
            val targetFile = File(moviesDir, fileName)

            val downloadEntity = DownloadEntity(
                title = title,
                sourceUrl = sourceUrl,
                mediaUrl = mediaUrl,
                filePath = targetFile.absolutePath,
                fileFormat = fileFormat,
                qualityLabel = qualityLabel,
                resolution = resolution,
                totalBytes = estimatedSize,
                downloadedBytes = 0L,
                status = DownloadStatus.DOWNLOADING,
                thumbnailUrl = thumbnailUrl
            )

            val newId = downloadDao.insertDownload(downloadEntity)
            executeDownload(newId, mediaUrl, targetFile, 0L)
        }
    }

    fun resumeDownload(id: Long) {
        pausedIds.remove(id)
        scope.launch {
            val item = downloadDao.getDownloadByIdOnce(id) ?: return@launch
            val file = File(item.filePath)
            val existingBytes = if (file.exists()) file.length() else 0L

            downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)
            executeDownload(id, item.mediaUrl, file, existingBytes)
        }
    }

    fun pauseDownload(id: Long) {
        pausedIds.add(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED)
        }
    }

    fun cancelDownload(id: Long) {
        pausedIds.remove(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        scope.launch {
            downloadDao.updateStatus(id, DownloadStatus.CANCELED)
        }
    }

    fun retryDownload(id: Long) {
        resumeDownload(id)
    }

    fun deleteDownload(id: Long, deleteFileFromDisk: Boolean = true) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        pausedIds.remove(id)
        scope.launch {
            val item = downloadDao.getDownloadByIdOnce(id)
            if (item != null && deleteFileFromDisk) {
                try {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            downloadDao.deleteDownloadById(id)
        }
    }

    private fun executeDownload(downloadId: Long, url: String, targetFile: File, resumeOffset: Long) {
        activeJobs[downloadId]?.cancel()

        val job = scope.launch {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                var currentResumeOffset = resumeOffset
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")

                if (currentResumeOffset > 0) {
                    requestBuilder.header("Range", "bytes=$currentResumeOffset-")
                }

                var response = okHttpClient.newCall(requestBuilder.build()).execute()

                // If server returns 416 (Range Not Satisfiable), reset resume offset and redownload from start
                if (response.code == 416 && currentResumeOffset > 0) {
                    response.close()
                    currentResumeOffset = 0L
                    val freshRequest = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "*/*")
                        .build()
                    response = okHttpClient.newCall(freshRequest).execute()
                }

                if (!response.isSuccessful && response.code != 206) {
                    val code = response.code
                    val errorDesc = when (code) {
                        403 -> "الموقع يمنع التنزيل المباشر (403 Forbidden)"
                        404 -> "الملف غير موجود أو انتهت صلاحيته (404 Not Found)"
                        410 -> "تمت إزالة الفيديو من المصدر (410 Gone)"
                        429 -> "تم تجاوز عدد الطلبات المسموح به (429 Too Many Requests)"
                        in 500..599 -> "خطأ في خادم الموقع ($code Server Error)"
                        else -> "خطأ اتصال بالخادم (HTTP $code)"
                    }
                    response.close()
                    throw IllegalStateException(errorDesc)
                }

                val body = response.body ?: throw IllegalStateException("لم يتم استلام أي بيانات من الخادم")
                val responseContentLength = body.contentLength()
                val totalBytes = if (currentResumeOffset > 0) {
                    currentResumeOffset + responseContentLength
                } else {
                    if (responseContentLength > 0) responseContentLength else 40_000_000L
                }

                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile, currentResumeOffset > 0)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var currentBytes = currentResumeOffset

                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var currentSpeed = 0L

                var lastDbUpdateTime = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (pausedIds.contains(downloadId)) {
                        break
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead
                    bytesSinceLastCalc += bytesRead

                    val now = System.currentTimeMillis()

                    // Calculate speed every 500ms
                    if (now - lastSpeedCalcTime >= 500) {
                        val durationSec = (now - lastSpeedCalcTime) / 1000.0
                        if (durationSec > 0) {
                            currentSpeed = (bytesSinceLastCalc / durationSec).toLong()
                        }
                        bytesSinceLastCalc = 0L
                        lastSpeedCalcTime = now
                    }

                    // Update database every 750ms to prevent SQLite contention
                    if (now - lastDbUpdateTime >= 750) {
                        downloadDao.updateProgress(
                            id = downloadId,
                            downloaded = currentBytes,
                            total = totalBytes,
                            speed = currentSpeed,
                            status = DownloadStatus.DOWNLOADING
                        )
                        lastDbUpdateTime = now
                    }
                }

                outputStream.flush()

                if (pausedIds.contains(downloadId)) {
                    downloadDao.updateProgress(
                        id = downloadId,
                        downloaded = currentBytes,
                        total = totalBytes,
                        speed = 0L,
                        status = DownloadStatus.PAUSED
                    )
                } else {
                    // Completed!
                    downloadDao.updateProgress(
                        id = downloadId,
                        downloaded = currentBytes,
                        total = currentBytes,
                        speed = 0L,
                        status = DownloadStatus.COMPLETED
                    )
                }
            } catch (e: Exception) {
                if (pausedIds.contains(downloadId)) {
                    downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
                } else {
                    e.printStackTrace()
                    downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, e.localizedMessage ?: "فشل التنزيل")
                }
            } finally {
                try {
                    inputStream?.close()
                } catch (_: Exception) {}
                try {
                    outputStream?.close()
                } catch (_: Exception) {}
                activeJobs.remove(downloadId)
            }
        }

        activeJobs[downloadId] = job
    }
}
