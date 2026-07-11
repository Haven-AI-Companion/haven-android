package xyz.ssfdre38.haven.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import xyz.ssfdre38.haven.ui.components.VrmAvatarView

/**
 * Proposed AOSP AgentSurfaceView rendering layout.
 * Wraps the transparent SceneView 3D graphics rendering interface.
 */
@Composable
fun AgentSurfaceView(
    modelPath: String,
    mood: String,
    animationIndex: Int,
    modifier: Modifier = Modifier
) {
    VrmAvatarView(
        modelPath = modelPath,
        mood = mood,
        isSpeaking = false,
        animationIndex = animationIndex,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    )
}
