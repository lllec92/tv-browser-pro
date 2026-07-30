package com.tvbrowser.pro

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Обёртка над WebView, настроенная для просмотра сайтов онлайн-кинотеатров
 * на Android TV: включён JavaScript, DOM storage, cookies, поддержка
 * fullscreen видео и autoplay. Также отслеживает загрузку страниц
 * для обнаружения прямых видеопотоков через [VideoInterceptor].
 */
class BrowserView(
    context: Context,
    private val container: FrameLayout,
    private val listener: Listener
) {

    interface Listener {
        fun onVideoDetected(url: String)
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onProgressChanged(progress: Int)
        fun onReceivedTitle(title: String?)
        fun onWebViewError(errorCode: Int, description: String?, failingUrl: String?)
    }

    companion object {
        private const val TAG = "BrowserView"
    }

    val webView: WebView = WebView(context)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    init {
        setupWebView()
        container.addView(
            webView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.userAgentString = settings.userAgentString + " TVBrowserPro/1.0"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.addJavascriptInterface(VideoBridge(), "AndroidVideoBridge")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (VideoInterceptor.isDirectVideoUrl(url) &&
                    !VideoInterceptor.looksProtectedByDrm(url)
                ) {
                    listener.onVideoDetected(url)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                listener.onPageStarted(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                listener.onPageFinished(url)
                view.evaluateJavascript(VideoInterceptor.VIDEO_SCAN_SCRIPT, null)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                val description = error.description?.toString()
                Log.e(
                    TAG,
                    "WebView ошибка: код=${error.errorCode}, описание=$description, url=${request.url}"
                )
                listener.onWebViewError(error.errorCode, description, request.url.toString())
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                listener.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                super.onReceivedTitle(view, title)
                listener.onReceivedTitle(title)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                container.addView(
                    view,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val cv = customView ?: return
                container.removeView(cv)
                customView = null
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
    }

    fun loadUrl(url: String) {
        webView.loadUrl(normalizeUrl(url))
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        val uri = Uri.parse(trimmed)
        return if (uri.scheme == null) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    fun destroy() {
        container.removeView(webView)
        webView.destroy()
    }

    private inner class VideoBridge {
        @JavascriptInterface
        fun onVideoFound(url: String) {
            if (url.isNotBlank()) {
                Log.d(TAG, "Video тег найден через JS-сканирование: $url")
                webView.post {
                    listener.onVideoDetected(url)
                }
            }
        }

        @JavascriptInterface
        fun onScanError(message: String) {
            Log.e(TAG, "Ошибка сканирования video тегов: $message")
        }
    }
}
