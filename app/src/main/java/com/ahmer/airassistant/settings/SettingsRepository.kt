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
    val selectedDeviceAddress: String? = null,
    val inEarDetectionEnabled: Boolean = true,
    val showBatteryNotification: Boolean = true,
    val lowBatteryAlertsEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val TRIGGER_GESTURE = stringPreferencesKey("trigger_gesture")
        val AUTO_START = booleanPreferencesKey("auto_start_on_connect")
        val PASS_THROUGH = booleanPreferencesKey("pass_through_to_music")
        val TEST_MODE = booleanPreferencesKey("test_mode")
        val SELECTED_DEVICE_ADDRESS = stringPreferencesKey("selected_device_address")
        val IN_EAR_DETECTION = booleanPreferencesKey("in_ear_detection")
        val SHOW_BATTERY_NOTIFICATION = booleanPreferencesKey("show_battery_notification")
        val LOW_BATTERY_ALERTS = booleanPreferencesKey("low_battery_alerts")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val gestureStr = preferences[PreferencesKeys.TRIGGER_GESTURE]
        val gesture = try {
            if (gestureStr != null) TriggerGesture.valueOf(gestureStr) else TriggerGesture.SINGLE_TAP_AIRPODS_1
        } catch (e: Exception) {
            TriggerGesture.SINGLE_TAP_AIRPODS_1
        }

        UserSettings(
            triggerGesture = gesture,
            autoStartOnAirPodsConnect = preferences[PreferencesKeys.AUTO_START] ?: true,
            passThroughToMusicApp = preferences[PreferencesKeys.PASS_THROUGH] ?: true,
            testModeEnabled = preferences[PreferencesKeys.TEST_MODE] ?: false,
            selectedDeviceAddress = preferences[PreferencesKeys.SELECTED_DEVICE_ADDRESS],
            inEarDetectionEnabled = preferences[PreferencesKeys.IN_EAR_DETECTION] ?: true,
            showBatteryNotification = preferences[PreferencesKeys.SHOW_BATTERY_NOTIFICATION] ?: true,
            lowBatteryAlertsEnabled = preferences[PreferencesKeys.LOW_BATTERY_ALERTS] ?: true
        )
    }

    suspend fun updateTriggerGesture(gesture: TriggerGesture) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRIGGER_GESTURE] = gesture.name
        }
    }

    suspend fun updateAutoStart(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_START] = enabled
        }
    }
    suspend fun setAutoStart(enabled: Boolean) = updateAutoStart(enabled)

    suspend fun updatePassThrough(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PASS_THROUGH] = enabled
        }
    }
    suspend fun setPassThrough(enabled: Boolean) = updatePassThrough(enabled)

    suspend fun updateTestMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEST_MODE] = enabled
        }
    }
    suspend fun setTestMode(enabled: Boolean) = updateTestMode(enabled)

    suspend fun setSelectedDeviceAddress(address: String?) {
        context.dataStore.edit { preferences ->
            if (address != null) {
                preferences[PreferencesKeys.SELECTED_DEVICE_ADDRESS] = address
            } else {
                preferences.remove(PreferencesKeys.SELECTED_DEVICE_ADDRESS)
            }
        }
    }

    suspend fun updateInEarDetection(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IN_EAR_DETECTION] = enabled
        }
    }

    suspend fun updateShowBatteryNotification(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_BATTERY_NOTIFICATION] = enabled
        }
    }

    suspend fun updateLowBatteryAlerts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOW_BATTERY_ALERTS] = enabled
        }
    }
}
