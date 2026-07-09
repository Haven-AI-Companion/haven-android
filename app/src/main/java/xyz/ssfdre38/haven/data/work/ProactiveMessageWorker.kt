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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first

class ProactiveMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "haven_proactive_messages"
        const val NOTIFICATION_ID_BASE = 5000
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

    private fun showNotification(context: Context, character: CharacterEntity, messageText: String, isSilent: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Companion Check-ins"
            val descriptionText = "Notifications sent by your characters when they check in on you."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to launch MainActivity with characterId parameter
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("characterId", character.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            character.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification using native chat bubble icon
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(character.name)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(isSilent)

        notificationManager.notify(NOTIFICATION_ID_BASE + character.id, builder.build())
    }

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
