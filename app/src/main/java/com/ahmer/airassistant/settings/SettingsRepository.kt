package com.ahmer.airassistant.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "air_assistant_settings")

enum class TriggerGesture(val title: String, val description: String) {
    SINGLE_TAP_AIRPODS_1(
        "Double-Tap Earbud (AirPods 1/2)",
        "Trigger when you double-tap your AirPod earbud (standard AirPods 1 tap)"
    ),
    DOUBLE_PRESS(
        "Double Squeeze (AirPods 3 / Pro)",
        "Press the AirPods stem twice within 500ms"
    ),
    TRIPLE_PRESS(
        "Triple Press (Rapid)",
        "Press three times rapidly"
    ),
    NEXT_TRACK(
        "Next Track (Skip Forward)",
        "Trigger on Next Track media event"
    ),
    PREVIOUS_TRACK(
        "Previous Track (Rewind)",
        "Trigger on Previous Track media event"
    ),
    HEADSET_HOOK(
        "Headset Hook / Call Key",
        "Trigger on standard headset hook key event"
    ),
    ANY_EVENT_POC(
        "Any Media Key (Feasibility POC)",
        "Trigger assistant on any detected media event (Testing only)"
    )
}

data class UserSettings(
    val triggerGesture: TriggerGesture = TriggerGesture.SINGLE_TAP_AIRPODS_1,
    val autoStartOnAirPodsConnect: Boolean = true,
    val passThroughToMusicApp: Boolean = true,
    val testModeEnabled: Boolean = false,
    val selectedDeviceAddress: String? = null
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val TRIGGER_GESTURE = stringPreferencesKey("trigger_gesture")
        val AUTO_START = booleanPreferencesKey("auto_start_on_connect")
        val PASS_THROUGH = booleanPreferencesKey("pass_through_to_music")
        val TEST_MODE = booleanPreferencesKey("test_mode")
        val SELECTED_DEVICE_ADDRESS = stringPreferencesKey("selected_device_address")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val gestureStr = preferences[PreferencesKeys.TRIGGER_GESTURE] ?: TriggerGesture.DOUBLE_PRESS.name
        val gesture = try {
            TriggerGesture.valueOf(gestureStr)
        } catch (e: Exception) {
            TriggerGesture.DOUBLE_PRESS
        }

        UserSettings(
            triggerGesture = gesture,
            autoStartOnAirPodsConnect = preferences[PreferencesKeys.AUTO_START] ?: true,
            passThroughToMusicApp = preferences[PreferencesKeys.PASS_THROUGH] ?: true,
            testModeEnabled = preferences[PreferencesKeys.TEST_MODE] ?: false,
            selectedDeviceAddress = preferences[PreferencesKeys.SELECTED_DEVICE_ADDRESS]
        )
    }

    suspend fun updateTriggerGesture(gesture: TriggerGesture) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRIGGER_GESTURE] = gesture.name
        }
    }

    suspend fun setSelectedDeviceAddress(address: String?) {
        context.dataStore.edit { preferences ->
            if (address != null) {
                preferences[PreferencesKeys.SELECTED_DEVICE_ADDRESS] = address
            } else {
                preferences.remove(PreferencesKeys.SELECTED_DEVICE_ADDRESS)
            }
        }
    }

    suspend fun setAutoStart(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_START] = enabled
        }
    }

    suspend fun setPassThrough(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PASS_THROUGH] = enabled
        }
    }
}

