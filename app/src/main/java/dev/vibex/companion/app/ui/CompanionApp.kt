package dev.vibex.companion.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
    val isPairModal = state.route is Route.ManualPair
    val settingsSub = (state.route as? Route.Settings)?.section != null
    val inCollabChat = state.route is Route.Collaboration && state.selectedThread != null
    var isConversationsChromeHidden by remember { mutableStateOf(false) }
    var isCollabChatOpen by remember { mutableStateOf(false) }
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(enabled = fullscreen || isPairModal || settingsSub || inCollabChat || state.route is Route.Folders) {
        when {
            isPairModal -> model.go(Route.Conversations)
            fullscreen -> model.go(Route.Conversations)
            settingsSub -> model.go(Route.Settings())
            inCollabChat -> model.closeCollaborationThread()
            state.route is Route.Folders -> model.go(Route.Conversations)
        }
    }

    val colors = VibexTheme.colors
    state.pendingCloudFallbackOrigin?.let {
        val hostName = state.pendingCloudFallbackHostName ?: "VibeX 桌面端"
        AlertDialog(
            onDismissRequest = { model.cancelSwitchToCloudFallback() },
            containerColor = colors.card,
            title = {
                Text(
                    "局域网已断开，是否切换至云服务？",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    "当前与电脑「$hostName」的局域网直连已断开，但检测到可通过「VibeX 云服务 · Beta」继续连接。是否切换至云服务？",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { model.confirmSwitchToCloudFallback() }) {
                    Text(
                        "切换至云服务",
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { model.cancelSwitchToCloudFallback() }) {
                    Text(
                        "保持局域网",
                        color = colors.textSecondary,
                    )
                }
            },
        )
    }

    state.pendingConflictDeviceName?.let { existingDeviceName ->
        AlertDialog(
            onDismissRequest = { model.cancelReplaceMobileDevice() },
            containerColor = colors.card,
            text = {
                Text(
                    "这台电脑已连接手机「$existingDeviceName」，要替换吗？",
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                )
            },
            confirmButton = {
                TextButton(onClick = { model.confirmReplaceMobileDevice() }) {
                    Text("替换", fontWeight = FontWeight.Bold, color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { model.cancelReplaceMobileDevice() }) {
                    Text("取消", color = colors.textSecondary)
                }
            },
        )
    }

    // The tab shell keeps showing the page it was on while a conversation or the pairing
    // sheet slides over it, so the page underneath never changes mid-transition.
    val tabRouteHolder = remember { TabRouteHolder() }
    if (!fullscreen && !isPairModal) tabRouteHolder.route = state.route
    val tabRoute = tabRouteHolder.route

    val tabs = listOf(
        LiquidTab(
            "对话",
            Icons.Outlined.ChatBubbleOutline,
            Icons.Rounded.ChatBubble,
            tabRoute is Route.Conversations || tabRoute is Route.Folders,
        ) { model.go(Route.Conversations) },
        LiquidTab(
            "看板",
            Icons.Outlined.GridView,
            Icons.Rounded.GridView,
            tabRoute is Route.Status,
        ) { model.go(Route.Status) },
        LiquidTab(
            "协作空间",
            Icons.Outlined.Groups,
            Icons.Rounded.Groups,
            tabRoute is Route.Collaboration,
        ) { model.go(Route.Collaboration) },
        LiquidTab(
            "设置",
            Icons.Outlined.Settings,
            Icons.Rounded.Settings,
            tabRoute is Route.Settings,
        ) { model.go(Route.Settings()) },
    )

    val hasServer = state.profiles.isNotEmpty()
    val authExpired = state.connection == ConnectionState.AuthRequired
    val manualConnectNeeded = hasServer && !state.autoConnect && state.connection == ConnectionState.Offline
    val showConnectGate = !hasServer || authExpired || manualConnectNeeded

    val activeOpenConvId = (state.route as? Route.Timeline)?.conversationId
    val inlineToast = state.activeAttentionToast?.takeIf { toast ->
        toast.conversationId != activeOpenConvId
    }
    val hostNotices: @Composable () -> Unit = {
        CompanionToastOverlay(
            toast = inlineToast,
            actionBusy = state.attentionActionBusy,
            actionError = state.attentionActionError,
            onDismiss = { model.dismissAttentionToast() },
            onOpenConversation = { convId -> model.openConversation(convId) },
            onRespondPermission = { convId, permId, optId ->
                model.respondToastPermission(convId, permId, optId)
            },
            onRespondQuestion = { convId, question, choice, fallbackText ->
                model.respondToastQuestion(convId, question, choice, fallbackText)
            },
            onSendQuickReply = { toast, text ->
                model.sendToastQuickReply(toast, text)
            },
        )
        state.error?.let { HostBanner(it, hostBannerSeverity(it)) { model.dismissNotice() } }
        state.notice?.let { HostBanner(it, NoticeSeverity.Info) { model.dismissNotice() } }
    }
    val requiresServer: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (showConnectGate) {
            DesktopEmptyState(state = state, model = model, authExpired = authExpired)
        } else {
            content()
        }
    }

    val layer = when (val route = state.route) {
        Route.ManualPair -> ShellLayer.Pairing
        else -> if (state.showsLogin) ShellLayer.Login else when (route) {
            is Route.Timeline -> ShellLayer.Conversation(route.conversationId)
            else -> ShellLayer.Tabs
        }
    }

    state.desktopConnect?.let { sheet ->
        DesktopConnectDialog(
            state = state,
            sheet = sheet,
            model = model,
            onPairWithCode = {
                model.dismissDesktopConnect()
                model.go(Route.ManualPair)
            },
        )
    }

    GlowBackground {
        AnimatedContent(
            targetState = layer,
            transitionSpec = { layerTransition(initialState, targetState) },
            modifier = Modifier.fillMaxSize(),
            label = "shellLayer",
        ) { shownLayer ->
            when (shownLayer) {
                ShellLayer.Login -> Box(Modifier.fillMaxSize()) {
                    LoginScreen(state = state, model = model, onPairWithCode = { model.go(Route.ManualPair) })
                    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding()) {
                        state.error?.let { HostBanner(it, hostBannerSeverity(it)) { model.dismissNotice() } }
                    }
                }
                ShellLayer.Pairing -> PairingCodeScreen(
                    state = state,
                    model = model,
                    onScan = launchScanner,
                    onDismiss = { model.go(Route.Conversations) },
                )
                is ShellLayer.Conversation -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.bg)
                        .statusBarsPadding(),
                ) {
                    TimelinePane(state, model, shownLayer.conversationId)
                    Column(Modifier.align(Alignment.TopCenter)) {
                        hostNotices()
                    }
                }
                ShellLayer.Tabs -> {
                    val pageCoversBottomBar = inCollabChat ||
                        (tabRoute is Route.Collaboration && isCollabChatOpen) ||
                        (tabRoute is Route.Conversations && isConversationsChromeHidden)
                    val showFloatingNavBar = !isKeyboardVisible && !pageCoversBottomBar

                    Box(Modifier.fillMaxSize().background(colors.bg)) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(bottom = if (showFloatingNavBar) 66.dp else 0.dp),
                        ) {
                            AnimatedContent(
                                targetState = tabRoute,
                                transitionSpec = { tabTransition(initialState, targetState) },
                                modifier = Modifier.fillMaxSize(),
                                label = "tabRoute",
                            ) { route ->
                                Box(Modifier.fillMaxSize().background(colors.bg)) {
                                    when (route) {
                                        is Route.Settings -> SettingsScreen(
                                            state = state,
                                            model = model,
                                            section = route.section,
                                            onOpen = { model.go(Route.Settings(it)) },
                                            onBack = { model.go(Route.Settings()) },
                                            onScan = launchScanner,
                                            onManualPair = { model.go(Route.ManualPair) },
                                        )
                                        Route.Folders -> requiresServer { FoldersScreen(state, model) }
                                        Route.Status -> requiresServer { StatusScreen(state, model) }
                                        Route.Collaboration -> requiresServer {
                                            CollaborationScreen(
                                                state = state,
                                                model = model,
                                                onChatOpenChange = { isCollabChatOpen = it },
                                            )
                                        }
                                        else -> requiresServer {
                                            ConversationList(
                                                state = state,
                                                model = model,
                                                onChromeHiddenChange = { isConversationsChromeHidden = it },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Column(
                            Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding(),
                        ) {
                            hostNotices()
                        }
                        if (!isKeyboardVisible) {
                            AnimatedVisibility(
                                visible = !pageCoversBottomBar,
                                enter = NavMotion.bottomBarEnter,
                                exit = NavMotion.bottomBarExit,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                LiquidNavBar(tabs = tabs)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class TabRouteHolder {
    var route: Route = Route.Conversations
}

private sealed interface ShellLayer {
    val elevation: Float

    data object Login : ShellLayer {
        override val elevation = 0f
    }

    data object Tabs : ShellLayer {
        override val elevation = 0f
    }

    data class Conversation(val conversationId: String) : ShellLayer {
        override val elevation = 1f
    }

    data object Pairing : ShellLayer {
        override val elevation = 2f
    }
}

private fun layerTransition(from: ShellLayer, to: ShellLayer): ContentTransform = when {
    to is ShellLayer.Pairing -> NavMotion.present(to.elevation)
    from is ShellLayer.Pairing -> NavMotion.dismiss(to.elevation)
    to.elevation > from.elevation -> NavMotion.push(to.elevation)
    to.elevation < from.elevation -> NavMotion.pop(to.elevation)
    else -> NavMotion.crossfade(to.elevation)
}

private val Route.settingsDepth: Float
    get() = if (this is Route.Settings && section != null) 1f else 0f

private fun tabTransition(from: Route, to: Route): ContentTransform {
    val withinSettings = from is Route.Settings && to is Route.Settings
    return when {
        withinSettings && to.settingsDepth > from.settingsDepth -> NavMotion.push(to.settingsDepth)
        withinSettings && to.settingsDepth < from.settingsDepth -> NavMotion.pop(to.settingsDepth)
        else -> NavMotion.crossfade(to.settingsDepth)
    }
}

@Composable
private fun DesktopEmptyState(state: UiState, model: CompanionModel, authExpired: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = if (authExpired) Icons.Rounded.KeyOff else Icons.Rounded.Computer,
            title = if (authExpired) "连接凭据已失效" else "未连接电脑",
            action = "连接电脑",
            onAction = {
                when {
                    state.accountSignedIn -> model.openDesktopConnect()
                    state.profiles.isNotEmpty() && !authExpired -> model.connectSelected()
                    else -> model.go(Route.ManualPair)
                }
            },
        )
    }
}
