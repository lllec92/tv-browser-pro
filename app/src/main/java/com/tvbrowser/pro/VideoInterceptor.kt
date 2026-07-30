package com.tvbrowser.pro

import android.util.Log

/**
 * Утилита для анализа URL и содержимого страниц с целью
 * определения прямых видео-источников, которые можно
 * воспроизвести через аппаратный декодер устройства (ExoPlayer),
 * вместо слабого встроенного плеера WebView.
 */
object VideoInterceptor {

    private const val TAG = "VideoInterceptor"

    private val DIRECT_VIDEO_EXTENSIONS = listOf(
        ".mp4", ".m3u8", ".mpd", ".webm", ".mkv", ".ts"
    )

    /**
     * JavaScript, который внедряется в страницу после её загрузки.
     * Ищет все <video> теги и их источники (src или <source>)
     * и передаёт их в нативный код через JavascriptInterface
     * "AndroidVideoBridge".
     */
    const val VIDEO_SCAN_SCRIPT = """
        (function() {
            try {
                var videos = document.getElementsByTagName('video');
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    if (v.currentSrc) {
                        AndroidVideoBridge.onVideoFound(v.currentSrc);
                    } else if (v.src) {
                        AndroidVideoBridge.onVideoFound(v.src);
                    } else {
                        var sources = v.getElementsByTagName('source');
                        for (var j = 0; j < sources.length; j++) {
                            if (sources[j].src) {
                                AndroidVideoBridge.onVideoFound(sources[j].src);
                            }
                        }
                    }
                }
            } catch (e) {
                AndroidVideoBridge.onScanError(e.toString());
            }
        })();
    """

    /**
     * Проверяет, похож ли переданный URL на прямую ссылку на видеопоток
     * (mp4, m3u8, mpd и т.д.), которую можно передать в ExoPlayer.
     */
    fun isDirectVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('?').substringBefore('#')

        val hasVideoExtension = DIRECT_VIDEO_EXTENSIONS.any { path.endsWith(it) }
        if (hasVideoExtension) {
            Log.d(TAG, "Найден прямой видео-URL по расширению: $url")
            return true
        }

        val hasHlsOrDashHint = lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd")
        if (hasHlsOrDashHint) {
            Log.d(TAG, "Найден вероятный видео-URL (HLS/DASH): $url")
            return true
        }

        return false
    }

    /**
     * Признаки того, что поток защищён DRM (Widevine и т.д.) и не должен
     * обрабатываться напрямую через ExoPlayer без DRM-лицензии.
     */
    fun looksProtectedByDrm(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("widevine") ||
            lowerUrl.contains("drm") ||
            lowerUrl.contains("license")
    }

    /**
     * Определяет MIME-тип для передачи в MediaItem на основе URL,
     * чтобы Media3 выбрал правильный медиа-источник (HLS/DASH/Progressive).
     */
    fun guessMimeType(url: String): String? {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".ts") -> "video/mp2t"
            else -> null
        }
    }
}
