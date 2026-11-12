package dev.vibex.companion.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.ExtensionCatalogItem
import dev.vibex.companion.SubagentRoleItem
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.SettingsSection
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.data.CollapseDisplay
import dev.vibex.companion.app.data.HostProfile
import dev.vibex.companion.app.data.MessageStreamDisplay
import dev.vibex.companion.app.data.ThinkingDisplay
import dev.vibex.companion.app.data.VibeXAccountRepository
import dev.vibex.companion.app.monitor.CompanionMonitorService
import dev.vibex.companion.app.ui.theme.Appearance
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun SettingsScreen(
    state: UiState,
    model: CompanionModel,
    section: SettingsSection?,
    onOpen: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onManualPair: () -> Unit = {},
) {
    when (section) {
        null -> SettingsIndex(state, model, onOpen)
        SettingsSection.Profile -> SettingsPage("个人资料", onBack) { ProfileSettings(state) }
        SettingsSection.Account -> SettingsPage("VibeX 账号", onBack) { AccountSettings(state, model, onOpen) }
        SettingsSection.Connection -> SettingsPage("已配对的电脑", onBack) { ConnectionSettings(state, model, onScan, onManualPair) }
        SettingsSection.Extensions -> SettingsPage("技能与扩展", onBack) { ExtensionsSettings(state, model) }
        SettingsSection.Font -> SettingsPage("字体", onBack) { FontSettings(state, model) }
        SettingsSection.Notification -> SettingsPage("通知", onBack) { NotificationSettings(state, model) }
        SettingsSection.About -> SettingsPage("关于 VibeX", onBack) { AboutSettings(state) }
    }
}

