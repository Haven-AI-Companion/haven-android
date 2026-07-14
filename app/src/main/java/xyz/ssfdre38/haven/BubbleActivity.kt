package xyz.ssfdre38.haven

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import xyz.ssfdre38.haven.theme.HavenTheme

class BubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val characterId = intent.getIntExtra("characterId", -1).takeIf { it != -1 }
        
        enableEdgeToEdge()
        setContent {
            HavenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(startCharacterId = characterId)
                }
            }
        }
    }
}
