package xyz.ssfdre38.haven.data.wallpaper

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.DefaultDataRepository
import xyz.ssfdre38.haven.data.database.AppDatabase
import xyz.ssfdre38.haven.data.database.CharacterEntity
import java.io.File

class HavenWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return CompanionWallpaperEngine()
    }

    inner class CompanionWallpaperEngine : Engine(), SensorEventListener {

        private val scope = CoroutineScope(Dispatchers.Main + Job())
        private var repository: DataRepository? = null
        private var activeCharacter: CharacterEntity? = null
        private var avatarBitmap: Bitmap? = null
        private var lastMessageText: String? = null
        private var messagesJob: Job? = null
        
        // Parallax sensor variables
        private var sensorManager: SensorManager? = null
        private var rotationSensor: Sensor? = null
        private var offsetX = 0f
        private var offsetY = 0f
        private val maxOffset = 40f // Maximum parallax translation in pixels

        // Paint definitions
        private val bgPaint = Paint()
        private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26FFFFFF") // Glassmorphic translucent white
            style = Paint.Style.FILL
        }
        private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val textNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val textDetailsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9FFFFFF")
            textSize = 36f
        }
        private val textMessagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            
            // Initialize database & repository correctly
            val database = AppDatabase.getInstance(applicationContext)
            repository = DefaultDataRepository(database.havenDao())

            // Setup sensors for parallax
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)

            // Start database flow collection
            scope.launch {
                repository?.getAllCharacters()?.collect { characters ->
                    val char = characters.firstOrNull()
                    if (char != null) {
                        val prevChar = activeCharacter
                        activeCharacter = char
                        
                        // Restart messages collection if active character changes
                        if (prevChar?.id != char.id) {
                            messagesJob?.cancel()
                            messagesJob = scope.launch {
                                repository?.getMessagesForCharacter(char.id)?.collect { messagesList ->
                                    lastMessageText = messagesList.lastOrNull()?.text
                                    triggerRedraw()
                                }
                            }
                        }
                        
                        if (prevChar?.avatarPath != char.avatarPath) {
                            loadAvatarBitmap(char.avatarPath)
                        } else {
                            triggerRedraw()
                        }
                    }
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            sensorManager?.unregisterListener(this)
            scope.cancel()
            avatarBitmap?.recycle()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
                triggerRedraw()
            } else {
                sensorManager?.unregisterListener(this)
            }
        }

        private fun loadAvatarBitmap(path: String?) {
            scope.launch(Dispatchers.IO) {
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val decoded = BitmapFactory.decodeFile(file.absolutePath)
                        if (decoded != null) {
                            avatarBitmap?.recycle()
                            avatarBitmap = getRoundedCornerBitmap(decoded, 48f)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    triggerRedraw()
                }
            }
        }

        private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val color = 0xff424242.toInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = RectF(rect)
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            canvas.drawRoundRect(rectF, pixels, pixels, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            bitmap.recycle()
            return output
        }

        private fun triggerRedraw() {
            if (isVisible) {
                val canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    try {
                        drawWallpaper(canvas)
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                }
            }
        }

        private fun drawWallpaper(canvas: Canvas) {
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()

            // 1. Draw theme gradient background
            val char = activeCharacter
            val colors = when (char?.id) {
                1 -> intArrayOf(Color.parseColor("#1E1035"), Color.parseColor("#0C051A"))
                2 -> intArrayOf(Color.parseColor("#3D1E03"), Color.parseColor("#150A00"))
                3 -> intArrayOf(Color.parseColor("#05201A"), Color.parseColor("#010A08"))
                else -> intArrayOf(Color.parseColor("#121212"), Color.parseColor("#080808"))
            }
            val gradient = LinearGradient(0f, 0f, 0f, height, colors, null, Shader.TileMode.CLAMP)
            bgPaint.shader = gradient
            canvas.drawRect(0f, 0f, width, height, bgPaint)

            // 2. Draw dynamic avatar image with Parallax offsets
            val bitmap = avatarBitmap
            if (bitmap != null) {
                val avatarWidth = width * 0.75f
                val avatarHeight = (bitmap.height.toFloat() / bitmap.width.toFloat()) * avatarWidth
                
                val left = (width - avatarWidth) / 2f + offsetX
                val top = (height - avatarHeight) / 2.5f + offsetY
                val rectF = RectF(left, top, left + avatarWidth, top + avatarHeight)
                
                canvas.drawBitmap(bitmap, null, rectF, avatarPaint)
            }

            // 3. Draw bottom glassmorphic info card
            if (char != null) {
                val cardWidth = width * 0.88f
                val cardHeight = 360f
                val cardLeft = (width - cardWidth) / 2f
                val cardTop = height - cardHeight - 120f
                val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
                
                // Draw rounded glass card
                canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)
                canvas.drawRoundRect(cardRect, 36f, 36f, cardBorderPaint)

                // Draw Text details (Name, Location, Mood)
                val padding = 48f
                canvas.drawText(char.name, cardLeft + padding, cardTop + padding + 60f, textNamePaint)

                val detailsText = "📍 ${char.currentLocation.ifBlank { "Cozy Room" }}  •  Mood: ${char.currentMood.ifBlank { "Smiling" }}"
                canvas.drawText(detailsText, cardLeft + padding, cardTop + padding + 130f, textDetailsPaint)

                // Draw small conversational message bubble safely cached
                val lastMsg = lastMessageText
                if (!lastMsg.isNullOrBlank()) {
                    val cleanMsg = if (lastMsg.length > 50) lastMsg.take(47) + "..." else lastMsg
                    val displayMsg = "\"$cleanMsg\""
                    canvas.drawText(displayMsg, cardLeft + padding, cardTop + padding + 220f, textMessagePaint)
                }
            }
        }

        // Sensor listeners mapping rotation vector tilt to parallax offsets
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            val pitch = orientation[1] // Tilt back/forth
            val roll = orientation[2]  // Tilt left/right
            
            // Map pitch/roll radians directly to pixel offsets with smoothing
            val targetX = (roll * 2f).coerceIn(-1f, 1f) * maxOffset
            val targetY = (pitch * 2f).coerceIn(-1f, 1f) * maxOffset
            
            offsetX = offsetX * 0.9f + targetX * 0.1f
            offsetY = offsetY * 0.9f + targetY * 0.1f
            
            triggerRedraw()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
