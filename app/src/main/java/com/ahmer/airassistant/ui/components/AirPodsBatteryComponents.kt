package com.ahmer.airassistant.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahmer.airassistant.bluetooth.AirPodsBatteryInfo
import kotlinx.coroutines.delay

/**
 * Returns color appropriate for battery level and charging status.
 */
@Composable
fun getBatteryColor(percent: Int, isCharging: Boolean): Color {
    return when {
        isCharging -> Color(0xFF10B981) // Emerald Green when charging
        percent <= 20 -> MaterialTheme.colorScheme.error // Red warning
        percent <= 50 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFF10B981) // Emerald Green healthy
    }
}

/**
 * Individual battery gauge column for Left, Right, or Case.
 */
@Composable
fun BatteryGaugeItem(
    label: String,
    percent: Int?,
    isCharging: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icon & Charging Badge
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = label,
                            modifier = Modifier.size(20.dp),
                            tint = if (percent != null) getBatteryColor(percent, isCharging) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isCharging) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981),
                        modifier = Modifier
                            .size(14.dp)
                            .offset(x = 2.dp, y = (-2).dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = "Charging",
                                tint = Color.Black,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            // Percentage Text
            if (percent != null) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getBatteryColor(percent, isCharging)
                )

                // Mini Progress Bar
                LinearProgressIndicator(
                    progress = { (percent.coerceIn(0, 100) / 100f) },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = getBatteryColor(percent, isCharging),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            } else {
                Text(
                    text = "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Modern battery container displaying Left, Right, Case or Fallback level.
 */
@Composable
fun AirPodsBatteryCard(
    batteryInfo: AirPodsBatteryInfo?,
    modifier: Modifier = Modifier
) {
    if (batteryInfo == null || !batteryInfo.hasAnyBattery) {
        // Battery unavailable state
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.BatteryUnknown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Battery level unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Low battery warning banner if needed
        if (batteryInfo.isLowBattery) {
            LowBatteryAlertBanner(text = batteryInfo.lowBatteryWarningText ?: "Low Battery (< 20%)")
        }

        if (batteryInfo.hasSeparateLevels) {
            // 3-Column Gauges (Left, Case, Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatteryGaugeItem(
                    label = "Left Pod",
                    percent = batteryInfo.left,
                    isCharging = batteryInfo.isLeftCharging,
                    icon = Icons.Default.Headphones,
                    modifier = Modifier.weight(1f)
                )

                BatteryGaugeItem(
                    label = "Case",
                    percent = batteryInfo.case,
                    isCharging = batteryInfo.isCaseCharging,
                    icon = Icons.Default.BatteryChargingFull,
                    modifier = Modifier.weight(1f)
                )

                BatteryGaugeItem(
                    label = "Right Pod",
                    percent = batteryInfo.right,
                    isCharging = batteryInfo.isRightCharging,
                    icon = Icons.Default.Headphones,
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (batteryInfo.hasOverall) {
            // Single Overall Battery Level
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = getBatteryColor(batteryInfo.overall ?: 100, false),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AirPods Battery",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "${batteryInfo.overall}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = getBatteryColor(batteryInfo.overall ?: 100, false)
                    )
                }
            }
        }
    }
}

/**
 * Red warning banner when battery is <= 20%.
 */
@Composable
fun LowBatteryAlertBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * iOS-styled AirPods Connection Popup modal with smooth slide-up animation.
 * Automatically dismisses after [autoDismissSeconds] seconds.
 */
@Composable
fun AirPodsConnectionPopup(
    batteryInfo: AirPodsBatteryInfo,
    onDismiss: () -> Unit,
    autoDismissSeconds: Int = 6
) {
    // Auto-dismiss countdown timer
    LaunchedEffect(batteryInfo) {
        delay(autoDismissSeconds * 1000L)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept clicks so card doesn't dismiss itself
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Grabber handle
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(2.dp)
                            )
                    )

                    // Header with device name and close icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = batteryInfo.modelName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connected & Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Battery gauges
                    AirPodsBatteryCard(batteryInfo = batteryInfo)

                    // Footer action button
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

