package xyz.ssfdre38.haven.agent

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Base prototype of the proposed AOSP AgentService.
 * Handles background-persistent companion lifecycles and coordinates system events.
 */
abstract class AgentService : Service() {

    protected val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val binder = AgentBinder()

    private val _systemEvents = MutableSharedFlow<AgentEvent>()
    val systemEvents: SharedFlow<AgentEvent> = _systemEvents

    inner class AgentBinder : Binder() {
        fun getService(): AgentService = this@AgentService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        observeSystemTelemetry()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun observeSystemTelemetry() {
        serviceScope.launch {
            // Periodically collect or stream telemetry updates
        }
    }

    fun dispatchEvent(event: AgentEvent) {
        serviceScope.launch {
            _systemEvents.emit(event)
        }
    }
}

sealed class AgentEvent {
    data class NotificationReceived(val packageName: String, val text: String) : AgentEvent()
    data class DeviceStateChanged(val batteryLevel: Int, val isScreenOn: Boolean) : AgentEvent()
    data class LocationUpdated(val latitude: Double, val longitude: Double) : AgentEvent()
}
