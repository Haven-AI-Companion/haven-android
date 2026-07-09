package xyz.ssfdre38.haven.data.network

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
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
        .connectTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
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
            put("Code", pairingCode)
            put("DeviceName", deviceName)
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
        val url = "${serverUrl.trimEnd('/')}/api/chat"
        val requestBodyJson = JSONObject().apply {
            put("prompt", prompt)
            put("model", null)
            put("image", null)
            if (conversationId != null) {
                put("conversationId", conversationId)
            }
            if (displayName != null) {
                put("displayName", displayName)
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer $token")
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
            .readTimeout(300, TimeUnit.SECONDS)
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
                        val imagesDir = File(context.filesDir, "generated")
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
    fun downloadImage(context: Context, imageUrl: String): String? {
        val request = okhttp3.Request.Builder().url(imageUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        val imagesDir = File(context.filesDir, "generated")
                        if (!imagesDir.exists()) imagesDir.mkdirs()
                        val file = File(imagesDir, "gen_${UUID.randomUUID()}.png")
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
}
