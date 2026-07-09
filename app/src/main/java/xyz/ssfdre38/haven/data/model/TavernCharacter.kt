package xyz.ssfdre38.haven.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TavernCharacterRoot(
    val chara_version: String? = null,
    val data: TavernCharacterData? = null
)

@Serializable
data class TavernCharacterData(
    val name: String? = "",
    val description: String? = "",
    val personality: String? = "",
    val scenario: String? = "",
    val first_mes: String? = "",
    val mes_example: String? = "",
    val system_prompt: String? = "",
    val creator_notes: String? = "",
    val creator: String? = "",
    val character_version: String? = ""
)
