package xyz.ssfdre38.haven.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale
import xyz.ssfdre38.haven.MainActivity
import xyz.ssfdre38.haven.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class WakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isListening = false

    companion object {
        private const val CHANNEL_ID = "haven_wake_word_service"
        private const val NOTIFICATION_ID = 9001
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopListening()
            stopSelf()
        } else {
            startListening()
        }
        return START_STICKY
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // SpeechRecognizer occasionally returns error codes (like 7: No match or 8: Busy)
                    // We just restart it so it continues monitoring the wake words
                    if (isListening) {
                        scope.launch {
                            kotlinx.coroutines.delay(500)
                            startListening()
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkMatches(matches)
                    if (isListening) {
                        startListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    checkMatches(matches)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun checkMatches(matches: ArrayList<String>?) {
        if (matches == null) return
        for (match in matches) {
            val lower = match.lowercase(Locale.getDefault())
            if (lower.contains("hey nova") || lower.contains("hey hasaji") || lower.contains("nova") || lower.contains("hasaji")) {
                triggerWakeAction()
                break
            }
        }
    }

    private fun triggerWakeAction() {
        // 1. Play feedback chime (a soft Android system notification sound or standard tone)
        try {
            val mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch active character ID from DB
        scope.launch {
            val database = AppDatabase.getInstance(applicationContext)
            val characters = database.havenDao().getAllCharacters().first()
            val activeChar = characters.maxByOrNull { it.relationshipXp } ?: characters.firstOrNull()
            
            if (activeChar != null) {
                // 3. Launch MainActivity with Start Voice Call deep link
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("characterId", activeChar.id)
                    putExtra("startVoiceCall", true)
                }
                startActivity(intent)
            }
        }
    }

    private fun startListening() {
        isListening = true
        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.startListening(recognizerIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Wake Word Monitoring"
            val descriptionText = "Listens for voice wake words (Hey Nova / Hey Hasaji) to activate hands-free mode."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Haven Wake Word Active")
            .setContentText("Listening for 'Hey Nova' or 'Hey Hasaji'...")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
