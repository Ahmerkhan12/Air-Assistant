package com.ahmer.airassistant.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ahmer.airassistant.assistant.AssistantLauncher
import com.ahmer.airassistant.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AirAccessibilityService : AccessibilityService() {

    private val tag = "AirAccessibility"
    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var gestureDetector: GestureDetector
    private lateinit var assistantLauncher: AssistantLauncher
    private lateinit var settingsRepository: SettingsRepository
    private var passThroughToMusic = true

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "AirAccessibilityService onCreate")

        assistantLauncher = AssistantLauncher(this)
        gestureDetector = GestureDetector(this, assistantLauncher)
        settingsRepository = SettingsRepository(this)

        settingsRepository.userSettingsFlow.onEach { settings ->
            gestureDetector.setConfiguredGesture(settings.triggerGesture)
            passThroughToMusic = settings.passThroughToMusicApp
        }.launchIn(scope)

        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(tag, "AirAccessibilityService onServiceConnected - Requesting Key Filtering")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val keyCode = event.keyCode
        Log.d(tag, "Accessibility onKeyEvent: keyCode=$keyCode action=${event.action}")

        // Check if this is a headset / media key
        if (isMediaKey(keyCode)) {
            val handled = gestureDetector.processKeyEvent(event)
            if (handled) {
                // If user doesn't want music to skip, consume event by returning true
                return !passThroughToMusic
            }
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        Log.d(tag, "AirAccessibilityService onDestroy")
        serviceJob.cancel()
        instance = null
        super.onDestroy()
    }

    private fun isMediaKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_STOP
    }

    companion object {
        var instance: AirAccessibilityService? = null
            private set

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${AirAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.split(":").any { it.equals(expectedServiceName, ignoreCase = true) }
        }
    }
}

