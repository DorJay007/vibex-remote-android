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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.vibex.companion.CollaborationMessageItem
import dev.vibex.companion.CollaborationThreadSummary
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun CollaborationScreen(state: UiState, model: CompanionModel) {
    val activeThread = state.selectedThread
    LaunchedEffect(state.selectedHostId, state.selectedProjectId) {
        model.loadCollaboration()
    }
    if (activeThread != null) {
        CollaborationChatPane(state, model, activeThread)
        return
    }
    val colors = VibexTheme.colors
    val host = state.profiles.find { it.hostId == state.selectedHostId }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showCreateBot by remember { mutableStateOf(false) }

    val threads = state.collaborationThreads.filter { item ->
        val matchesKind = when (filter) {
            "dm" -> item.kind == "dm"
            "group" -> item.kind != "dm"
            else -> true
        }
        val matchesQuery = search.isBlank() ||
            item.name.contains(search, ignoreCase = true) ||
            item.subtitle.contains(search, ignoreCase = true)
        matchesKind && matchesQuery
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("协作空间") { ConnectionChip(state.connection, host?.name) }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchField(search, { search = it }, "搜索私聊助手或协作群组")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "全部", "dm" to "私聊助手", "group" to "群组").forEach { (id, label) ->
                        val selected = filter == id
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) colors.onAccent else colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) colors.accent else colors.bgElevated)
                                .border(0.75.dp, if (selected) colors.accent else colors.stroke, RoundedCornerShape(50))
                                .clickable { filter = id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (state.connection.allowsWrites) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PressSurface(onClick = { showCreateBot = true }, radius = 10.dp) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.bgElevated)
                                    .border(0.75.dp, colors.stroke, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.PersonAdd, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                Text("助手", fontSize = 12.sp, color = colors.textPrimary)
                            }
                        }
                        PressSurface(onClick = { showCreateGroup = true }, radius = 10.dp) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accent)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                                Text("群组", fontSize = 12.sp, color = colors.onAccent)
                            }
                        }
                    }
                }
            }
        }

        CompanionRefresh(
            refreshing = state.collaborationLoading,
            onRefresh = model::loadCollaboration,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.collaborationLoading && state.collaborationThreads.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                }
                threads.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.Groups,
                        title = "暂无协作会话",
                        message = "可在此创建一对一 AI 助手私聊或多智能体协作群组。",
                        action = if (state.connection.allowsWrites) "新建协作群组" else null,
                        onAction = if (state.connection.allowsWrites) ({ showCreateGroup = true }) else null,
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(threads, key = { it.id }) { thread ->
                            CollaborationThreadRow(thread) {
                                model.openCollaborationThread(thread)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroup) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Text("新建协作群组") },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("群组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = groupName.isNotBlank(),
                    onClick = {
                        model.createCollaborationGroup(groupName.trim())
                        showCreateGroup = false
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroup = false }) { Text("取消") }
            },
        )
    }

    if (showCreateBot) {
        var botName by remember { mutableStateOf("") }
        var botDesc by remember { mutableStateOf("") }
        val agents = state.catalog?.agents.orEmpty().filter { it.ready }
        var selectedAgent by remember {
            mutableStateOf(agents.firstOrNull()?.id ?: "claude_code")
        }
        AlertDialog(
            onDismissRequest = { showCreateBot = false },
            title = { Text("新建协作助手") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = botName,
                        onValueChange = { botName = it },
                        label = { Text("助手名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = botDesc,
                        onValueChange = { botDesc = it },
                        label = { Text("职责描述（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (agents.isNotEmpty()) {
                        Text("绑定 Agent", fontSize = 12.sp, color = colors.textSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(agents, key = { it.id }) { agent ->
                                val chosen = agent.id == selectedAgent
                                Text(
                                    agent.displayName.ifBlank { agent.id },
                                    fontSize = 12.sp,
                                    color = if (chosen) colors.onAccent else colors.textPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (chosen) colors.accent else colors.bgElevated)
                                        .clickable { selectedAgent = agent.id }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = botName.isNotBlank(),
                    onClick = {
                        model.createCollaborationBot(botName.trim(), selectedAgent, botDesc.trim())
                        showCreateBot = false
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBot = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CollaborationThreadRow(thread: CollaborationThreadSummary, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    val isDm = thread.kind == "dm"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShapeRow)
            .background(colors.bgElevated.copy(if (colors.isDark) 0.65f else 0.92f))
            .border(0.75.dp, colors.stroke, ShapeRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isDm && thread.agentId.isNotBlank()) {
            AgentAvatar(thread.agentId, 40.dp, displayName = thread.name)
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isDm) Icons.Rounded.SmartToy else Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    thread.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isDm) "私聊" else "群组",
                    fontSize = 10.sp,
                    color = colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accent.copy(0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                thread.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (thread.unreadCount > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${thread.unreadCount}", fontSize = 11.sp, color = colors.onAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CollaborationChatPane(
    state: UiState,
    model: CompanionModel,
    thread: CollaborationThreadSummary,
) {
    val colors = VibexTheme.colors
    var draft by rememberSaveable(thread.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressSurface(onClick = model::closeCollaborationThread, radius = 12.dp, modifier = Modifier.size(40.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.textPrimary)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(thread.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, maxLines = 1)
                Text(
                    if (thread.kind == "dm") "私聊助手 · ${thread.agentId.ifBlank { "Agent" }}" else "多智能体协作群组",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            PressSurface(onClick = model::loadCollaboration, radius = 12.dp, modifier = Modifier.size(40.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Refresh, "刷新", tint = colors.textSecondary)
                }
            }
        }

        if (state.collaborationMessages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "开始协作对话",
                    message = "发送消息后，群组内的助手将按座位和职责自动响应。",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.collaborationMessages, key = { it.id }) { msg ->
                    CollaborationMessageBubble(msg)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 92.dp, top = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgElevated)
                .border(0.75.dp, colors.stroke, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("发送协作消息（可用 @ 点名助手）…", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            PressSurface(
                enabled = draft.isNotBlank() && state.connection.allowsWrites,
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        model.sendCollaborationMessage(text)
                        draft = ""
                    }
                },
                radius = 20.dp,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank() && state.connection.allowsWrites) colors.accent else colors.stroke),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "发送",
                        tint = if (draft.isNotBlank() && state.connection.allowsWrites) colors.onAccent else colors.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollaborationMessageBubble(msg: CollaborationMessageItem) {
    val colors = VibexTheme.colors
    val isUser = msg.senderKind.equals("user", ignoreCase = true)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            if (!isUser && msg.agentId.isNotBlank()) {
                AgentAvatar(msg.agentId, 18.dp, displayName = msg.senderName)
            }
            Text(msg.senderName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
            if (msg.seq > 0) {
                Text("#${msg.seq}", fontSize = 11.sp, color = colors.textTertiary, fontFamily = PlexMono)
            }
        }
        Column(
            Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isUser) colors.accent.copy(0.18f) else colors.bgElevated)
                .border(0.75.dp, if (isUser) colors.accent.copy(0.35f) else colors.stroke, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MarkdownContent(msg.content, fillWidth = false)
            if (msg.receipts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    msg.receipts.take(4).forEach { receipt ->
                        Text(
                            receipt,
                            fontSize = 10.sp,
                            color = colors.textTertiary,
                            fontFamily = PlexMono,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.bg.copy(0.5f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
