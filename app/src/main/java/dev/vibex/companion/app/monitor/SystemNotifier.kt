package dev.vibex.companion.app.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.vibex.companion.PendingQuestion
import dev.vibex.companion.PermissionPrompt
import dev.vibex.companion.app.MainActivity
import dev.vibex.companion.app.R

enum class VibeXActivityPhase(val shortLabel: String) {
    Thinking("思考中"),
    Exploring("查阅中"),
    Developing("开发中"),
    Verifying("验证中"),
    Summarizing("总结中"),
    WaitingInput("待确认"),
    Completed("已完成"),
    Failed("异常"),
}

data class TaskLiveActivityState(
    val conversationId: String,
    val projectName: String,
    val conversationTitle: String,
    val phase: VibeXActivityPhase,
    val stageTitle: String,
    val stageSubtitle: String,
    val progress: Float,
    val completed: Int,
    val total: Int,
    val stepCount: Int,
    val isRunning: Boolean,
    val startedAtMs: Long,
)

sealed class AttentionToastKind {
    data class Permission(val prompt: PermissionPrompt) : AttentionToastKind()
    data class Question(val question: PendingQuestion) : AttentionToastKind()
    data object Created : AttentionToastKind()
    data object Completed : AttentionToastKind()
    data class Failed(val message: String) : AttentionToastKind()

    val durationSeconds: Float
        get() = when (this) {
            is Permission, is Question -> 7.0f
            is Created -> 4.0f
            is Completed, is Failed -> 4.0f
        }
}

data class CompanionAttentionToast(
    val id: String,
    val conversationId: String,
    val workspaceId: String,
    val agentId: String,
    val projectName: String,
    val title: String,
    val subtitle: String,
    val kind: AttentionToastKind,
    val createdAtMs: Long = System.currentTimeMillis(),
)

data class SystemNotificationAction(
    val label: String,
    val actionKind: String,
    val conversationId: String,
    val targetId: String = "",
    val optionId: String = "",
)

object SystemNotifier {
    private const val CHANNEL_ALERTS = "vibex_alerts_v2"
    private const val CHANNEL_STATUS = "vibex_status"
    private const val CHANNEL_LIVE = "vibex_live_activity"
    private const val NOTIFICATION_ID_LIVE = 2001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "VibeX 即时通知与审批提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "会话完成、执行异常、新会话及权限审批横幅提醒"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val liveChannel = NotificationChannel(
            CHANNEL_LIVE,
            "VibeX 灵动岛与实时任务进度",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "实时同步 AI Agent 思考、代码开发、验证与完成进度"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "VibeX 状态通知",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "电脑连接状态与系统提示"
        }
        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(liveChannel)
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

    fun notifyLiveTaskProgress(
        context: Context,
        state: TaskLiveActivityState,
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_conversation_id", state.conversationId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            ("live:${state.conversationId}").hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val percent = if (state.phase == VibeXActivityPhase.Completed) {
            100
        } else {
            (state.progress * 100f).toInt().coerceIn(8, 99)
        }

        val stepSuffix = when {
            state.total > 0 -> " · 计划 ${state.completed}/${state.total}"
            state.stepCount > 0 -> " · 已推进 ${state.stepCount} 步"
            else -> ""
        }

        val extras = Bundle().apply {
            // Android 16 / Honor MagicOS Magic Capsule / Xiaomi HyperIsland / ColorOS Live Alert hints
            putBoolean("android.substitutableNotification", true)
            putString("android.shortCriticalText", "${state.phase.shortLabel} $percent%")
            putInt("mibundle.notification.focus.progress", percent)
            putString("mibundle.notification.focus.ticker", "${state.stageTitle} $percent%")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setSubText("${state.projectName} · $percent%")
            .setContentTitle("${state.stageTitle} · ${state.conversationTitle}")
            .setContentText("${state.stageSubtitle}$stepSuffix")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("${state.projectName} · ${state.stageTitle} ($percent%)")
                    .bigText("${state.stageSubtitle}\n会话：${state.conversationTitle}$stepSuffix"),
            )
            .setOngoing(state.isRunning)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(state.isRunning)
            .setWhen(state.startedAtMs)
            .setShowWhen(true)
            .setProgress(100, percent, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .addExtras(extras)
            .addAction(
                0,
                "进入会话",
                contentPendingIntent,
            )

        if (!state.isRunning) {
            builder.setAutoCancel(true)
            builder.setTimeoutAfter(25_000L)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_LIVE, builder.build())
        }
    }

    fun cancelLiveTaskProgress(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_LIVE)
        }
    }

    fun notifyAlert(
        context: Context,
        notificationKey: String,
        title: String,
        body: String,
        conversationId: String? = null,
        actions: List<SystemNotificationAction> = emptyList(),
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(25_000L)
            .setContentIntent(pendingIntent)

        actions.take(3).forEachIndexed { idx, act ->
            val actIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("vibex_notif_action", act.actionKind)
                putExtra("vibex_notif_conv_id", act.conversationId)
                putExtra("vibex_notif_target_id", act.targetId)
                putExtra("vibex_notif_option_id", act.optionId)
                putExtra("vibex_notif_id", notificationKey.hashCode())
            }
            val actPending = PendingIntent.getActivity(
                context,
                ("$notificationKey:act:$idx").hashCode(),
                actIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, act.label, actPending)
        }

        if (actions.isEmpty() && !conversationId.isNullOrBlank()) {
            builder.addAction(0, "打开会话", pendingIntent)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationKey.hashCode(), builder.build())
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
