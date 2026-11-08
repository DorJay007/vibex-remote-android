package dev.vibex.companion.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.ConversationSummary
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

private data class KanbanColumn(val id: String, val label: String)

private val columns = listOf(
    KanbanColumn("todo", "待开始"),
    KanbanColumn("inprogress", "进行中"),
    KanbanColumn("inreview", "待检查"),
    KanbanColumn("done", "已完成"),
)

private val boardModes = listOf(
    "kanban" to "四栏",
    "list" to "列表",
    "monitor" to "监控",
    "canvas" to "画布",
    "usage" to "用量",
)

@Composable
fun StatusScreen(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    var mode by rememberSaveable { mutableStateOf("kanban") }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    val projects = state.catalog?.projects.orEmpty()
    val currentProject = projects.find { it.id == state.selectedProjectId }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // iOS Inline Top Bar: Project Pill | 看板 | + Button
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterStart)) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(colors.bgElevated)
                        .clickable { showProjectMenu = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text(
                        currentProject?.name ?: "全部项目",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                    )
                    Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(expanded = showProjectMenu, onDismissRequest = { showProjectMenu = false }) {
                    projects.forEach { proj ->
                        DropdownMenuItem(
                            text = { Text(proj.name) },
                            trailingIcon = if (proj.id == state.selectedProjectId) {
                                { Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                            } else null,
                            onClick = {
                                showProjectMenu = false
                                model.selectProject(proj.id)
                            },
                        )
                    }
                }
            }
            Text(
                "看板",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            Icon(
                Icons.Rounded.AddCircle,
                contentDescription = "新建会话",
                tint = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(24.dp)
                    .clickable {
                        model.loadCatalog()
                        showCreate = true
                    },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            boardModes.forEach { (id, label) ->
                val selected = mode == id
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) Color.White else colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent else colors.bgElevated)
                        .clickable { mode = id }
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        HorizontalDivider(color = colors.hairline)
        if (showCreate) {
            NewConversationSheet(state, model) { showCreate = false }
        }
        when {
            state.selectedProjectId == null && state.conversations.isEmpty() -> EmptyState(
                Icons.Outlined.SpaceDashboard,
                "先选择文件夹",
                "看板按当前项目近几天的会话分组。",
                "去选择",
                { model.go(Route.Folders) },
            )
            mode == "kanban" -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(columns, key = { it.id }) { column ->
                    val items = state.conversations.filter { normalizeStatus(it.status) == column.id }
                    val open = column.id !in collapsed
                    KanbanSection(
                        column = column,
                        count = items.size,
                        open = open,
                        color = kanbanColor(column.id, colors),
                        onToggle = {
                            collapsed = if (open) collapsed + column.id else collapsed - column.id
                        },
                    ) {
                        if (open) {
                            items.forEach { item ->
                                KanbanSessionCard(
                                    item = item,
                                    branch = state.catalog?.workspaces.orEmpty()
                                        .find { it.id == item.workspaceId }?.branch
                                        ?.ifBlank { null },
                                    onMoveStatus = if (state.connection.allowsWrites) { nextStatus ->
                                        model.setConversationStatus(item.id, nextStatus)
                                    } else null,
                                ) { model.openConversation(item.id) }
                            }
                        }
                    }
                }
            }
            mode == "list" -> BoardListView(state, model)
            mode == "monitor" -> BoardMonitorView(state, model)
            mode == "canvas" -> BoardCanvasView(state, model)
            else -> BoardUsageDashboardView(state)
        }
    }
}

