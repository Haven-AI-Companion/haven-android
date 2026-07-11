package xyz.ssfdre38.haven.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import xyz.ssfdre38.haven.MainActivity
import xyz.ssfdre38.haven.R
import xyz.ssfdre38.haven.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class HavenAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, HavenAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            updateWidgets(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "xyz.ssfdre38.haven.ACTION_UPDATE_WIDGET"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, HavenAppWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val dao = db.havenDao()
                    
                    val characters = dao.getAllCharactersList()
                    val activeChar = characters.maxByOrNull { it.relationshipXp } ?: characters.firstOrNull()
                    
                    for (appWidgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.haven_widget)
                        
                        if (activeChar != null) {
                            views.setTextViewText(R.id.widget_companion_name, activeChar.name)
                            views.setTextViewText(
                                R.id.widget_companion_location,
                                "Location: ${activeChar.currentLocation.ifBlank { "Group Lobby" }}"
                            )
                            
                            val lastMsg = dao.getLastMessage(activeChar.id)
                            views.setTextViewText(
                                R.id.widget_last_message,
                                lastMsg?.text ?: "No messages yet."
                            )
                            
                            // Load avatar bitmap if exists
                            if (activeChar.avatarPath != null) {
                                val file = File(activeChar.avatarPath)
                                if (file.exists()) {
                                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                                    if (bitmap != null) {
                                        views.setImageViewBitmap(R.id.widget_avatar, bitmap)
                                    }
                                }
                            }
                            
                            // Click intent to launch app to chat
                            val clickIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("characterId", activeChar.id)
                            }
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                activeChar.id,
                                clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_avatar, pendingIntent)
                            views.setOnClickPendingIntent(R.id.widget_companion_name, pendingIntent)
                            views.setOnClickPendingIntent(R.id.widget_last_message, pendingIntent)
                        } else {
                            views.setTextViewText(R.id.widget_companion_name, "Haven")
                            views.setTextViewText(R.id.widget_companion_location, "No companions yet.")
                            views.setTextViewText(R.id.widget_last_message, "Tap to open and import companions.")
                            
                            val clickIntent = Intent(context, MainActivity::class.java)
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                0,
                                clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_companion_name, pendingIntent)
                        }
                        
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
