package com.ahmer.airassistant.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import android.view.KeyEvent
import com.ahmer.airassistant.gesture.MediaEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private var bleScanner: BluetoothLeScanner? = null
    private var isBleScanning = false
    private var lastActiveAirPodsConnected = false

    private val _bluetoothState = MutableStateFlow(BluetoothState())
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    // Event flow to trigger connection popup modal
    private val _connectionPopupEvent = MutableSharedFlow<AirPodsBatteryInfo>(replay = 0, extraBufferCapacity = 2)
    val connectionPopupEvent: SharedFlow<AirPodsBatteryInfo> = _connectionPopupEvent.asSharedFlow()

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

            when (action) {
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (level in 0..100) {
                        Log.d(tag, "Device battery broadcast: ${device?.name ?: "Unknown"} -> $level%")
                        updateOverallBattery(level)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    refreshDevices()
                    startBleScan()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    refreshDevices()
                }
                else -> {
                    refreshDevices()
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val scanRecord = result?.scanRecord ?: return
            val manufacturerData = scanRecord.getManufacturerSpecificData(AirPodsBleDecoder.APPLE_MANUFACTURER_ID)
                ?: return

            val decoded = AirPodsBleDecoder.decode(manufacturerData)
            if (decoded != null && decoded.hasAnyBattery) {
                Log.d(tag, "Decoded AirPods battery: Left=${decoded.left}%, Right=${decoded.right}%, Case=${decoded.case}%")
                updateAirPodsBattery(decoded)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "AirPods BLE scan failed: errorCode=$errorCode")
            isBleScanning = false
        }
    }

    init {
        registerReceivers()
        connectProfiles()
        refreshDevices()
        startBleScan()
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

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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

    /**
     * Reads battery level using Android's internal getBatteryLevel reflection method.
     */
    @SuppressLint("MissingPermission")
    fun getDeviceBatteryLevel(device: BluetoothDevice): Int? {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int
            if (level != null && level in 0..100) level else null
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (isBleScanning || !hasScanPermission() || adapter == null || !adapter.isEnabled) {
            return
        }

        try {
            bleScanner = adapter.bluetoothLeScanner
            if (bleScanner == null) return

            val scanFilter = ScanFilter.Builder()
                .setManufacturerData(AirPodsBleDecoder.APPLE_MANUFACTURER_ID, byteArrayOf())
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            bleScanner?.startScan(listOf(scanFilter), scanSettings, bleScanCallback)
            isBleScanning = true
            Log.d(tag, "Started AirPods BLE scan for battery beacons")
        } catch (e: SecurityException) {
            Log.w(tag, "Security exception starting BLE scan: ${e.message}")
        } catch (e: Exception) {
            Log.w(tag, "Error starting BLE scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (!isBleScanning) return
        try {
            bleScanner?.stopScan(bleScanCallback)
            isBleScanning = false
            Log.d(tag, "Stopped AirPods BLE scan")
        } catch (e: Exception) {
            Log.w(tag, "Error stopping BLE scan: ${e.message}")
        }
    }

    private var lastLeftInEar: Boolean? = null
    private var lastRightInEar: Boolean? = null

    private fun updateAirPodsBattery(newBattery: AirPodsBatteryInfo) {
        MediaEventBus.updateBattery(newBattery)
        handleInEarDetection(newBattery)

        _bluetoothState.update { current ->
            val updatedActive = current.activeAirPods?.copy(batteryInfo = newBattery)
            current.copy(
                latestBatteryInfo = newBattery,
                activeAirPods = updatedActive
            )
        }
    }

    private fun handleInEarDetection(battery: AirPodsBatteryInfo) {
        if (!battery.isInEarSupported) return

        val prevLeft = lastLeftInEar
        val prevRight = lastRightInEar

        lastLeftInEar = battery.isLeftInEar
        lastRightInEar = battery.isRightInEar

        // Only react after baseline is established
        if (prevLeft == null && prevRight == null) return

        val wasInEar = (prevLeft == true) || (prevRight == true)
        val nowInEar = battery.isLeftInEar || battery.isRightInEar

        if (wasInEar && !nowInEar) {
            Log.d(tag, "In-Ear Detection: Earbud removed from ear -> Pausing playback")
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else if (!wasInEar && nowInEar) {
            Log.d(tag, "In-Ear Detection: Earbud inserted into ear -> Resuming playback")
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        try {
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.w(tag, "Failed to dispatch in-ear media key: ${e.message}")
        }
    }

    private fun updateOverallBattery(overallPercent: Int) {
        _bluetoothState.update { current ->
            val existing = current.latestBatteryInfo ?: AirPodsBatteryInfo()
            val updated = existing.copy(overall = overallPercent, lastUpdated = System.currentTimeMillis())
            val updatedActive = current.activeAirPods?.copy(batteryInfo = updated)
            current.copy(
                latestBatteryInfo = updated,
                activeAirPods = updatedActive
            )
        }
    }

    /**
     * Triggers a preview of the AirPods connection popup for in-app testing.
     */
    fun triggerPopupPreview() {
        val sampleBattery = _bluetoothState.value.latestBatteryInfo ?: AirPodsBatteryInfo(
            left = 85,
            right = 90,
            case = 75,
            isLeftCharging = true,
            isRightCharging = false,
            isCaseCharging = false,
            modelName = "AirPods Pro"
        )
        _connectionPopupEvent.tryEmit(sampleBattery)
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        scope.launch {
            val isEnabled = adapter?.isEnabled == true
            val hasPermission = hasConnectPermission()
            val hasScan = hasScanPermission()

            if (adapter == null || !adapter.isEnabled || !hasPermission) {
                _bluetoothState.update {
                    it.copy(
                        isBluetoothEnabled = isEnabled,
                        hasConnectPermission = hasPermission,
                        hasScanPermission = hasScan,
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
                    val rawBattery = getDeviceBatteryLevel(device)
                    val existing = connectedSet[address]
                    val profiles = (existing?.supportedProfiles ?: emptyList()) + "HEADSET (HFP)"
                    val battery = existing?.batteryInfo ?: (if (rawBattery != null) AirPodsBatteryInfo(overall = rawBattery) else null)

                    connectedSet[address] = BluetoothDeviceInfo(
                        name = name,
                        address = address,
                        isAirPods = isAirPods,
                        isConnected = true,
                        isBonded = true,
                        supportedProfiles = profiles.distinct(),
                        batteryInfo = battery
                    )
                }

                // 2. Check A2DP profile connected devices
                a2dpProfile?.connectedDevices?.forEach { device ->
                    val name = getDeviceDisplayName(device)
                    val address = device.address ?: ""
                    val isAirPods = isLikelyAirPods(name)
                    val rawBattery = getDeviceBatteryLevel(device)
                    val existing = connectedSet[address]
                    val profiles = (existing?.supportedProfiles ?: emptyList()) + "A2DP (Audio)"
                    val battery = existing?.batteryInfo ?: (if (rawBattery != null) AirPodsBatteryInfo(overall = rawBattery) else null)

                    connectedSet[address] = BluetoothDeviceInfo(
                        name = name,
                        address = address,
                        isAirPods = isAirPods,
                        isConnected = true,
                        isBonded = true,
                        supportedProfiles = profiles.distinct(),
                        batteryInfo = battery
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
                                connectedSet[name] = BluetoothDeviceInfo(
                                    name = name,
                                    address = "",
                                    isAirPods = isLikelyAirPods(name),
                                    isConnected = true,
                                    isBonded = true,
                                    supportedProfiles = listOf("Audio Active"),
                                    batteryInfo = _bluetoothState.value.latestBatteryInfo
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "AudioManager device fallback failed", e)
                    }
                }

                // 4. Collect paired/bonded devices
                adapter.bondedDevices?.forEach { device ->
                    val name = getDeviceDisplayName(device)
                    val address = device.address ?: ""
                    val isAirPods = isLikelyAirPods(name)
                    val isConnectedNow = connectedSet.containsKey(address)
                    val rawBattery = if (isConnectedNow) getDeviceBatteryLevel(device) else null
                    val existing = connectedSet[address]

                    bondedList.add(
                        BluetoothDeviceInfo(
                            name = name,
                            address = address,
                            isAirPods = isAirPods,
                            isConnected = isConnectedNow,
                            isBonded = true,
                            batteryInfo = existing?.batteryInfo ?: (if (rawBattery != null) AirPodsBatteryInfo(overall = rawBattery) else null)
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

            val isAirPodsNowConnected = targetActive != null && targetActive.isAirPods

            // Check if connection just occurred: trigger connection popup modal!
            if (!lastActiveAirPodsConnected && isAirPodsNowConnected) {
                val batteryToShow = targetActive.batteryInfo ?: _bluetoothState.value.latestBatteryInfo ?: AirPodsBatteryInfo(
                    modelName = targetActive.name
                )
                _connectionPopupEvent.tryEmit(batteryToShow)
            }
            lastActiveAirPodsConnected = isAirPodsNowConnected

            _bluetoothState.update {
                it.copy(
                    isBluetoothEnabled = isEnabled,
                    hasConnectPermission = hasPermission,
                    hasScanPermission = hasScan,
                    connectedDevices = connectedList,
                    bondedDevices = bondedList,
                    activeAirPods = targetActive,
                    pairedAirPods = targetPaired,
                    latestBatteryInfo = targetActive?.batteryInfo ?: it.latestBatteryInfo,
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
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun cleanUp() {
        stopBleScan()
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
