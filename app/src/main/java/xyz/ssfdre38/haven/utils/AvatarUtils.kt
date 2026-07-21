package xyz.ssfdre38.haven.utils

import java.io.File

object AvatarUtils {
    fun resolveAvatarModel(avatarPath: String?, serverUrl: String? = null): Any? {
        if (avatarPath.isNullOrBlank()) return null
        
        // 1. Local file on disk
        val file = File(avatarPath)
        if (file.exists() && file.length() > 0) {
            return file
        }
        
        // 2. HTTP / HTTPS URL
        if (avatarPath.startsWith("http://", ignoreCase = true) || avatarPath.startsWith("https://", ignoreCase = true)) {
            return avatarPath
        }
        
        // 3. Relative server path
        if (!serverUrl.isNullOrBlank()) {
            val cleanServer = serverUrl.trimEnd('/')
            val cleanPath = if (avatarPath.startsWith("/")) avatarPath else "/$avatarPath"
            return "$cleanServer$cleanPath"
        }
        
        return avatarPath
    }
}
