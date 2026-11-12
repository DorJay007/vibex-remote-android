package dev.vibex.companion.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import dev.vibex.companion.app.monitor.CompanionMonitorService
import dev.vibex.companion.app.monitor.SystemIslandOverlayController
import dev.vibex.companion.app.monitor.SystemNotifier
import dev.vibex.companion.app.ui.CompanionApp
import dev.vibex.companion.app.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val model: CompanionModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SystemNotifier.ensureChannels(this)
        CompanionMonitorService.start(this)
        requestNotificationPermissionIfNeeded()
        handleIntentExtras(intent)
        setContent {
            val state by model.state.collectAsState()
            CompanionTheme(appearance = state.appearance, accent = state.accent) {
                CompanionApp(state = state, model = model)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SystemIslandOverlayController.setAppForeground(this, true)
        model.onAppForegroundChanged(true)
    }

    override fun onStop() {
        super.onStop()
        model.onAppForegroundChanged(false)
        SystemIslandOverlayController.setAppForeground(this, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIntentExtras(intent: Intent?) {
        val notifAction = intent?.getStringExtra("vibex_notif_action")?.takeIf { it.isNotBlank() }
        if (notifAction != null) {
            val convId = intent.getStringExtra("vibex_notif_conv_id").orEmpty()
            val targetId = intent.getStringExtra("vibex_notif_target_id").orEmpty()
            val optionId = intent.getStringExtra("vibex_notif_option_id").orEmpty()
            val notifId = intent.getIntExtra("vibex_notif_id", 0)
            intent.removeExtra("vibex_notif_action")
            intent.removeExtra("vibex_notif_conv_id")
            intent.removeExtra("vibex_notif_target_id")
            intent.removeExtra("vibex_notif_option_id")
            intent.removeExtra("vibex_notif_id")
            if (notifId != 0) {
                runCatching {
                    androidx.core.app.NotificationManagerCompat.from(this).cancel(notifId)
                }
            }
            model.handleSystemNotificationAction(
                actionKind = notifAction,
                conversationId = convId,
                targetId = targetId,
                optionId = optionId,
            )
            return
        }
        val openConvId = intent?.getStringExtra("open_conversation_id")?.takeIf { it.isNotBlank() }
        if (openConvId != null) {
            intent.removeExtra("open_conversation_id")
            model.openConversation(openConvId)
            return
        }
        val raw = intent?.getStringExtra("pairing_invitation")?.takeIf { it.isNotBlank() }
            ?: intent?.dataString?.takeIf { it.startsWith("vibex-pairing:", ignoreCase = true) }
            ?: return
        intent?.removeExtra("pairing_invitation")
        model.pairFromRaw(raw)
    }
}
