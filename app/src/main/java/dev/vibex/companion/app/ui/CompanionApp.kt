package dev.vibex.companion.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import dev.vibex.companion.app.ui.theme.PlexMono
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
        val thisDeviceName = Build.MODEL.ifBlank { "本机" }
        AlertDialog(
            onDismissRequest = { model.cancelReplaceMobileDevice() },
            containerColor = colors.card,
            title = {
                Text(
                    "选择要远程连接的手机",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    "同一台电脑仅限一台手机远程连接。当前电脑已连接「$existingDeviceName」，若切换为「$thisDeviceName」（本机）连接，「$existingDeviceName」将被强制下线并清除凭据，下次需重新连接。",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { model.confirmReplaceMobileDevice() }) {
                    Text(
                        "使用「$thisDeviceName」（本机）",
                        fontWeight = FontWeight.Bold,
                        color = colors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { model.cancelReplaceMobileDevice() }) {
                    Text(
                        "保留「$existingDeviceName」",
                        color = colors.textSecondary,
                    )
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
            RequiresServerView(
                state = state,
                model = model,
                authExpired = authExpired,
                onScan = launchScanner,
                onManualPair = { model.go(Route.ManualPair) },
            )
        } else {
            content()
        }
    }

    val layer = when (val route = state.route) {
        Route.Onboarding, Route.ManualPair -> ShellLayer.Pairing
        is Route.Timeline -> ShellLayer.Conversation(route.conversationId)
        else -> ShellLayer.Tabs
    }

    GlowBackground {
        AnimatedContent(
            targetState = layer,
            transitionSpec = { layerTransition(initialState, targetState) },
            modifier = Modifier.fillMaxSize(),
            label = "shellLayer",
        ) { shownLayer ->
            when (shownLayer) {
                ShellLayer.Pairing -> OnboardingScreen(
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
fun RequiresServerView(
    state: UiState,
    model: CompanionModel,
    authExpired: Boolean,
    onScan: () -> Unit,
    onManualPair: () -> Unit,
) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current
    var quickCode by remember { mutableStateOf("") }
    val activeMode = state.preferredDiscoveryMode ?: "lan"
    val isLan = activeMode == "lan"

    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (authExpired) Icons.Rounded.KeyOff else Icons.Rounded.Computer,
                contentDescription = null,
                tint = if (state.pendingConnectSasCode != null) colors.accent else colors.textTertiary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                when {
                    state.pendingConnectSasCode != null ->
                        "等待电脑「${state.pendingConnectHostName ?: "VibeX"}」确认"
                    authExpired -> "连接凭据已失效"
                    else -> "连接电脑"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            if (state.pendingConnectSasCode != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "请在电脑端顶部提示条点击「连接」",
                        fontSize = 13.5.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "核对码 ${state.pendingConnectSasCode}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                    state.pendingConnectOrigin?.takeIf {
                        !it.startsWith("https://", ignoreCase = true) && !it.contains("/t/")
                    }?.let {
                        Text(it, fontSize = 11.5.sp, color = colors.textTertiary)
                    }
                    Text(
                        "取消等待",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { model.cancelPendingConnectRequest() },
                    )
                }
            } else {
                // 1. Mode Switcher (Default: 局域网直连, never auto-cloud)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(11.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLan) colors.accent else colors.card)
                            .clickable { model.selectDiscoveryMode("lan") }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "局域网直连",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLan) colors.onAccent else colors.textSecondary,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isLan) colors.accent else colors.card)
                            .clickable { model.selectDiscoveryMode("cloud") }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "云服务 Beta",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isLan) colors.onAccent else colors.textSecondary,
                        )
                    }
                }

                // 2. Discovered Computers Card with inline Refresh
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp)),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isLan) "同一 Wi-Fi 在线电脑" else "云服务在线电脑 · Beta",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            Modifier.clickable(enabled = !state.discoveringHosts) {
                                model.discoverHosts(autoRequestIfSingle = false)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                            Text(
                                if (state.discoveringHosts) "搜索中…" else "刷新",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent,
                            )
                        }
                    }
                    HorizontalDivider(color = colors.hairline)

                    if (state.discoveredHosts.isNotEmpty()) {
                        state.discoveredHosts.forEachIndexed { idx, host ->
                            if (idx > 0) {
                                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 14.dp))
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { model.requestConnectToHost(host) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val isCloudHost = host.origin.contains("/t/") ||
                                    host.origin.startsWith("https://", ignoreCase = true)
                                Icon(Icons.Rounded.Computer, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        host.name.removeSuffix(".local").ifBlank { "VibeX 桌面端" },
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        if (isCloudHost) "VibeX 云服务 · Beta" else host.origin,
                                        fontSize = 11.5.sp,
                                        color = if (isCloudHost) colors.accent else colors.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accent)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("一键连接", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (state.discoveringHosts) {
                                    if (isLan) "正在扫描同一 Wi-Fi 下的电脑…" else "正在检测本机已配对的云端电脑…"
                                } else {
                                    if (isLan) "当前 Wi-Fi 未发现电脑" else "云服务仅展示本机已配对过的电脑"
                                },
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                            )
                            if (!state.discoveringHosts) {
                                if (isLan) {
                                    Text(
                                        "跨网络或未扫到？点击切换至「云服务 Beta」",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.accent,
                                        modifier = Modifier.clickable { model.selectDiscoveryMode("cloud") },
                                    )
                                } else {
                                    Text(
                                        "首次跨网配对请使用下方「扫码配对」或输入 8 位连接码",
                                        fontSize = 11.5.sp,
                                        color = colors.textTertiary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Inline Connection Code + Scan QR (No jumping to a duplicate second page!)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.bg)
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                        ) {
                            if (quickCode.isEmpty()) {
                                Text(
                                    "输入 8 位连接码或粘贴邀请",
                                    fontSize = 13.sp,
                                    color = colors.textTertiary,
                                )
                            }
                            BasicTextField(
                                value = quickCode,
                                onValueChange = { quickCode = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontFamily = PlexMono,
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (quickCode.isBlank()) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.1f))
                                    .clickable {
                                        val pasted = clipboard.getText()?.text?.trim().orEmpty()
                                        if (pasted.isNotEmpty()) {
                                            quickCode = pasted
                                            if (pasted.startsWith("vibex-pairing:") || pasted.startsWith("{")) {
                                                model.pairFromRaw(pasted)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.ContentPaste, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                                Text("粘贴", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
                            }
                        } else {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent)
                                    .clickable(enabled = !state.pairing) {
                                        val trimmed = quickCode.trim()
                                        if (trimmed.startsWith("vibex-pairing:") || trimmed.startsWith("{")) {
                                            model.pairFromRaw(trimmed)
                                        } else {
                                            model.pairManual("", trimmed)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text("连接", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.accent)
                            .clickable(onClick = onScan)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (authExpired) "扫码重新配对" else "扫码配对",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onAccent,
                        )
                    }
                }

                // 4. Auto-connect toggle & quick connect for saved computer
                if (state.profiles.isNotEmpty() && !state.autoConnect && state.connection == ConnectionState.Offline) {
                    val saved = state.profiles.find { it.hostId == state.selectedHostId } ?: state.profiles.first()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.card)
                            .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                            .clickable { model.connectSelected() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Computer, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(saved.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text("已配对电脑 · 自动连接已关闭", fontSize = 11.5.sp, color = colors.textSecondary)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accent)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("立即连接", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "自动连接已配对电脑",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.autoConnect,
                        onCheckedChange = { model.setAutoConnect(it) },
                    )
                }
            }
        }
    }
}
