package dev.vibex.companion.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    val fullscreen = state.route is Route.Timeline
    val settingsSub = (state.route as? Route.Settings)?.section != null
    val inCollabChat = state.route is Route.Collaboration && state.selectedThread != null
    BackHandler(enabled = fullscreen || settingsSub || inCollabChat || state.route is Route.Folders) {
        when {
            fullscreen -> model.go(Route.Conversations)
            settingsSub -> model.go(Route.Settings())
            inCollabChat -> model.closeCollaborationThread()
            state.route is Route.Folders -> model.go(Route.Conversations)
        }
    }
    if (state.route is Route.Onboarding || state.route is Route.ManualPair) {
        GlowBackground {
            OnboardingScreen(state, model) {
                scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("将连接二维码放入框内"))
            }
        }
        return
    }
    val tabs = listOf(
        LiquidTab(
            "对话",
            Icons.Outlined.Forum,
            Icons.Rounded.Forum,
            state.route is Route.Conversations || state.route is Route.Timeline || state.route is Route.Folders,
        ) { model.go(Route.Conversations) },
        LiquidTab(
            "看板",
            Icons.Outlined.SpaceDashboard,
            Icons.Rounded.SpaceDashboard,
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
    GlowBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = if (fullscreen) 0.dp else 86.dp),
            ) {
                state.error?.let { HostBanner(it, hostBannerSeverity(it)) { model.dismissNotice() } }
                state.notice?.let { HostBanner(it, NoticeSeverity.Info) { model.dismissNotice() } }
                when (val route = state.route) {
                    Route.Folders -> FoldersScreen(state, model)
                    Route.Conversations -> ConversationList(state, model)
                    Route.Status -> StatusScreen(state, model)
                    Route.Collaboration -> CollaborationScreen(state, model)
                    is Route.Settings -> SettingsScreen(
                        state,
                        model,
                        section = route.section,
                        onOpen = { model.go(Route.Settings(it)) },
                        onBack = { model.go(Route.Settings()) },
                        onScan = {
                            scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE))
                        },
                    )
                    is Route.Timeline -> TimelinePane(state, model, route.conversationId)
                    else -> ConversationList(state, model)
                }
            }
            if (!fullscreen) {
                LiquidNavBar(
                    tabs = tabs,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
