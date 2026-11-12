package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.AccountDesktop
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.DesktopConnectRoute
import dev.vibex.companion.app.DesktopConnectSheet
import dev.vibex.companion.app.DesktopConnectStage
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

/** 「发现你的电脑」: lists the account's desktops and connects one in a single tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopConnectDialog(
    state: UiState,
    sheet: DesktopConnectSheet,
    model: CompanionModel,
    onPairWithCode: () -> Unit,
) {
    val colors = VibexTheme.colors
    val desktops = state.accountDesktops
    val selected = desktops.firstOrNull { it.hostId == sheet.selectedHostId }
    ModalBottomSheet(
        onDismissRequest = { model.dismissDesktopConnect() },
        containerColor = colors.bgElevated,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val stage = sheet.stage
            val noDesktop = stage == DesktopConnectStage.Choosing && desktops.isEmpty()
            if (!noDesktop) {
  Text(
      "发现你的电脑",
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = colors.textPrimary,
  )
     }
            when (stage) {
                DesktopConnectStage.Syncing -> LoadingState("正在同步你的电脑")
                DesktopConnectStage.Choosing -> if (noDesktop) {
                    NoDesktopGuide(onDownload = { model.openAccountSitePath("/download") })
                } else {
                    DesktopList(desktops, selected, onSelect = model::selectAccountDesktop)
                    selected?.let { RouteButtons(state, it, model) }
                }
                is DesktopConnectStage.Connecting -> {
                    selected?.let { DesktopRow(it, isSelected = false, onClick = null) }
                    LoadingState("正在连接")
                }
                is DesktopConnectStage.AwaitingApproval -> ApprovalPrompt(stage.sasCode)
                is DesktopConnectStage.Failed -> FailureState(stage.message, onRetry = model::retryDesktopConnect)
            }
            if (stage == DesktopConnectStage.Choosing || stage is DesktopConnectStage.Failed) {
                Text(
                    "使用连接码配对",
                    fontSize = 13.sp,
                    color = colors.textTertiary,
                    modifier = Modifier
                        .clickable(onClick = onPairWithCode)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DesktopList(desktops: List<AccountDesktop>, selected: AccountDesktop?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        desktops.forEach { desktop ->
            DesktopRow(
                desktop = desktop,
                isSelected = desktops.size > 1 && desktop.hostId == selected?.hostId,
                onClick = if (desktops.size > 1) ({ onSelect(desktop.hostId) }) else null,
            )
        }
    }
}

@Composable
private fun DesktopRow(desktop: AccountDesktop, isSelected: Boolean, onClick: (() -> Unit)?) {
    val colors = VibexTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(0.75.dp, if (isSelected) colors.accent else colors.stroke, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Computer, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Text(
            desktop.hostName,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        desktop.platformBadge?.let {
            Text(
                it,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.segmentedTrack)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (isSelected) {
            Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RouteButtons(state: UiState, desktop: AccountDesktop, model: CompanionModel) {
    val colors = VibexTheme.colors
    val cloudEnabled = state.accountOverview?.relayPermission != false
    val lanEnabled = model.lanOriginFor(desktop) != null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteButton(
                label = "使用云服务",
                container = colors.accent,
                content = colors.onAccent,
                enabled = cloudEnabled,
                modifier = Modifier.weight(1f),
            ) { model.connectAccountDesktop(DesktopConnectRoute.Cloud) }
            RouteButton(
                label = "局域网",
                container = colors.segmentedTrack,
                content = colors.textPrimary,
                enabled = lanEnabled,
                modifier = Modifier.weight(1f),
            ) { model.connectAccountDesktop(DesktopConnectRoute.Lan) }
        }
        if (!cloudEnabled) {
            Text("账号未开通云服务", fontSize = 12.sp, color = colors.textTertiary)
        }
    }
}

@Composable
private fun RouteButton(
    label: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

@Composable
private fun ApprovalPrompt(sasCode: String) {
    val colors = VibexTheme.colors
    Text("请在电脑上点击“连接”确认", fontSize = 14.sp, color = colors.textSecondary)
    Text(
        "核对码 $sasCode",
        style = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlexMono,
            fontFeatureSettings = "tnum",
            color = colors.accent,
        ),
    )
    LoadingState("等待确认")
}

@Composable
private fun FailureState(message: String, onRetry: () -> Unit) {
    val colors = VibexTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.Warning, null, tint = colors.danger, modifier = Modifier.size(18.dp))
        Text(message, fontSize = 14.sp, color = colors.danger, textAlign = TextAlign.Center)
    }
    PrimaryButton(label = "重试", onClick = onRetry)
}

@Composable
private fun NoDesktopGuide(onDownload: () -> Unit) {
    val colors = VibexTheme.colors
    Text(
        "在电脑上安装 VibeX 并登录同一账号",
        fontSize = 14.sp,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
    )
    PrimaryButton(label = "下载 VibeX", onClick = onDownload)
}
