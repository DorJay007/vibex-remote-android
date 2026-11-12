package dev.vibex.companion.app.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.vibex.companion.PendingQuestion
import dev.vibex.companion.QuestionChoice
import dev.vibex.companion.app.MainActivity
import dev.vibex.companion.app.R
import dev.vibex.companion.app.ui.CompanionToastOverlay
import dev.vibex.companion.app.ui.DynamicIslandOverlay
import dev.vibex.companion.app.ui.theme.Appearance
import dev.vibex.companion.app.ui.theme.AccentChoice
import dev.vibex.companion.app.ui.theme.CompanionTheme
import kotlinx.coroutines.flow.MutableStateFlow

class CompanionMonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "VibeX 后台实时同步", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "保持后台连接以驱动系统灵动岛与即时通知"
                    setShowBadge(false)
                },
            )
        }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("VibeX 后台守护中")
            .setContentText("正在实时同步 AI 任务进度与通知")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        SystemIslandOverlayController.detach()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "vibex-monitor"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, CompanionMonitorService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, CompanionMonitorService::class.java))
            }
        }
    }
}

private class OverlayLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

/**
 * System-level Dynamic Island & Instant Notification overlay controller.
 * Strictly renders ONLY when VibeX is in the background (`!isAppInForeground`),
 * using Android's system `WindowManager` (`TYPE_APPLICATION_OVERLAY`) so it floats
 * at the top of the system home screen / other apps just like iOS Dynamic Island.
 */
object SystemIslandOverlayController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isForegroundFlow = MutableStateFlow(true)
    private val liveActivityFlow = MutableStateFlow<TaskLiveActivityState?>(null)
    private val toastFlow = MutableStateFlow<CompanionAttentionToast?>(null)
    private val actionBusyFlow = MutableStateFlow(false)
    private val actionErrorFlow = MutableStateFlow<String?>(null)

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var appContext: Context? = null

    var onRespondPermission: ((String, String, String) -> Unit)? = null
    var onRespondQuestion: ((String, PendingQuestion, QuestionChoice?, String?) -> Unit)? = null
    var onSendQuickReply: ((CompanionAttentionToast, String) -> Unit)? = null
    var onDismissToast: (() -> Unit)? = null
    var onDismissIsland: (() -> Unit)? = null

    fun setAppForeground(context: Context, inForeground: Boolean) {
        appContext = context.applicationContext
        isForegroundFlow.value = inForeground
        mainHandler.post { syncWindow() }
    }

    fun updateState(
        context: Context,
        liveActivity: TaskLiveActivityState?,
        toast: CompanionAttentionToast?,
        busy: Boolean = false,
        error: String? = null,
    ) {
        appContext = context.applicationContext
        liveActivityFlow.value = liveActivity
        toastFlow.value = toast
        actionBusyFlow.value = busy
        actionErrorFlow.value = error
        mainHandler.post { syncWindow() }
    }

    fun detach() {
        mainHandler.post { removeWindowImmediate() }
    }

    private fun syncWindow() {
        val ctx = appContext ?: return
        val inForeground = isForegroundFlow.value
        val hasContent = liveActivityFlow.value != null || toastFlow.value != null

        // NEVER show system Dynamic Island inside the app itself — only when exited to background!
        if (inForeground || !hasContent || !Settings.canDrawOverlays(ctx)) {
            removeWindowImmediate()
            return
        }

        if (composeView != null) return

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        lifecycleOwner = owner

        val view = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val live by liveActivityFlow.collectAsState()
                val toast by toastFlow.collectAsState()
                val busy by actionBusyFlow.collectAsState()
                val err by actionErrorFlow.collectAsState()

                CompanionTheme(appearance = Appearance.Dark, accent = AccentChoice.Blue) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (toast != null) {
                            CompanionToastOverlay(
                                toast = toast,
                                actionBusy = busy,
                                actionError = err,
                                onDismiss = { onDismissToast?.invoke() },
                                onOpenConversation = { convId ->
                                    onDismissToast?.invoke()
                                    launchConversationFromBackground(ctx, convId)
                                },
                                onRespondPermission = { convId, permId, optId ->
                                    onRespondPermission?.invoke(convId, permId, optId)
                                },
                                onRespondQuestion = { convId, question, choice, fallbackText ->
                                    onRespondQuestion?.invoke(convId, question, choice, fallbackText)
                                },
                                onSendQuickReply = { t, text ->
                                    onSendQuickReply?.invoke(t, text)
                                },
                            )
                        } else if (live != null) {
                            DynamicIslandOverlay(
                                activity = live,
                                onOpenConversation = { convId ->
                                    launchConversationFromBackground(ctx, convId)
                                },
                                onDismiss = { onDismissIsland?.invoke() },
                            )
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 12
        }

        runCatching {
            wm.addView(view, params)
            composeView = view
        }.onFailure {
            owner.onDestroy()
            lifecycleOwner = null
            composeView = null
        }
    }

    private fun launchConversationFromBackground(context: Context, conversationId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId.isNotBlank()) {
                putExtra("open_conversation_id", conversationId)
            }
        }
        runCatching { context.startActivity(intent) }
    }

    private fun removeWindowImmediate() {
        val view = composeView
        val wm = windowManager
        composeView = null
        if (view != null && wm != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}
