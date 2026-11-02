package com.example.ui.model

data class WebViewUiState(
    val currentUrl: String = "https://m.youtube.com/",
    val pageTitle: String = "YouTube",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = true,
    val progress: Int = 0,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val isFullscreen: Boolean = false,
    val isDesktopMode: Boolean = false,
    val showTopBar: Boolean = true,
    val isInitialSplash: Boolean = true,
)
