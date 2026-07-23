package xyz.ssfdre38.haven.utils

import android.content.Context

object MacroUtils {
    /**
     * Replaces character card macro placeholders ({{user}}, <user>, {{char}}, <char>)
     * with the active user display name and character name.
     */
    fun parseMacros(text: String?, userName: String?, charName: String?): String {
        if (text.isNullOrBlank()) return text ?: ""
        val uName = if (userName.isNullOrBlank()) "User" else userName.trim()
        val cName = if (charName.isNullOrBlank()) "Companion" else charName.trim()

        return text
            .replace("{{user}}", uName, ignoreCase = true)
            .replace("<user>", uName, ignoreCase = true)
            .replace("{{char}}", cName, ignoreCase = true)
            .replace("<char>", cName, ignoreCase = true)
    }

    /**
     * Fetches the user's display name from app settings (haven_prefs -> user_name).
     */
    fun getUserDisplayName(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("user_name", "User")?.ifBlank { "User" } ?: "User"
    }
}
