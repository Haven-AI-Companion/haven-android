package xyz.ssfdre38.haven.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import xyz.ssfdre38.haven.MainActivity
import xyz.ssfdre38.haven.data.database.AppDatabase
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.MessageEntity
import xyz.ssfdre38.haven.data.network.HavenHttpClient
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ProactiveMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "haven_proactive_messages"
        const val NOTIFICATION_ID_BASE = 5000

        fun publishShortcut(context: Context, character: CharacterEntity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as? android.content.pm.ShortcutManager
            if (shortcutManager != null) {
                val intent = Intent(context, xyz.ssfdre38.haven.MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("characterId", character.id)
                    putExtra("isBubble", true)
                }

                val icon = if (character.avatarPath != null) {
                    val file = File(character.avatarPath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            android.graphics.drawable.Icon.createWithBitmap(bitmap)
                        } else {
                            android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.stat_notify_chat)
                        }
                    } else {
                        android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.stat_notify_chat)
                    }
                } else {
                    android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.stat_notify_chat)
                }

                val shortcut = android.content.pm.ShortcutInfo.Builder(context, character.id.toString())
                    .setShortLabel(character.name)
                    .setLongLabel(character.name)
                    .setIcon(icon)
                    .setIntent(intent)
                    .setLongLived(true)
                    .build()

                try {
                    shortcutManager.pushDynamicShortcut(shortcut)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        fun showNotification(context: Context, character: CharacterEntity, messageText: String, isSilent: Boolean) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create Channel for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Companion Check-ins"
                val descriptionText = "Notifications sent by your characters when they check in on you."
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Intent to launch MainActivity with characterId parameter
            val intent = Intent(context, xyz.ssfdre38.haven.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("characterId", character.id)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                character.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val shortcutId = character.id.toString()
            publishShortcut(context, character)

            val userPerson = androidx.core.app.Person.Builder()
                .setName("You")
                .build()

            val companionPerson = androidx.core.app.Person.Builder()
                .setName(character.name)
                .build()

            val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
                .addMessage(messageText, System.currentTimeMillis(), companionPerson)
                .setConversationTitle(character.name)
                .setGroupConversation(false)

            // Intent for the Bubble overlay
            val bubbleIntent = Intent(context, xyz.ssfdre38.haven.BubbleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("characterId", character.id)
                putExtra("isBubble", true)
            }
            val bubblePendingIntent = PendingIntent.getActivity(
                context,
                character.id,
                bubbleIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val iconCompat = if (character.avatarPath != null) {
                val file = File(character.avatarPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
                    } else {
                        androidx.core.graphics.drawable.IconCompat.createWithResource(context, android.R.drawable.stat_notify_chat)
                    }
                } else {
                    androidx.core.graphics.drawable.IconCompat.createWithResource(context, android.R.drawable.stat_notify_chat)
                }
            } else {
                androidx.core.graphics.drawable.IconCompat.createWithResource(context, android.R.drawable.stat_notify_chat)
            }

            val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(
                bubblePendingIntent,
                iconCompat
            )
                .setDesiredHeight(600)
                .setAutoExpandBubble(true)
                .setSuppressNotification(true)
                .build()

            // Build notification utilizing native chat bubble style and metadata
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val enableBubbles = sharedPrefs.getBoolean("enable_bubbles", true)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setShortcutId(shortcutId)
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSilent(isSilent)

            if (enableBubbles) {
                builder.setBubbleMetadata(bubbleMetadata)
            }

            notificationManager.notify(NOTIFICATION_ID_BASE + character.id, builder.build())
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val ashHost = sharedPrefs.getString("ash_host", null) ?: return Result.success()
        val ashPort = sharedPrefs.getString("ash_port", "18799")
        val token = sharedPrefs.getString("auth_token", null) ?: return Result.success()
        val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"

        val database = AppDatabase.getInstance(context)
        val dao = database.havenDao()

        // 1 hour inactivity threshold
        val inactivityThreshold = 1 * 60 * 60 * 1000L

        try {
            val characters = dao.getAllCharacters().first()
            if (characters.isEmpty()) return Result.success()

            val inactiveCharacters = mutableListOf<CharacterEntity>()

            for (char in characters) {
                val lastMsg = dao.getLastMessage(char.id)
                val timeElapsed = if (lastMsg != null) {
                    System.currentTimeMillis() - lastMsg.timestamp
                } else {
                    inactivityThreshold + 1 // Treat as inactive if no messages exist yet
                }

                if (timeElapsed >= inactivityThreshold) {
                    inactiveCharacters.add(char)
                }
            }

            if (inactiveCharacters.isEmpty()) return Result.success()

            // Select a random silent character to avoid spamming the user
            val chosenChar = inactiveCharacters.random()

            // Build system directive prompt
            val proactivePrompt = buildString {
                appendLine("You are ${chosenChar.name}.")
                if (chosenChar.personality.isNotBlank()) appendLine("Personality: ${chosenChar.personality}")
                if (chosenChar.scenario.isNotBlank()) appendLine("Scenario: ${chosenChar.scenario}")
                if (chosenChar.systemPrompt.isNotBlank()) appendLine(chosenChar.systemPrompt)
                appendLine()
                appendLine("[System Instruction: You are proactively texting the user after a long period of silence. Write a short, warm, in-character message checking in on them. Do not include any explanations, inner thoughts, or meta-text. Keep it under 2 sentences.]")
            }

            val deferred = CompletableDeferred<String>()
            val buffer = StringBuilder()

            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = proactivePrompt,
                token = token,
                onToken = { tokenPart ->
                    buffer.append(tokenPart).append(" ")
                },
                onComplete = {
                    deferred.complete(buffer.toString().trim())
                },
                onFailure = { err ->
                    deferred.completeExceptionally(err)
                }
            )

            val reply = deferred.await()
            if (reply.isBlank() || reply.startsWith("[Connection error")) {
                return Result.failure()
            }

            // Clean up any double spaces from streaming accumulation
            val cleanedReply = reply.replace("\\s+".toRegex(), " ")

            // Insert message into Room database
            dao.insertMessage(
                MessageEntity(
                    characterId = chosenChar.id,
                    sender = "character",
                    text = cleanedReply
                )
            )

            // Push message to server if character has conversationId
            val conversationId = chosenChar.conversationId
            if (!conversationId.isNullOrBlank()) {
                val url = "${serverUrl.trimEnd('/')}/api/conversations/$conversationId/messages"
                val bodyJson = org.json.JSONObject().apply {
                    put("role", "assistant")
                    put("content", cleanedReply)
                }.toString()
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                try {
                    okhttp3.OkHttpClient().newCall(request).execute().close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Check if Quiet Time is active
            val quietTimeEnabled = sharedPrefs.getBoolean("quiet_time_enabled", false)
            val quietTimeStart = sharedPrefs.getString("quiet_time_start", "22:00") ?: "22:00"
            val quietTimeEnd = sharedPrefs.getString("quiet_time_end", "07:00") ?: "07:00"

            var isQuietTime = false
            if (quietTimeEnabled) {
                try {
                    val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
                    isQuietTime = isTimeBetween(currentTime, quietTimeStart, quietTimeEnd)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Post System Notification
            showNotification(context, chosenChar, cleanedReply, isQuietTime)

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    // showNotification moved to companion object for general accessibility

    // publishShortcut moved to companion object for general accessibility

    private fun isTimeBetween(current: String, start: String, end: String): Boolean {
        val curMinutes = timeToMinutes(current)
        val startMinutes = timeToMinutes(start)
        val endMinutes = timeToMinutes(end)

        return if (startMinutes <= endMinutes) {
            curMinutes in startMinutes..endMinutes
        } else { // Spans midnight (e.g. 22:00 to 07:00)
            curMinutes >= startMinutes || curMinutes <= endMinutes
        }
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        if (parts.size < 2) return 0
        val hrs = parts[0].toIntOrNull() ?: 0
        val mins = parts[1].toIntOrNull() ?: 0
        return hrs * 60 + mins
    }
}
