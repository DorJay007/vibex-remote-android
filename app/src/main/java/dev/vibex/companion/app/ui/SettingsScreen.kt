package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.SettingsSection
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.data.CollapseDisplay
import dev.vibex.companion.app.data.HostProfile
import dev.vibex.companion.app.data.MessageStreamDisplay
import dev.vibex.companion.app.data.ThinkingDisplay
import dev.vibex.companion.app.monitor.CompanionMonitorService
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
) {
    when (section) {
        null -> SettingsIndex(onOpen)
        SettingsSection.Connection -> SettingsPage("连接", onBack) { ConnectionSettings(state, model, onScan) }
        SettingsSection.Appearance -> SettingsPage("外观", onBack) { AppearanceSettings(state, model) }
        SettingsSection.Stream -> SettingsPage("消息流控制", onBack) { StreamSettings(state, model) }
        SettingsSection.Host -> SettingsPage("Host", onBack) { HostSettings(state) }
        SettingsSection.Capabilities -> SettingsPage("能力", onBack) { CapabilitySettings(state) }
        SettingsSection.About -> SettingsPage("关于", onBack) { AboutSettings() }
    }
}

@Composable
private fun SettingsIndex(onOpen: (SettingsSection) -> Unit) {
    val colors = VibexTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item {
            GlassCard {
                IndexRow(Icons.Rounded.Link, "连接", "Host 档案、同步与本机") { onOpen(SettingsSection.Connection) }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 48.dp))
                IndexRow(Icons.Rounded.LightMode, "外观", "浅色、深色或跟随系统") { onOpen(SettingsSection.Appearance) }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 48.dp))
                IndexRow(Icons.Rounded.ChatBubbleOutline, "消息流控制", "思考、工具调用与消息折叠") { onOpen(SettingsSection.Stream) }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 48.dp))
                IndexRow(Icons.Rounded.Dns, "Host", "版本、协议与当前地址") { onOpen(SettingsSection.Host) }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 48.dp))
                IndexRow(Icons.Rounded.Shield, "能力", "这台设备可用的 Host 能力") { onOpen(SettingsSection.Capabilities) }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 48.dp))
                IndexRow(Icons.Rounded.Info, "关于", "VibeX Companion") { onOpen(SettingsSection.About) }
            }
        }
    }
}

@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val colors = VibexTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.textPrimary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                        .clip(ShapeRow)
                        .clickable(onClick = onBack)
                        .padding(6.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 44.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun IndexRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

private fun LazyListScope.StreamSettings(state: UiState, model: CompanionModel) {
    val prefs = state.streamDisplay
    item { GroupLabel("思考") }
    item {
        GlassCard {
            SettingChoice(
                title = "思考内容",
                options = listOf(
                    ThinkingDisplay.All to "全部",
                    ThinkingDisplay.Collapsed to "折叠",
                    ThinkingDisplay.Hidden to "隐藏",
                ),
                current = prefs.thinking,
                onSelect = { model.setStreamDisplay(prefs.copy(thinking = it)) },
            )
        }
    }
    item { GroupLabel("工具调用") }
    item {
        GlassCard {
            SettingChoice(
                title = "显示失败工具调用",
                options = listOf(true to "显示", false to "不显示"),
                current = prefs.showFailedTools,
                onSelect = { model.setStreamDisplay(prefs.copy(showFailedTools = it)) },
            )
            if (prefs.showFailedTools) {
                HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 16.dp))
                SettingChoice(
                    title = "失败工具调用",
                    options = listOf(CollapseDisplay.Expanded to "展开", CollapseDisplay.Collapsed to "折叠"),
                    current = prefs.failedTools,
                    onSelect = { model.setStreamDisplay(prefs.copy(failedTools = it)) },
                )
            }
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 16.dp))
            SettingChoice(
                title = "工具调用组件",
                options = listOf(CollapseDisplay.Expanded to "展开", CollapseDisplay.Collapsed to "折叠"),
                current = prefs.tools,
                onSelect = { model.setStreamDisplay(prefs.copy(tools = it)) },
            )
        }
    }
    item { GroupLabel("消息") }
    item {
        GlassCard {
            SettingChoice(
                title = "会话消息流",
                options = listOf(MessageStreamDisplay.All to "全部", MessageStreamDisplay.Collapsed to "折叠"),
                current = prefs.messages,
                onSelect = { model.setStreamDisplay(prefs.copy(messages = it)) },
            )
        }
    }
}

private fun LazyListScope.HostSettings(state: UiState) {
    val selected = state.profiles.find { it.hostId == state.selectedHostId }
    item {
        GlassCard {
            SettingsFact("版本", state.hostVersion.ifBlank { "未知" })
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 16.dp))
            SettingsFact("协议", state.hostProtocol.ifBlank { "未知" })
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 16.dp))
            SettingsFact("Host ID", selected?.hostId ?: "—")
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 16.dp))
            SettingsFact("当前地址", state.activeOrigin ?: selected?.lastSuccessOrigin ?: "没有")
        }
    }
}

