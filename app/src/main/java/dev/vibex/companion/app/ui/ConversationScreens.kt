package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vibex.companion.ConversationSummary
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationList(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    var showCreate by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    val project = state.catalog?.projects?.find { it.id == state.selectedProjectId }
    val filtered = state.conversations.filter {
        state.query.isBlank() || it.title.contains(state.query, true) || it.agentId.contains(state.query, true)
    }
    val pinned = filtered.filter { it.pinned }
    val rest = filtered.filter { !it.pinned }
    val sections = buildList {
        if (pinned.isNotEmpty()) add(DaySection("置顶", pinned))
        addAll(groupByDay(rest))
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConversationHeader(state, model)
                if (state.inbox.isNotEmpty()) {
                    Text(
                        "${state.inbox.size} 条待你处理",
                        color = colors.hold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clip(ShapeRow).background(colors.hold.copy(0.12f)).clickable {
                            model.openConversation(state.inbox.first().conversationId)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                SearchField(state.query, model::query, "搜索会话")
            }
            CompanionRefresh(refreshing = state.conversationsLoading, onRefresh = model::refreshConversations, modifier = Modifier.fillMaxSize()) {
                when {
                    state.selectedProjectId == null -> EmptyState(
                        Icons.Rounded.Folder,
                        "先选择文件夹",
                        "在「文件夹」里点一个 Host 项目目录，再同步近 3 天的会话。",
                        "去选择",
                        { model.go(Route.Folders) },
                    )
                    filtered.isEmpty() && !state.conversationsLoading -> EmptyState(
                        Icons.Outlined.Forum,
                        "近 ${state.sinceDays} 天没有会话",
                        if (state.connection.allowsWrites) "${project?.name ?: "这个项目"}最近没有活动。" else "上次同步 · 只读",
                        if (state.connection.allowsWrites) "新会话" else null,
                        if (state.connection.allowsWrites) ({ showCreate = true; model.loadCatalog() }) else null,
                        state.connection.allowsWrites,
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        sections.forEach { section ->
                            item(key = "h-${section.title}") {
                                Text(section.title, style = MaterialTheme.typography.titleMedium, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                            items(section.items, key = { it.id }) { item ->
                                SessionRow(
                                    item = item,
                                    agent = catalogAgent(state, item.agentId),
                                    onClick = { model.openConversation(item.id) },
                                    onLongClick = { offset -> menuAnchor = offset; menuId = item.id },
                                )
                            }
                        }
                        item { Spacer(Modifier.size(88.dp)) }
                    }
                }
            }
        }
        if (state.selectedProjectId != null && state.connection.allowsWrites) {
            FloatingActionButton(
                onClick = { showCreate = true; model.loadCatalog() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).zIndex(2f),
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Add, "新会话")
            }
        }
        if (showCreate) NewConversationSheet(state, model) { showCreate = false }
        val menuItem = state.conversations.find { it.id == menuId }
        if (menuItem != null && state.connection.allowsWrites) {
            ActionMenu(
                items = listOf(
                    ActionMenuItem(if (menuItem.pinned) "取消置顶" else "置顶") { model.pinConversation(menuItem.id, !menuItem.pinned) },
                    ActionMenuItem("归档") { model.archiveConversation(menuItem.id) },
                    ActionMenuItem("删除", danger = true) { model.deleteConversation(menuItem.id) },
                ),
                anchor = menuAnchor,
                onDismiss = { menuId = null },
            )
        }
    }
}

@Composable
private fun ConversationHeader(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val project = state.catalog?.projects?.find { it.id == state.selectedProjectId }
    var projectOpen by remember { mutableStateOf(false) }
    var daysOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.align(Alignment.Center).padding(horizontal = 108.dp)) {
            Row(
                Modifier.clip(ShapeRow).clickable { projectOpen = true }.padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    project?.name ?: "会话",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary)
            }
            GlassMenu(projectOpen, { projectOpen = false }) {
                state.catalog?.projects.orEmpty().forEach { item ->
                    GlassMenuItem(item.name.ifBlank { item.id }, selected = item.id == state.selectedProjectId) {
                        projectOpen = false
                        model.selectProject(item.id)
                    }
                }
                GlassMenuItem("管理文件夹") {
                    projectOpen = false
                    model.go(Route.Folders)
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            Row(
                Modifier
                    .clip(ShapeRow)
                    .background(colors.bgElevated.copy(if (colors.isDark) 0.55f else 0.88f))
                    .clickable { daysOpen = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("近 ${state.sinceDays} 天", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
            GlassMenu(daysOpen, { daysOpen = false }) {
                listOf(3, 7, 30).forEach { days ->
                    GlassMenuItem("近 $days 天", selected = state.sinceDays == days) {
                        daysOpen = false
                        model.setSinceDays(days)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionRow(
    item: ConversationSummary,
    agent: dev.vibex.companion.CatalogAgent? = null,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit = {},
) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .shadow(4.dp, ShapeRow, ambientColor = colors.textPrimary.copy(0.07f), spotColor = colors.textPrimary.copy(0.10f))
            .clip(ShapeRow)
            .background(colors.card)
            .then(longPressModifier(onClick, onLongClick))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            AgentAvatar(item.agentId, iconSvg = agent?.iconSvg, displayName = agent?.displayName.orEmpty())
            Box(
                Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(8.dp).clip(CircleShape)
                    .background(statusColor(item.status, colors))
                    .border(1.5.dp, colors.card, CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(item.title.ifBlank { "会话" }, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (item.pinned) "置顶" else null,
                    agentDisplayName(item.agentId, agent?.displayName.orEmpty()).ifBlank { null },
                    statusLabel(item.status),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(relativeTime(item.updatedAt), style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
    }
}

private data class DaySection(val title: String, val items: List<ConversationSummary>)

private fun groupByDay(items: List<ConversationSummary>): List<DaySection> {
    val grouped = linkedMapOf<String, MutableList<ConversationSummary>>()
    items.forEach { item ->
        grouped.getOrPut(dayBucket(item.updatedAt)) { mutableListOf() }.add(item)
    }
    return grouped.map { DaySection(it.key, it.value) }
}

private fun dayBucket(raw: String): String {
    val parsed = parseInstant(raw) ?: return "更早"
    val days = Duration.between(parsed, Instant.now()).toDays().coerceAtLeast(0)
    return when {
        days < 1 -> "今天"
        days < 2 -> "昨天"
        days < 7 -> "本周"
        else -> "更早"
    }
}

fun relativeTime(raw: String): String {
    val parsed = parseInstant(raw) ?: return ""
    val minutes = Duration.between(parsed, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}

private fun parseInstant(raw: String): Instant? {
    if (raw.isBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
}


@Composable
fun NewConversation(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val catalog = state.catalog
    val projectId = state.selectedProjectId
    val projectWorkspaces = catalog?.workspaces.orEmpty().filter { it.projectId == projectId }
    val agents = catalog?.agents.orEmpty().filter { it.ready }
    var modeNew by remember { mutableStateOf(false) }
    var workspace by remember { mutableStateOf(projectWorkspaces.firstOrNull()?.id.orEmpty()) }
    var agent by remember { mutableStateOf(agents.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newBranch by remember { mutableStateOf(projectWorkspaces.firstOrNull()?.branch.orEmpty()) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            IconButton(onClick = { model.go(Route.Conversations) }, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            Text("新会话", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 48.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (catalog == null) {
                Text("正在读取目录", color = colors.textSecondary)
                return
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !modeNew, onClick = { modeNew = false }, label = { Text("已有工作区") })
                FilterChip(selected = modeNew, onClick = { modeNew = true }, label = { Text("新建工作区") })
            }
            androidx.compose.material3.OutlinedTextField(
                title, { title = it }, label = { Text("会话名称") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = ShapeControl,
            )
            if (modeNew) {
                androidx.compose.material3.OutlinedTextField(
                    newName, { newName = it }, label = { Text("工作区名称") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = ShapeControl,
                )
                androidx.compose.material3.OutlinedTextField(
                    newBranch, { newBranch = it }, label = { Text("分支") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = ShapeControl,
                )
            } else {
                Text("工作区", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
                if (projectWorkspaces.isEmpty()) {
                    Text("这个项目还没有工作区", color = colors.textTertiary)
                }
                projectWorkspaces.forEach { item ->
                    GlassRow(selected = item.id == workspace, onClick = { workspace = item.id }) {
                        Text(item.name.ifBlank { item.id }, color = colors.textPrimary)
                        if (item.branch.isNotBlank()) Text(item.branch, color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("Agent", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            if (agents.isEmpty()) Text("Host 上还没有已启用的 Agent", color = colors.textTertiary)
            agents.forEach { item ->
                GlassRow(selected = item.id == agent, onClick = { agent = item.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AgentAvatar(item.id, iconSvg = item.iconSvg, displayName = item.displayName)
                        Column {
                            Text(agentDisplayName(item.id, item.displayName), color = colors.textPrimary)
                            Text(if (item.ready) "就绪" else "未就绪", color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            androidx.compose.material3.OutlinedTextField(
                prompt, { prompt = it }, label = { Text("第一句话") },
                modifier = Modifier.fillMaxWidth(), minLines = 4, shape = ShapeControl,
            )
            PrimaryButton(
                "创建",
                {
                    if (modeNew) {
                        val pid = projectId ?: return@PrimaryButton
                        model.createWorkspace(pid, newName.ifBlank { "workspace" }, newBranch.ifBlank { null }) { id ->
                            model.createConversation(id, agent, title, prompt)
                        }
                    } else {
                        model.createConversation(workspace, agent, title, prompt)
                    }
                },
                enabled = state.connection.allowsWrites && agent.isNotBlank() && (modeNew || workspace.isNotBlank()) && (!modeNew || newName.isNotBlank() || true),
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}
