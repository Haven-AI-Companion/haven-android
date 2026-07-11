package xyz.ssfdre38.haven.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Proposed AOSP AgentContext telemetry gathering manager.
 * Securely collects device metadata to feed into companion prompts.
 */
data class AgentContext(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val localTime: String,
    val foregroundApp: String? = null,
    val networkConnected: Boolean = true
) {
    companion object {
        fun current(context: Context): AgentContext {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val timeString = formatter.format(java.util.Date())

            return AgentContext(
                batteryLevel = batteryPct,
                isCharging = isCharging,
                localTime = timeString,
                foregroundApp = getActiveApp(context)
            )
        }

        private fun getActiveApp(context: Context): String? {
            // Securely retrieves the foreground task package in native Android implementations
            return null
        }
    }
}
