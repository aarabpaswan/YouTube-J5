package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.components.ErrorRetryView
import com.example.ui.components.SplashScreenView
import com.example.ui.model.WebViewUiState
import com.example.ui.theme.YouTubeRed
import com.example.util.NetworkMonitor
import kotlinx.coroutines.delay

@Composable
fun YouTubeMainScreen(
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var uiState by remember { mutableStateOf(WebViewUiState()) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Fullscreen video state
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Network status monitoring
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

    // Automatically fade splash screen after smooth entrance animation completes
    LaunchedEffect(Unit) {
        delay(1400)
        uiState = uiState.copy(isInitialSplash = false)
    }

    // Auto-reload if network is recovered while on error screen
    LaunchedEffect(isOnline) {
        if (isOnline && uiState.isError) {
            uiState = uiState.copy(isError = false, isLoading = true)
            webViewInstance?.reload()
        }
    }

    fun setFullscreenImmersive(fullscreen: Boolean) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun exitFullscreenVideo() {
        customView?.let {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
            uiState = uiState.copy(isFullscreen = false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setFullscreenImmersive(false)
        }
    }

    fun enterFullscreenVideo(view: View, callback: WebChromeClient.CustomViewCallback) {
        customView = view
        customViewCallback = callback
        uiState = uiState.copy(isFullscreen = true)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setFullscreenImmersive(true)
    }

    // Android Hardware Back-Button Handling
    BackHandler {
        when {
            customView != null -> {
                exitFullscreenVideo()
            }
            webViewInstance?.canGoBack() == true -> {
                webViewInstance?.goBack()
            }
            else -> {
                activity?.finish()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Direct Native YouTube WebView (Full screen, no headers, no extra buttons)
        YouTubeWebView(
            onProgressChange = { progress ->
                uiState = uiState.copy(
                    progress = progress,
                    isLoading = progress < 100
                )
                if (progress > 40 && uiState.isInitialSplash) {
                    uiState = uiState.copy(isInitialSplash = false)
                }
            },
            onTitleChange = { title ->
                uiState = uiState.copy(pageTitle = title)
            },
            onNavigationStateChange = { canGoBack, canGoForward, url ->
                uiState = uiState.copy(
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    currentUrl = url
                )
            },
            onPageStartedLoading = {
                uiState = uiState.copy(isLoading = true, isError = false)
            },
            onPageFinishedLoading = {
                uiState = uiState.copy(isLoading = false, isInitialSplash = false)
            },
            onErrorReceived = { errorMessage ->
                uiState = uiState.copy(
                    isError = true,
                    errorMessage = errorMessage,
                    isLoading = false,
                    isInitialSplash = false
                )
            },
            onEnterFullscreen = { view, callback ->
                enterFullscreenVideo(view, callback)
            },
            onExitFullscreen = {
                exitFullscreenVideo()
            },
            onOpenFileChooser = onOpenFileChooser,
            isDesktopMode = false,
            onWebViewCreated = { webView ->
                webViewInstance = webView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle slim YouTube Red loading bar at top of screen (only visible during loading)
        if (uiState.isLoading && uiState.progress in 1..99 && !uiState.isInitialSplash) {
            LinearProgressIndicator(
                progress = { uiState.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .align(Alignment.TopCenter),
                color = YouTubeRed,
                trackColor = Color.Transparent
            )
        }

        // Error / Offline Retry View (Only shown if offline/connection error)
        if (uiState.isError) {
            ErrorRetryView(
                onRetry = {
                    uiState = uiState.copy(isError = false, isLoading = true)
                    webViewInstance?.reload()
                },
                errorMessage = uiState.errorMessage
            )
        }

        // Animated Splash Screen during initial boot with white background
        AnimatedVisibility(
            visible = uiState.isInitialSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashScreenView()
        }

        // Fullscreen Video Player (HTML5 / YouTube Custom View without insets)
        customView?.let { fullView ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        FrameLayout(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            if (fullView.parent != null) {
                                (fullView.parent as? ViewGroup)?.removeView(fullView)
                            }
                            addView(
                                fullView,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
