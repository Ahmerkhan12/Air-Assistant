package com.ahmer.airassistant.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class AirBluetoothManager(private val context: Context) {

    private val tag = "AirBluetoothManager"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val androidBluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    private val adapter: BluetoothAdapter? = androidBluetoothManager?.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var headsetProfile: BluetoothHeadset? = null
    private var a2dpProfile: BluetoothA2dp? = null
    private var currentSelectedAddress: String? = null

    private val _bluetoothState = MutableStateFlow(BluetoothState())
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.HEADSET -> {
                    headsetProfile = proxy as BluetoothHeadset
                    Log.d(tag, "Headset profile proxy connected")
                }
                BluetoothProfile.A2DP -> {
                    a2dpProfile = proxy as BluetoothA2dp
                    Log.d(tag, "A2DP profile proxy connected")
                }
            }
            refreshDevices()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.HEADSET -> headsetProfile = null
                BluetoothProfile.A2DP -> a2dpProfile = null
            }
            refreshDevices()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(tag, "Bluetooth broadcast received: $action")
            refreshDevices()
        }
    }

    init {
        registerReceivers()
        connectProfiles()
        refreshDevices()
    }

    fun setSelectedDeviceAddress(address: String?) {
        currentSelectedAddress = address
        refreshDevices()
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceDisplayName(device: BluetoothDevice): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            device.alias ?: device.name ?: "Unknown Device"
        } else {
            device.name ?: "Unknown Device"
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        scope.launch {
            val isEnabled = adapter?.isEnabled == true
            val hasPermission = hasConnectPermission()

            if (!isEnabled || !hasPermission || adapter == null) {
                _bluetoothState.update {
                    it.copy(
                        isBluetoothEnabled = isEnabled,
                        hasConnectPermission = hasPermission,
                        connectedDevices = emptyList(),
                        bondedDevices = emptyList(),
                        activeAirPods = null,
                        pairedAirPods = null,
                        lastStateUpdateTime = System.currentTimeMillis()
                    )
                }
                return@launch
            }

            val connectedSet = mutableMapOf<String, BluetoothDeviceInfo>()
            val bondedList = mutableListOf<BluetoothDeviceInfo>()

            try {
                // 1. Check HEADSET (HFP) profile connected devices
                headsetProfile?.connectedDevices?.forEach { device ->
                    val name = getDeviceDisplayName(device)
                    val address = device.address ?: ""
                    val isAirPods = isLikelyAirPods(name)
                    val existing = connectedSet[address]
                    val profiles = (existing?.supportedProfiles ?: emptyList()) + "HEADSET (HFP)"
                    connectedSet[address] = BluetoothDeviceInfo(
                        name = name,
                        address = address,
                        isAirPods = isAirPods,
                        isConnected = true,
                        isBonded = true,
                        supportedProfiles = profiles.distinct()
                    )
                }

                // 2. Check A2DP profile connected devices
                a2dpProfile?.connectedDevices?.forEach { device ->
                    val name = getDeviceDisplayName(device)
                    val address = device.address ?: ""
                    val isAirPods = isLikelyAirPods(name)
                    val existing = connectedSet[address]
                    val profiles = (existing?.supportedProfiles ?: emptyList()) + "A2DP (Audio)"
                    connectedSet[address] = BluetoothDeviceInfo(
                        name = name,
                        address = address,
                        isAirPods = isAirPods,
                        isConnected = true,
                        isBonded = true,
                        supportedProfiles = profiles.distinct()
                    )
                }

                // 3. Fallback: Query system AudioManager for active Bluetooth outputs
                if (connectedSet.isEmpty() && audioManager != null) {
                    try {
                        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        outputs.forEach { audioDevice ->
                            if (audioDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                audioDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                audioDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                            ) {
                                val name = audioDevice.productName?.toString() ?: "Bluetooth Audio"
                                // AudioDeviceInfo does NOT expose MAC address — use name as key
                                connectedSet[name] = BluetoothDeviceInfo(
                                    name = name,
                                    address = "",
                                    isAirPods = isLikelyAirPods(name),
                                    isConnected = true,
                                    isBonded = true,
                                    supportedProfiles = listOf("Audio Active")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "AudioManager device fallback failed", e)
                    }
                }

                // 4. Collect paired/bonded devices (accurately marked with isConnected)
                adapter.bondedDevices?.forEach { device ->
                    val name = getDeviceDisplayName(device)
                    val address = device.address ?: ""
                    val isAirPods = isLikelyAirPods(name)
                    val isConnectedNow = connectedSet.containsKey(address)
                    bondedList.add(
                        BluetoothDeviceInfo(
                            name = name,
                            address = address,
                            isAirPods = isAirPods,
                            isConnected = isConnectedNow,
                            isBonded = true
                        )
                    )
                }
            } catch (e: SecurityException) {
                Log.e(tag, "Security exception while reading Bluetooth devices", e)
            }

            val connectedList = connectedSet.values.toList()

            // Resolve which device is the active target
            val targetActive: BluetoothDeviceInfo? = if (currentSelectedAddress != null) {
                connectedList.firstOrNull { it.address.equals(currentSelectedAddress, ignoreCase = true) }
            } else {
                connectedList.firstOrNull { it.isAirPods } ?: connectedList.firstOrNull()
            }

            // Resolve paired fallback for display when disconnected
            val targetPaired: BluetoothDeviceInfo? = if (currentSelectedAddress != null) {
                bondedList.firstOrNull { it.address.equals(currentSelectedAddress, ignoreCase = true) }
            } else {
                bondedList.firstOrNull { it.isAirPods }
            }

            _bluetoothState.update {
                it.copy(
                    isBluetoothEnabled = isEnabled,
                    hasConnectPermission = hasPermission,
                    connectedDevices = connectedList,
                    bondedDevices = bondedList,
                    activeAirPods = targetActive, // ONLY non-null if actively connected!
                    pairedAirPods = targetPaired,
                    lastStateUpdateTime = System.currentTimeMillis()
                )
            }
        }
    }

    private fun connectProfiles() {
        try {
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.e(tag, "Error connecting profile proxies", e)
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun cleanUp() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(tag, "Receiver already unregistered", e)
        }
        headsetProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        a2dpProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
    }

    companion object {
        fun isLikelyAirPods(deviceName: String?): Boolean {
            if (deviceName.isNullOrBlank()) return false
            val lower = deviceName.lowercase(Locale.ROOT)
            return lower.contains("airpod") ||
                    lower.contains("air pods") ||
                    lower.contains("air-pod") ||
                    lower.contains("pro's airpods") ||
                    lower.contains("airpods pro") ||
                    lower.contains("airpods max")
        }
    }
}
