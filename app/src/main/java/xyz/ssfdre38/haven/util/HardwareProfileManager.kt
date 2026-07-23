package xyz.ssfdre38.haven.util

import android.content.Context
import android.os.Build
import org.json.JSONObject

data class GpuRenderConfig(
    val tierName: String,
    val maxLightIntensity: Float,
    val shadowMapResolution: Int,
    val enableShadows: Boolean,
    val antiAliasing: String,
    val targetFps: Int
)

object HardwareProfileManager {

    fun getDeviceProfile(context: Context): GpuRenderConfig {
        val hardwareStr = (Build.HARDWARE + " " + Build.MODEL + " " + Build.BOARD + " " + Build.FINGERPRINT).lowercase()

        // High-Tier detection (Snapdragon 8 Gen 1/2/3, Tensor G2/G3, Exynos 2200/2400)
        if (hardwareStr.contains("sm8450") || hardwareStr.contains("sm8550") || hardwareStr.contains("sm8650") ||
            hardwareStr.contains("tensor") || hardwareStr.contains("s23") || hardwareStr.contains("s24") ||
            hardwareStr.contains("tab s8") || hardwareStr.contains("tab s9")) {
            return GpuRenderConfig(
                tierName = "High-End Flagship",
                maxLightIntensity = 120000f,
                shadowMapResolution = 2048,
                enableShadows = true,
                antiAliasing = "MSAA_4X",
                targetFps = 60
            )
        }

        // Low-Tier detection (Helio A22/G85, Snapdragon 680, Mali-G52, Adreno 610, entry devices)
        if (hardwareStr.contains("g85") || hardwareStr.contains("g88") || hardwareStr.contains("g35") ||
            hardwareStr.contains("sm6225") || hardwareStr.contains("a14") || hardwareStr.contains("g play")) {
            return GpuRenderConfig(
                tierName = "Low-End Entry",
                maxLightIntensity = 25000f,
                shadowMapResolution = 0,
                enableShadows = false,
                antiAliasing = "NONE",
                targetFps = 30
            )
        }

        // Mid-Tier fallback (Samsung Tab A9+, Snapdragon 695, Dimensity 7050, Adreno 619)
        return GpuRenderConfig(
            tierName = "Mid-Range Tablet (Samsung Tab A9+ / Adreno 619)",
            maxLightIntensity = 45000f,
            shadowMapResolution = 512,
            enableShadows = true,
            antiAliasing = "FXAA",
            targetFps = 45
        )
    }
}
