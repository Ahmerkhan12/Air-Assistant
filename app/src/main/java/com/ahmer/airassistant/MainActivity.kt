package com.ahmer.airassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ahmer.airassistant.assistant.AssistantLauncher
import com.ahmer.airassistant.bluetooth.AirBluetoothManager
import com.ahmer.airassistant.settings.SettingsRepository
import com.ahmer.airassistant.ui.DeviceScreen
import com.ahmer.airassistant.ui.HomeScreen
import com.ahmer.airassistant.ui.SettingsScreen
import com.ahmer.airassistant.ui.components.StatusChip
import com.ahmer.airassistant.ui.theme.AirAssistantTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: AirBluetoothManager
    private lateinit var assistantLauncher: AssistantLauncher
    private lateinit var settingsRepository: SettingsRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothManager.refreshDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = AirBluetoothManager(applicationContext)
        assistantLauncher = AssistantLauncher(this)
        settingsRepository = SettingsRepository(applicationContext)

        lifecycleScope.launch {
            settingsRepository.userSettingsFlow.collect { settings ->
                bluetoothManager.setSelectedDeviceAddress(settings.selectedDeviceAddress)
            }
        }

        requestRequiredPermissions()

        setContent {
            AirAssistantTheme {
                MainAppScaffold(
                    bluetoothManager = bluetoothManager,
                    assistantLauncher = assistantLauncher,
                    settingsRepository = settingsRepository,
                    onRequestPermissions = { requestRequiredPermissions() }
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        bluetoothManager.cleanUp()
        super.onDestroy()
    }
}

enum class NavigationItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Assistant", Icons.Default.Headphones),
    DEVICES("Devices", Icons.Default.Bluetooth),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    bluetoothManager: AirBluetoothManager,
    assistantLauncher: AssistantLauncher,
    settingsRepository: SettingsRepository,
    onRequestPermissions: () -> Unit
) {
    var selectedItem by remember { mutableStateOf(NavigationItem.HOME) }
    val bluetoothState by bluetoothManager.bluetoothState.collectAsState()

    // Intercept back gesture: if not on HOME, return to HOME rather than exiting
    BackHandler(enabled = selectedItem != NavigationItem.HOME) {
        selectedItem = NavigationItem.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "AirAssistant",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val isConnected = bluetoothState.hasActiveAirPods
                    StatusChip(
                        text = if (isConnected) "AirPods" else "Offline",
                        isActive = isConnected,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                NavigationItem.entries.forEach { item ->
                    val isSelected = selectedItem == item
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedItem = item },
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = selectedItem,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                },
                label = "tabTransition"
            ) { targetItem ->
                when (targetItem) {
                    NavigationItem.HOME -> HomeScreen(
                        bluetoothManager = bluetoothManager,
                        settingsRepository = settingsRepository,
                        assistantLauncher = assistantLauncher,
                        onRequestPermissions = onRequestPermissions
                    )
                    NavigationItem.DEVICES -> DeviceScreen(
                        bluetoothManager = bluetoothManager,
                        settingsRepository = settingsRepository
                    )
                    NavigationItem.SETTINGS -> SettingsScreen(
                        assistantLauncher = assistantLauncher,
                        settingsRepository = settingsRepository
                    )
                }
            }
        }
    }
}
