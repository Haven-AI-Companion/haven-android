package xyz.ssfdre38.haven.data.network

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

object HavenHttpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(1800, TimeUnit.SECONDS)
        .readTimeout(1800, TimeUnit.SECONDS)
        .writeTimeout(1800, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.SECONDS))
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Confirms mobile pairing code and exchanges it for a JWT token
     */
    fun pairDevice(
        serverUrl: String, // e.g., "http://100.x.y.z:18799"
        pairingCode: String,
        deviceName: String,
        onResult: (Result<String>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/auth/mobile/pair/confirm"
        val requestBodyJson = JSONObject().apply {
            put("code", pairingCode)
            put("device_name", deviceName)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        val errMsg = try {
                            JSONObject(errBody).getString("error")
                        } catch (e: Exception) {
                            "Pairing failed with status: ${response.code}"
                        }
                        onResult(Result.failure(Exception(errMsg)))
                        return
                    }

                    try {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val token = json.getString("token")
                        onResult(Result.success(token))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Streams completions from ash-server api/chat line by line.
     */
    fun streamChat(
        serverUrl: String, // e.g., "http://100.x.y.z:18799"
        prompt: String,
        token: String,
        conversationId: String? = null,
        displayName: String? = null,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        client.connectionPool.evictAll()
        val url = "${serverUrl.trimEnd('/')}/api/chat"
        val requestBodyJson = JSONObject().apply {
            put("prompt", prompt)
            put("model", null)
            put("image", null)
            if (conversationId != null) {
                put("conversation_id", conversationId)
            }
            if (displayName != null) {
                put("display_name", displayName)
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Connection", "close")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onFailure(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onFailure(Exception("Server returned status: ${response.code}"))
                        return
                    }

                    try {
                        val reader = response.body?.charStream()?.buffered()
                        if (reader == null) {
                            onFailure(Exception("Response body is empty"))
                            return
                        }

                        while (true) {
                            val line = reader.readLine() ?: break
                            // Since ash-server writes: token + "\n",
                            // each readLine() gives us the exact token!
                            onToken(line)
                        }
                        onComplete()
                    } catch (e: Exception) {
                        onFailure(e)
                    }
                }
            }
        })
    }

    /**
     * Generates an image using sd-server v1/images/generations, decodes the base64 output, 
     * saves the file locally and returns the local file path.
     */
    fun generateImage(
        context: Context,
        sdServerUrl: String, // e.g., "http://100.x.y.z:8080"
        prompt: String,
        onResult: (Result<String>) -> Unit
    ) {
        val url = "${sdServerUrl.trimEnd('/')}/v1/images/generations"
        val requestBodyJson = JSONObject().apply {
            put("prompt", prompt)
            put("negative_prompt", "text, watermark, bad anatomy, duplicate, split screen, multi panel, list, borders, signature, extra limbs")
            put("n", 1)
            put("size", "512x512")
            put("seed", (1..2000000000).random())
            put("response_format", "b64_json")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val imageClient = client.newBuilder()
            .readTimeout(1800, TimeUnit.SECONDS)
            .build()

        imageClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onResult(Result.failure(Exception("Generation failed: HTTP ${response.code}")))
                        return
                    }

                    try {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val dataArray: JSONArray = json.getJSONArray("data")
                        if (dataArray.length() == 0) {
                            onResult(Result.failure(Exception("No image returned from server")))
                            return
                        }

                        val base64Data = dataArray.getJSONObject(0).getString("b64_json")
                        val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)

                        // Save image to internal storage
                        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val imagesDir = File(baseDir, "generated")
                        if (!imagesDir.exists()) imagesDir.mkdirs()

                        val file = File(imagesDir, "gen_${UUID.randomUUID()}.png")
                        FileOutputStream(file).use { fos ->
                            fos.write(imageBytes)
                        }

                        onResult(Result.success(file.absolutePath))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Downloads an image from an HTTP URL and saves it to internal storage, returning the local file path.
     */
    fun downloadImage(context: Context, imageUrl: String, companionName: String): String? {
        val request = okhttp3.Request.Builder().url(imageUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val cleanName = companionName.replace("[^a-zA-Z0-9]".toRegex(), "_")
                        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val imagesDir = File(baseDir, "companion/images/$cleanName")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        
                        val ext = if (imageUrl.lowercase().endsWith(".webp")) ".webp"
                                  else if (imageUrl.lowercase().endsWith(".jpg") || imageUrl.lowercase().endsWith(".jpeg")) ".jpg"
                                  else ".png"
                        
                        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        val dateTime = sdf.format(java.util.Date())
                        val file = File(imagesDir, "${cleanName}_$dateTime$ext")
                        
                        java.io.FileOutputStream(file).use { fos ->
                            fos.write(bytes)
                        }
                        return file.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Downloads a GLB model file from an HTTP URL and saves it to internal storage under vrm_models directory.
     */
    fun downloadGlb(context: Context, modelUrl: String, characterName: String): String? {
        val request = okhttp3.Request.Builder().url(modelUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                        val localDir = File(baseDir, "vrm_models").apply { mkdirs() }
                        val file = File(localDir, "${characterName.replace("\\s+".toRegex(), "_")}_avatar_${System.currentTimeMillis()}.glb")
                        java.io.FileOutputStream(file).use { fos ->
                            fos.write(bytes)
                        }
                        return file.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Executes a server plugin tool using api/tools/execute.
     */
    fun executeTool(
        serverUrl: String,
        token: String,
        toolName: String,
        arguments: JSONObject,
        onResult: (Result<String>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/tools/execute"
        val requestBodyJson = JSONObject().apply {
            put("tool", toolName)
            put("arguments", arguments)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onResult(Result.failure(Exception("Tool execution failed: HTTP ${response.code}")))
                        return
                    }

                    try {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val result = json.getString("result")
                        onResult(Result.success(result))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Generates TTS speech audio from text using api/tts.
     */
    fun generateTts(
        serverUrl: String,
        token: String,
        text: String,
        voice: String,
        onResult: (Result<String>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/tts"
        val requestBodyJson = JSONObject().apply {
            put("text", text)
            put("voice", voice)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onResult(Result.failure(Exception("TTS failed: HTTP ${response.code}")))
                        return
                    }

                    try {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val relativeUrl = json.getString("url")
                        onResult(Result.success(relativeUrl))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Evicts all idle connections in the pool. Call this when the app is resumed
     * from background to clear any stale/orphaned sockets.
     */
    fun evictAllConnections() {
        try {
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Resets active HTTP client socket connections and queues.
     */
    fun resetConnections() {
        try {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Tests the connectivity to the server's public health check endpoint.
     */
    fun testConnection(
        serverUrl: String,
        onResult: (Result<String>) -> Unit
    ) {
        val url = if (serverUrl.contains("/health")) serverUrl else "${serverUrl.trimEnd('/')}/health"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val shortTimeoutClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        shortTimeoutClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (response.isSuccessful) {
                        onResult(Result.success("Connection Successful!"))
                    } else {
                        onResult(Result.failure(Exception("Server returned HTTP ${response.code}")))
                    }
                }
            }
        })
    }

    fun testConnectionLatency(
        url: String,
        onResult: (Result<Pair<String, Long>>) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val shortTimeoutClient = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        shortTimeoutClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val latency = System.currentTimeMillis() - startTime
                    if (response.isSuccessful) {
                        onResult(Result.success(Pair("Successful", latency)))
                    } else {
                        onResult(Result.success(Pair("HTTP ${response.code}", latency)))
                    }
                }
            }
        })
    }

    /**
     * Fetches the list of available text-to-speech voices from the server.
     */
    fun getAvailableVoices(
        serverUrl: String,
        onResult: (Result<List<Pair<String, String>>>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/tts/voices"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        onResult(Result.failure(Exception("Failed to load voices: HTTP ${response.code}")))
                        return
                    }

                    try {
                        val body = response.body?.string() ?: ""
                        val array = JSONArray(body)
                        val list = mutableListOf<Pair<String, String>>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            list.add(Pair(obj.getString("id"), obj.getString("name")))
                        }
                        onResult(Result.success(list))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Pulls messages for a conversation ID from the server
     */
    fun getConversationMessages(
        serverUrl: String,
        token: String,
        conversationId: String
    ): List<JSONObject> {
        val url = "${serverUrl.trimEnd('/')}/api/conversations/$conversationId/messages"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                val array = JSONArray(body)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
                return list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Pulls memories for a companion from the server
     */
    fun getMemories(
        serverUrl: String,
        token: String,
        companionName: String
    ): List<JSONObject> {
        val url = "${serverUrl.trimEnd('/')}/api/sync/memories?companion=${java.net.URLEncoder.encode(companionName, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                val array = JSONArray(body)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
                return list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Pushes a memory to the server
     */
    fun saveMemory(
        serverUrl: String,
        token: String,
        companionName: String,
        content: String,
        category: String
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sync/memories"
        val json = JSONObject().apply {
            put("companion_name", companionName)
            put("content", content)
            put("category", category)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Deletes a memory on the server
     */
    fun deleteMemory(
        serverUrl: String,
        token: String,
        companionName: String,
        content: String
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sync/memories?companion=${java.net.URLEncoder.encode(companionName, "UTF-8")}&content=${java.net.URLEncoder.encode(content, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Pulls diary entries for a companion from the server
     */
    fun getDiaries(
        serverUrl: String,
        token: String,
        companionName: String
    ): List<JSONObject> {
        val url = "${serverUrl.trimEnd('/')}/api/sync/diaries?companion=${java.net.URLEncoder.encode(companionName, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                val array = JSONArray(body)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
                return list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Pushes a diary entry to the server
     */
    fun saveDiary(
        serverUrl: String,
        token: String,
        companionName: String,
        dateString: String,
        content: String
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sync/diaries"
        val json = JSONObject().apply {
            put("companion_name", companionName)
            put("date_string", dateString)
            put("content", content)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Pushes a companion profile configuration to the server to be saved locally.
     */
    fun saveCompanion(
        serverUrl: String,
        token: String,
        character: xyz.ssfdre38.haven.data.database.CharacterEntity
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/companions"
        val requestBodyJson = JSONObject().apply {
            put("name", character.name)
            put("voiceId", character.voiceId)
            put("description", character.description)
            put("personality", character.personality)
            put("scenario", character.scenario)
            put("firstMessage", character.firstMessage)
            put("systemPrompt", character.systemPrompt)
            put("avatarPath", character.avatarPath)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Pulls all group chats from the server
     */
    fun getGroups(
        serverUrl: String,
        token: String
    ): List<JSONObject> {
        val url = "${serverUrl.trimEnd('/')}/api/sync/groups"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                val array = JSONArray(body)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
                return list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Pushes a group chat to the server
     */
    fun saveGroup(
        serverUrl: String,
        token: String,
        id: String,
        name: String,
        characterNames: String
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sync/groups"
        val json = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("character_names", characterNames)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Deletes a group chat on the server
     */
    fun deleteGroup(
        serverUrl: String,
        token: String,
        id: String
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sync/groups/$id"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Pulls group messages from the server
     */
    fun getGroupMessages(
        serverUrl: String,
        token: String,
        groupId: String
    ): List<JSONObject> {
        val url = "${serverUrl.trimEnd('/')}/api/sync/groups/$groupId/messages"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                val array = JSONArray(body)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
                return list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Pushes a group message to the server
     */
    fun saveGroupMessage(
        serverUrl: String,
        token: String,
        groupId: String,
        sender: String,
        characterName: String?,
        content: String
    ): Boolean {
        val url = "${serverUrl.trimEnd('/')}/api/sync/groups/$groupId/messages"
        val json = JSONObject().apply {
            put("sender", sender)
            put("character_name", characterName)
            put("content", content)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun updateUserProfile(
        serverUrl: String,
        token: String,
        displayName: String,
        gender: String,
        onResult: (Result<Boolean>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/users/me/profile"
        val json = JSONObject().apply {
            put("displayName", displayName)
            put("gender", gender)
        }.toString()
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onResult(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        onResult(Result.success(true))
                    } else {
                        onResult(Result.failure(Exception("Failed to update profile: ${it.code}")))
                    }
                }
            }
        })
    }

    fun uploadUserAvatar(
        serverUrl: String,
        token: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onResult: (Result<String>) -> Unit
    ) {
        val url = "${serverUrl.trimEnd('/')}/api/users/me/profile/avatar"
        val fileMediaType = mimeType.toMediaTypeOrNull()
        val fileBody = fileBytes.toRequestBody(fileMediaType)
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onResult(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string() ?: ""
                        try {
                            val obj = JSONObject(body)
                            val path = obj.optString("avatarPath", "")
                            onResult(Result.success(path))
                        } catch (e: Exception) {
                            onResult(Result.failure(e))
                        }
                    } else {
                        onResult(Result.failure(Exception("Failed to upload avatar: ${it.code}")))
                    }
                }
            }
        })
    }
}