private fun LazyListScope.ConnectionSettings(state: UiState, model: CompanionModel, onScan: () -> Unit) {
    val selected = state.profiles.find { it.hostId == state.selectedHostId }
    item { GroupLabel("连接") }
    item {
        GlassCard {
            state.profiles.forEachIndexed { index, profile ->
                if (index > 0) HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 48.dp))
                HostRow(
                    profile,
                    selected = profile.hostId == state.selectedHostId,
                    origin = if (profile.hostId == state.selectedHostId) state.activeOrigin else profile.lastSuccessOrigin,
                    connection = if (profile.hostId == state.selectedHostId) connectionLabel(state.connection) else "已保存",
                ) { model.selectHost(profile.hostId) }
            }
            if (state.profiles.isEmpty()) {
                Text("还没有 Host", color = VibexTheme.colors.textSecondary, modifier = Modifier.padding(16.dp))
            }
        }
    }
    item { GroupLabel("操作") }
    item {
        GlassCard {
            SettingsAction(Icons.Outlined.QrCodeScanner, "再扫描邀请", "更新这台 Host 的地址") { onScan() }
            HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 48.dp))
            var confirm by remember { mutableStateOf(false) }
            SettingsAction(Icons.Outlined.LinkOff, "忘记这台 Host", selected?.name ?: "撤销这台设备") { confirm = true }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("忘记这台 Host") },
                    text = { Text("会撤销这台设备。会话缓存在本机删除。") },
                    confirmButton = { TextButton(onClick = { confirm = false; model.forgetSelected() }) { Text("忘记") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
                )
            }
        }
    }
    item { GroupLabel("会话同步") }
    item {
        GlassCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsIconBadge(Icons.Rounded.Dns)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("默认时间窗口", style = MaterialTheme.typography.titleMedium, color = VibexTheme.colors.textPrimary)
                    Text("近 ${state.sinceDays} 天", style = MaterialTheme.typography.bodyMedium, color = VibexTheme.colors.textSecondary)
                }
            }
            Row(Modifier.padding(start = 54.dp, end = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 30).forEach { days ->
                    val selectedDays = state.sinceDays == days
                    Text(
                        "近 $days 天",
                        color = if (selectedDays) VibexTheme.colors.onAccent else VibexTheme.colors.textPrimary,
                        modifier = Modifier
                            .clip(ShapeRow)
                            .background(if (selectedDays) VibexTheme.colors.accent else VibexTheme.colors.bg.copy(0.4f))
                            .clickable { model.setSinceDays(days) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
    item { GroupLabel("本机") }
    item {
        val context = LocalContext.current
        val colors = VibexTheme.colors
        GlassCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsIconBadge(Icons.Rounded.NotificationsActive)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("持续监控", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("保持与 Host 的前台连接", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                Switch(checked = state.monitor, onCheckedChange = {
                    model.setMonitor(it)
                    if (it) CompanionMonitorService.start(context) else CompanionMonitorService.stop(context)
                })
            }
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 48.dp))
            SettingsAction(Icons.Rounded.Info, "清除本机会话缓存", "离线事件会重新从 Host 拉取") { model.clearOfflineCache() }
        }
    }
}

private fun LazyListScope.AppearanceSettings(state: UiState, model: CompanionModel) {
    item { GroupLabel("主题") }
    item {
        GlassCard {
            SettingChoice(
                title = "外观",
                options = listOf(
                    dev.vibex.companion.app.ui.theme.Appearance.Light to "浅色",
                    dev.vibex.companion.app.ui.theme.Appearance.Dark to "深色",
                    dev.vibex.companion.app.ui.theme.Appearance.System to "系统",
                ),
                current = state.appearance,
                onSelect = model::setAppearance,
            )
        }
    }
    item { GroupLabel("主题色") }
    item {
        GlassCard {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("强调色", style = MaterialTheme.typography.titleMedium, color = VibexTheme.colors.textPrimary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    dev.vibex.companion.app.ui.theme.AccentChoice.entries.forEach { choice ->
                        val selected = state.accent == choice
                        val swatch = choice.color(VibexTheme.colors.isDark)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(swatch)
                                .then(
                                    if (selected) Modifier.border(2.dp, VibexTheme.colors.textPrimary, androidx.compose.foundation.shape.CircleShape)
                                    else Modifier.border(1.dp, VibexTheme.colors.stroke, androidx.compose.foundation.shape.CircleShape)
                                )
                                .clickable { model.setAccent(choice) },
                        )
                    }
                }
                Text(state.accent.label, style = MaterialTheme.typography.bodyMedium, color = VibexTheme.colors.textSecondary)
            }
        }
    }
}

private fun LazyListScope.CapabilitySettings(state: UiState) {
    item {
        GlassCard {
            val caps = state.hostCapabilities.ifEmpty { listOf("还没有拉取到 capabilities") }
            caps.forEachIndexed { index, cap ->
                if (index > 0) HorizontalDivider(color = VibexTheme.colors.hairline, modifier = Modifier.padding(start = 16.dp))
                Text(
                    cap,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VibexTheme.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontFamily = PlexMono,
                )
            }
        }
    }
}

private fun LazyListScope.AboutSettings() {
    item {
        GlassCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsIconBadge(Icons.Rounded.Info)
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text("VibeX Companion", style = MaterialTheme.typography.titleMedium, color = VibexTheme.colors.textPrimary)
                    Text("1.0.0 · 只连接 Host", style = MaterialTheme.typography.bodyMedium, color = VibexTheme.colors.textTertiary)
                }
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
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bg.copy(if (colors.isDark) 0.35f else 0.55f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                val selected = current == value
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent else colors.bgElevated.copy(0f))
                        .clickable { onSelect(value) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = VibexTheme.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun HostRow(profile: HostProfile, selected: Boolean, origin: String?, connection: String, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Row(
        Modifier.fillMaxWidth().background(if (selected) colors.accent.copy(0.10f) else colors.bgElevated.copy(0f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIconBadge(Icons.Rounded.Dns)
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(origin ?: "没有已验证地址", style = MaterialTheme.typography.labelMedium, color = colors.textTertiary, fontFamily = PlexMono)
        }
        Text(connection, style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.accent else colors.textTertiary)
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun SettingsFact(title: String, value: String) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, fontFamily = PlexMono)
    }
}
