package dev.vibex.companion.app.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class CompanionMonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "VibeX 监控", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("VibeX")
            .setContentText("持续监控已打开")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    companion object {
        private const val CHANNEL = "vibex-monitor"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CompanionMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CompanionMonitorService::class.java))
        }
    }
}
