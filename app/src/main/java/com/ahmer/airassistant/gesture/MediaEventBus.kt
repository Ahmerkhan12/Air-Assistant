package com.ahmer.airassistant.gesture

import com.ahmer.airassistant.bluetooth.AirPodsBatteryInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MediaEventBus {
    private const val MAX_LOG_SIZE = 100

    private val _events = MutableStateFlow<List<MediaButtonEvent>>(emptyList())
    val events: StateFlow<List<MediaButtonEvent>> = _events.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _latestBattery = MutableStateFlow<AirPodsBatteryInfo?>(null)
    val latestBattery: StateFlow<AirPodsBatteryInfo?> = _latestBattery.asStateFlow()

    fun postEvent(event: MediaButtonEvent) {
        _events.update { currentList ->
            val updated = listOf(event) + currentList
            if (updated.size > MAX_LOG_SIZE) updated.take(MAX_LOG_SIZE) else updated
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateBattery(info: AirPodsBatteryInfo) {
        _latestBattery.value = info
    }

    fun clearEvents() {
        _events.value = emptyList()
    }
}
