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

class MainActivity : ComponentActivity() {
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

        // Check if app was opened via a notification click
        val startCharacterId = intent.getIntExtra("characterId", -1).takeIf { it != -1 }
        if (startCharacterId != null) {
            intent.removeExtra("characterId")
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

        enableEdgeToEdge()
        setContent {
            HavenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(startCharacterId = startCharacterId)
                }
            }
        }
    }
}
