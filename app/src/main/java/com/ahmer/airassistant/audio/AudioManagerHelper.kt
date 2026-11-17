package com.ahmer.airassistant.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class AudioRoutingState(
    val isBluetoothA2dpConnected: Boolean = false,
    val isBluetoothScoConnected: Boolean = false,
    val activeCommunicationDevice: String? = null,
    val availableAudioDevices: List<String> = emptyList()
)

class AudioManagerHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getAudioRoutingState(): AudioRoutingState {
        val availableDevices = mutableListOf<String>()
        var activeDeviceName: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            devices.forEach { device ->
                val typeName = getDeviceTypeName(device.type)
                availableDevices.add("${device.productName} ($typeName)")
            }
            audioManager.communicationDevice?.let { active ->
                activeDeviceName = "${active.productName} (${getDeviceTypeName(active.type)})"
            }
        } else {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS)
            devices.forEach { device ->
                availableDevices.add("${device.productName} (${getDeviceTypeName(device.type)})")
            }
        }

        @Suppress("DEPRECATION")
        val isA2dpOn = audioManager.isBluetoothA2dpOn
        @Suppress("DEPRECATION")
        val isScoOn = audioManager.isBluetoothScoOn

        return AudioRoutingState(
            isBluetoothA2dpConnected = isA2dpOn,
            isBluetoothScoConnected = isScoOn,
            activeCommunicationDevice = activeDeviceName,
            availableAudioDevices = availableDevices.distinct()
        )
    }

    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO (Headset)"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            else -> "Audio Type ($type)"
        }
    }
}

