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
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingCompanionService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCompanionService)
            setViewTreeViewModelStoreOwner(this@FloatingCompanionService)

            setContent {
                var companionModelPath by remember { mutableStateOf<String?>(null) }
                var mood by remember { mutableStateOf("neutral") }
                
                LaunchedEffect(Unit) {
                    repository?.getAllCharacters()?.collect { list ->
                        val char = list.firstOrNull()
                        if (char != null) {
                            companionModelPath = char.vrmModelPath
                            mood = char.currentMood
                        }
                    }
                }

                val path = companionModelPath
                if (path != null && File(path).exists()) {
                    Box(modifier = Modifier.size(160.dp, 220.dp)) {
                        VrmAvatarView(
                            modelPath = path,
                            mood = mood,
                            isSpeaking = false,
                            modifier = Modifier.fillMaxSize()
                        )
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
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
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
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(composeView, params)
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
