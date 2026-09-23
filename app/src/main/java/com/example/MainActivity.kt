package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.QualitySelectorSheet
import com.example.ui.components.VideoPlayerView
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.VideoDownloaderTheme
import com.example.ui.viewmodel.DownloaderViewModel

enum class NavTab(val title: String) {
    HOME("الرئيسية"),
    BROWSER("المتصفح الذكي"),
    DOWNLOADS("التحميلات")
}

class MainActivity : ComponentActivity() {

    private val viewModel: DownloaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle shared URL from external apps (e.g. browser, YouTube, Twitter)
        handleIntent(intent)

        setContent {
            VideoDownloaderTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    onBrowserUrl = { url ->
                        // Pass URL to browser if requested
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val urlRegex = Regex("https?://[^\\s]+")
                val matched = urlRegex.find(sharedText)?.value ?: sharedText.trim()
                viewModel.onUrlChanged(matched)
                viewModel.analyzeUrl(matched)
            }
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: DownloaderViewModel,
    onBrowserUrl: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    var browserInitialUrl by remember { mutableStateOf("https://peach.blender.org/download/") }

    val activeCount by viewModel.activeCount.collectAsState()
    val sheetMedia by viewModel.sheetMedia.collectAsState()
    val playingVideo by viewModel.currentPlayingVideo.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        bottomBar = {
            if (playingVideo == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav_bar")
                ) {
                    // Home Tab
                    NavigationBarItem(
                        selected = selectedTab == NavTab.HOME,
                        onClick = { selectedTab = NavTab.HOME },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == NavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = NavTab.HOME.title
                            )
                        },
                        label = {
                            Text(
                                text = NavTab.HOME.title,
                                fontWeight = if (selectedTab == NavTab.HOME) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_home")
                    )

                    // Browser Tab
                    NavigationBarItem(
                        selected = selectedTab == NavTab.BROWSER,
                        onClick = { selectedTab = NavTab.BROWSER },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == NavTab.BROWSER) Icons.Filled.Language else Icons.Outlined.Language,
                                contentDescription = NavTab.BROWSER.title
                            )
                        },
                        label = {
                            Text(
                                text = NavTab.BROWSER.title,
                                fontWeight = if (selectedTab == NavTab.BROWSER) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_browser")
                    )

                    // Downloads Tab
                    NavigationBarItem(
                        selected = selectedTab == NavTab.DOWNLOADS,
                        onClick = { selectedTab = NavTab.DOWNLOADS },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("$activeCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == NavTab.DOWNLOADS) Icons.Filled.Download else Icons.Outlined.Download,
                                    contentDescription = NavTab.DOWNLOADS.title
                                )
                            }
                        },
                        label = {
                            Text(
                                text = NavTab.DOWNLOADS.title,
                                fontWeight = if (selectedTab == NavTab.DOWNLOADS) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_downloads")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavTab.HOME -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToDownloads = { selectedTab = NavTab.DOWNLOADS },
                        onNavigateToBrowser = { url ->
                            if (!url.isNullOrEmpty()) {
                                browserInitialUrl = url
                            }
                            selectedTab = NavTab.BROWSER
                        }
                    )
                }

                NavTab.BROWSER -> {
                    BrowserScreen(
                        viewModel = viewModel,
                        initialUrl = browserInitialUrl
                    )
                }

                NavTab.DOWNLOADS -> {
                    DownloadsScreen(
                        viewModel = viewModel,
                        onNavigateToHome = { selectedTab = NavTab.HOME }
                    )
                }
            }

            // Quality Selection Modal Sheet
            sheetMedia?.let { media ->
                QualitySelectorSheet(
                    media = media,
                    onDismiss = { viewModel.dismissQualitySheet() },
                    onSelectQuality = { quality ->
                        viewModel.startDownloadWithQuality(quality)
                        selectedTab = NavTab.DOWNLOADS
                    }
                )
            }

            // Fullscreen In-App Video Player Overlay
            AnimatedVisibility(
                visible = playingVideo != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                playingVideo?.let { video ->
                    VideoPlayerView(
                        video = video,
                        onClose = { viewModel.closePlayer() }
                    )
                }
            }
        }
    }
}
