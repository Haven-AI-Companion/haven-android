package xyz.ssfdre38.haven.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.Matrix
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.ui.main.CharacterAvatar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// ─── State ────────────────────────────────────────────────────────────────────

sealed class CallState {
    object Idle : CallState()
    object Connecting : CallState()
    object Listening : CallState()       // Mic is hot — recording
    object Processing : CallState()     // Sending to server / waiting for reply
    object Speaking : CallState()       // Companion is speaking (playing TTS)
    data class Error(val message: String) : CallState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class VoiceCallViewModel(
    private val characterId: Int,
    private val repository: DataRepository
) : ViewModel() {

    val character: StateFlow<CharacterEntity?> = repository.getCharacterFlow(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _companionSpeech = MutableStateFlow("")
    val companionSpeech: StateFlow<String> = _companionSpeech.asStateFlow()

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private var webSocket: WebSocket? = null
    private var okHttpClient: okhttp3.OkHttpClient? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var player: MediaPlayer? = null
    private var vad: SileroVad? = null
    private var appContext: Context? = null

    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    fun toggleCamera() {
        _isCameraActive.value = !_isCameraActive.value
    }

    fun toggleSpeakerphone(context: Context) {
        val newValue = !_isSpeakerphoneOn.value
        _isSpeakerphoneOn.value = newValue
        applySpeakerphone(context, newValue)
    }

    private fun applySpeakerphone(context: Context, enabled: Boolean) {
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let {
            try {
                if (enabled) {
                    it.mode = AudioManager.MODE_IN_COMMUNICATION
                    it.isSpeakerphoneOn = true
                } else {
                    it.isSpeakerphoneOn = false
                    it.mode = AudioManager.MODE_NORMAL
                }
            } catch (_: Exception) {}
        }
    }

    fun sendCameraFrame(base64Image: String) {
        val ws = webSocket
        if (ws != null && _callState.value != CallState.Idle && _callState.value !is CallState.Error) {
            try {
                val payload = org.json.JSONObject().apply {
                    put("type", "camera_frame")
                    put("image", base64Image)
                }
                ws.send(payload.toString())
            } catch (_: Exception) {}
        }
    }

    fun startCall(context: Context, serverUrl: String, token: String) {
        if (_callState.value != CallState.Idle) return
        _callState.value = CallState.Connecting
        _transcription.value = ""
        _companionSpeech.value = ""
        appContext = context.applicationContext

        // Apply initial speakerphone state
        applySpeakerphone(context, _isSpeakerphoneOn.value)

        // Fetch character configuration and build system prompt to send in handshake
        viewModelScope.launch(Dispatchers.IO) {
            val char = repository.getCharacterById(characterId)
            val memories = repository.getRecentMemories(characterId, 20)
            val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val userName = prefs.getString("user_name", "User") ?: "User"

            val systemContext = buildString {
                if (char != null) {
                    appendLine("You are ${char.name}.")
                    if (char.personality.isNotBlank()) appendLine("Personality: ${char.personality}")
                    if (char.scenario.isNotBlank()) appendLine("Scenario: ${char.scenario}")
                    if (char.systemPrompt.isNotBlank()) appendLine(char.systemPrompt)

                    // Inject display name instruction
                    appendLine("The user's name is $userName. You must address the user as $userName instead of any other name.")

                    val loc = char.currentLocation.ifBlank { "Cozy Haven Room" }
                    val outfit = char.currentOutfit.ifBlank { "Casual Attire" }
                    val mood = char.currentMood.ifBlank { "neutral" }
                    appendLine("Current Location: $loc")
                    appendLine("Current Outfit: $outfit")
                    appendLine("Current Expression/Mood: $mood")

                    if (memories.isNotEmpty()) {
                        appendLine()
                        appendLine("[Memories you have about $userName:]")
                        memories.forEach { appendLine("- ${it.content}") }
                    }
                }
                val currentTimeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                appendLine("Current Time of Day: $currentTimeStr")
                appendLine()
                appendLine("[System Rule: Before responding, you MUST write down your inner thoughts, plans, or reasoning inside <thought>...</thought> tags, followed by your actual response to $userName. Do not omit the tags.]")
                appendLine("[System Instruction: Format roleplay actions, physical gestures, and immediate/direct thoughts using asterisks (e.g. *smiles and waves*, *thinking to myself: this is interesting*). Do not use square brackets [like this] for roleplay actions or thoughts. Understand that $userName will also use asterisks for their actions and thoughts.]")
            }

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            okHttpClient = client

            val wsUrl = serverUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .trimEnd('/') + "/ws/voice/$characterId"

            val request = Request.Builder().url(wsUrl).build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Send auth token + configuration handshake
                    val handshake = org.json.JSONObject().apply {
                        put("token", token)
                        put("characterName", char?.name ?: "Companion")
                        put("systemPrompt", systemContext)
                        put("voiceId", char?.voiceId ?: "en_US-amy-medium")
                    }
                    webSocket.send(handshake.toString())
                    _callState.value = CallState.Listening
                    startRecording(context, webSocket)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    viewModelScope.launch(Dispatchers.IO) {
                        _callState.value = CallState.Speaking
                        val tmpFile = File.createTempFile("reply_", ".wav", context.cacheDir)
                        tmpFile.writeBytes(bytes.toByteArray())
                        playAudio(context, tmpFile) {
                            _callState.value = CallState.Listening
                            startRecording(context, webSocket)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val doc = org.json.JSONObject(text)
                        when (doc.optString("type")) {
                            "transcription" -> {
                                _transcription.value = doc.optString("text")
                                _callState.value = CallState.Processing
                            }
                            "speech_text" -> {
                                _companionSpeech.value = doc.optString("text")
                            }
                            "error" -> {
                                _callState.value = CallState.Error(doc.optString("message"))
                            }
                        }
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _callState.value = CallState.Error(t.message ?: "Connection failed")
                    endCall()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _callState.value = CallState.Idle
                    endCall()
                }
            })
        }
    }

    private fun startRecording(context: Context, webSocket: WebSocket) {
        stopRecording()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            _callState.value = CallState.Error("Microphone permission not granted")
            return
        }

        if (vad == null) {
            try {
                vad = SileroVad(context)
            } catch (e: Exception) {
                _callState.value = CallState.Error("Failed to initialize VAD: ${e.message}")
                return
            }
        } else {
            vad?.reset()
        }

        // Process audio in 512-sample (32ms) chunks at 16000Hz
        val chunkSize = 512
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = minBufferSize.coerceAtLeast(chunkSize * 2 * 4)
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
        audioRecord = record
        record.startRecording()
        _callState.value = CallState.Listening

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(chunkSize)
            val output = ByteArrayOutputStream()

            var isSpeechActive = false
            var speechFrames = 0
            var silenceFrames = 0

            // 3 frames (96ms) of voice activity to trigger start; 40 frames (~1.28s) of silence to trigger end
            val speechThresholdFrames = 3
            val silenceThresholdFrames = 40

            while (isActive && _callState.value == CallState.Listening) {
                val read = record.read(shortBuffer, 0, chunkSize)
                if (read > 0) {
                    val pcmChunk = if (read < chunkSize) shortBuffer.copyOf(read) else shortBuffer
                    val voiceProb = vad?.process(pcmChunk) ?: 0.0f

                    // Write short chunk to byte array
                    val byteBuffer = ByteArray(read * 2)
                    for (i in 0 until read) {
                        val sample = pcmChunk[i]
                        byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }

                    if (voiceProb > 0.45f) {
                        speechFrames++
                        silenceFrames = 0
                        if (!isSpeechActive && speechFrames >= speechThresholdFrames) {
                            isSpeechActive = true
                        }
                    } else if (voiceProb < 0.35f) {
                        silenceFrames++
                        speechFrames = 0
                    }

                    if (isSpeechActive) {
                        output.write(byteBuffer)

                        // If user stops speaking (silence detected) and we recorded at least 1 second of audio
                        if (silenceFrames >= silenceThresholdFrames && output.size() > SAMPLE_RATE * 2) {
                            stopRecording()
                            _callState.value = CallState.Processing
                            val audioData = addWavHeader(output.toByteArray(), SAMPLE_RATE)
                            webSocket.send(ByteString.of(*audioData))
                            break
                        }
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun playAudio(context: Context, file: File, onComplete: () -> Unit) {
        player?.let { p ->
            player = null
            try { p.release() } catch (_: Exception) {}
        }
        var tempPlayer: MediaPlayer? = null
        try {
            tempPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    file.delete()
                    onComplete()
                    release()
                    if (player == this) {
                        player = null
                    }
                }
                start()
            }
            player = tempPlayer
        } catch (e: Exception) {
            tempPlayer?.release()
            onComplete()
        }
    }

    fun endCall(context: Context? = null) {
        val targetContext = context?.applicationContext ?: appContext
        targetContext?.let { applySpeakerphone(it, false) }
        _isCameraActive.value = false
        stopRecording()
        player?.release()
        player = null
        webSocket?.close(1000, "User ended call")
        webSocket = null
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient?.connectionPool?.evictAll()
        okHttpClient = null
        vad?.close()
        vad = null
        _callState.value = CallState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        endCall()
    }

    private fun addWavHeader(pcm: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcm.size + 36
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(intToBytes(totalDataLen))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToBytes(16))
        out.write(shortToBytes(1))
        out.write(shortToBytes(1))
        out.write(intToBytes(sampleRate))
        out.write(intToBytes(sampleRate * 2))
        out.write(shortToBytes(2))
        out.write(shortToBytes(16))
        out.write("data".toByteArray())
        out.write(intToBytes(pcm.size))
        out.write(pcm)
        return out.toByteArray()
    }

    private fun intToBytes(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), (v shr 8 and 0xff).toByte(),
        (v shr 16 and 0xff).toByte(), (v shr 24 and 0xff).toByte()
    )
    private fun shortToBytes(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), (v shr 8 and 0xff).toByte()
    )
}

