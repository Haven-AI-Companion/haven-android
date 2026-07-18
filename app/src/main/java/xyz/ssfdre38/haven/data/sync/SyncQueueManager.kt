package xyz.ssfdre38.haven.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import xyz.ssfdre38.haven.data.network.HavenHttpClient

object SyncQueueManager {

    private const val TAG = "SyncQueueManager"
    private const val DATABASE_NAME = "sync_queue.db"
    private const val DATABASE_VERSION = 1
    private const val TABLE_NAME = "pending_syncs"

    private const val COL_ID = "id"
    private const val COL_ACTION_TYPE = "action_type"
    private const val COL_PAYLOAD = "payload"
    private const val COL_CREATED_AT = "created_at"

    // Action types
    const val ACTION_SAVE_MEMORY = "SAVE_MEMORY"
    const val ACTION_DELETE_MEMORY = "DELETE_MEMORY"
    const val ACTION_SAVE_DIARY = "SAVE_DIARY"
    const val ACTION_SAVE_GROUP = "SAVE_GROUP"
    const val ACTION_DELETE_GROUP = "DELETE_GROUP"
    const val ACTION_SAVE_GROUP_MESSAGE = "SAVE_GROUP_MESSAGE"
    const val ACTION_SAVE_COMPANION = "SAVE_COMPANION"

    private var dbHelper: SQLiteOpenHelper? = null
    private var isNetworkCallbackRegistered = false

