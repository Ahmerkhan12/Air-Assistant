package com.ahmer.airassistant.gesture

import android.view.KeyEvent

data class MediaButtonEvent(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val keyCode: Int,
    val keyName: String = getKeyName(keyCode),
    val action: String,
    val clickCount: Int = 1,
    val triggeredAssistant: Boolean = false,
    val details: String = ""
) {
    companion object {
        fun getKeyName(keyCode: Int): String {
            return when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> "KEYCODE_MEDIA_PLAY"
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "KEYCODE_MEDIA_PAUSE"
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "KEYCODE_MEDIA_PLAY_PAUSE"
                KeyEvent.KEYCODE_MEDIA_NEXT -> "KEYCODE_MEDIA_NEXT"
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "KEYCODE_MEDIA_PREVIOUS"
                KeyEvent.KEYCODE_MEDIA_STOP -> "KEYCODE_MEDIA_STOP"
                KeyEvent.KEYCODE_HEADSETHOOK -> "KEYCODE_HEADSETHOOK"
                KeyEvent.KEYCODE_VOICE_ASSIST -> "KEYCODE_VOICE_ASSIST"
                else -> "KEYCODE ($keyCode)"
            }
        }
    }
}

