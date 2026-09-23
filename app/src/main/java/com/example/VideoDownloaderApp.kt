package com.example

import android.app.Application
import com.example.data.repository.DownloadRepository

class VideoDownloaderApp : Application() {

    lateinit var repository: DownloadRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository(this)
    }
}
