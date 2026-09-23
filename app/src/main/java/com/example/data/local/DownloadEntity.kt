package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val mediaUrl: String,
    val filePath: String,
    val fileFormat: String, // mp4, webm, m4a, mp3
    val qualityLabel: String, // 1080p, 720p, 480p, 360p, Audio Only
    val resolution: String, // 1920x1080, 1280x720, etc.
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val downloadSpeed: Long = 0L, // bytes per second
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val formattedSize: String
        get() = formatByteCount(totalBytes)

    val formattedDownloadedSize: String
        get() = formatByteCount(downloadedBytes)

    val formattedSpeed: String
        get() = if (downloadSpeed > 0) "${formatByteCount(downloadSpeed)}/s" else "0 KB/s"

    /**
     * Estimates remaining time in Arabic based on remaining bytes and current download speed.
     * e.g. "متبقي حوالي دقيقتين", "متبقي 45 ثانية", "متبقي أقل من دقيقة"
     */
    val formattedRemainingTime: String
        get() {
            if (downloadSpeed <= 0 || totalBytes <= 0 || downloadedBytes >= totalBytes) return ""
            val remainingBytes = totalBytes - downloadedBytes
            val remainingSeconds = (remainingBytes / downloadSpeed).coerceAtLeast(1)

            return when {
                remainingSeconds < 60 -> "يتبقى $remainingSeconds ثانية"
                remainingSeconds < 120 -> "يتبقى حوالي دقيقة واحدة"
                remainingSeconds < 180 -> "يتبقى حوالي دقيقتين"
                remainingSeconds < 3600 -> {
                    val minutes = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    if (secs > 0) "يتبقى $minutes دقيقة و $secs ثانية" else "يتبقى $minutes دقيقة"
                }
                else -> {
                    val hours = remainingSeconds / 3600
                    val mins = (remainingSeconds % 3600) / 60
                    "يتبقى $hours ساعة و $mins دقيقة"
                }
            }
        }

    companion object {
        fun formatByteCount(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups.coerceIn(0, units.size - 1)])
        }
    }
}
