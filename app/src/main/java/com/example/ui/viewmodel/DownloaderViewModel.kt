package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VideoDownloaderApp
import com.example.core.sniffer.DetectedMedia
import com.example.core.sniffer.VideoQualityOption
import com.example.core.sniffer.VideoSniffer
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Success(val media: DetectedMedia) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

enum class DownloadFilter {
    ALL,
    ACTIVE,
    COMPLETED,
    AUDIO
}

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as VideoDownloaderApp).repository

    // URL input state
    val inputUrl = MutableStateFlow("")

    // Sniffer / Analysis state
    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    // Quality Selector Sheet state
    private val _sheetMedia = MutableStateFlow<DetectedMedia?>(null)
    val sheetMedia: StateFlow<DetectedMedia?> = _sheetMedia.asStateFlow()

    // Downloads list
    val allDownloads = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter selection
    val currentFilter = MutableStateFlow(DownloadFilter.ALL)

    // Filtered downloads
    val filteredDownloads: StateFlow<List<DownloadEntity>> = combine(allDownloads, currentFilter) { list, filter ->
        when (filter) {
            DownloadFilter.ALL -> list
            DownloadFilter.ACTIVE -> list.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
            DownloadFilter.COMPLETED -> list.filter { it.status == DownloadStatus.COMPLETED }
            DownloadFilter.AUDIO -> list.filter { it.fileFormat == "m4a" || it.fileFormat == "mp3" || it.qualityLabel.contains("Audio", ignoreCase = true) || it.qualityLabel.contains("صوت") }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active downloading items count
    val activeCount: StateFlow<Int> = allDownloads.combine(allDownloads) { list, _ ->
        list.count { it.status == DownloadStatus.DOWNLOADING }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Video Player state
    private val _currentPlayingVideo = MutableStateFlow<DownloadEntity?>(null)
    val currentPlayingVideo: StateFlow<DownloadEntity?> = _currentPlayingVideo.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        inputUrl.value = newUrl
        if (_analysisState.value is AnalysisState.Error) {
            _analysisState.value = AnalysisState.Idle
        }
    }

    fun pasteFromClipboard() {
        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    inputUrl.value = text
                    analyzeUrl(text)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun analyzeUrl(url: String = inputUrl.value) {
        val target = url.trim()
        if (target.isBlank()) {
            _analysisState.value = AnalysisState.Error("يرجى إدخال رابط صالح أولاً")
            return
        }

        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading
            val result = VideoSniffer.inspectUrl(target)
            result.onSuccess { media ->
                _analysisState.value = AnalysisState.Success(media)
                _sheetMedia.value = media
            }.onFailure { err ->
                _analysisState.value = AnalysisState.Error(
                    err.localizedMessage ?: "تعذر فحص الرابط، تأكد من الاتصال بالإنترنت وصلاحية الرابط"
                )
            }
        }
    }

    fun showQualitySheet(media: DetectedMedia) {
        _sheetMedia.value = media
    }

    fun dismissQualitySheet() {
        _sheetMedia.value = null
    }

    fun startDownloadWithQuality(quality: VideoQualityOption) {
        val media = _sheetMedia.value ?: return
        repository.startDownload(
            title = media.title,
            sourceUrl = media.pageUrl,
            qualityOption = quality,
            thumbnailUrl = media.thumbnailUrl
        )
        dismissQualitySheet()
    }

    fun pauseDownload(id: Long) = repository.pauseDownload(id)

    fun resumeDownload(id: Long) = repository.resumeDownload(id)

    fun cancelDownload(id: Long) = repository.cancelDownload(id)

    fun retryDownload(id: Long) = repository.retryDownload(id)

    fun deleteDownload(id: Long) {
        if (_currentPlayingVideo.value?.id == id) {
            _currentPlayingVideo.value = null
        }
        repository.deleteDownload(id)
    }

    fun setFilter(filter: DownloadFilter) {
        currentFilter.value = filter
    }

    fun playVideo(item: DownloadEntity) {
        _currentPlayingVideo.value = item
    }

    fun closePlayer() {
        _currentPlayingVideo.value = null
    }
}