@Composable
private fun SettingsIndex(
    state: UiState,
    model: CompanionModel,
    onOpen: (SettingsSection) -> Unit,
) {
    val colors = VibexTheme.colors
    val selected = state.profiles.find { it.hostId == state.selectedHostId } ?: state.profiles.firstOrNull()
    val isOnline = state.connection == ConnectionState.Online
    val versionStr = state.hostVersion.takeIf { it.isNotBlank() }?.let { "v$it" } ?: "v0.4.5"

    LazyColumn(
        Modifier.fillMaxSize().background(colors.bg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }

        // 1. VibeX Account Card (Single top Personal Center)
        item {
            IosSettingsCard {
                val accountUser = state.accountOverview?.user
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(SettingsSection.Account) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BotAvatarBadge(color = Color(0xFF007AFF), size = 46.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (state.accountSignedIn && accountUser != null) accountUser.resolvedDisplayName else "VibeX 账号",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            if (state.accountSignedIn) {
                                val bal = VibeXAccountRepository.formatCredits(state.accountOverview?.balanceMicro ?: 0L)
                                Text(
                                    "$$bal",
                                    fontSize = 11.5.sp,
                                    fontFamily = PlexMono,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF007AFF),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF007AFF).copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            } else {
                                Text(
                                    "未登录",
                                    fontSize = 12.sp,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                        Text(
                            if (state.accountSignedIn && accountUser != null && accountUser.email.isNotBlank()) {
                                "${accountUser.email} · 个人中心与云端同步"
                            } else {
                                "登录以跨端同步电脑、查看订阅与模型额度"
                            },
                            fontSize = 12.5.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 2. Paired Computers & Skills/Extensions Card
        item {
            IosSettingsCard {
                val activeUrl = state.activeOrigin ?: selected?.lastSuccessOrigin ?: ""
                val isCloudActive = activeUrl.contains("/t/") ||
                    activeUrl.startsWith("https://", ignoreCase = true)
                val hostSub = if (selected != null) {
                    val hostAddr = activeUrl
                        .removePrefix("http://")
                        .removePrefix("https://")
                        .substringBefore("/")
                        .substringBefore(":")
                        .ifBlank { "127.0.0.1" }
                    if (isCloudActive) {
                        "VibeX 云服务 · Beta"
                    } else {
                        "局域网直连 · $hostAddr"
                    }
                } else {
                    "扫码或输入连接码"
                }
                IosSettingsRow(
                    icon = Icons.Rounded.Computer,
                    tint = Color(0xFF007AFF),
                    title = "已配对的电脑",
                    subtitle = hostSub,
                    trailing = {
                        val badgeColor = when (state.connection) {
                            ConnectionState.Online -> Color(0xFF34C759)
                            ConnectionState.Connecting, ConnectionState.Recovering -> Color(0xFFFF9500)
                            ConnectionState.AuthRequired, ConnectionState.Incompatible -> colors.danger
                            else -> colors.textTertiary
                        }
                        val badgeText = when {
                            selected == null -> "未连接"
                            state.connection == ConnectionState.Online -> {
                                val rtt = state.realtimeRttMs?.let { " · ${it}ms" }.orEmpty()
                                "已连接$rtt"
                            }
                            else -> connectionLabel(state.connection)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor),
                            )
                            Text(
                                badgeText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isOnline) Color(0xFF34C759) else colors.textSecondary,
                            )
                        }
                    },
                    onClick = { onOpen(SettingsSection.Connection) },
                )
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 56.dp))
                IosSettingsRow(
                    icon = Icons.Rounded.AutoAwesome,
                    tint = Color(0xFFAF52DE),
                    title = "技能与扩展",
                    subtitle = "技能 · MCP · 插件",
                    onClick = { onOpen(SettingsSection.Extensions) },
                )
            }
        }

        // 3. Theme, Font, Notifications Card (1:1 with iOS screenshot)
        item {
            IosSettingsCard {
                // Inline Theme Segmented Row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColoredIconBadge(Icons.Rounded.Contrast, Color(0xFF5856D6))
                    Text(
                        "主题",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.segmentedTrack)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        listOf(
                            Appearance.System to "跟随系统",
                            Appearance.Light to "浅色",
                            Appearance.Dark to "深色",
                        ).forEach { (mode, label) ->
                            val active = state.appearance == mode
                            Text(
                                label,
                                fontSize = 12.5.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) colors.textPrimary else colors.textSecondary,
                                modifier = Modifier
                                    .then(
                                        if (active) Modifier.shadow(2.dp, RoundedCornerShape(7.dp), ambientColor = Color.Black.copy(0.08f))
                                        else Modifier,
                                    )
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (active) colors.segmentedThumb else Color.Transparent)
                                    .clickable { model.setAppearance(mode) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 56.dp))
                // Font Row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(SettingsSection.Font) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF9500).copy(0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("AA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("字体", fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                        Text("字号大小与字体样式", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Text("系统默认 · 标准", fontSize = 13.sp, color = colors.textSecondary)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 56.dp))
                // Notification Row
                IosSettingsRow(
                    icon = Icons.Rounded.NotificationsActive,
                    tint = Color(0xFFFF3B30),
                    title = "通知",
                    subtitle = "任务完成、审批与协作消息提醒",
                    trailing = {
                        Text(
                            if (state.monitor) "持续监控已开启" else "系统通知已开启",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                    },
                    onClick = { onOpen(SettingsSection.Notification) },
                )
            }
        }

        // 4. About & Version Card (1:1 with iOS screenshot)
        item {
            IosSettingsCard {
                IosSettingsRow(
                    icon = Icons.Rounded.Info,
                    tint = Color(0xFF30B0C7),
                    title = "关于 VibeX",
                    trailing = {
                        Text("本地优先 AI 研发平台", fontSize = 13.sp, color = colors.textSecondary)
                    },
                    onClick = { onOpen(SettingsSection.About) },
                )
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 56.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColoredIconBadge(Icons.Rounded.Verified, Color(0xFF34C759))
                    Text(
                        "当前版本",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$versionStr (协议 v${state.hostProtocol.ifBlank { "1.0" }})",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun IosSettingsCard(content: @Composable () -> Unit) {
    val colors = VibexTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card)
            .border(0.6.dp, colors.stroke, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

@Composable
private fun ColoredIconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun BotAvatarBadge(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.5.dp).height(9.dp).clip(CircleShape).background(Color.White))
            Box(Modifier.width(4.5.dp).height(9.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun IosSettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ColoredIconBadge(icon, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val colors = VibexTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(colors.bg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .border(0.5.dp, colors.stroke, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 48.dp),
                )
            }
        }
        content()
    }
}

// MARK: - 1. 个人资料详情页
private fun LazyListScope.ProfileSettings(state: UiState) {
    item {
        val colors = VibexTheme.colors
        var userName by rememberSaveable { mutableStateOf("LTF") }
        var userHandle by rememberSaveable { mutableStateOf("@ltf") }
        var userRole by rememberSaveable { mutableStateOf("VibeX Creator") }
        var userBio by rememberSaveable { mutableStateOf("本地优先 AI 协同工作流与多智能体架构") }
        var avatarColorIdx by rememberSaveable { mutableIntStateOf(0) }
        val palette = listOf(
            Color(0xFF007AFF),
            Color(0xFF5856D6),
            Color(0xFFAF52DE),
            Color(0xFFFF2D55),
            Color(0xFFFF9500),
            Color(0xFF34C759),
            Color(0xFF30B0C7),
            Color(0xFF1C1C1E),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosSettingsCard {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BotAvatarBadge(color = palette[avatarColorIdx % palette.size], size = 72.dp)
                    Text(userName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("$userHandle · $userRole", fontSize = 13.sp, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        palette.forEachIndexed { idx, swatch ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(
                                        if (avatarColorIdx == idx) Modifier.border(2.dp, colors.textPrimary, CircleShape)
                                        else Modifier,
                                    )
                                    .clickable { avatarColorIdx = idx },
                            )
                        }
                    }
                }
            }
            GroupLabel("基本信息")
            IosSettingsCard {
                ProfileFieldRow("昵称", userName) { userName = it }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 16.dp))
                ProfileFieldRow("标识 (Handle)", userHandle) { userHandle = it }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 16.dp))
                ProfileFieldRow("头衔 / 角色", userRole) { userRole = it }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 16.dp))
                ProfileFieldRow("个人简介", userBio) { userBio = it }
            }
        }
    }
}