@Composable
private fun BoardListView(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    var filterStatus by rememberSaveable { mutableStateOf("all") }
    val filtered = state.conversations.filter {
        filterStatus == "all" || normalizeStatus(it.status) == filterStatus
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (listOf("all" to "全部") + columns.map { it.id to it.label }).forEach { (id, label) ->
                val selected = filterStatus == id
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.accent else colors.bgElevated)
                        .border(0.75.dp, if (selected) colors.accent else colors.stroke, RoundedCornerShape(50))
                        .clickable { filterStatus = id }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { item ->
                val statusId = normalizeStatus(item.status)
                val statusLabel = columns.firstOrNull { it.id == statusId }?.label ?: item.status
                val statusColor = kanbanColor(statusId, colors)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                        .clickable { model.openConversation(item.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AgentAvatar(item.agentId, 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${agentDisplayName(item.agentId)} · ${relativeShort(item.updatedAt)}", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Text(
                        statusLabel,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardMonitorView(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val inProgress = state.conversations.filter { normalizeStatus(it.status) == "inprogress" }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("待你拍板 (${state.inbox.size})", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
        }
        if (state.inbox.isEmpty()) {
            item {
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.pass, modifier = Modifier.size(18.dp))
                        Text("当前没有挂起的权限或提问请求", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
            }
        } else {
            items(state.inbox, key = { it.id }) { pending ->
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = colors.hold, modifier = Modifier.size(18.dp))
                        Text(pending.conversationTitle.ifBlank { "会话待审批" }, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            "打开",
                            fontSize = 12.sp,
                            color = colors.accent,
                            modifier = Modifier.clickable { model.openConversation(pending.conversationId) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(pending.title.ifBlank { pending.body }, fontSize = 13.sp, color = colors.textSecondary)
                }
            }
        }
        item {
            Text("运行中任务 (${inProgress.size})", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
        }
        items(inProgress, key = { it.id }) { item ->
            KanbanSessionCard(
                item = item,
                branch = state.catalog?.workspaces.orEmpty().find { it.id == item.workspaceId }?.branch?.ifBlank { null },
                onMoveStatus = null,
            ) { model.openConversation(item.id) }
        }
    }
}

@Composable
private fun BoardCanvasView(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val project = state.catalog?.projects?.find { it.id == state.selectedProjectId }
    val workspaces = state.catalog?.workspaces.orEmpty().filter {
        state.selectedProjectId == null || it.projectId == state.selectedProjectId
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Folder, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Column {
                        Text(project?.name ?: "当前项目拓扑", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text("${workspaces.size} 个工作区 · ${state.conversations.size} 个会话节点", fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            }
        }
        items(state.conversations, key = { it.id }) { item ->
            val statusId = normalizeStatus(item.status)
            val statusColor = kanbanColor(statusId, colors)
            val ws = workspaces.find { it.id == item.workspaceId }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(statusColor.copy(0.6f)),
                )
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(1.dp, statusColor.copy(0.45f), RoundedCornerShape(14.dp))
                        .clickable { model.openConversation(item.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AgentAvatar(item.agentId, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(agentDisplayName(item.agentId), ws?.branch?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            fontFamily = PlexMono,
                        )
                    }
                    StatusDot(statusColor)
                }
            }
        }
    }
}

@Composable
private fun BoardUsageDashboardView(state: UiState) {
    val colors = VibexTheme.colors
    val total = state.conversations.size
    val active = state.conversations.count { normalizeStatus(it.status) == "inprogress" }
    val review = state.conversations.count { normalizeStatus(it.status) == "inreview" }
    val done = state.conversations.count { normalizeStatus(it.status) == "done" }
    val byAgent = state.conversations.groupingBy { it.agentId.ifBlank { "unknown" } }.eachCount()
        .entries.sortedByDescending { it.value }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UsageStatCard("总会话", "$total", colors.accent, Modifier.weight(1f))
                UsageStatCard("进行中", "$active", colors.accent, Modifier.weight(1f))
                UsageStatCard("待检查", "$review", colors.hold, Modifier.weight(1f))
                UsageStatCard("已完成", "$done", colors.pass, Modifier.weight(1f))
            }
        }
        item {
            GlassCard(padding = 14.dp) {
                Text("Agent 会话分布", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                if (byAgent.isEmpty()) {
                    Text("暂无统计数据", fontSize = 13.sp, color = colors.textSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        byAgent.forEach { (agentId, count) ->
                            val ratio = if (total > 0) count.toFloat() / total.toFloat() else 0f
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(agentDisplayName(agentId), fontSize = 13.sp, color = colors.textPrimary)
                                    Text("$count (${(ratio * 100).toInt()}%)", fontSize = 12.sp, color = colors.textSecondary, fontFamily = PlexMono)
                                }
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = colors.accent,
                                    trackColor = colors.stroke,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageStatCard(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint, fontFamily = PlexMono)
        Text(label, fontSize = 11.sp, color = colors.textSecondary)
    }
}

@Composable
private fun KanbanSection(
    column: KanbanColumn,
    count: Int,
    open: Boolean,
    color: Color,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = VibexTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.bgElevated.copy(if (colors.isDark) 0.55f else 0.90f))
            .border(0.75.dp, colors.stroke, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(ShapeRow).clickable(onClick = onToggle).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = color, modifier = Modifier.size(16.dp))
                Text(column.label, style = MaterialTheme.typography.titleMedium, color = color)
            }
            Spacer(Modifier.weight(1f))
            BoxCount(count, color)
        }
        content()
    }
}

@Composable
private fun BoxCount(count: Int, color: Color) {
    Text(
        "$count",
        color = color,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun KanbanSessionCard(
    item: ConversationSummary,
    branch: String?,
    onMoveStatus: ((String) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val currentStatus = normalizeStatus(item.status)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AgentAvatar(item.agentId, 22.dp)
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                relativeShort(item.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Bolt, null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
            Text(
                branch ?: "main",
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onMoveStatus != null) {
                columns.filter { it.id != currentStatus }.take(2).forEach { target ->
                    Text(
                        "→${target.label}",
                        fontSize = 10.sp,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.copy(0.10f))
                            .clickable { onMoveStatus(target.id) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun relativeShort(iso: String): String =
    iso.takeIf { it.isNotBlank() }?.substringAfter('T')?.take(5)?.ifBlank { iso.take(10) } ?: "刚刚"

private fun kanbanColor(id: String, colors: dev.vibex.companion.app.ui.theme.CompanionColors): Color = when (id) {
    "inprogress" -> colors.accent
    "inreview" -> colors.hold
    "done" -> colors.pass
    else -> colors.textSecondary
}
