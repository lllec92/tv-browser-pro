package com.tvbrowser.pro

import android.view.KeyEvent

/**
 * Централизованная обработка событий пульта Android TV.
 * Поддерживает D-pad (UP/DOWN/LEFT/RIGHT), OK (выбор / Play-Pause)
 * и BACK (назад / выход).
 */
class RemoteController(private val callback: Callback) {

    interface Callback {
        fun onDpadUp() {}
        fun onDpadDown() {}
        fun onDpadLeft() {}
        fun onDpadRight() {}
        fun onSelect() {}
        fun onPlayPause() {}

        /**
         * Возвращает true, если BACK был обработан вручную
         * (например, выход из плеера), иначе false, чтобы
         * событие продолжило обычную обработку.
         */
        fun onBackPressed(): Boolean = false
    }

    /**
     * Возвращает true, если событие было полностью обработано
     * и не должно передаваться дальше по системе фокуса.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                callback.onDpadUp()
                false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                callback.onDpadDown()
                false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                callback.onDpadLeft()
                false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                callback.onDpadRight()
                false
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                callback.onSelect()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                callback.onPlayPause()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                callback.onBackPressed()
            }
            else -> false
        }
    }
}