@Composable
private fun ProfileFieldRow(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = VibexTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.5.sp, color = colors.textPrimary, modifier = Modifier.width(108.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textSecondary, fontSize = 14.5.sp, textAlign = TextAlign.End),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.weight(1f),
        )
    }
}

// MARK: - 2. 已配对的电脑 (1:1 with media_1794109013958_65476c6e.jpg)
private fun LazyListScope.ConnectionSettings(
    state: UiState,
    model: CompanionModel,
    onScan: () -> Unit,
    onManualPair: () -> Unit,
) {
    item {
        val colors = VibexTheme.colors
        var detailProfile by remember { mutableStateOf<HostProfile?>(null) }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GroupLabel("连接偏好")
            IosSettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("自动连接已配对电脑", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                        Text("打开应用或断开后自动重连已配对电脑", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Switch(
                        checked = state.autoConnect,
                        onCheckedChange = { model.setAutoConnect(it) },
                    )
                }
            }

            GroupLabel("新增连接")
            IosSettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManualPair)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent.copy(0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("配对新的电脑", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        Text(
                            "扫码或输入连接码",
                            fontSize = 12.5.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            GroupLabel("历史配对的电脑 (${state.profiles.size})")
            if (state.profiles.isEmpty()) {
                IosSettingsCard {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Computer, null, tint = colors.textTertiary, modifier = Modifier.size(34.dp))
                        Text("尚未配对电脑", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                    }
                }
            } else {
                IosSettingsCard {
                    state.profiles.forEachIndexed { index, profile ->
                        if (index > 0) HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 68.dp))
                        val isActive = profile.hostId == state.selectedHostId
                        val origin = if (isActive) (state.activeOrigin ?: profile.lastSuccessOrigin) else profile.lastSuccessOrigin
                        val isCloudOrigin = (origin ?: "").contains("/t/") ||
                            (origin ?: "").startsWith("https://", ignoreCase = true)
                        val hostAddr = (origin ?: "")
                            .removePrefix("http://")
                            .removePrefix("https://")
                            .substringBefore("/")
                            .substringBefore(":")
                            .ifBlank { "127.0.0.1" }
                        val displayName = if (isCloudOrigin) {
                            "VibeX 桌面端"
                        } else if (profile.name.startsWith("VibeX (")) {
                            profile.name
                        } else {
                            "VibeX ($hostAddr)"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isActive) model.selectHost(profile.hostId)
                                    else detailProfile = profile
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) Color(0xFF34C759).copy(0.14f) else colors.bg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.LaptopMac,
                                    null,
                                    tint = if (isActive) Color(0xFF34C759) else colors.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        displayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        if (isCloudOrigin) "云服务 Beta" else "局域网",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCloudOrigin) colors.accent else colors.textSecondary,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isCloudOrigin) colors.accent.copy(0.12f) else colors.segmentedTrack)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                if (!isCloudOrigin && !origin.isNullOrBlank()) {
                                    Text(
                                        origin,
                                        fontSize = 12.sp,
                                        fontFamily = PlexMono,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isActive) {
                                    val telemetryLine = if (state.connection == ConnectionState.Online) {
                                        val rttText = state.realtimeRttMs?.let { "${it} ms" } ?: "测量中"
                                        val e2eeSuffix = if (!origin.isNullOrBlank() && dev.vibex.companion.E2eeSessionManager.isE2eeActive(origin)) " · 端到端加密" else ""
                                        "在线 · 实时延迟 $rttText$e2eeSuffix"
                                    } else {
                                        val reason = state.connectionFailureReason ?: connectionLabel(state.connection)
                                        "状态: $reason"
                                    }
                                    Text(
                                        telemetryLine,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (state.connection == ConnectionState.Online) Color(0xFF34C759) else colors.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        "点击切换连接",
                                        fontSize = 12.sp,
                                        color = colors.textTertiary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (detailProfile != null) {
                AlertDialog(
                    onDismissRequest = { detailProfile = null },
                    containerColor = colors.card,
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("连接诊断", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(
                                "Beta",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    },
                    text = {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bg)
                                .border(0.6.dp, colors.stroke, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DiagnosticRow(
                                "当前状态",
                                connectionLabel(state.connection),
                                valueColor = if (state.connection == ConnectionState.Online) Color(0xFF34C759) else colors.textPrimary,
                            )
                            DiagnosticRow("传输通道", state.transportPath, valueColor = colors.accent)
                            DiagnosticRow(
                                "实时延迟",
                                if (state.connection == ConnectionState.Online && state.realtimeRttMs != null) "${state.realtimeRttMs} ms" else "—",
                            )
                            if (state.connection != ConnectionState.Online && !state.connectionFailureReason.isNullOrBlank()) {
                                DiagnosticRow("离线原因", state.connectionFailureReason, valueColor = colors.danger)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            detailProfile = null
                            model.connectSelected()
                        }) { Text("重新连接", fontWeight = FontWeight.SemiBold, color = colors.accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            detailProfile = null
                            model.forgetSelected()
                        }) { Text("移除此电脑", color = colors.danger) }
                    },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    mono: Boolean = false,
) {
    val colors = VibexTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.width(68.dp),
        )
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) PlexMono else null,
            color = valueColor ?: colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// MARK: - 3. 技能与扩展 (1:1 with media_1794111048362_5707f911.jpg & SkillsHubSettingsView)
private fun LazyListScope.ExtensionsSettings(state: UiState, model: CompanionModel) {
    item {
        LaunchedEffect(Unit) {
            model.loadExtensionsCatalog()
        }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val colors = VibexTheme.colors

        // Synthesize exact Subagent Roles matching Desktop MultiAgentSettings.tsx & iOS SkillsHubSettingsView
        val synthesizedRoles = remember(state.catalog?.agents, state.subagentRoles) {
            val catalogAgents = state.catalog?.agents.orEmpty()
            val defaultAgentList = if (catalogAgents.isNotEmpty()) {
                catalogAgents.map { it.id to agentDisplayName(it.id, it.displayName) }
            } else {
                listOf(
                    "claude_code" to "Claude Code",
                    "codex" to "Codex",
                    "antigravity" to "Google Antigravity",
                    "gemini" to "Gemini CLI",
                    "grok" to "Grok",
                    "cursor" to "Cursor",
                    "kimi_code" to "Kimi Code",
                    "opencode" to "OpenCode",
                )
            }
            val defaults = defaultAgentList.map { (agentId, label) ->
                SubagentRoleItem(
                    id = "default-agent:$agentId",
                    name = "$label · 默认配置",
                    teamId = "builtin",
                    teamName = "内置子代理角色",
                    summary = "使用 $label 默认模型与会话配置执行委派子任务",
                    agentId = label,
                    isBuiltin = true,
                )
            }
            val custom = state.subagentRoles.filterNot { role ->
                role.isBuiltin || defaults.any { it.id == role.id || it.name == role.name }
            }
            defaults + custom
        }

        val tabs = listOf(
            Triple("技能", Icons.Rounded.AutoAwesome, state.skillsCatalog.size),
            Triple("MCP", Icons.Rounded.Dns, state.mcpCatalog.size),
            Triple("插件", Icons.Rounded.Extension, state.pluginsCatalog.size),
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top 3-tab pill bar (Skills · MCP · Plugins; Subagents managed in Collaboration Space)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { idx, (label, icon, count) ->
                    val selected = tab == idx
                    Row(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.accent else colors.card)
                            .border(0.6.dp, if (selected) Color.Transparent else colors.stroke, RoundedCornerShape(10.dp))
                            .clickable { tab = idx }
                            .padding(horizontal = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.5.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(
                            icon,
                            null,
                            tint = if (selected) Color.White else colors.textPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else colors.textPrimary,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            "$count",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else colors.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) Color.White.copy(0.24f) else colors.segmentedTrack)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            // Search bar
            val safeTab = tab.coerceIn(0, tabs.lastIndex)
            val currentLabel = tabs[safeTab].first
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.card)
                    .border(0.6.dp, colors.stroke, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("搜索$currentLabel...", fontSize = 14.sp, color = colors.textTertiary)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Cancel,
                        null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp).clickable { searchQuery = "" },
                    )
                }
            }

            val q = searchQuery.trim().lowercase()
            if (state.extensionsLoading && state.skillsCatalog.isEmpty() && state.mcpCatalog.isEmpty() && state.pluginsCatalog.isEmpty()) {
                SettingsCardSkeletonView(rows = 5)
            } else {
                when (safeTab) {
                    0 -> CatalogCardList(
                        items = state.skillsCatalog.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.summary.lowercase().contains(q) },
                        icon = Icons.Rounded.AutoAwesome,
                        emptyTitle = "暂无匹配的技能",
                        emptySubtitle = "桌面端安装的本地与全局 Agent 技能将在此显示。",
                    )
                    1 -> CatalogCardList(
                        items = state.mcpCatalog.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.summary.lowercase().contains(q) },
                        icon = Icons.Rounded.Dns,
                        emptyTitle = "暂无 MCP 服务",
                        emptySubtitle = "桌面端配置的本地与全局 MCP 服务将在此显示。",
                    )
                    else -> CatalogCardList(
                        items = state.pluginsCatalog.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.summary.lowercase().contains(q) },
                        icon = Icons.Rounded.Extension,
                        emptyTitle = "暂无已安装插件",
                        emptySubtitle = "桌面端安装并启用的插件将在此显示。",
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogCardList(
    items: List<ExtensionCatalogItem>,
    icon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
) {
    val colors = VibexTheme.colors
    if (items.isEmpty()) {
        IosSettingsCard {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(40.dp))
                Text(emptyTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    emptySubtitle,
                    fontSize = 13.5.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    IosSettingsCard {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        if (item.kind.isNotBlank()) {
                            Text(
                                item.kind,
                                fontSize = 10.5.sp,
                                fontFamily = PlexMono,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.accent.copy(0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (item.summary.isNotBlank()) {
                        Text(item.summary, fontSize = 12.sp, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    item.status.ifBlank { "已启用" },
                    fontSize = 11.5.sp,
                    color = if (item.status.contains("停用") || item.status.contains("禁用")) colors.textTertiary else Color(0xFF34C759),
                )
            }
        }
    }
}

@Composable
private fun SubagentRolesList(roles: List<SubagentRoleItem>) {
    val colors = VibexTheme.colors
    val grouped = remember(roles) { roles.groupBy { it.teamName.ifBlank { "内置子代理角色" } } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (team, items) ->
            GroupLabel(team)
            IosSettingsCard {
                items.forEachIndexed { index, role ->
                    if (index > 0) HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 16.dp))
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AgentAvatar(role.id.removePrefix("default-agent:"), size = 22.dp, displayName = role.agentId)
                            Text(role.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text(
                                if (role.isBuiltin) "内置角色" else "自定义角色",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (role.summary.isNotBlank()) {
                            Text(role.summary, fontSize = 12.sp, color = colors.textSecondary)
                        }
                        if (role.agentId.isNotBlank()) {
                            Text(
                                "引擎: ${role.agentId}",
                                fontSize = 11.sp,
                                fontFamily = PlexMono,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - 4. 字体与通知设置页
private fun LazyListScope.FontSettings(state: UiState, model: CompanionModel) {
    item {
        var fontSize by rememberSaveable { mutableStateOf("标准") }
        var fontStyle by rememberSaveable { mutableStateOf("系统默认") }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GroupLabel("字体样式")
            IosSettingsCard {
                SettingChoice(
                    title = "字体风格",
                    options = listOf("系统默认" to "系统默认", "圆体" to "圆体", "等宽" to "等宽"),
                    current = fontStyle,
                    onSelect = { fontStyle = it },
                )
            }
            GroupLabel("字号大小")
            IosSettingsCard {
                SettingChoice(
                    title = "阅读字号",
                    options = listOf("紧凑" to "紧凑", "标准" to "标准", "偏大" to "偏大"),
                    current = fontSize,
                    onSelect = { fontSize = it },
                )
            }
        }
    }
}

private fun LazyListScope.NotificationSettings(state: UiState, model: CompanionModel) {
    item {
        val context = LocalContext.current
        val colors = VibexTheme.colors
        IosSettingsCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColoredIconBadge(Icons.Rounded.NotificationsActive, Color(0xFFFF3B30))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("后台持续监控通知", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                    Text("保持与桌面端长连接并推送审批与完成提醒", fontSize = 12.sp, color = colors.textSecondary)
                }
                Switch(
                    checked = state.monitor,
                    onCheckedChange = {
                        model.setMonitor(it)
                        if (it) CompanionMonitorService.start(context) else CompanionMonitorService.stop(context)
                    },
                )
            }
        }
    }
}

private fun LazyListScope.AboutSettings(state: UiState) {
    item {
        val colors = VibexTheme.colors
        IosSettingsCard {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandMark(56.dp)
                Text("VibeX Companion", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    "v${state.hostVersion.ifBlank { "0.4.5" }} (协议 v${state.hostProtocol.ifBlank { "1.0" }})",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
                Text(
                    "本地优先 AI 协同工作流与多智能体研发平台移动伴侣端",
                    fontSize = 12.5.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun <T> SettingChoice(title: String, options: List<Pair<T, String>>, current: T, onSelect: (T) -> Unit) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.segmentedTrack)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (value, label) ->
                val selected = current == value
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (selected) Modifier.shadow(2.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(0.08f))
                            else Modifier,
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) colors.segmentedThumb else Color.Transparent)
                        .clickable { onSelect(value) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

private fun LazyListScope.AccountSettings(
    state: UiState,
    model: CompanionModel,
    onOpen: (SettingsSection) -> Unit,
) {
    val overview = state.accountOverview
    val user = overview?.user

    // 1. Account Profile / Login Card
    item {
        val colors = VibexTheme.colors
        LaunchedEffect(state.accountSignedIn) {
            if (state.accountSignedIn) {
                model.refreshAccount()
            }
        }
        IosSettingsCard {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.accountLoading && !state.accountSignedIn -> {
                        AccountProfileSkeletonRow()
                    }
                    state.accountSignedIn && user != null -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    user.avatarInitial,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        user.resolvedDisplayName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                    )
                                    val isActive = overview.hasActiveCreditsOrTier
                                    Text(
                                        if (isActive) "已激活" else "免费版",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isActive) colors.accent else colors.textSecondary,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isActive) colors.accent.copy(alpha = 0.14f) else colors.segmentedTrack)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                                if (user.email.isNotBlank()) {
                                    Text(user.email, fontSize = 12.5.sp, color = colors.textSecondary)
                                }
                                if (user.accountIdentifier.isNotBlank()) {
                                    Text(
                                        "ID: ${user.accountIdentifier}",
                                        fontSize = 11.5.sp,
                                        fontFamily = PlexMono,
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                            TextButton(onClick = { model.refreshAccount() }) {
                                Text("刷新", fontSize = 12.5.sp, color = colors.accent)
                            }
                        }
                    }
                    else -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ColoredIconBadge(Icons.Rounded.Verified, Color(0xFF007AFF))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("登录 VibeX 账号", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Text(
                                    "跨设备自动同步已配对电脑、查看订阅计划与模型路由额度",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent)
                                .clickable { model.requestAccountLogin() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "登录",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }

                if (!state.accountError.isNullOrBlank()) {
                    Text(
                        state.accountError,
                        fontSize = 12.sp,
                        color = colors.danger,
                    )
                }
            }
        }
    }

    // 2. Subscription & Credits Usage
    if (state.accountSignedIn && overview != null) {
        item { GroupLabel("订阅与额度") }
        item {
            val colors = VibexTheme.colors
            IosSettingsCard {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                overview.planDisplayName,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            val activeCount = overview.activeGrants.size
                            Text(
                                if (activeCount > 0) "${activeCount} 个额度包生效中" else "支持 Claude / OpenAI / Gemini 等多模型路由与云中继",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            "充值 / 订阅",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accent.copy(alpha = 0.12f))
                                .clickable { model.openAccountSitePath("/account/plan") }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    HorizontalDivider(color = colors.hairline)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("可用模型路由余额", fontSize = 12.sp, color = colors.textSecondary)
                            Text(
                                "$${VibeXAccountRepository.formatCredits(overview.balanceMicro)}",
                                fontSize = 22.sp,
                                fontFamily = PlexMono,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                        }
                        if (overview.activeTotalMicro > 0L) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "总额度 $${VibeXAccountRepository.formatCredits(overview.activeTotalMicro)}",
                                    fontSize = 11.5.sp,
                                    fontFamily = PlexMono,
                                    color = colors.textTertiary,
                                )
                                Text(
                                    "剩余 ${(overview.remainingRatio * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 30-Day Usage
        item { GroupLabel("近 30 天模型路由用量") }
        item {
            val colors = VibexTheme.colors
            val rows = overview.usage30d.byModel
            val spent30d = rows.sumOf { it.costMicro }
            val reqs30d = rows.sumOf { it.requests }
            val tokens30d = rows.sumOf { it.tokens }
            IosSettingsCard {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountMetricChip(Modifier.weight(1f), "30天消耗", "$${VibeXAccountRepository.formatCredits(spent30d)}")
                        AccountMetricChip(Modifier.weight(1f), "请求次数", reqs30d.toString())
                        AccountMetricChip(Modifier.weight(1f), "总 Token", VibeXAccountRepository.formatCompact(tokens30d))
                    }
                    if (rows.isNotEmpty()) {
                        HorizontalDivider(color = colors.hairline)
                        rows.take(6).forEach { row ->
                            val modelLabel = overview.usage30d.modelNames[row.modelId] ?: row.modelId
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    modelLabel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${row.requests} 次 · $${VibeXAccountRepository.formatCredits(row.costMicro)}",
                                    fontSize = 12.sp,
                                    fontFamily = PlexMono,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    } else {
                        Text("近 30 天暂无模型路由调用记录", fontSize = 12.sp, color = colors.textTertiary)
                    }
                }
            }
        }
    }

    // 4. Cloud Synced Hosts (Summary + Sync Trigger, full management in ConnectionSettings)
    if (state.accountSignedIn) {
        item { GroupLabel("电脑跨端云同步") }
        item {
            val colors = VibexTheme.colors
            val syncedCount = maxOf(state.accountDesktops.size, state.accountSavedHosts.size)
            IosSettingsCard {
                Column(Modifier.fillMaxWidth()) {
                    IosSettingsRow(
                        icon = Icons.Rounded.Computer,
                        tint = Color(0xFF007AFF),
                        title = "管理已配对与云端同步的电脑",
                        subtitle = if (syncedCount > 0) {
                            "账号下已关联 $syncedCount 台电脑，支持局域网与云服务自动切换"
                        } else {
                            "在桌面端登录同一账号或扫码配对后自动跨设备同步"
                        },
                        onClick = { onOpen(SettingsSection.Connection) },
                    )
                    HorizontalDivider(color = colors.hairline)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.accountSyncingHosts) { model.syncAccountHosts(forcePush = true) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (state.accountSyncingHosts) "正在同步已配对电脑…" else "立即同步账号下的电脑",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }

    // 5. Security, Seats, Billing & Local Collaboration Profile Links
    item { GroupLabel("安全、账单与个人偏好") }
    item {
        IosSettingsCard {
            IosSettingsRow(
                icon = Icons.Rounded.Verified,
                tint = Color(0xFFFF9500),
                title = "通行密钥与账号安全",
                subtitle = "管理 Passkey、登录设备与双重验证",
                onClick = { model.openAccountSitePath("/account/security") },
            )
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 56.dp))
            IosSettingsRow(
                icon = Icons.Rounded.Groups,
                tint = Color(0xFF007AFF),
                title = "团队与协作席位",
                subtitle = "管理团队成员席位与共享额度",
                onClick = { model.openAccountSitePath("/account") },
            )
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 56.dp))
            IosSettingsRow(
                icon = Icons.Rounded.Dns,
                tint = Color(0xFFAF52DE),
                title = "账单与兑换码记录",
                subtitle = "查看历史订单、充值明细与额度兑换",
                onClick = { model.openAccountSitePath("/account/billing") },
            )
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 56.dp))
            IosSettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                tint = Color(0xFF5856D6),
                title = "协作空间个人形象与昵称",
                subtitle = "自定义本机在多智能体协作空间中的显示名与头像配色",
                onClick = { onOpen(SettingsSection.Profile) },
            )
        }
    }

    if (state.accountSignedIn) {
        item {
            val colors = VibexTheme.colors
            IosSettingsCard {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.accountSigningOut) { model.signOutAccount() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.accountSigningOut) "正在退出…" else "退出 VibeX 账号",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountMetricChip(modifier: Modifier = Modifier, title: String, value: String) {
    val colors = VibexTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.segmentedTrack)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 11.sp, color = colors.textSecondary)
        Text(value, fontSize = 14.sp, fontFamily = PlexMono, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = VibexTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}
