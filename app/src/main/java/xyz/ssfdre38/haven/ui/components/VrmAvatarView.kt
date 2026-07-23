package xyz.ssfdre38.haven.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.SceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberMainLightNode
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
    onClothingStateChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cleanDiskPath = remember(modelPath) {
        if (modelPath.startsWith("file://", ignoreCase = true)) modelPath.substring(7) else modelPath
    }
    val modelFile = remember(cleanDiskPath) { File(cleanDiskPath) }
    if (!modelFile.exists()) return

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val scope = rememberCoroutineScope()

    var modelNodeRef by remember { mutableStateOf<io.github.sceneview.node.ModelNode?>(null) }
    var topVisible by remember { mutableStateOf(true) }
    var bottomVisible by remember { mutableStateOf(true) }
    var isUndressed by remember { mutableStateOf(false) }

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
                val openTarget = 0.4f + (0..50).random() / 100f
                val steps = 4
                for (i in 1..steps) {
                    speakMouthOpenFactor = (openTarget / steps) * i
                    kotlinx.coroutines.delay(30)
                }
                kotlinx.coroutines.delay((50..120).random().toLong())
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

    // Auto-blinking animation effect
    LaunchedEffect(modelNodeRef, baseWeights, speakMouthOpenFactor) {
        val node = modelNodeRef ?: return@LaunchedEffect
        while (true) {
            delay((3000..6000).random().toLong())
            if (baseWeights[0] > 0.5f) continue

            val blinkWeights = baseWeights.clone()
            if (blinkWeights.size > 14) {
                blinkWeights[10] = speakMouthOpenFactor
                blinkWeights[14] = speakMouthOpenFactor * 0.3f
            } else if (blinkWeights.size > 6) {
                blinkWeights[6] = speakMouthOpenFactor
            }
            blinkWeights[0] = 1.0f
            node.setMorphWeights(blinkWeights, offset = 0)
            
            delay(120)
            
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

    // Update morph targets whenever mood/weights change
    LaunchedEffect(modelNodeRef, baseWeights, speakMouthOpenFactor) {
        val node = modelNodeRef ?: return@LaunchedEffect
        val currentWeights = baseWeights.clone()
        if (currentWeights.size > 14) {
            currentWeights[10] = speakMouthOpenFactor
            currentWeights[14] = speakMouthOpenFactor * 0.3f
        } else if (currentWeights.size > 6) {
            currentWeights[6] = speakMouthOpenFactor
        }
        node.setMorphWeights(currentWeights, offset = 0)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hardwareConfig = remember(context) { xyz.ssfdre38.haven.util.HardwareProfileManager.getDeviceProfile(context) }

    val mainLightNode = rememberMainLightNode(engine) {
        intensity = hardwareConfig.maxLightIntensity
        color = io.github.sceneview.math.Color(1.0f, 0.98f, 0.95f)
        rotation = Rotation(x = -45f, y = 45f, z = 0f)
    }

    val fillLightNode = rememberNode {
        io.github.sceneview.node.LightNode(
            engine = engine,
            type = com.google.android.filament.LightManager.Type.DIRECTIONAL,
            apply = {
                intensity(hardwareConfig.maxLightIntensity * 0.55f)
                color(0.9f, 0.95f, 1.0f)
            }
        ).apply {
            rotation = Rotation(x = -15f, y = -45f, z = 0f)
        }
    }

    val backLightNode = rememberNode {
        io.github.sceneview.node.LightNode(
            engine = engine,
            type = com.google.android.filament.LightManager.Type.DIRECTIONAL,
            apply = {
                intensity(hardwareConfig.maxLightIntensity * 0.65f)
                color(1.0f, 1.0f, 1.0f)
            }
        ).apply {
            rotation = Rotation(x = 45f, y = 180f, z = 0f)
        }
    }

    val cameraNode = rememberCameraNode(engine) {
        position = Position(0f, 1.25f, 1.25f)
        rotation = Rotation(-8f, 0f, 0f)
    }

    val modelInstance = rememberModelInstance(modelLoader, modelFile.absolutePath)
    if (modelInstance == null) return

    Box(modifier = modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            cameraManipulator = rememberCameraManipulator()
        ) {
            NodeLifecycle(mainLightNode) {}
            NodeLifecycle(fillLightNode) {}
            NodeLifecycle(backLightNode) {}

            modelInstance.materialInstances.forEach { mat ->
                try {
                    mat.isDoubleSided = true
                    try { mat.setParameter("roughnessFactor", 0.8f) } catch (_: Exception) {}
                    try { mat.setParameter("metallicFactor", 0.0f) } catch (_: Exception) {}
                } catch (_: Exception) {}
            }

            val modelNode = rememberNode {
                io.github.sceneview.node.ModelNode(
                    modelInstance = modelInstance
                ).apply {
                    position = Position(0f, -0.6f, 0f)
                    scale = Scale(0.9f)
                }
            }
            NodeLifecycle(modelNode) {}
            
            LaunchedEffect(modelNode, animationIndex) {
                modelNodeRef = modelNode
                try {
                    cameraNode.position = Position(0f, 0.85f, 1.8f)
                    cameraNode.rotation = Rotation(-4f, 0f, 0f)
                    modelNode.playAnimation(animationIndex = animationIndex, loop = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Floating 3D Wardrobe Layer Controls Overlay
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isUndressed,
                    onClick = {
                        isUndressed = !isUndressed
                        onClothingStateChanged?.invoke(if (isUndressed) "Undressed, Naked in bed" else "Fully Dressed")
                    },
                    label = { Text(if (isUndressed) "✨ Dress" else "✨ Strip", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = topVisible && !isUndressed,
                    onClick = {
                        topVisible = !topVisible
                        onClothingStateChanged?.invoke(if (topVisible) "Top On" else "Top Off")
                    },
                    label = { Text("👚 Top", style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = bottomVisible && !isUndressed,
                    onClick = {
                        bottomVisible = !bottomVisible
                        onClothingStateChanged?.invoke(if (bottomVisible) "Skirt On" else "Skirt Off")
                    },
                    label = { Text("👗 Skirt", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
