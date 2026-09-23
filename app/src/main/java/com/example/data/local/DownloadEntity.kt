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
