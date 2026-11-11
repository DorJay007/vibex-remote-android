package dev.vibex.companion.app.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.vibex.companion.app.MainActivity
import dev.vibex.companion.app.R

object SystemNotifier {
    private const val CHANNEL_ALERTS = "vibex_alerts"
    private const val CHANNEL_STATUS = "vibex_status"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "VibeX 任务与审批提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "会话完成、执行异常及权限审批提醒"
            enableVibration(true)
        }
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "VibeX 状态通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "电脑连接状态与系统提示"
        }
        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(statusChannel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyAlert(
        context: Context,
        notificationKey: String,
        title: String,
        body: String,
        conversationId: String? = null,
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra("open_conversation_id", conversationId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000L)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationKey.hashCode(), notification)
        }
    }

    fun notifyStatus(
        context: Context,
        title: String,
        body: String,
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000L)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                ("status:$title").hashCode(),
                notification,
            )
        }
    }
}
