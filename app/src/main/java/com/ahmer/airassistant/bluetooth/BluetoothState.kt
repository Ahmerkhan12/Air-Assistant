package com.ahmer.airassistant.bluetooth

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isAirPods: Boolean,
    val isConnected: Boolean,
    val isBonded: Boolean = true,
    val supportedProfiles: List<String> = emptyList(),
    val batteryInfo: AirPodsBatteryInfo? = null
)

data class BluetoothState(
    val isBluetoothEnabled: Boolean = false,
    val hasConnectPermission: Boolean = false,
    val hasScanPermission: Boolean = false,
    val connectedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val bondedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val activeAirPods: BluetoothDeviceInfo? = null,
    val pairedAirPods: BluetoothDeviceInfo? = null,
    val latestBatteryInfo: AirPodsBatteryInfo? = null,
    val lastStateUpdateTime: Long = System.currentTimeMillis()
) {
    val hasActiveAirPods: Boolean
        get() = activeAirPods != null && activeAirPods.isConnected
}
