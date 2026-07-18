package xyz.ssfdre38.haven

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import xyz.ssfdre38.haven.data.work.ProactiveMessageWorker
import xyz.ssfdre38.haven.theme.HavenTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
class MainActivity : ComponentActivity() {
    private val activityScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule periodic background character check-in checks
        try {
            val proactiveRequest = PeriodicWorkRequestBuilder<ProactiveMessageWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "proactive_check_in",
                ExistingPeriodicWorkPolicy.REPLACE,
                proactiveRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Check if app was opened via a notification click or wake word trigger
        val startCharacterId = intent.getIntExtra("characterId", -1).takeIf { it != -1 }
        val startVoiceCall = intent.getBooleanExtra("startVoiceCall", false)
        if (startCharacterId != null) {
            intent.removeExtra("characterId")
            intent.removeExtra("startVoiceCall")
        }

        // Request runtime permissions at startup (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf<String>()
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.RECORD_AUDIO)
            }
            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), 101)
            }
        } else {
            // Older versions only need RECORD_AUDIO requested at runtime
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
            }
        }

        try {
            xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start overlay companion service on boot if enabled
        try {
            val prefs = getSharedPreferences("haven_prefs", MODE_PRIVATE)
            val overlayEnabled = prefs.getBoolean("enable_overlay", false)
            if (overlayEnabled && android.provider.Settings.canDrawOverlays(this)) {
                startService(android.content.Intent(this, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize offline sync queueing
        try {
            xyz.ssfdre38.haven.data.sync.SyncQueueManager.registerNetworkCallback(this)
            activityScope.launch {
                xyz.ssfdre38.haven.data.sync.SyncQueueManager.processQueue(applicationContext)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Register broadcast listener for music tracking
        try {
            xyz.ssfdre38.haven.data.receiver.MediaTracker.register(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()
        setContent {
            HavenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(startCharacterId = startCharacterId, startVoiceCall = startVoiceCall)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Clear stale socket connections when app returns to foreground
        try {
            xyz.ssfdre38.haven.data.network.HavenHttpClient.evictAllConnections()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            activityScope.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            xyz.ssfdre38.haven.data.receiver.MediaTracker.unregister(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
