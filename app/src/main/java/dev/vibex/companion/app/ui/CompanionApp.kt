package dev.vibex.companion.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.NoticeSeverity
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun CompanionApp(state: UiState, model: CompanionModel) {
    SplashGate { CompanionShell(state, model) }
}

@Composable
private fun CompanionShell(state: UiState, model: CompanionModel) {
    val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(model::pairFromRaw)
    }
    val launchScanner = {
        scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("对准桌面端二维码即可自动识别连接"))
    }
    val fullscreen = state.route is Route.Timeline
    val isPairModal = state.route is Route.Onboarding || state.route is Route.ManualPair
    val settingsSub = (state.route as? Route.Settings)?.section != null
    val inCollabChat = state.route is Route.Collaboration && state.selectedThread != null

    BackHandler(enabled = fullscreen || isPairModal || settingsSub || inCollabChat || state.route is Route.Folders) {
        when {
            isPairModal -> model.go(Route.Conversations)
            fullscreen -> model.go(Route.Conversations)
            settingsSub -> model.go(Route.Settings())
            inCollabChat -> model.closeCollaborationThread()
            state.route is Route.Folders -> model.go(Route.Conversations)
        }
    }

    if (isPairModal) {
        GlowBackground {
            OnboardingScreen(
                state = state,
                model = model,
                onScan = launchScanner,
                onDismiss = { model.go(Route.Conversations) },
            )
        }
        return
    }

    val tabs = listOf(
        LiquidTab(
            "对话",
            Icons.Outlined.ChatBubbleOutline,
            Icons.Rounded.ChatBubble,
            state.route is Route.Conversations || state.route is Route.Timeline || state.route is Route.Folders,
        ) { model.go(Route.Conversations) },
        LiquidTab(
            "看板",
            Icons.Outlined.GridView,
            Icons.Rounded.GridView,
            state.route is Route.Status,
        ) { model.go(Route.Status) },
        LiquidTab(
            "协作空间",
            Icons.Outlined.Groups,
            Icons.Rounded.Groups,
            state.route is Route.Collaboration,
        ) { model.go(Route.Collaboration) },
        LiquidTab(
            "设置",
            Icons.Outlined.Settings,
            Icons.Rounded.Settings,
            state.route is Route.Settings,
        ) { model.go(Route.Settings()) },
    )

    val hasServer = state.profiles.isNotEmpty()
    val authExpired = state.connection == ConnectionState.AuthRequired

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                state.error?.let { HostBanner(it, hostBannerSeverity(it)) { model.dismissNotice() } }
                state.notice?.let { HostBanner(it, NoticeSeverity.Info) { model.dismissNotice() } }
                when (val route = state.route) {
                    is Route.Settings -> SettingsScreen(
                        state = state,
                        model = model,
                        section = route.section,
                        onOpen = { model.go(Route.Settings(it)) },
                        onBack = { model.go(Route.Settings()) },
                        onScan = launchScanner,
                        onManualPair = { model.go(Route.ManualPair) },
                    )
                    Route.Folders -> if (!hasServer || authExpired) {
                        RequiresServerView(
                            authExpired = authExpired,
                            onScan = launchScanner,
                            onManualPair = { model.go(Route.ManualPair) },
                        )
                    } else {
                        FoldersScreen(state, model)
                    }
                    Route.Status -> if (!hasServer || authExpired) {
                        RequiresServerView(
                            authExpired = authExpired,
                            onScan = launchScanner,
                            onManualPair = { model.go(Route.ManualPair) },
                        )
                    } else {
                        StatusScreen(state, model)
                    }
                    Route.Collaboration -> if (!hasServer || authExpired) {
                        RequiresServerView(
                            authExpired = authExpired,
                            onScan = launchScanner,
                            onManualPair = { model.go(Route.ManualPair) },
                        )
                    } else {
                        CollaborationScreen(state, model)
                    }
                    is Route.Timeline -> TimelinePane(state, model, route.conversationId)
                    else -> if (!hasServer || authExpired) {
                        RequiresServerView(
                            authExpired = authExpired,
                            onScan = launchScanner,
                            onManualPair = { model.go(Route.ManualPair) },
                        )
                    } else {
                        ConversationList(state, model)
                    }
                }
            }
            if (!fullscreen && !inCollabChat) {
                LiquidNavBar(tabs = tabs)
            }
        }
    }
}

@Composable
fun RequiresServerView(
    authExpired: Boolean,
    onScan: () -> Unit,
    onManualPair: () -> Unit,
) {
    val colors = VibexTheme.colors
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                if (authExpired) Icons.Rounded.KeyOff else Icons.Rounded.Computer,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                if (authExpired) "连接凭据已失效" else "尚未连接电脑",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                if (authExpired) "桌面端配对已更新或过期，请重新扫码或使用连接码配对。"
                else "打开桌面端 VibeX → 设置 → 远程连接，扫码或粘贴连接码即可连接工作站。",
                fontSize = 13.5.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent)
                        .clickable(onClick = onScan)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                    Text(
                        if (authExpired) "扫码重新配对" else "扫码配对",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onAccent,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgElevated)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(10.dp))
                        .clickable(onClick = onManualPair)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "连接码配对",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent,
                    )
                }
            }
        }
    }
}