    @Synchronized
    private fun getHelper(context: Context): SQLiteOpenHelper {
        if (dbHelper == null) {
            dbHelper = object : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE $TABLE_NAME (
                            $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                            $COL_ACTION_TYPE TEXT NOT NULL,
                            $COL_PAYLOAD TEXT NOT NULL,
                            $COL_CREATED_AT INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                    onCreate(db)
                }
            }
        }
        return dbHelper!!
    }

    /**
     * Enqueues a failed sync action to the local SQLite database.
     */
    fun enqueue(context: Context, actionType: String, payload: JSONObject) {
        val helper = getHelper(context)
        try {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put(COL_ACTION_TYPE, actionType)
                put(COL_PAYLOAD, payload.toString())
                put(COL_CREATED_AT, System.currentTimeMillis())
            }
            db.insert(TABLE_NAME, null, values)
            Log.d(TAG, "Enqueued pending sync action: $actionType")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue sync action", e)
        }
    }

    /**
     * Automatically registers a system-wide network status callback.
     * When connection is restored, it triggers the pending queue processor.
     */
    fun registerNetworkCallback(context: Context) {
        if (isNetworkCallbackRegistered) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "Internet connection available. Processing sync queue...")
                    CoroutineScope(Dispatchers.IO).launch {
                        processQueue(appContext)
                    }
                }
            })
            isNetworkCallbackRegistered = true
            Log.d(TAG, "System-wide NetworkCallback registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private val isProcessing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Processes all cached pending sync requests sequentially.
     */
    fun processQueue(context: Context) {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "processQueue is already running. Skipping concurrent execution.")
            return
        }
        try {
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val host = sharedPrefs.getString("ash_host", "") ?: ""
            val port = sharedPrefs.getString("ash_port", "") ?: ""
            val token = sharedPrefs.getString("auth_token", "") ?: ""

            if (host.isBlank() || token.isBlank()) {
                Log.d(TAG, "Aborting queue processing: Haven connection preferences are not configured yet.")
                return
            }

            val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
            val serverUrl = "$formattedHost:${port.trim()}"

            val helper = getHelper(context)
            val db = try {
                helper.writableDatabase
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open sync database for reading", e)
                return
            }

        val cursor = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT ASC"
        )

        cursor.use { c ->
            val idIndex = c.getColumnIndex(COL_ID)
            val typeIndex = c.getColumnIndex(COL_ACTION_TYPE)
            val payloadIndex = c.getColumnIndex(COL_PAYLOAD)

            while (c.moveToNext()) {
                val id = c.getInt(idIndex)
                val type = c.getString(typeIndex)
                val payloadStr = c.getString(payloadIndex)

                try {
                    val payload = JSONObject(payloadStr)
                    Log.d(TAG, "Processing sync item ID $id ($type)...")

                    val success = when (type) {
                        ACTION_SAVE_MEMORY -> {
                            HavenHttpClient.saveMemory(
                                serverUrl = serverUrl,
                                token = token,
                                companionName = payload.getString("companion_name"),
                                content = payload.getString("content"),
                                category = payload.getString("category")
                            )
                        }
                        ACTION_DELETE_MEMORY -> {
                            HavenHttpClient.deleteMemory(
                                serverUrl = serverUrl,
                                token = token,
                                companionName = payload.getString("companion_name"),
                                content = payload.getString("content")
                            )
                        }
                        ACTION_SAVE_DIARY -> {
                            HavenHttpClient.saveDiary(
                                serverUrl = serverUrl,
                                token = token,
                                companionName = payload.getString("companion_name"),
                                dateString = payload.getString("date_string"),
                                content = payload.getString("content")
                            )
                        }
                        ACTION_SAVE_GROUP -> {
                            val scenario = if (payload.has("scenario") && !payload.isNull("scenario")) payload.getString("scenario") else null
                            val systemPrompt = if (payload.has("system_prompt") && !payload.isNull("system_prompt")) payload.getString("system_prompt") else null
                            HavenHttpClient.saveGroup(
                                serverUrl = serverUrl,
                                token = token,
                                id = payload.getString("id"),
                                name = payload.getString("name"),
                                characterNames = payload.getString("character_names"),
                                scenario = scenario,
                                systemPrompt = systemPrompt
                            )
                        }
                        ACTION_DELETE_GROUP -> {
                            HavenHttpClient.deleteGroup(
                                serverUrl = serverUrl,
                                token = token,
                                id = payload.getString("id")
                            )
                        }
                        ACTION_SAVE_GROUP_MESSAGE -> {
                            val charName = if (payload.has("character_name") && !payload.isNull("character_name")) {
                                payload.getString("character_name")
                            } else null

                            HavenHttpClient.saveGroupMessage(
                                serverUrl = serverUrl,
                                token = token,
                                groupId = payload.getString("group_id"),
                                sender = payload.getString("sender"),
                                characterName = charName,
                                content = payload.getString("content")
                            )
                        }
                        ACTION_SAVE_COMPANION -> {
                            val char = xyz.ssfdre38.haven.data.database.CharacterEntity(
                                name = payload.getString("name"),
                                voiceId = payload.getString("voiceId"),
                                description = payload.getString("description"),
                                personality = payload.getString("personality"),
                                scenario = payload.getString("scenario"),
                                firstMessage = payload.getString("firstMessage"),
                                systemPrompt = payload.getString("systemPrompt"),
                                avatarPath = if (payload.has("avatarPath") && !payload.isNull("avatarPath")) payload.getString("avatarPath") else null
                            )
                            HavenHttpClient.saveCompanion(
                                context = context,
                                serverUrl = serverUrl,
                                token = token,
                                character = char
                            )
                        }
                        else -> false
                    }

                    if (success) {
                        db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
                        Log.d(TAG, "Successfully synced and removed item ID $id from queue.")
                    } else {
                        Log.w(TAG, "Failed to sync item ID $id. Pausing queue to preserve message ordering.")
                        break // Pause queue processing if a request fails, preserving order.
                    }
                } catch (e: org.json.JSONException) {
                    Log.e(TAG, "Malformed payload for item ID $id. Deleting to avoid stuck queue.", e)
                    db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString()))
                } catch (e: Exception) {
                    Log.e(TAG, "Transient error executing enqueued sync action for item ID $id. Pausing queue.", e)
                    break // Pause queue processing to preserve message ordering and avoid data loss
                }
            }
        }
    } finally {
        isProcessing.set(false)
    }
}
}
