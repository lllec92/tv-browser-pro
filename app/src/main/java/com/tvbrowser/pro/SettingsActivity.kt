package com.tvbrowser.pro

import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Экран настроек TV Browser Pro: управление встроенным ExoPlayer,
 * аппаратным ускорением, авто-fullscreen, а также очистка
 * кеша и cookies браузера.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchExoPlayer: Switch
    private lateinit var switchHwAccel: Switch
    private lateinit var switchAutoFullscreen: Switch
    private lateinit var btnClearCache: Button
    private lateinit var btnClearCookies: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)

        switchExoPlayer = findViewById(R.id.switch_exoplayer)
        switchHwAccel = findViewById(R.id.switch_hw_accel)
        switchAutoFullscreen = findViewById(R.id.switch_auto_fullscreen)
        btnClearCache = findViewById(R.id.btn_clear_cache)
        btnClearCookies = findViewById(R.id.btn_clear_cookies)

        switchExoPlayer.isChecked = prefs.getBoolean(MainActivity.KEY_USE_EXOPLAYER, true)
        switchHwAccel.isChecked = prefs.getBoolean(MainActivity.KEY_HW_ACCEL, true)
        switchAutoFullscreen.isChecked = prefs.getBoolean(MainActivity.KEY_AUTO_FULLSCREEN, true)

        switchExoPlayer.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_USE_EXOPLAYER, isChecked).apply()
        }

        switchHwAccel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_HW_ACCEL, isChecked).apply()
        }

        switchAutoFullscreen.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(MainActivity.KEY_AUTO_FULLSCREEN, isChecked).apply()
        }

        btnClearCache.setOnClickListener {
            clearCache()
        }

        btnClearCookies.setOnClickListener {
            clearCookies()
        }
    }

    private fun clearCache() {
        WebStorage.getInstance().deleteAllData()
        cacheDir.deleteRecursively()
        Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun clearCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show()
    }
}
