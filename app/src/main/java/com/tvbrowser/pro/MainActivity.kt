package com.tvbrowser.pro

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Главный экран TV Browser Pro: адресная строка, кнопки навигации,
 * WebView для просмотра сайтов и автоматическое определение
 * видеопотоков, которые лучше воспроизвести через ExoPlayer.
 */
class MainActivity : AppCompatActivity(), BrowserView.Listener {

    companion object {
        private const val TAG = "MainActivity"
        const val PREFS_NAME = "tv_browser_prefs"
        const val KEY_USE_EXOPLAYER = "use_exoplayer"
        const val KEY_HW_ACCEL = "hardware_accel"
        const val KEY_AUTO_FULLSCREEN = "auto_fullscreen"
        const val KEY_FAVORITES = "favorites_set"
        const val DEFAULT_URL = "https://www.google.com"
    }

    private lateinit var editUrl: EditText
    private lateinit var btnOpen: Button
    private lateinit var btnBack: Button
    private lateinit var btnHome: Button
    private lateinit var btnFavorites: Button
    private lateinit var btnSettings: Button
    private lateinit var webviewContainer: FrameLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var prefs: SharedPreferences
    private lateinit var browserView: BrowserView

    private var lastVideoUrlHandled: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        editUrl = findViewById(R.id.edit_url)
        btnOpen = findViewById(R.id.btn_open)
        btnBack = findViewById(R.id.btn_back)
        btnHome = findViewById(R.id.btn_home)
        btnFavorites = findViewById(R.id.btn_favorites)
        btnSettings = findViewById(R.id.btn_settings)
        webviewContainer = findViewById(R.id.webview_container)
        progressBar = findViewById(R.id.progress_bar)

        browserView = BrowserView(this, webviewContainer, this)

        setupListeners()

        val startUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: DEFAULT_URL
        editUrl.setText(startUrl)
        browserView.loadUrl(startUrl)
        editUrl.requestFocus()
    }

    private fun setupListeners() {
        btnOpen.setOnClickListener { openCurrentUrl() }

        editUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                openCurrentUrl()
                true
            } else {
                false
            }
        }

        btnBack.setOnClickListener {
            if (browserView.canGoBack()) {
                browserView.goBack()
            } else {
                Toast.makeText(this, "Нет истории для перехода назад", Toast.LENGTH_SHORT).show()
            }
        }

        btnHome.setOnClickListener {
            editUrl.setText(DEFAULT_URL)
            browserView.loadUrl(DEFAULT_URL)
        }

        btnFavorites.setOnClickListener {
            showFavoritesDialog()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun openCurrentUrl() {
        val url = editUrl.text.toString().trim()
        if (url.isNotEmpty()) {
            browserView.loadUrl(url)
        }
    }

    private fun showFavoritesDialog() {
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toList() ?: emptyList()
        val currentUrl = browserView.webView.url ?: editUrl.text.toString()
        val items = favorites.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.btn_favorites)

        if (items.isEmpty()) {
            builder.setMessage("Список избранного пуст")
        } else {
            builder.setItems(items) { _, which ->
                val selected = items[which]
                editUrl.setText(selected)
                browserView.loadUrl(selected)
            }
        }

        builder.setPositiveButton("Добавить текущую страницу") { _, _ ->
            addFavorite(currentUrl)
        }
        builder.setNegativeButton("Закрыть", null)
        builder.show()
    }

    private fun addFavorite(url: String) {
        if (url.isBlank()) return
        val current = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(url)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        Toast.makeText(this, "Добавлено в избранное", Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && browserView.handleKeyEvent(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onVideoDetected(url: String) {
        val useExoPlayer = prefs.getBoolean(KEY_USE_EXOPLAYER, true)
        if (!useExoPlayer) {
            return
        }
        if (url == lastVideoUrlHandled) {
            return
        }
        lastVideoUrlHandled = url
        Log.d(TAG, "Открываю видео в ExoPlayer: $url")

        runOnUiThread {
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, url)
            intent.putExtra(VideoPlayerActivity.EXTRA_MIME_TYPE, VideoInterceptor.guessMimeType(url))
            startActivity(intent)
        }
    }

    override fun onPageStarted(url: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            editUrl.setText(url)
        }
    }

    override fun onPageFinished(url: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
        }
        lastVideoUrlHandled = null
    }

    override fun onProgressChanged(progress: Int) {
        runOnUiThread {
            progressBar.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
        }
    }

    override fun onReceivedTitle(title: String?) {
        // Заголовок страницы можно использовать для отображения в UI при необходимости.
    }

    override fun onWebViewError(errorCode: Int, description: String?, failingUrl: String?) {
        Log.e(TAG, "Ошибка WebView [$errorCode]: $description, url=$failingUrl")
    }

    override fun onDestroy() {
        super.onDestroy()
        browserView.destroy()
    }
}
