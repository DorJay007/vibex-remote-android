package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.PairingInvitationParser
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun OnboardingScreen(
    state: UiState,
    model: CompanionModel,
    onScan: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }
    val trimmedCode = code.trim()
    val isInvitation = trimmedCode.startsWith("vibex-pairing:") || trimmedCode.startsWith("{")
    val canSubmit = !state.pairing && trimmedCode.isNotEmpty()
    val activeMode = state.preferredDiscoveryMode ?: "lan"
    val isLan = activeMode == "lan"

    LaunchedEffect(activeMode) {
        if (state.discoveredHosts.isEmpty() && !state.discoveringHosts) {
            model.discoverHosts(autoRequestIfSingle = false, modeOverride = activeMode)
        }
    }

    val submit = {
        if (canSubmit) {
            if (isInvitation) {
                model.pairFromRaw(trimmedCode)
            } else {
                model.pairManual("", trimmedCode)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Inline Navigation Bar: 取消 | 配对电脑 | 配对
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.bgElevated)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "取消",
                fontSize = 16.sp,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(enabled = !state.pairing, onClick = onDismiss),
            )
            Text(
                "配对电脑",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(Modifier.align(Alignment.CenterEnd)) {
                if (state.pairing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
                } else {
                    Text(
                        "配对",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSubmit) colors.accent else colors.textTertiary,
                        modifier = Modifier.clickable(enabled = canSubmit, onClick = submit),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.hairline)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.pendingConnectSasCode != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "等待电脑「${state.pendingConnectHostName ?: "VibeX"}」确认连接",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        "请在电脑顶部弹窗点击「连接」",
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                    )
                    Text(
                        "核对码 ${state.pendingConnectSasCode}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlexMono,
                        color = colors.accent,
                    )
                    Text(
                        "取消等待",
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.clickable { model.cancelPendingConnectRequest() },
                    )
                }
            } else {
                // 1. Segmented Mode Switcher (Default: 局域网直连)
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

                // 2. Discovered Online Computers Card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(12.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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

                    if (state.discoveredHosts.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                if (state.discoveringHosts) {
                                    if (isLan) "正在扫描同一局域网内在线的 VibeX 电脑…"
                                    else "正在通过云服务搜索在线电脑…"
                                } else {
                                    if (isLan) "当前 Wi-Fi 未发现电脑"
                                    else "未在云服务发现电脑，请确认电脑端在线"
                                },
                                fontSize = 12.5.sp,
                                color = colors.textSecondary,
                            )
                            if (!state.discoveringHosts && isLan) {
                                Text(
                                    "跨网络或未扫到？点击切换至「云服务 Beta」",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.accent,
                                    modifier = Modifier.clickable { model.selectDiscoveryMode("cloud") },
                                )
                            }
                        }
                    } else {
                        state.discoveredHosts.forEachIndexed { idx, host ->
                            if (idx > 0) {
                                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 14.dp))
                            }
                            val isCloudHost = host.origin.contains("/t/") ||
                                host.origin.startsWith("https://", ignoreCase = true)
                            val cleanHostName = host.name.removeSuffix(".local").ifBlank { "VibeX 桌面端" }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.pairing) { model.requestConnectToHost(host) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.accent.copy(0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Computer, null, tint = colors.accent, modifier = Modifier.size(19.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        cleanHostName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        if (isCloudHost) "VibeX 云服务 · Beta" else host.origin,
                                        fontSize = 12.sp,
                                        fontFamily = if (isCloudHost) null else PlexMono,
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
                                    Text("一键连接", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onAccent)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Combined QR Scan + Connection Code Card
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(0.75.dp, colors.stroke, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.pairing, onClick = onScan)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("扫码配对", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("扫描桌面端二维码自动配对", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (code.isEmpty()) {
                            Text(
                                "输入 8 位连接码或粘贴邀请码",
                                fontSize = 13.5.sp,
                                color = colors.textTertiary,
                                fontFamily = PlexMono,
                            )
                        }
                        BasicTextField(
                            value = code,
                            onValueChange = { code = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontFamily = PlexMono,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (code.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = "清除",
                            tint = colors.textTertiary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { code = "" },
                        )
                    }
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.accent.copy(0.14f))
                            .clickable {
                                val pasted = clipboard.getText()?.text.orEmpty().trim()
                                if (pasted.isNotEmpty()) {
                                    code = pasted
                                    if (pasted.startsWith("vibex-pairing:") || pasted.startsWith("{")) {
                                        model.pairFromRaw(pasted)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                        Text("粘贴", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
                    }
                }
            }

            state.error?.let { err ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = colors.danger, modifier = Modifier.size(16.dp))
                    Text(err, fontSize = 13.sp, color = colors.danger)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
