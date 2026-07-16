package xyz.ssfdre38.haven.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VrmAvatarView(
    modelPath: String,
    mood: String,
    isSpeaking: Boolean = false,
    animationIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val modelFile = remember(modelPath) { File(modelPath) }
    if (!modelFile.exists()) return

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val scope = rememberCoroutineScope()

    var modelNodeRef by remember { mutableStateOf<io.github.sceneview.node.ModelNode?>(null) }

    // Map companion mood string to morph target weights
    val baseWeights = remember(mood) {
        val weights = FloatArray(16)
        val cleanMood = mood.lowercase()
        when {
            cleanMood.contains("smile") || cleanMood.contains("happy") || cleanMood.contains("blush") || cleanMood.contains("joy") -> {
                weights[3] = 0.8f // Smile/Joy
            }
            cleanMood.contains("angry") || cleanMood.contains("mad") || cleanMood.contains("annoyed") -> {
                weights[1] = 0.8f // Angry
            }
            cleanMood.contains("sad") || cleanMood.contains("sorrow") || cleanMood.contains("cry") -> {
                weights[4] = 0.8f // Sorrow
            }
            cleanMood.contains("surprise") || cleanMood.contains("shock") || cleanMood.contains("wonder") -> {
                weights[5] = 0.8f // Surprise
            }
            cleanMood.contains("sleepy") || cleanMood.contains("closed") || cleanMood.contains("tired") -> {
                weights[0] = 0.9f // Blink/Closed eyes
            }
        }
        weights
    }

    // Speech Viseme / Lip Sync Animation
    var speakMouthOpenFactor by remember { mutableStateOf(0f) }
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            while (true) {
                // Randomize open/close duration and target open factor to look natural
                val openTarget = 0.4f + (0..50).random() / 100f
                // Ease open
                val steps = 4
                for (i in 1..steps) {
                    speakMouthOpenFactor = (openTarget / steps) * i
                    kotlinx.coroutines.delay(30)
                }
                kotlinx.coroutines.delay((50..120).random().toLong())
                // Ease close
                for (i in steps downTo 0) {
                    speakMouthOpenFactor = (openTarget / steps) * i
                    kotlinx.coroutines.delay(20)
                }
                kotlinx.coroutines.delay((30..80).random().toLong())
            }
        } else {
            speakMouthOpenFactor = 0f
        }
    }

    // Auto-blinking animation effect running in the background
    LaunchedEffect(modelNodeRef, baseWeights, speakMouthOpenFactor) {
        val node = modelNodeRef ?: return@LaunchedEffect
        scope.launch {
            while (true) {
                // Blink interval: random time between 3 to 6 seconds
                delay((3000..6000).random().toLong())
                
                // If the mood is already closed-eyes, don't blink
                if (baseWeights[0] > 0.5f) continue

                // Blink down: set eye-blink morph target weight (index 0) to 1.0
                val blinkWeights = baseWeights.clone()
                if (blinkWeights.size > 14) {
                    blinkWeights[10] = speakMouthOpenFactor
                    blinkWeights[14] = speakMouthOpenFactor * 0.3f
                } else if (blinkWeights.size > 6) {
                    blinkWeights[6] = speakMouthOpenFactor
                }
                blinkWeights[0] = 1.0f
                node.setMorphWeights(blinkWeights, offset = 0)
                
                delay(120) // eyes closed duration
                
                // Blink up: restore base mood weights
                val restoreWeights = baseWeights.clone()
                if (restoreWeights.size > 14) {
                    restoreWeights[10] = speakMouthOpenFactor
                    restoreWeights[14] = speakMouthOpenFactor * 0.3f
                } else if (restoreWeights.size > 6) {
                    restoreWeights[6] = speakMouthOpenFactor
                }
                node.setMorphWeights(restoreWeights, offset = 0)
            }
        }
    }

    // Update morph targets whenever mood/weights change
    LaunchedEffect(modelNodeRef, baseWeights, speakMouthOpenFactor) {
        val node = modelNodeRef ?: return@LaunchedEffect
        val currentWeights = baseWeights.clone()
        if (currentWeights.size > 14) {
            currentWeights[10] = speakMouthOpenFactor // Viseme A
            currentWeights[14] = speakMouthOpenFactor * 0.3f // Viseme O
        } else if (currentWeights.size > 6) {
            currentWeights[6] = speakMouthOpenFactor // fallback viseme
        }
        node.setMorphWeights(currentWeights, offset = 0)
    }

    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 1.25f, 1.25f) // torso/head framing
        rotation = Rotation(-8f, 0f, 0f)      // look slightly down
    }

    SceneView(
        modifier = modifier.fillMaxSize(),
        surfaceType = io.github.sceneview.SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = rememberCameraManipulator()
    ) {
        val modelInstance = rememberModelInstance(modelLoader, modelFile.absolutePath)
        if (modelInstance != null) {
            val modelNode = rememberNode {
                io.github.sceneview.node.ModelNode(
                    modelInstance = modelInstance
                ).apply {
                    position = Position(0f, 0f, 0f)
                    scale = Scale(1.0f)
                }
            }
            NodeLifecycle(modelNode) {}
            
            // Trigger animation and update state reference
            LaunchedEffect(modelNode, animationIndex) {
                modelNodeRef = modelNode
                try {
                    modelNode.playAnimation(animationIndex = animationIndex, loop = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
