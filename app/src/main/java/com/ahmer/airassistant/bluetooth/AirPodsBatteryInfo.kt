package com.ahmer.airassistant.bluetooth

data class AirPodsBatteryInfo(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val overall: Int? = null,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val modelName: String = "AirPods",
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val hasLeft: Boolean get() = left != null && left in 0..100
    val hasRight: Boolean get() = right != null && right in 0..100
    val hasCase: Boolean get() = case != null && case in 0..100
    val hasOverall: Boolean get() = overall != null && overall in 0..100

    val hasSeparateLevels: Boolean get() = hasLeft || hasRight || hasCase
    val hasAnyBattery: Boolean get() = hasSeparateLevels || hasOverall

    /**
     * Checks if any non-charging pod or case is below 20%.
     */
    val isLowBattery: Boolean
        get() {
            if (hasLeft && !isLeftCharging && (left ?: 100) <= 20) return true
            if (hasRight && !isRightCharging && (right ?: 100) <= 20) return true
            if (hasCase && !isCaseCharging && (case ?: 100) <= 20) return true
            if (!hasSeparateLevels && hasOverall && (overall ?: 100) <= 20) return true
            return false
        }

    /**
     * Human readable description of low battery components.
     */
    val lowBatteryWarningText: String?
        get() {
            val warnings = mutableListOf<String>()
            if (hasLeft && !isLeftCharging && (left ?: 100) <= 20) warnings.add("Left ($left%)")
            if (hasRight && !isRightCharging && (right ?: 100) <= 20) warnings.add("Right ($right%)")
            if (hasCase && !isCaseCharging && (case ?: 100) <= 20) warnings.add("Case ($case%)")
            if (warnings.isEmpty() && hasOverall && (overall ?: 100) <= 20) {
                warnings.add("Battery ($overall%)")
            }
            return if (warnings.isNotEmpty()) "Low Battery: ${warnings.joinToString(", ")}" else null
        }
}

