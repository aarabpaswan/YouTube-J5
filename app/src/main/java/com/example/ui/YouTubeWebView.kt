package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val YOUTUBE_HOME_URL = "https://m.youtube.com/"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    onProgressChange: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
    onNavigationStateChange: (canGoBack: Boolean, canGoForward: Boolean, url: String) -> Unit,
    onPageStartedLoading: (String) -> Unit,
    onPageFinishedLoading: (String) -> Unit,
    onErrorReceived: (String) -> Unit,
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit,
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    isDesktopMode: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true

            // Cookie setup
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // WebSettings configuration
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)

                // Clean User Agent to avoid Google Auth "disallowed_useragent" error
                val defaultUa = userAgentString
                val cleanMobileUa = defaultUa
                    .replace("; wv", "")
                    .replace("Version/4.0 ", "")
                userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else cleanMobileUa
            }
        }
    }

    // Update User Agent if desktop mode changes
    DisposableEffect(isDesktopMode) {
        val defaultUa = webView.settings.userAgentString
        val cleanMobileUa = defaultUa
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
        webView.settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else cleanMobileUa
        onDispose { }
    }

    // Attach clients and initial load
    DisposableEffect(webView) {
        onWebViewCreated(webView)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChange(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank()) {
                    onTitleChange(title)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onEnterFullscreen(view, callback)
                }
            }

            override fun onHideCustomView() {
                onExitFullscreen()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                return onOpenFileChooser(filePathCallback, fileChooserParams)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                if (transport != null && view != null) {
                    // Route multi-window popups (like Google Auth redirects) directly back into this WebView
                    val newWebView = WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                innerView: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val url = request?.url?.toString()
                                if (url != null) {
                                    view.loadUrl(url)
                                }
                                return true
                            }
                        }
                    }
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null) {
                    onPageStartedLoading(url)
                    onNavigationStateChange(
                        view?.canGoBack() == true,
                        view?.canGoForward() == true,
                        url
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    onPageFinishedLoading(url)
                    onNavigationStateChange(
                        view?.canGoBack() == true,
                        view?.canGoForward() == true,
                        url
                    )
                }
                CookieManager.getInstance().flush()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleUrlLoading(view, uri)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                val uri = Uri.parse(url)
                return handleUrlLoading(view, uri)
            }

            private fun handleUrlLoading(view: WebView?, uri: Uri): Boolean {
                val scheme = uri.scheme?.lowercase() ?: return false
                val host = uri.host?.lowercase() ?: ""

                // Handle standard HTTP/HTTPS URLs inside YouTube WebView
                if (scheme == "http" || scheme == "https") {
                    // Check if it's an external YouTube app intent that was triggered by website
                    if (uri.path?.startsWith("/intent/") == true) {
                        return false
                    }
                    // Stay inside WebView for YouTube, Google Accounts, Sign-in, etc.
                    return false
                }

                // Handle intent:// schemes
                if (scheme == "intent") {
                    try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        if (intent != null) {
                            val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                            if (!fallbackUrl.isNullOrEmpty()) {
                                view?.loadUrl(fallbackUrl)
                                return true
                            }
                            if (context.packageManager.resolveActivity(intent, 0) != null) {
                                context.startActivity(intent)
                                return true
                            }
                        }
                    } catch (_: Exception) {
                    }
                    return true
                }

                // Handle mailto:, tel:, market:
                if (scheme == "mailto" || scheme == "tel" || scheme == "market") {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                    }
                    return true
                }

                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Connection error"
                    } else {
                        "Connection error"
                    }
                    onErrorReceived(desc)
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                if (failingUrl == view?.url || failingUrl?.contains("youtube.com") == true) {
                    onErrorReceived(description ?: "Network connection failed ($errorCode)")
                }
            }
        }

        webView.loadUrl(YOUTUBE_HOME_URL)

        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}
