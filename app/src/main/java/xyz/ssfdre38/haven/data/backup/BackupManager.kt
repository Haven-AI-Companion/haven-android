package xyz.ssfdre38.haven.data.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import xyz.ssfdre38.haven.data.database.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    suspend fun exportBackup(context: Context, outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.havenDao()

            // Fetch all data from DB
            val characters = dao.getAllCharacters().first()
            val groupChats = dao.getAllGroupChats().first()

            // Export to ZIP
            ZipOutputStream(outputStream).use { zos ->
                // Write Metadata
                val infoJson = JSONObject().apply {
                    put("version", 1)
                    put("timestamp", System.currentTimeMillis())
                    put("characterCount", characters.size)
                }
                writeTextFileToZip(zos, "backup_info.json", infoJson.toString(2))

                // Write Characters
                val charArray = JSONArray()
                val allMessages = mutableListOf<MessageEntity>()
                val allDiaryEntries = mutableListOf<DiaryEntryEntity>()
                val allMemories = mutableListOf<MemoryEntity>()

                for (char in characters) {
                    val charObj = JSONObject().apply {
                        put("id", char.id)
                        put("name", char.name)
                        // Save just the filename of the avatar
                        val avatarName = char.avatarPath?.let { File(it).name }
                        put("avatarName", avatarName)
                        put("voiceId", char.voiceId)
                        put("description", char.description)
                        put("personality", char.personality)
                        put("scenario", char.scenario)
                        put("firstMessage", char.firstMessage)
                        put("messageExample", char.messageExample)
                        put("systemPrompt", char.systemPrompt)
                        put("currentLocation", char.currentLocation)
                        put("currentOutfit", char.currentOutfit)
                        put("currentMood", char.currentMood)
                        put("relationshipXp", char.relationshipXp)
                        put("messageCount", char.messageCount)
                        put("createdAt", char.createdAt)
                    }
                    charArray.put(charObj)

                    // Gather related records
                    allMessages.addAll(dao.getMessagesForCharacter(char.id).first())
                    allDiaryEntries.addAll(dao.getDiaryEntries(char.id).first())
                    allMemories.addAll(dao.getRecentMemories(char.id, Int.MAX_VALUE))
                }
                writeTextFileToZip(zos, "characters.json", charArray.toString(2))

                // Write Messages
                val msgArray = JSONArray()
                for (msg in allMessages) {
                    val msgObj = JSONObject().apply {
                        put("id", msg.id)
                        put("characterId", msg.characterId)
                        put("sender", msg.sender)
                        put("text", msg.text)
                        put("imageName", msg.imagePath?.let { File(it).name })
                        put("audioName", msg.audioPath?.let { File(it).name })
                        put("timestamp", msg.timestamp)
                    }
                    msgArray.put(msgObj)
                }
                writeTextFileToZip(zos, "messages.json", msgArray.toString(2))

                // Write Diary Entries
                val diaryArray = JSONArray()
                for (entry in allDiaryEntries) {
                    val entryObj = JSONObject().apply {
                        put("id", entry.id)
                        put("characterId", entry.characterId)
                        put("dateString", entry.dateString)
                        put("content", entry.content)
                        put("createdAt", entry.createdAt)
                    }
                    diaryArray.put(entryObj)
                }
                writeTextFileToZip(zos, "diary_entries.json", diaryArray.toString(2))

                // Write Memories
                val memoryArray = JSONArray()
                for (mem in allMemories) {
                    val memObj = JSONObject().apply {
                        put("id", mem.id)
                        put("characterId", mem.characterId)
                        put("content", mem.content)
                        put("category", mem.category)
                        put("createdAt", mem.createdAt)
                    }
                    memoryArray.put(memObj)
                }
                writeTextFileToZip(zos, "memories.json", memoryArray.toString(2))

                // Write Group Chats
                val groupArray = JSONArray()
                val allGroupMessages = mutableListOf<GroupMessageEntity>()
                for (group in groupChats) {
                    val grpObj = JSONObject().apply {
                        put("id", group.id)
                        put("name", group.name)
                        put("characterIdsString", group.characterIdsString)
                        put("createdAt", group.createdAt)
                    }
                    groupArray.put(grpObj)
                    allGroupMessages.addAll(dao.getGroupMessages(group.id).first())
                }
                writeTextFileToZip(zos, "group_chats.json", groupArray.toString(2))

                // Write Group Messages
                val gMsgArray = JSONArray()
                for (gMsg in allGroupMessages) {
                    val gMsgObj = JSONObject().apply {
                        put("id", gMsg.id)
                        put("groupId", gMsg.groupId)
                        put("sender", gMsg.sender)
                        put("characterId", gMsg.characterId) // Nullable
                        put("text", gMsg.text)
                        put("imageName", gMsg.imagePath?.let { File(it).name })
                        put("audioName", gMsg.audioPath?.let { File(it).name })
                        put("timestamp", gMsg.timestamp)
                    }
                    gMsgArray.put(gMsgObj)
                }
                writeTextFileToZip(zos, "group_messages.json", gMsgArray.toString(2))

                // Export Avatar files
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val avatarDir = File(baseDir, "avatars")
                if (avatarDir.exists() && avatarDir.isDirectory) {
                    avatarDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            writeBinaryFileToZip(zos, "avatars/${file.name}", file)
                        }
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun importBackup(context: Context, inputStream: InputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.havenDao()

            var infoJson: JSONObject? = null
            var charactersJson: JSONArray? = null
            var messagesJson: JSONArray? = null
            var diaryJson: JSONArray? = null
            var memoriesJson: JSONArray? = null
            var groupsJson: JSONArray? = null
            var groupMessagesJson: JSONArray? = null

            val tempDir = File(context.cacheDir, "backup_temp")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val avatarDir = File(baseDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()

            // 1. Extract ZIP contents
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("avatars/")) {
                        val fileName = name.substring("avatars/".length)
                        if (fileName.isNotEmpty()) {
                            val targetFile = File(avatarDir, fileName)
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    } else {
                        val content = zis.bufferedReader().readText()
                        when (name) {
                            "backup_info.json" -> infoJson = JSONObject(content)
                            "characters.json" -> charactersJson = JSONArray(content)
                            "messages.json" -> messagesJson = JSONArray(content)
                            "diary_entries.json" -> diaryJson = JSONArray(content)
                            "memories.json" -> memoriesJson = JSONArray(content)
                            "group_chats.json" -> groupsJson = JSONArray(content)
                            "group_messages.json" -> groupMessagesJson = JSONArray(content)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val info = infoJson ?: return@withContext Result.failure(Exception("Invalid .haven package: missing metadata"))
            val characters = charactersJson ?: return@withContext Result.failure(Exception("Invalid .haven package: missing characters"))
            val messages = messagesJson
            val diary = diaryJson
            val memories = memoriesJson
            val groups = groupsJson
            val groupMessages = groupMessagesJson

            // 2. Perform DB Import in a Transactional manner
            db.runInTransaction {
                // Clear existing database tables to perform a clean restore
                // (This matches Room database clean restore pattern)
                db.clearAllTables()

                // Insert characters & track ID mapping
                val charIdMap = mutableMapOf<Int, Int>()
                for (i in 0 until characters.length()) {
                    val cObj = characters.getJSONObject(i)
                    val oldId = cObj.getInt("id")
                    
                    // Reconstruct local avatar absolute path
                    val avatarName = cObj.optString("avatarName", "")
                    val avatarPath = if (avatarName.isNotEmpty()) {
                        File(avatarDir, avatarName).absolutePath
                    } else {
                        null
                    }

                    val char = CharacterEntity(
                        name = cObj.getString("name"),
                        avatarPath = avatarPath,
                        voiceId = cObj.optString("voiceId", "en_US-amy-medium"),
                        description = cObj.optString("description", ""),
                        personality = cObj.optString("personality", ""),
                        scenario = cObj.optString("scenario", ""),
                        firstMessage = cObj.optString("firstMessage", ""),
                        messageExample = cObj.optString("messageExample", ""),
                        systemPrompt = cObj.optString("systemPrompt", ""),
                        currentLocation = cObj.optString("currentLocation", ""),
                        currentOutfit = cObj.optString("currentOutfit", ""),
                        currentMood = cObj.optString("currentMood", ""),
                        relationshipXp = cObj.optInt("relationshipXp", 0),
                        messageCount = cObj.optInt("messageCount", 0),
                        createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                    )

                    // Insert using blocking run on dao in transaction context
                    val newId = kotlinx.coroutines.runBlocking { dao.insertCharacter(char) }.toInt()
                    charIdMap[oldId] = newId
                }

                // Insert Messages
                if (messages != null) {
                    for (i in 0 until messages.length()) {
                        val mObj = messages.getJSONObject(i)
                        val oldCharId = mObj.getInt("characterId")
                        val newCharId = charIdMap[oldCharId] ?: continue

                        val msg = MessageEntity(
                            characterId = newCharId,
                            sender = mObj.getString("sender"),
                            text = mObj.getString("text"),
                            imagePath = mObj.optString("imageName", "").let { 
                                if (it.isNotEmpty()) File(avatarDir, it).absolutePath else null 
                            },
                            audioPath = mObj.optString("audioName", "").let { 
                                if (it.isNotEmpty()) File(avatarDir, it).absolutePath else null 
                            },
                            timestamp = mObj.getLong("timestamp")
                        )
                        kotlinx.coroutines.runBlocking { dao.insertMessage(msg) }
                    }
                }

                // Insert Diary Entries
                if (diary != null) {
                    for (i in 0 until diary.length()) {
                        val dObj = diary.getJSONObject(i)
                        val oldCharId = dObj.getInt("characterId")
                        val newCharId = charIdMap[oldCharId] ?: continue

                        val entry = DiaryEntryEntity(
                            characterId = newCharId,
                            dateString = dObj.getString("dateString"),
                            content = dObj.getString("content"),
                            createdAt = dObj.getLong("createdAt")
                        )
                        kotlinx.coroutines.runBlocking { dao.insertDiaryEntry(entry) }
                    }
                }

                // Insert Memories
                if (memories != null) {
                    for (i in 0 until memories.length()) {
                        val memObj = memories.getJSONObject(i)
                        val oldCharId = memObj.getInt("characterId")
                        val newCharId = charIdMap[oldCharId] ?: continue

                        val memory = MemoryEntity(
                            characterId = newCharId,
                            content = memObj.getString("content"),
                            category = memObj.optString("category", "general"),
                            createdAt = memObj.getLong("createdAt")
                        )
                        kotlinx.coroutines.runBlocking { dao.insertMemory(memory) }
                    }
                }

                // Insert Group Chats
                val groupIdMap = mutableMapOf<Int, Int>()
                if (groups != null) {
                    for (i in 0 until groups.length()) {
                        val gObj = groups.getJSONObject(i)
                        val oldGroupId = gObj.getInt("id")
                        val oldIdsString = gObj.getString("characterIdsString")
                        
                        // Map mapped character ids
                        val newIdsString = oldIdsString.split(",")
                            .mapNotNull { it.toIntOrNull() }
                            .mapNotNull { charIdMap[it] }
                            .joinToString(",")

                        val group = GroupChatEntity(
                            name = gObj.getString("name"),
                            characterIdsString = newIdsString,
                            createdAt = gObj.getLong("createdAt")
                        )
                        val newGroupId = kotlinx.coroutines.runBlocking { dao.insertGroupChat(group) }.toInt()
                        groupIdMap[oldGroupId] = newGroupId
                    }
                }

                // Insert Group Messages
                if (groupMessages != null) {
                    for (i in 0 until groupMessages.length()) {
                        val gmObj = groupMessages.getJSONObject(i)
                        val oldGroupId = gmObj.getInt("groupId")
                        val newGroupId = groupIdMap[oldGroupId] ?: continue
                        
                        val oldCharId = gmObj.optInt("characterId", -1)
                        val newCharId = if (oldCharId != -1) charIdMap[oldCharId] else null

                        val gMsg = GroupMessageEntity(
                            groupId = newGroupId,
                            sender = gmObj.getString("sender"),
                            characterId = newCharId,
                            text = gmObj.getString("text"),
                            imagePath = gmObj.optString("imageName", "").let { 
                                if (it.isNotEmpty()) File(avatarDir, it).absolutePath else null 
                            },
                            audioPath = gmObj.optString("audioName", "").let { 
                                if (it.isNotEmpty()) File(avatarDir, it).absolutePath else null 
                            },
                            timestamp = gmObj.getLong("timestamp")
                        )
                        kotlinx.coroutines.runBlocking { dao.insertGroupMessage(gMsg) }
                    }
                }
            }

            tempDir.deleteRecursively()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun writeTextFileToZip(zos: ZipOutputStream, entryName: String, text: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun writeBinaryFileToZip(zos: ZipOutputStream, entryName: String, file: File) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        file.inputStream().use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }
}
