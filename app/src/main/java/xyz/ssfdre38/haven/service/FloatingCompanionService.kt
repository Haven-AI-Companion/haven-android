package xyz.ssfdre38.haven.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.DefaultDataRepository
import xyz.ssfdre38.haven.data.database.AppDatabase
import xyz.ssfdre38.haven.ui.components.VrmAvatarView
import java.io.File

class FloatingCompanionService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var repository: DataRepository? = null
    private var isUserDragging = false

    // LifecycleOwner implementation
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    // SavedStateRegistryOwner implementation
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    // ViewModelStoreOwner implementation
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val database = AppDatabase.getInstance(applicationContext)
        repository = DefaultDataRepository(database.havenDao())

        // Setup layouts params for floating companion window
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 150
            y = 300
        }

        // Initialize Compose view
        composeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingCompanionService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCompanionService)
            setViewTreeViewModelStoreOwner(this@FloatingCompanionService)

            setContent {
                var companionModelPath by remember { mutableStateOf<String?>(null) }
                var mood by remember { mutableStateOf("neutral") }
                var activeAnimationIndex by remember { mutableStateOf(0) }
                
                LaunchedEffect(Unit) {
                    repository?.getAllCharacters()?.collect { list ->
                        val char = list.maxByOrNull { it.relationshipXp } ?: list.firstOrNull()
                        if (char != null) {
                            companionModelPath = char.vrmModelPath
                            mood = char.currentMood
                        }
                    }
                }

                // Periodically trigger idle gestures (index 1 or 2) in the background to simulate autonomous behavior
                LaunchedEffect(companionModelPath) {
                    if (companionModelPath == null) return@LaunchedEffect
                    while (true) {
                        kotlinx.coroutines.delay((25000..50000).random().toLong())
                        if (isUserDragging) continue
                        
                        val gestureIndex = (1..2).random()
                        activeAnimationIndex = gestureIndex
                        
                        // Play gesture for 4 seconds, then blend back to standard idle
                        kotlinx.coroutines.delay(4000)
                        activeAnimationIndex = 0
                    }
                }

                // Autonomous Walking Behavior across the home screen
                LaunchedEffect(companionModelPath) {
                    if (companionModelPath == null) return@LaunchedEffect
                    while (true) {
                        // Wait a random duration between 40 to 80 seconds before walking
                        kotlinx.coroutines.delay((40000..80000).random().toLong())
                        if (isUserDragging) continue // skip auto-walk if user is actively dragging
                        
                        val displayMetrics = android.util.DisplayMetrics()
                        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            val bounds = wm.currentWindowMetrics.bounds
                            displayMetrics.widthPixels = bounds.width()
                        } else {
                            @Suppress("DEPRECATION")
                            wm.defaultDisplay.getMetrics(displayMetrics)
                        }
                        
                        // Target a new random position on the screen
                        val maxTargetX = (displayMetrics.widthPixels - 160).coerceAtLeast(100)
                        val targetX = (30..maxTargetX).random()
                        val startX = params.x
                        val distance = Math.abs(targetX - startX)
                        if (distance < 50) continue // Skip tiny walks
                        
                        // Set animation index 3 (standard locomotion/walk in VRMs)
                        activeAnimationIndex = 3
                        
                        // Smoothly transition x layout coordinate over time
                        val steps = (distance / 3).coerceAtLeast(15)
                        val stepDelay = 30L
                        val delta = (targetX - startX).toFloat() / steps
                        
                        for (step in 1..steps) {
                            if (isUserDragging) break // Immediately abort walk if user grabs companion
                            params.x = (startX + delta * step).toInt()
                            try {
                                windowManager?.updateViewLayout(composeView, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            kotlinx.coroutines.delay(stepDelay)
                        }
                        
                        // Restore idle breathing state
                        activeAnimationIndex = 0
                    }
                }

                val path = companionModelPath
                val fileExists = remember(path) { path?.let { File(it).exists() } == true }
                if (fileExists && path != null) {
                    Box(modifier = Modifier.size(160.dp, 220.dp)) {
                        VrmAvatarView(
                            modelPath = path,
                            mood = mood,
                            isSpeaking = false,
                            animationIndex = activeAnimationIndex,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (path != null || companionModelPath != null) {
                    // Turn off service dynamically if VRM model is missing
                    LaunchedEffect(path) {
                        stopSelf()
                    }
                }
            }
        }

        // Touch listener for dragging the character around the home screen
        composeView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var lastClickTime: Long = 0
            private var touchDownTime: Long = 0

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchDownTime = System.currentTimeMillis()
                        isUserDragging = true
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isUserDragging = false
                        val duration = System.currentTimeMillis() - touchDownTime
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (duration < 250 && diffX < 10 && diffY < 10) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < 300) {
                                try {
                                    val intent = Intent(this@FloatingCompanionService, xyz.ssfdre38.haven.MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            lastClickTime = currentTime
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        isUserDragging = true
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(composeView, params)
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isUserDragging = false
                        return true
                    }
                }
                return false
            }
        })

        // Safely add view only if overlay permissions are still granted
        try {
            if (android.provider.Settings.canDrawOverlays(this)) {
                windowManager?.addView(composeView, params)
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            } else {
                stopSelf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
        scope.cancel()
        store.clear()
        if (composeView != null) {
            windowManager?.removeView(composeView)
        }
    }
}
