package com.example.core.sniffer

data class VideoQualityOption(
    val qualityLabel: String,    // e.g. "1080p Full HD", "720p HD", "480p SD", "360p", "صوت MP3"
    val resolution: String,      // e.g. "1920x1080", "1280x720"
    val fileFormat: String,      // "mp4", "webm", "m4a"
    val downloadUrl: String,     // Target direct stream URL
    val estimatedSizeBytes: Long,// Size in bytes
    val badge: String,           // "FHD", "HD", "SD", "AUDIO"
    val isAudioOnly: Boolean = false,
    val bitrateKbps: Int = 2500
) {
    val formattedSize: String
        get() = com.example.data.local.DownloadEntity.formatByteCount(estimatedSizeBytes)
}

data class DetectedMedia(
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String?,
    val qualities: List<VideoQualityOption>,
    val durationSeconds: Long = 0L,
    val detectedAt: Long = System.currentTimeMillis()
)
