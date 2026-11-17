package com.ahmer.airassistant.gesture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.ahmer.airassistant.assistant.AssistantLauncher
import com.ahmer.airassistant.settings.TriggerGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GestureDetector(
    private val context: Context,
    private val assistantLauncher: AssistantLauncher,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val tag = "GestureDetector"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var clickCount = 0
    private var lastClickTime = 0L
    private val doubleClickTimeout = 450L // ms window for multi-click

    private var currentConfiguredGesture = TriggerGesture.DOUBLE_PRESS

    fun setConfiguredGesture(gesture: TriggerGesture) {
        currentConfiguredGesture = gesture
    }

    /**
     * Processes incoming KeyEvents from MediaSession or MediaButtonReceiver.
     * Returns true if event is consumed, false if allowed to pass through to system/media apps.
     */
    fun processKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"

        Log.d(tag, "Received KeyEvent: keyCode=$keyCode (${MediaButtonEvent.getKeyName(keyCode)}), action=$action")

        // Only process on ACTION_UP to avoid double counting per press
        if (event.action != KeyEvent.ACTION_UP) {
            MediaEventBus.postEvent(
                MediaButtonEvent(
                    keyCode = keyCode,
                    action = action,
                    clickCount = 1,
                    triggeredAssistant = false,
                    details = "Key Down Event recorded"
                )
            )
            return false
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < doubleClickTimeout) {
            clickCount++
        } else {
            clickCount = 1
        }
        lastClickTime = currentTime

        val currentClicks = clickCount
        var triggeredAssistant = false

        // Check if gesture matches user trigger configuration
        when (currentConfiguredGesture) {
            TriggerGesture.SINGLE_TAP_AIRPODS_1 -> {
                if (isPlayPauseKey(keyCode) || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    triggeredAssistant = true
                    triggerAssistant("AirPods 1/2 Earbud Double-Tap: ${MediaButtonEvent.getKeyName(keyCode)}")
                }
            }
            TriggerGesture.DOUBLE_PRESS -> {
                if (isPlayPauseKey(keyCode) && currentClicks == 2) {
                    triggeredAssistant = true
                    triggerAssistant("Double Press ($currentClicks clicks) on ${MediaButtonEvent.getKeyName(keyCode)}")
                }
            }
            TriggerGesture.TRIPLE_PRESS -> {
                if (isPlayPauseKey(keyCode) && currentClicks == 3) {
                    triggeredAssistant = true
                    triggerAssistant("Triple Press ($currentClicks clicks) on ${MediaButtonEvent.getKeyName(keyCode)}")
                }
            }
            TriggerGesture.NEXT_TRACK -> {
                if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                    triggeredAssistant = true
                    triggerAssistant("Next Track key pressed")
                }
            }
            TriggerGesture.PREVIOUS_TRACK -> {
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                    triggeredAssistant = true
                    triggerAssistant("Previous Track key pressed")
                }
            }
            TriggerGesture.HEADSET_HOOK -> {
                if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                    triggeredAssistant = true
                    triggerAssistant("Headset hook pressed")
                }
            }
            TriggerGesture.ANY_EVENT_POC -> {
                triggeredAssistant = true
                triggerAssistant("POC mode: Activated by ${MediaButtonEvent.getKeyName(keyCode)}")
            }
        }

        val details = if (triggeredAssistant) {
            "Triggered Assistant via $currentConfiguredGesture"
        } else {
            "Click count: $currentClicks (Target: $currentConfiguredGesture)"
        }

        MediaEventBus.postEvent(
            MediaButtonEvent(
                keyCode = keyCode,
                action = action,
                clickCount = currentClicks,
                triggeredAssistant = triggeredAssistant,
                details = details
            )
        )

        // Reset click count after timeout window
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            clickCount = 0
        }, doubleClickTimeout)

        return triggeredAssistant
    }

    private fun isPlayPauseKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK
    }

    private fun triggerAssistant(reason: String) {
        Log.i(tag, "Triggering Assistant! Reason: $reason")
        coroutineScope.launch {
            assistantLauncher.launchAssistant()
        }
    }
}

