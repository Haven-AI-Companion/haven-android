package xyz.ssfdre38.haven.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

object MediaTracker {
    var currentTrack: String? = null
    var currentArtist: String? = null
    var isPlaying: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: return
            
            // Extract metadata
            val track = intent.getStringExtra("track") ?: intent.getStringExtra("title")
            val artist = intent.getStringExtra("artist")
            // Some players broadcast playback status (playstate or playing)
            val playState = intent.getBooleanExtra("playstate", false) || intent.getBooleanExtra("playing", false)
            
            if (!track.isNullOrBlank()) {
                currentTrack = track
                currentArtist = artist
            }
            isPlaying = playState
        }
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.sec.android.app.music.metachanged")
            addAction("com.nullsoft.winamp.metachanged")
            addAction("com.amazon.mp3.metachanged")
            addAction("com.miui.player.metachanged")
            addAction("com.real.RealPlayer.metachanged")
            addAction("com.spotify.music.playbackstatechanged")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