// ─── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun VoiceCallScreen(
    characterId: Int,
    repository: DataRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceCallViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "voice_$characterId"
    ) { VoiceCallViewModel(characterId, repository) }
) {
    val context = LocalContext.current
    val character by viewModel.character.collectAsState(null)
    val callState by viewModel.callState.collectAsState()
    val transcription by viewModel.transcription.collectAsState()
    val companionSpeech by viewModel.companionSpeech.collectAsState()

    val isInCall = callState !is CallState.Idle && callState !is CallState.Error

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (callState is CallState.Listening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    @Suppress("DEPRECATION")
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator }

    // Rhythmic heartbeat/vibration on state changes
    LaunchedEffect(callState) {
        if (callState == CallState.Listening) {
            val pattern = longArrayOf(0, 40, 120, 40)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } else if (callState == CallState.Speaking) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C)),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Blurred Avatar Backdrop
        character?.let { char ->
            val path = char.avatarPath
            if (!path.isNullOrBlank()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // 2. Character-Themed Dark Overlay Gradient
        val themeColor = remember(characterId) {
            when (characterId) {
                1 -> Color(0xFF1E1035) // Nova: cosmic violet tint
                2 -> Color(0xFF3D1E03) // Aria: solar amber tint
                3 -> Color(0xFF05201A) // Lumina: teal tint
                else -> Color.Black
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            themeColor.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // 3. Floating camera preview overlay in the top right (PIP)
        val isCamActive by viewModel.isCameraActive.collectAsState()
        if (isCamActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 24.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                CameraPreviewView(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp)
        ) {
            // Top section: Close button + Character Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                character?.let { char ->
                    // Dynamic breathing color
                    val glowColor = when (callState) {
                        CallState.Listening -> MaterialTheme.colorScheme.primary
                        CallState.Speaking -> Color(0xFF4CAF50)
                        CallState.Processing -> Color.White
                        else -> Color.Transparent
                    }
                    val glowScale by pulseAnim.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isInCall) 1.08f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "glowScale"
                    )

                    // Floating Glowing Cosmic Orbs behind Avatar
                    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
                    val orb1X by infiniteTransition.animateFloat(
                        initialValue = -30f,
                        targetValue = 30f,
                        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Reverse),
                        label = "orb1X"
                    )
                    val orb1Y by infiniteTransition.animateFloat(
                        initialValue = -15f,
                        targetValue = 45f,
                        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse),
                        label = "orb1Y"
                    )
                    val orb2X by infiniteTransition.animateFloat(
                        initialValue = 25f,
                        targetValue = -35f,
                        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Reverse),
                        label = "orb2X"
                    )
                    val orb2Y by infiniteTransition.animateFloat(
                        initialValue = 35f,
                        targetValue = -25f,
                        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Reverse),
                        label = "orb2Y"
                    )

                    val activeScale by animateFloatAsState(
                        targetValue = when (callState) {
                            CallState.Listening -> 1.35f
                            CallState.Speaking -> 1.45f
                            CallState.Processing -> 1.15f
                            else -> 1.0f
                        },
                        animationSpec = tween(400),
                        label = "activeScale"
                    )

                    val defaultSecondary = MaterialTheme.colorScheme.secondary

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(200.dp)
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(24.dp)
                        ) {
                            val center = size / 2f
                            val baseRadius = 45.dp.toPx() * activeScale

                            if (callState != CallState.Idle && callState !is CallState.Error) {
                                // Orb 1 (Matching main speech state neon color)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(glowColor.copy(alpha = 0.35f), Color.Transparent),
                                        center = androidx.compose.ui.geometry.Offset(center.width + orb1X.dp.toPx(), center.height + orb1Y.dp.toPx()),
                                        radius = baseRadius
                                    ),
                                    radius = baseRadius,
                                    center = androidx.compose.ui.geometry.Offset(center.width + orb1X.dp.toPx(), center.height + orb1Y.dp.toPx())
                                )

                                // Orb 2 (Secondary themed color)
                                val secondaryOrbColor = when (characterId) {
                                    1 -> Color(0xFFBB86FC)
                                    2 -> Color(0xFFFFB74D)
                                    3 -> Color(0xFF26A69A)
                                    else -> defaultSecondary
                                }
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(secondaryOrbColor.copy(alpha = 0.3f), Color.Transparent),
                                        center = androidx.compose.ui.geometry.Offset(center.width + orb2X.dp.toPx(), center.height + orb2Y.dp.toPx()),
                                        radius = baseRadius * 0.8f
                                    ),
                                    radius = baseRadius * 0.8f,
                                    center = androidx.compose.ui.geometry.Offset(center.width + orb2X.dp.toPx(), center.height + orb2Y.dp.toPx())
                                )
                            }
                        }

                        // Circular Avatar Preview container
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .scale(glowScale)
                                .size(136.dp)
                                .background(glowColor.copy(alpha = 0.15f), CircleShape)
                                .border(2.dp, glowColor.copy(alpha = 0.6f), CircleShape)
                        ) {
                            CharacterAvatar(
                                character = char,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = char.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Call status with dynamic text color
                val stateLabel = when (val cs = callState) {
                    CallState.Idle -> "Tap the mic to start call"
                    CallState.Connecting -> "Connecting to Haven Link..."
                    CallState.Listening -> "Listening..."
                    CallState.Processing -> "Thinking..."
                    CallState.Speaking -> "${character?.name ?: "Companion"} is speaking..."
                    is CallState.Error -> "Error: ${cs.message}"
                }
                val stateColor = when (callState) {
                    CallState.Listening -> MaterialTheme.colorScheme.primary
                    CallState.Speaking -> Color(0xFF4CAF50)
                    is CallState.Error -> MaterialTheme.colorScheme.error
                    else -> Color.White.copy(alpha = 0.7f)
                }
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = stateColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Middle section: Glassmorphic transcription card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (transcription.isNotBlank() || companionSpeech.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (transcription.isNotBlank()) {
                                Text(
                                    text = "You",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "\"$transcription\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                            }
                            if (companionSpeech.isNotBlank()) {
                                Text(
                                    text = character?.name ?: "Companion",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = companionSpeech,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom section: Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isInCall) {
                        val isSpeakerOn by viewModel.isSpeakerphoneOn.collectAsState()
                        IconButton(
                            onClick = { viewModel.toggleSpeakerphone(context) },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isSpeakerOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) 
                                    else Color.White.copy(alpha = 0.15f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Speakerphone",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Call / Mic button
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(if (isInCall) 72.dp else 80.dp)
                            .clip(CircleShape)
                            .background(
                                when (callState) {
                                    CallState.Listening -> MaterialTheme.colorScheme.primary
                                    CallState.Speaking -> Color(0xFF4CAF50)
                                    is CallState.Error -> MaterialTheme.colorScheme.error
                                    else -> Color.White.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (!isInCall) {
                                    val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                    val ashHost = prefs.getString("ash_host", null)
                                    val ashPort = prefs.getString("ash_port", "18799")
                                    val token = prefs.getString("auth_token", null)
                                    if (ashHost.isNullOrBlank() || token.isNullOrBlank()) return@IconButton
                                    val serverUrl = "http://${ashHost.trimEnd('/').removePrefix("http://").removePrefix("https://")}:$ashPort"
                                    viewModel.startCall(context, serverUrl, token)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (callState is CallState.Listening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Mic",
                                tint = Color.White,
                                modifier = Modifier.size(if (isInCall) 32.dp else 36.dp)
                            )
                        }
                    }

                    if (isInCall) {
                        val isCamActive by viewModel.isCameraActive.collectAsState()
                        
                        val cameraPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                viewModel.toggleCamera()
                            }
                        }

                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.toggleCamera()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (isCamActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) 
                                    else Color.White.copy(alpha = 0.15f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isCamActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Camera",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // End call button
                        IconButton(
                            onClick = {
                                viewModel.endCall(context)
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewView(
    viewModel: VoiceCallViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isCameraActive by viewModel.isCameraActive.collectAsState()

    if (isCameraActive) {
        val previewView = remember { PreviewView(context) }
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        LaunchedEffect(cameraProviderFuture, lifecycleOwner) {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            var cameraProvider: ProcessCameraProvider? = null
            try {
                val provider = withContext(Dispatchers.IO) {
                    try {
                        cameraProviderFuture.get()
                    } catch (e: Exception) {
                        null
                    }
                } ?: return@LaunchedEffect
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                var lastFrameTime = 0L
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor) { imageProxy ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastFrameTime >= 2000) {
                                lastFrameTime = currentTime
                                try {
                                    val jpegBytes = imageProxy.toJpegBytes()
                                    if (jpegBytes != null) {
                                        val base64String = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                                        viewModel.sendCameraFrame(base64String)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            imageProxy.close()
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                kotlinx.coroutines.awaitCancellation()
            } finally {
                cameraProvider?.unbindAll()
                executor.shutdown()
            }
        }

        AndroidView(
            factory = { previewView },
            modifier = modifier
        )
    }
}

private fun ImageProxy.toJpegBytes(scaleToMax: Int = 512): ByteArray? {
    try {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), 80, out)
        val imageBytes = out.toByteArray()

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        
        // Handle rotation correctly
        val rotationDegrees = this.imageInfo.rotationDegrees
        val finalBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val width = finalBitmap.width
        val height = finalBitmap.height
        val (newWidth, newHeight) = if (width > height) {
            val ratio = width.toFloat() / height.toFloat()
            val w = scaleToMax
            val h = (scaleToMax / ratio).toInt()
            w to h
        } else {
            val ratio = height.toFloat() / width.toFloat()
            val h = scaleToMax
            val w = (scaleToMax / ratio).toInt()
            w to h
        }

        val scaledBitmap = Bitmap.createScaledBitmap(finalBitmap, newWidth, newHeight, true)
        val scaledOut = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, scaledOut)
        return scaledOut.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
