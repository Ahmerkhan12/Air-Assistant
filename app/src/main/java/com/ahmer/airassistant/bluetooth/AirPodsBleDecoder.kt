package com.ahmer.airassistant.bluetooth

import android.util.Log

object AirPodsBleDecoder {

    private const val TAG = "AirPodsBleDecoder"
    const val APPLE_MANUFACTURER_ID = 0x004c

    /**
     * Decodes raw Apple BLE manufacturer advertisement data into AirPodsBatteryInfo.
     * Returns null if data does not match an AirPods proximity beacon.
     */
    fun decode(manufacturerData: ByteArray): AirPodsBatteryInfo? {
        if (manufacturerData.size < 6) return null

        try {
            val type = manufacturerData[0].toInt() and 0xFF
            // Proximity Pairing / Status beacon type is 0x07
            if (type != 0x07) return null

            val length = manufacturerData[1].toInt() and 0xFF
            if (manufacturerData.size < 6 || (length > 0 && manufacturerData.size < length + 2)) {
                // Not enough bytes
                return null
            }

            val deviceModelByte = manufacturerData[2].toInt() and 0xFF
            val modelName = resolveModelName(deviceModelByte)

            // Byte 3: Pods battery (Upper nibble: Left/Right, Lower nibble: Right/Left)
            val byte3 = manufacturerData[3].toInt() and 0xFF
            val rawPodA = (byte3 shr 4) and 0x0F
            val rawPodB = byte3 and 0x0F

            // Byte 4: Case battery and charging indicators
            val byte4 = manufacturerData[4].toInt() and 0xFF
            val rawCase = (byte4 shr 4) and 0x0F
            val chargingBits = byte4 and 0x0F

            // Byte 5: In-ear status & Flip bit (Bit 5 / 0x20 determines left/right flip)
            val byte5 = manufacturerData[5].toInt() and 0xFF
            val isFlipped = (byte5 and 0x20) != 0

            // Determine left vs right based on flip bit
            val (rawLeft, rawRight) = if (isFlipped) {
                Pair(rawPodB, rawPodA)
            } else {
                Pair(rawPodA, rawPodB)
            }

            // Charging bitmask:
            // Bit 0 = Pod A charging, Bit 1 = Pod B charging, Bit 2 = Case charging
            val isPodACharging = (chargingBits and 0x01) != 0
            val isPodBCharging = (chargingBits and 0x02) != 0
            val isCaseCharging = (chargingBits and 0x04) != 0

            val (isLeftCharging, isRightCharging) = if (isFlipped) {
                Pair(isPodBCharging, isPodACharging)
            } else {
                Pair(isPodACharging, isPodBCharging)
            }

            // Values: 0..10 represent 0%..100% (value * 10, or 100% for 10).
            // Value 15 (0xF) means disconnected or unavailable.
            val leftPercent = toPercentage(rawLeft)
            val rightPercent = toPercentage(rawRight)
            val casePercent = toPercentage(rawCase)

            if (leftPercent == null && rightPercent == null && casePercent == null) {
                return null
            }

            return AirPodsBatteryInfo(
                left = leftPercent,
                right = rightPercent,
                case = casePercent,
                isLeftCharging = isLeftCharging,
                isRightCharging = isRightCharging,
                isCaseCharging = isCaseCharging,
                modelName = modelName,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error decoding AirPods BLE packet: ${e.message}")
            return null
        }
    }

    private fun toPercentage(rawValue: Int): Int? {
        return when (rawValue) {
            in 0..9 -> rawValue * 10 + 5 // Map 0..9 to 5%..95% midpoints
            10 -> 100
            else -> null // 15 or unknown = disconnected / out of range
        }
    }

    private fun resolveModelName(modelByte: Int): String {
        return when (modelByte) {
            0x02, 0x0F -> "AirPods"
            0x0E -> "AirPods Pro"
            0x13 -> "AirPods (3rd Gen)"
            0x14 -> "AirPods Pro (2nd Gen)"
            0x0A -> "AirPods Max"
            0x17 -> "AirPods 4"
            else -> "AirPods"
        }
    }
}

