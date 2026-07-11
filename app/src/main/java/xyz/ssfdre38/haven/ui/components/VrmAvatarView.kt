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

    // Auto-blinking animation effect running in the background
    LaunchedEffect(modelNodeRef, baseWeights) {
        val node = modelNodeRef ?: return@LaunchedEffect
        scope.launch {
            while (true) {
                // Blink interval: random time between 3 to 6 seconds
                delay((3000..6000).random().toLong())
                
                // If the mood is already closed-eyes, don't blink
                if (baseWeights[0] > 0.5f) continue

                // Blink down: set eye-blink morph target weight (index 0) to 1.0
                val blinkWeights = baseWeights.clone()
                blinkWeights[0] = 1.0f
                node.setMorphWeights(blinkWeights, offset = 0)
                
                delay(120) // eyes closed duration
                
                // Blink up: restore base mood weights
                node.setMorphWeights(baseWeights, offset = 0)
            }
        }
    }

    // Update morph targets whenever mood/weights change
    LaunchedEffect(modelNodeRef, baseWeights) {
        modelNodeRef?.setMorphWeights(baseWeights, offset = 0)
    }

    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 1.25f, 1.25f) // torso/head framing
        rotation = Rotation(-8f, 0f, 0f)      // look slightly down
    }

    SceneView(
        modifier = modifier.fillMaxSize(),
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
            
            // Trigger animation and update state reference
            LaunchedEffect(modelNode) {
                modelNodeRef = modelNode
                try {
                    modelNode.playAnimation(animationIndex = 0, loop = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
