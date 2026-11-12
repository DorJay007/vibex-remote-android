package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.CatalogProject
import dev.vibex.companion.KanbanSessionStatus
import dev.vibex.companion.KanbanSessionSummary
import dev.vibex.companion.KanbanSortField
import dev.vibex.companion.PlanUsageResponse
import dev.vibex.companion.PlanUsageWindow
import dev.vibex.companion.ProjectUsageStatistics
import dev.vibex.companion.UsageDashboardTab
import dev.vibex.companion.UsageDateRange
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

private enum class KanbanViewMode(val id: String, val title: String, val icon: ImageVector) {
    Columns("columns", "四栏看板", Icons.Rounded.ViewColumn),
    Usage("usage", "计量统计", Icons.Rounded.BarChart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var viewMode by rememberSaveable { mutableStateOf(KanbanViewMode.Columns.id) }
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(state.selectedProjectId) }
    var sessions by remember { mutableStateOf<List<KanbanSessionSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var usageRefreshTrigger by remember { mutableIntStateOf(0) }

    // Modals & Alerts
    var showProjectMenu by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var createInitialStatus by remember { mutableStateOf(KanbanSessionStatus.Todo) }
    var renamingSession by remember { mutableStateOf<KanbanSessionSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deletingSession by remember { mutableStateOf<KanbanSessionSummary?>(null) }

    val projects = state.catalog?.projects.orEmpty()
    val workspaces = state.catalog?.workspaces.orEmpty()
    val selectedProject = projects.find { it.id.equals(selectedProjectId, ignoreCase = true) }

    suspend fun reload() {
        runCatching {
            val loaded = model.fetchKanbanSessions(selectedProjectId)
            sessions = loaded
            errorMessage = null
            isLoading = false
        }.onFailure { err ->
            if (sessions.isEmpty() && state.conversations.isNotEmpty()) {
                sessions = state.conversations.map { conv ->
                    val branch = workspaces.find { it.id == conv.workspaceId }?.branch
                    KanbanSessionSummary.fromConversationSummary(conv, branch)
                }
            }
            errorMessage = err.message ?: "加载看板失败"
            isLoading = false
        }
    }

    LaunchedEffect(state.selectedHostId, selectedProjectId, state.connection) {
        isLoading = sessions.isEmpty()
        while (true) {
            reload()
            delay(if (errorMessage == null) 15_000L else 4_000L)
        }
    }

    val activeSessions = remember(sessions) { sessions.filter { !it.isArchived } }

    fun openSession(session: KanbanSessionSummary) {
        if (session.completesWhenViewed) {
            sessions = sessions.map { if (it.id == session.id) it.copy(status = KanbanSessionStatus.Done) else it }
            scope.launch {
                runCatching { model.mutateKanbanSessionViewed(session.id) }
            }
        }
        model.openConversation(session.id)
    }

    fun performStatusChange(session: KanbanSessionSummary, newStatus: KanbanSessionStatus) {
        val oldStatus = session.status
        sessions = sessions.map { if (it.id == session.id) it.copy(status = newStatus) else it }
        scope.launch {
            runCatching {
                model.mutateKanbanSessionStatus(session.id, newStatus)
                reload()
            }.onFailure { err ->
                sessions = sessions.map { if (it.id == session.id) it.copy(status = oldStatus) else it }
                noticeMessage = err.message ?: "状态修改失败"
            }
        }
    }

    fun performTogglePin(session: KanbanSessionSummary) {
        val nextPinned = !session.isPinned
        sessions = sessions.map {
            if (it.id == session.id) it.copy(pinnedAt = if (nextPinned) Instant.now().toString() else null) else it
        }
        scope.launch {
            runCatching {
                model.mutateKanbanSessionPinned(session.id, nextPinned)
                reload()
            }.onFailure { err ->
                noticeMessage = err.message ?: "置顶操作失败"
                reload()
            }
        }
    }

    fun performRename(session: KanbanSessionSummary, newName: String) {
        val trimmed = newName.trim()
        sessions = sessions.map {
            if (it.id == session.id) it.copy(name = trimmed.ifEmpty { null }) else it
        }
        scope.launch {
            runCatching {
                model.mutateKanbanSessionRename(session.id, trimmed.ifEmpty { null })
                reload()
            }.onFailure { err ->
                noticeMessage = err.message ?: "重命名失败"
                reload()
            }
        }
    }

    fun performDelete(session: KanbanSessionSummary) {
        sessions = sessions.filterNot { it.id == session.id }
        scope.launch {
            runCatching {
                model.mutateKanbanSessionDelete(session.id)
                reload()
            }.onFailure { err ->
                noticeMessage = err.message ?: "删除失败"
                reload()
            }
        }
    }

    fun performFork(session: KanbanSessionSummary) {
        scope.launch {
            runCatching {
                val forkedId = model.mutateKanbanSessionFork(session.id)
                noticeMessage = "已分叉会话"
                reload()
                if (!forkedId.isNullOrBlank()) {
                    model.openConversation(forkedId)
                }
            }.onFailure { err ->
                noticeMessage = "分叉失败：${err.message ?: "未知错误"}"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // Row 1 (Single Navigation Bar):
        // - Columns mode: Project Selector Menu | 看板 | 计量统计 Icon + 新建会话 Icon
        // - Usage mode: ← 看板 | 计量统计 | ↻ 刷新
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (viewMode == KanbanViewMode.Columns.id) {
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
                        Icon(
                            imageVector = if (selectedProject == null) Icons.Rounded.Layers else Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            selectedProject?.name ?: "全部项目",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 115.dp),
                        )
                        Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = showProjectMenu, onDismissRequest = { showProjectMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("全部项目") },
                            leadingIcon = { Icon(Icons.Rounded.Layers, null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = if (selectedProjectId == null) {
                                { Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                            } else null,
                            onClick = {
                                showProjectMenu = false
                                selectedProjectId = null
                            },
                        )
                        HorizontalDivider()
                        projects.forEach { proj ->
                            val isSelected = selectedProjectId?.equals(proj.id, ignoreCase = true) == true
                            DropdownMenuItem(
                                text = { Text(proj.name) },
                                leadingIcon = { Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = if (isSelected) {
                                    { Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showProjectMenu = false
                                    selectedProjectId = proj.id
                                    model.selectProjectInPlace(proj.id)
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

                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.BarChart,
                        contentDescription = "计量统计",
                        tint = colors.accent,
                        modifier = Modifier
                            .size(21.dp)
                            .clickable { viewMode = KanbanViewMode.Usage.id },
                    )
                    Icon(
                        Icons.Rounded.AddCircle,
                        contentDescription = "新建会话",
                        tint = colors.accent,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                createInitialStatus = KanbanSessionStatus.Todo
                                model.loadCatalogSilent()
                                showCreateSheet = true
                            },
                    )
                }
            } else {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .clickable { viewMode = KanbanViewMode.Columns.id }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBackIos,
                        contentDescription = "返回看板",
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "看板",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent,
                    )
                }

                Text(
                    "计量统计",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )

                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "刷新统计",
                    tint = colors.accent,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(21.dp)
                        .clickable { usageRefreshTrigger++ },
                )
            }
        }

        HorizontalDivider(color = colors.hairline)

        if (noticeMessage != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bgElevated)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Info, null, tint = colors.accent, modifier = Modifier.size(15.dp))
                Text(
                    noticeMessage.orEmpty(),
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { noticeMessage = null },
                )
            }
        }

        when (viewMode) {
            KanbanViewMode.Columns.id -> {
                ColumnsBoardContent(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    activeSessions = activeSessions,
                    onReload = { scope.launch { reload() } },
                    onOpenUsage = { viewMode = KanbanViewMode.Usage.id },
                    onCreateInColumn = { colStatus ->
                        createInitialStatus = colStatus
                        showCreateSheet = true
                    },
                    onOpenSession = ::openSession,
                    onStatusChange = ::performStatusChange,
                    onTogglePin = ::performTogglePin,
                    onRequestRename = { target ->
                        renameDraft = target.resolvedTitle
                        renamingSession = target
                    },
                    onFork = ::performFork,
                    onCopyText = { text, msg ->
                        clipboard.setText(AnnotatedString(text))
                        noticeMessage = msg
                    },
                    onRequestDelete = { deletingSession = it },
                )
            }

            else -> {
                KanbanUsageDashboardView(
                    model = model,
                    projects = projects,
                    initialProjectId = selectedProjectId,
                    refreshTrigger = usageRefreshTrigger,
                )
            }
        }
    }

    if (showCreateSheet) {
        NewConversationSheet(state, model) {
            showCreateSheet = false
            scope.launch { reload() }
        }
    }

    if (renamingSession != null) {
        val target = renamingSession!!
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("会话名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        performRename(target, renameDraft)
                        renamingSession = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) { Text("取消") }
            },
        )
    }

    if (deletingSession != null) {
        val target = deletingSession!!
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            title = { Text("删除会话") },
            text = { Text("确定删除会话“${target.resolvedTitle}”吗？正在执行中的会话不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        performDelete(target)
                        deletingSession = null
                    },
                ) { Text("删除", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSession = null }) { Text("取消") }
            },
        )
    }
}

// ============================================================================
// MARK: - 1. Four-Column Kanban Board (`columnsBoardContent`)
// ============================================================================

@Composable
private fun ColumnsBoardContent(
    isLoading: Boolean,
    errorMessage: String?,
    activeSessions: List<KanbanSessionSummary>,
    onReload: () -> Unit,
    onOpenUsage: () -> Unit,
    onCreateInColumn: (KanbanSessionStatus) -> Unit,
    onOpenSession: (KanbanSessionSummary) -> Unit,
    onStatusChange: (KanbanSessionSummary, KanbanSessionStatus) -> Unit,
    onTogglePin: (KanbanSessionSummary) -> Unit,
    onRequestRename: (KanbanSessionSummary) -> Unit,
    onFork: (KanbanSessionSummary) -> Unit,
    onCopyText: (String, String) -> Unit,
    onRequestDelete: (KanbanSessionSummary) -> Unit,
) {
    val colors = VibexTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var didTriggerFullPullHaptic by remember { mutableStateOf(false) }
    val refreshThresholdPx = with(density) { 45.dp.toPx() }
    val usageThresholdPx = with(density) { 100.dp.toPx() }
    val pullOffsetDp = with(density) { pullOffsetPx.toDp() }

    if (isLoading && activeSessions.isEmpty()) {
        KanbanBoardSkeletonView(modifier = Modifier.fillMaxSize())
        return
    }

    if (errorMessage != null && activeSessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Warning,
                title = "加载失败",
                message = errorMessage,
                action = "重试",
                onAction = onReload,
            )
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f || pullOffsetPx > 0f) {
                            val next = (pullOffsetPx + dragAmount * 0.65f).coerceIn(0f, usageThresholdPx * 1.35f)
                            pullOffsetPx = next
                            if (next >= usageThresholdPx && !didTriggerFullPullHaptic) {
                                didTriggerFullPullHaptic = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (next < usageThresholdPx) {
                                didTriggerFullPullHaptic = false
                            }
                        }
                    },
                    onDragEnd = {
                        val finalPx = pullOffsetPx
                        pullOffsetPx = 0f
                        didTriggerFullPullHaptic = false
                        if (finalPx >= usageThresholdPx) {
                            onOpenUsage()
                        } else if (finalPx >= refreshThresholdPx) {
                            onReload()
                        }
                    },
                    onDragCancel = {
                        pullOffsetPx = 0f
                        didTriggerFullPullHaptic = false
                    },
                )
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columnWidth = minOf(maxWidth - 52.dp, 344.dp).coerceAtLeast(260.dp)
            val boardListState = rememberLazyListState()
            LazyRow(
                state = boardListState,
                flingBehavior = rememberSnapFlingBehavior(boardListState),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp + (pullOffsetDp * 0.35f).coerceAtMost(42.dp),
                    bottom = 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(KanbanSessionStatus.activeColumns, key = { it.rawValue }) { colStatus ->
                    val colItems = remember(activeSessions, colStatus) {
                        sortKanbanSessions(activeSessions.filter { it.status == colStatus }, KanbanSortField.DefaultOrder)
                    }
                    KanbanColumnCard(
                        status = colStatus,
                        width = columnWidth,
                        items = colItems,
                        onCreateInColumn = { onCreateInColumn(colStatus) },
                        onOpenSession = onOpenSession,
                        onStatusChange = onStatusChange,
                        onTogglePin = onTogglePin,
                        onRequestRename = onRequestRename,
                        onFork = onFork,
                        onCopyText = onCopyText,
                        onRequestDelete = onRequestDelete,
                    )
                }
            }
        }

        if (pullOffsetPx > with(density) { 8.dp.toPx() }) {
            val isDeepPull = pullOffsetPx >= usageThresholdPx
            val isHalfPull = pullOffsetPx >= refreshThresholdPx && !isDeepPull
            val label = when {
                isDeepPull -> "松手打开计量统计"
                isHalfPull -> "松手刷新看板 · 继续下拉打开计量统计"
                else -> "下拉刷新 · 拉到底打开计量统计"
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (isDeepPull) colors.accent else colors.bgElevated)
                    .border(0.5.dp, colors.stroke, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isDeepPull) Icons.Rounded.BarChart else Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = if (isDeepPull) Color.White else colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDeepPull) Color.White else colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun KanbanColumnCard(
    status: KanbanSessionStatus,
    width: Dp,
    items: List<KanbanSessionSummary>,
    onCreateInColumn: () -> Unit,
    onOpenSession: (KanbanSessionSummary) -> Unit,
    onStatusChange: (KanbanSessionSummary, KanbanSessionStatus) -> Unit,
    onTogglePin: (KanbanSessionSummary) -> Unit,
    onRequestRename: (KanbanSessionSummary) -> Unit,
    onFork: (KanbanSessionSummary) -> Unit,
    onCopyText: (String, String) -> Unit,
    onRequestDelete: (KanbanSessionSummary) -> Unit,
) {
    val colors = VibexTheme.colors
    val colColor = statusColor(status)

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bgElevated)
            .border(0.5.dp, colors.stroke, RoundedCornerShape(16.dp)),
    ) {
        // Column Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(colColor.copy(0.06f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colColor))
            Text(status.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text("${items.size}", fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = PlexMono, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = 0.06f))
                    .clickable(onClick = onCreateInColumn),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "新建会话", tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            }
        }

        if (items.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("暂无${status.title}会话", fontSize = 12.sp, color = colors.textSecondary)
                Row(
                    Modifier.clickable(onClick = onCreateInColumn),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Text("新建会话", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.accent)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { session ->
                    KanbanSessionItemCard(
                        session = session,
                        showStatusPill = false,
                        onOpenSession = { onOpenSession(session) },
                        onStatusChange = { onStatusChange(session, it) },
                        onTogglePin = { onTogglePin(session) },
                        onRequestRename = { onRequestRename(session) },
                        onFork = { onFork(session) },
                        onCopyText = onCopyText,
                        onRequestDelete = { onRequestDelete(session) },
                    )
                }
            }
        }
    }
}

// ============================================================================
// MARK: - 2. Usage Dashboard (`KanbanUsageDashboardView.swift` 1:1 Parity, <= 2 Rows)
// ============================================================================

private data class PlanAgentDescriptor(
    val id: String,
    val title: String,
    val sourceNote: String,
)

private val planAgentDescriptors = listOf(
    PlanAgentDescriptor("claude_code", "Claude Code 套餐", "读取设置里的 Claude 订阅凭据"),
    PlanAgentDescriptor("codex", "Codex 套餐", "读取已安装的 Codex 与账号限额"),
    PlanAgentDescriptor("cursor", "Cursor 套餐", "读取本机 Cursor 登录凭据与官方用量接口"),
    PlanAgentDescriptor("grok", "Grok 套餐", "读取本机 Grok 登录凭据与官方套餐接口"),
    PlanAgentDescriptor("opencode", "OpenCode Go 套餐", "读取 OpenCode Go 订阅凭据与官方用量接口"),
    PlanAgentDescriptor("kimi_code", "Kimi 套餐", "读取 Kimi Code 订阅凭据与官方用量接口"),
    PlanAgentDescriptor("antigravity", "Antigravity 套餐", "读取设置里的 Antigravity 订阅凭据"),
)

private fun usageTabIcon(tab: UsageDashboardTab): ImageVector = when (tab) {
    UsageDashboardTab.Overview -> Icons.Rounded.TrendingUp
    UsageDashboardTab.Folders -> Icons.Rounded.Folder
    UsageDashboardTab.Agents -> Icons.Rounded.Bolt
    UsageDashboardTab.Models -> Icons.Rounded.Tag
    UsageDashboardTab.Sessions -> Icons.AutoMirrored.Rounded.List
    UsageDashboardTab.Plan -> Icons.Rounded.Inventory2
}

@Composable
private fun KanbanUsageDashboardView(
    model: CompanionModel,
    projects: List<CatalogProject>,
    initialProjectId: String?,
    refreshTrigger: Int = 0,
) {
    val colors = VibexTheme.colors
    val scope = rememberCoroutineScope()

    var selectedTarget by rememberSaveable {
        mutableStateOf(if (!initialProjectId.isNullOrBlank()) "project:$initialProjectId" else "global")
    }
    var dateRange by remember { mutableStateOf(UsageDateRange.SevenDays) }
    var activeTab by remember { mutableStateOf(UsageDashboardTab.Overview) }
    var statistics by remember { mutableStateOf<ProjectUsageStatistics?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sessionSortByCost by rememberSaveable { mutableStateOf(true) }
    var showTabMenu by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }

    // Plan usage state
    var selectedPlanAgent by remember { mutableStateOf(planAgentDescriptors.first()) }
    val planUsageByAgent = remember { mutableStateMapOf<String, PlanUsageResponse>() }
    var isLoadingPlan by remember { mutableStateOf(false) }

    suspend fun reloadStatistics() {
        isLoading = true
        val sc = if (selectedTarget == "global") "global" else "project"
        val pid = if (selectedTarget == "global") null else selectedTarget.removePrefix("project:")
        runCatching {
            statistics = model.fetchUsageStatistics(sc, pid, dateRange)
            errorMessage = null
        }.onFailure { err ->
            errorMessage = err.message ?: "读取计量统计失败"
        }
        isLoading = false
    }

    suspend fun loadPlanUsage(agentId: String, force: Boolean = false) {
        if (!force && planUsageByAgent[agentId] != null) return
        isLoadingPlan = true
        val res = model.fetchAgentPlanUsage(agentId)
        planUsageByAgent[agentId] = res
        isLoadingPlan = false
    }

    LaunchedEffect(selectedTarget, dateRange) {
        reloadStatistics()
    }

    LaunchedEffect(activeTab, selectedPlanAgent.id) {
        if (activeTab == UsageDashboardTab.Plan) {
            loadPlanUsage(selectedPlanAgent.id)
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            if (activeTab == UsageDashboardTab.Plan) {
                loadPlanUsage(selectedPlanAgent.id, force = true)
            } else {
                reloadStatistics()
            }
        }
    }

    val selectedTargetLabel = remember(selectedTarget, projects) {
        if (selectedTarget == "global") "全局"
        else {
            val id = selectedTarget.removePrefix("project:")
            projects.find { it.id.equals(id, ignoreCase = true) }?.name ?: "当前项目"
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Row 2 (Unified Filter Row): Left [概览 ▾] + [全局 ▾] closely grouped (6.dp) | Right [7天 / 30天 / 全部]
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 1. Dimension Dropdown (概览 / 目录 / Agent / 模型 / 会话 / 套餐)
                Box {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.bgElevated)
                            .border(0.5.dp, colors.stroke, CircleShape)
                            .clickable { showTabMenu = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = usageTabIcon(activeTab),
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            activeTab.label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                        Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                    }
                    DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }) {
                        UsageDashboardTab.entries.forEach { tab ->
                            DropdownMenuItem(
                                text = { Text(tab.label) },
                                leadingIcon = { Icon(usageTabIcon(tab), null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = if (activeTab == tab) {
                                    { Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showTabMenu = false
                                    activeTab = tab
                                },
                            )
                        }
                    }
                }

                // 2. Scope Dropdown (全局 / 项目) — Placed right next to 概览 ▾
                if (activeTab != UsageDashboardTab.Plan) {
                    Box {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(colors.bgElevated)
                                .border(0.5.dp, colors.stroke, CircleShape)
                                .clickable { showTargetMenu = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (selectedTarget == "global") Icons.Rounded.Public else Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                selectedTargetLabel,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 88.dp),
                            )
                            Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                        }
                        DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("全局") },
                                trailingIcon = if (selectedTarget == "global") {
                                    { Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showTargetMenu = false
                                    selectedTarget = "global"
                                },
                            )
                            projects.forEach { project ->
                                val target = "project:${project.id}"
                                DropdownMenuItem(
                                    text = { Text(project.name) },
                                    trailingIcon = if (selectedTarget == target) {
                                        { Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = {
                                        showTargetMenu = false
                                        selectedTarget = target
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (activeTab != UsageDashboardTab.Plan) {
                Row(
                    Modifier
                        .width(142.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.segmentedTrack)
                        .padding(2.dp),
                ) {
                    UsageDateRange.entries.forEach { range ->
                        val selected = dateRange == range
                        Text(
                            range.label,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selected) Modifier.shadow(1.5.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black.copy(0.08f))
                                    else Modifier,
                                )
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) colors.segmentedThumb else Color.Transparent)
                                .clickable { dateRange = range }
                                .padding(vertical = 5.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.hairline)

        if (activeTab == UsageDashboardTab.Plan) {
            UsagePlanTabContent(
                selectedPlanAgent = selectedPlanAgent,
                onSelectPlanAgent = { selectedPlanAgent = it },
                planUsageByAgent = planUsageByAgent,
                isLoadingPlan = isLoadingPlan,
            )
        } else if (isLoading && statistics == null) {
            KanbanBoardSkeletonView(modifier = Modifier.fillMaxSize())
        } else if (errorMessage != null && statistics == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Warning,
                    title = "统计读取失败",
                    message = errorMessage.orEmpty(),
                    action = "重试",
                    onAction = { scope.launch { reloadStatistics() } },
                )
            }
        } else if (statistics != null) {
            val stats = statistics!!
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (activeTab) {
                    UsageDashboardTab.Overview -> {
                        item { UsageOverviewContent(stats) }
                    }
                    UsageDashboardTab.Folders -> {
                        if (stats.byFolder.isEmpty()) {
                            item { EmptyState(Icons.Rounded.Folder, "暂无目录数据", "当前范围内没有工作区目录记录。") }
                        } else {
                            items(stats.byFolder, key = { "${it.workspaceId}-${it.folder.orEmpty()}" }) { item ->
                                val counts = item.tokens.preferred
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.card)
                                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Folder, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(item.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                        if (item.cost != null) {
                                            Text(formatUsageCost(item.cost), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono, color = colors.pass)
                                        }
                                    }
                                    val folderPath = item.folder
                                    if (!folderPath.isNullOrBlank()) {
                                        Text(folderPath, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("${item.sessionCount} 会话", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("总 Token: ${formatUsageTokens(counts.resolvedTotal)}", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                    UsageDashboardTab.Agents -> {
                        if (stats.byAgent.isEmpty()) {
                            item { EmptyState(Icons.Rounded.Bolt, "暂无 Agent 数据", "当前范围内没有 Agent 统计记录。") }
                        } else {
                            items(stats.byAgent, key = { it.agentId }) { item ->
                                val counts = item.tokens.preferred
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.card)
                                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AgentAvatar(item.agentId, 20.dp)
                                        Text(item.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                        if (item.cost != null) {
                                            Text(formatUsageCost(item.cost), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono, color = colors.pass)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("${item.sessionCount} 会话", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("输入: ${formatUsageTokens(counts.inputTokens)}", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("输出: ${formatUsageTokens(counts.outputTokens)}", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("总计: ${formatUsageTokens(counts.resolvedTotal)}", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                    UsageDashboardTab.Models -> {
                        if (stats.byModel.isEmpty()) {
                            item { EmptyState(Icons.Rounded.Tag, "暂无模型数据", "当前范围内没有模型用量记录。") }
                        } else {
                            items(stats.byModel, key = { it.model }) { item ->
                                val counts = item.tokens.preferred
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.card)
                                        .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.Memory, null, tint = Color(0xFF5856D6), modifier = Modifier.size(16.dp))
                                        Text(item.model, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (item.cost != null) {
                                            Text(formatUsageCost(item.cost), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono, color = colors.pass)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("${item.sessionCount} 会话", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("输入: ${formatUsageTokens(counts.inputTokens)}", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("输出: ${formatUsageTokens(counts.outputTokens)}", fontSize = 12.sp, color = colors.textSecondary)
                                        Text("总 Token: ${formatUsageTokens(counts.resolvedTotal)}", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                    UsageDashboardTab.Sessions -> {
                        val sorted = if (sessionSortByCost) {
                            stats.sessions.sortedByDescending { it.cost ?: -1.0 }
                        } else {
                            stats.sessions.sortedByDescending { it.timestamp }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("会话列表 (${sorted.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Row(
                                    Modifier
                                        .width(136.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.segmentedTrack)
                                        .padding(2.dp),
                                ) {
                                    listOf(true to "按费用", false to "按时间").forEach { (byCost, label) ->
                                        val selected = sessionSortByCost == byCost
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (selected) colors.textPrimary else colors.textSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (selected) Modifier.shadow(1.5.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black.copy(0.08f))
                                                    else Modifier,
                                                )
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (selected) colors.segmentedThumb else Color.Transparent)
                                                .clickable { sessionSortByCost = byCost }
                                                .padding(vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        items(sorted.take(50), key = { it.sessionId }) { item ->
                            val counts = item.tokens.preferred
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.card)
                                    .border(0.75.dp, colors.stroke, RoundedCornerShape(12.dp))
                                    .clickable { model.openConversation(item.sessionId) }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.displayTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (item.cost != null) {
                                        Text(formatUsageCost(item.cost), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono, color = colors.pass)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val itemAgentId = item.agentId
                                    if (!itemAgentId.isNullOrBlank()) {
                                        Row(
                                            Modifier
                                                .clip(CircleShape)
                                                .background(colors.accent.copy(0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            AgentAvatar(itemAgentId, 11.dp)
                                            Text(agentDisplayName(itemAgentId), fontSize = 10.sp, color = colors.textPrimary)
                                        }
                                    }
                                    val itemModel = item.model
                                    if (!itemModel.isNullOrBlank()) {
                                        Text(itemModel, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1)
                                    }
                                    if ((item.turns ?: 0) > 0) {
                                        Text("${item.turns} 轮次", fontSize = 11.sp, color = colors.textSecondary)
                                    }
                                    Text("${formatUsageTokens(counts.resolvedTotal)} Token", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                    UsageDashboardTab.Plan -> Unit
                }
            }
        }
    }
}

@Composable
private fun UsageOverviewContent(stats: ProjectUsageStatistics) {
    val colors = VibexTheme.colors
    val preferred = stats.totalTokens.preferred

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 2x2 Summary Metric Grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UsageMetricCard(
                title = "总费用",
                value = formatUsageCost(stats.resolvedCost),
                subtitle = if (stats.totalSessions > 0 && stats.resolvedCost != null) {
                    "平均 ${formatUsageCost(stats.resolvedCost!! / stats.totalSessions.toDouble())}/会话"
                } else "按协议与日志估算",
                icon = Icons.Rounded.Paid,
                tint = Color(0xFF34C759),
                modifier = Modifier.weight(1f),
            )
            UsageMetricCard(
                title = "总会话",
                value = "${stats.totalSessions}",
                subtitle = "${stats.activeDaysCount} 个活跃日",
                icon = Icons.Rounded.ChatBubble,
                tint = Color(0xFF007AFF),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UsageMetricCard(
                title = "总 Token",
                value = formatUsageTokens(preferred.resolvedTotal),
                subtitle = "平均每次 ${formatUsageTokens(stats.avgTokensPerSession)} Token",
                icon = Icons.Rounded.Memory,
                tint = Color(0xFFAF52DE),
                modifier = Modifier.weight(1f),
            )
            UsageMetricCard(
                title = "缓存命中率",
                value = stats.cacheHitRatePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--",
                subtitle = "缓存读取 ${formatUsageTokens(preferred.cacheReadTokens)}",
                icon = Icons.Rounded.Bolt,
                tint = Color(0xFFFF9500),
                modifier = Modifier.weight(1f),
            )
        }

        // Token Composition
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.card)
                .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Token 构成", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            val total = preferred.resolvedTotal.coerceAtLeast(1L).toFloat()
            listOf(
                Triple("输入", preferred.inputTokens, Color(0xFF007AFF)),
                Triple("输出", preferred.outputTokens, Color(0xFFAF52DE)),
                Triple("缓存写入", preferred.cacheWriteTokens, Color(0xFFFF9500)),
                Triple("缓存读取", preferred.cacheReadTokens, Color(0xFF34C759)),
            ).forEach { (label, count, barColor) ->
                val ratio = (count.toFloat() / total).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(barColor))
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(formatUsageTokens(count), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexMono, color = colors.textPrimary)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(colors.segmentedTrack),
                    ) {
                        if (count > 0L) {
                            Box(
                                Modifier
                                    .fillMaxWidth(ratio.coerceAtLeast(0.02f))
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(barColor),
                            )
                        }
                    }
                }
            }
        }

        // Daily Trend
        if (stats.dailyUsage.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("用量趋势", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${stats.dailyUsage.size} 天", fontSize = 12.sp, color = colors.textSecondary)
                }
                val maxTokens = (stats.dailyUsage.maxOfOrNull { it.tokens.preferred.resolvedTotal } ?: 1L).coerceAtLeast(1L).toFloat()
                stats.dailyUsage.takeLast(14).reversed().forEach { day ->
                    val dayTokens = day.tokens.preferred.resolvedTotal
                    val ratio = (dayTokens.toFloat() / maxTokens).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(day.date, fontSize = 12.sp, fontFamily = PlexMono, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            Text("${day.sessions} 会话", fontSize = 11.sp, color = colors.textSecondary)
                            Text("${formatUsageTokens(dayTokens)} Token", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexMono, color = colors.textPrimary)
                            if (day.cost != null) {
                                Text(formatUsageCost(day.cost), fontSize = 12.sp, fontFamily = PlexMono, color = colors.pass)
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(colors.segmentedTrack),
                        ) {
                            if (dayTokens > 0L) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(ratio.coerceAtLeast(0.02f))
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Models
        if (stats.byModel.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("常用模型", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                stats.byModel.take(5).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.model, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${item.sessionCount} 会话", fontSize = 12.sp, color = colors.textSecondary)
                        Text(formatUsageTokens(item.tokens.preferred.resolvedTotal), fontSize = 12.sp, fontFamily = PlexMono, color = colors.textSecondary)
                        if (item.cost != null) {
                            Text(formatUsageCost(item.cost), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsagePlanTabContent(
    selectedPlanAgent: PlanAgentDescriptor,
    onSelectPlanAgent: (PlanAgentDescriptor) -> Unit,
    planUsageByAgent: Map<String, PlanUsageResponse>,
    isLoadingPlan: Boolean,
) {
    val colors = VibexTheme.colors
    val response = planUsageByAgent[selectedPlanAgent.id]

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(planAgentDescriptors, key = { it.id }) { descriptor ->
                    val isSelected = selectedPlanAgent.id == descriptor.id
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accent else colors.bgElevated)
                            .clickable { onSelectPlanAgent(descriptor) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        AgentAvatar(descriptor.id, 14.dp)
                        Text(
                            agentDisplayName(descriptor.id),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.textPrimary,
                        )
                    }
                }
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(selectedPlanAgent.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text(selectedPlanAgent.sourceNote, fontSize = 12.sp, color = colors.textSecondary)
                    }
                    if (response is PlanUsageResponse.Ok && !response.usage.planType.isNullOrBlank()) {
                        Text(
                            "${response.usage.planType!!.uppercase()} 套餐",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.accent.copy(0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                when {
                    isLoadingPlan && response == null -> {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(24.dp))
                        }
                    }
                    response is PlanUsageResponse.Ok -> {
                        if (response.usage.windows.isEmpty()) {
                            Text("账号未返回任何限额窗口", fontSize = 13.sp, color = colors.textSecondary)
                        } else {
                            response.usage.windows.forEach { window ->
                                PlanWindowRow(window)
                            }
                        }
                        val credits = response.usage.credits
                        if (credits != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (colors.isDark) colors.codeSurface else colors.bg)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.CreditCard, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                                Text(
                                    if (credits.unlimited) "额度不限量" else "赠送额度余额：${credits.balance ?: "--"}",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                    response is PlanUsageResponse.Unavailable -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Info, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Text(response.unavailableDescription, fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                    response is PlanUsageResponse.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Warning, null, tint = colors.hold, modifier = Modifier.size(16.dp))
                            Text(response.message, fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanWindowRow(window: PlanUsageWindow) {
    val colors = VibexTheme.colors
    val used = window.clampedUsedPercent
    val remaining = window.remainingPercent
    val barColor = when {
        used == null -> colors.accent
        used >= 90 -> colors.danger
        used >= 70 -> colors.hold
        else -> colors.accent
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (colors.isDark) colors.codeSurface else colors.bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(window.localizedTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                val reset = window.resetLabel()
                if (!reset.isNullOrBlank()) {
                    Text(reset, fontSize = 12.sp, color = colors.textSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(used?.let { "已用 $it%" } ?: "--", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexMono, color = colors.textPrimary)
                Text(remaining?.let { "剩余 $it%" } ?: "--", fontSize = 12.sp, fontFamily = PlexMono, color = colors.textSecondary)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.segmentedTrack),
        ) {
            val ratio = ((used ?: 0) / 100f).coerceIn(0f, 1f)
            if (ratio > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
        }
    }
}

@Composable
private fun UsageMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 12.sp, color = colors.textSecondary)
        }
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = PlexMono, color = colors.textPrimary, maxLines = 1)
        Text(subtitle, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================================================================
// MARK: - Reusable Session Card & Context Menu (`BoardView.sessionCard` Parity)
// ============================================================================

@Composable
private fun KanbanSessionItemCard(
    session: KanbanSessionSummary,
    showStatusPill: Boolean,
    onOpenSession: () -> Unit,
    onStatusChange: (KanbanSessionStatus) -> Unit,
    onTogglePin: () -> Unit,
    onRequestRename: () -> Unit,
    onFork: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = VibexTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    val member = session.collaboration?.memberName?.takeIf { it.isNotBlank() }
    val showsStatus = !session.isRunning && (showStatusPill || session.status == KanbanSessionStatus.InReview)
    val accentColor = if (session.isRunning) Color(0xFFFF9500) else statusColor(session.status)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(
                width = if (session.isRunning) 1.dp else 0.6.dp,
                color = if (session.isRunning) Color(0xFFFF9500).copy(0.4f) else colors.stroke,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onOpenSession),
    ) {
        if (session.isRunning || session.status == KanbanSessionStatus.InReview) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 1.5.dp, top = 10.dp, bottom = 10.dp)
                    .width(3.dp)
                    .heightIn(min = 24.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (session.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.padding(top = 2.dp).size(12.dp),
                    )
                }
                Text(
                    session.resolvedTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "菜单",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showMenu = true },
                    )
                    KanbanSessionContextMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        session = session,
                        onOpenSession = onOpenSession,
                        onStatusChange = onStatusChange,
                        onTogglePin = onTogglePin,
                        onRequestRename = onRequestRename,
                        onFork = onFork,
                        onCopyText = onCopyText,
                        onRequestDelete = onRequestDelete,
                    )
                }
            }

            val prompt = session.firstPrompt?.trim()
            if (!prompt.isNullOrEmpty() && prompt != session.resolvedTitle) {
                val cleanedPrompt = remember(prompt) { cleanMarkdownPreview(prompt) }
                Text(
                    cleanedPrompt,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (session.isRunning || showsStatus || member != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (session.isRunning) {
                        Text(
                            "进行中",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF9500),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFFF9500).copy(0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else if (showsStatus) {
                        val stColor = statusColor(session.status)
                        Text(
                            session.status.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stColor,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(stColor.copy(0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (member != null) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF5856D6).copy(0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(Icons.Rounded.Groups, null, tint = Color(0xFF5856D6), modifier = Modifier.size(10.dp))
                            Text(member, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5856D6), maxLines = 1)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AgentAvatar(session.effectiveAgentKey, 13.dp)
                Text(
                    session.model?.takeIf { it.isNotBlank() } ?: session.agentDisplayLabel,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.workspaceDisplayLabel.isNotBlank()) {
                    Text("·", fontSize = 11.sp, color = colors.textTertiary)
                    Text(
                        session.workspaceDisplayLabel,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    shortRelativeIso(session.updatedAt),
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun KanbanSessionContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    session: KanbanSessionSummary,
    onOpenSession: () -> Unit,
    onStatusChange: (KanbanSessionStatus) -> Unit,
    onTogglePin: () -> Unit,
    onRequestRename: () -> Unit,
    onFork: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val colors = VibexTheme.colors
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("打开会话") },
            leadingIcon = { Icon(Icons.Rounded.ChatBubble, null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismiss()
                onOpenSession()
            },
        )
        KanbanSessionStatus.activeColumns.forEach { st ->
            if (st != session.status) {
                DropdownMenuItem(
                    text = { Text("移至：${st.title}") },
                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        onDismiss()
                        onStatusChange(st)
                    },
                )
            }
        }
        DropdownMenuItem(
            text = { Text(if (session.isPinned) "取消置顶" else "置顶") },
            leadingIcon = { Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismiss()
                onTogglePin()
            },
        )
        DropdownMenuItem(
            text = { Text("重命名会话") },
            leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismiss()
                onRequestRename()
            },
        )
        if (session.isForkSupported) {
            DropdownMenuItem(
                text = { Text("从本会话 Fork") },
                leadingIcon = { Icon(Icons.Rounded.CallSplit, null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismiss()
                    onFork()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("复制会话名称") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismiss()
                onCopyText(session.resolvedTitle, "已复制会话名称")
            },
        )
        DropdownMenuItem(
            text = { Text("复制会话信息") },
            leadingIcon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismiss()
                onCopyText(session.sessionInfoText, "已复制会话信息")
            },
        )
        HorizontalDivider()
        if (session.isArchived) {
            DropdownMenuItem(
                text = { Text("移至待办") },
                leadingIcon = { Icon(Icons.Rounded.Unarchive, null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismiss()
                    onStatusChange(KanbanSessionStatus.Todo)
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text("归档") },
                leadingIcon = { Icon(Icons.Rounded.Archive, null, modifier = Modifier.size(16.dp)) },
                onClick = {
                    onDismiss()
                    onStatusChange(KanbanSessionStatus.Archived)
                },
            )
        }
        DropdownMenuItem(
            text = { Text("删除会话", color = colors.danger) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = colors.danger, modifier = Modifier.size(16.dp)) },
            onClick = {
                onDismiss()
                onRequestDelete()
            },
        )
    }
}

// ============================================================================
// MARK: - Formatting & Sorting Helpers
// ============================================================================

private fun statusColor(status: KanbanSessionStatus): Color = when (status) {
    KanbanSessionStatus.Todo -> Color(0xFF007AFF)
    KanbanSessionStatus.InProgress -> Color(0xFFFF9500)
    KanbanSessionStatus.InReview -> Color(0xFFAF52DE)
    KanbanSessionStatus.Done -> Color(0xFF34C759)
    KanbanSessionStatus.Archived -> Color(0xFF8E8E93)
}

private fun sortKanbanSessions(
    list: List<KanbanSessionSummary>,
    field: KanbanSortField,
): List<KanbanSessionSummary> {
    return list.sortedWith { left, right ->
        if (left.isPinned != right.isPinned) {
            return@sortedWith if (left.isPinned) -1 else 1
        }
        when (field) {
            KanbanSortField.DefaultOrder, KanbanSortField.Time -> right.updatedAt.compareTo(left.updatedAt)
            KanbanSortField.Name -> {
                val cmp = left.resolvedTitle.compareTo(right.resolvedTitle, ignoreCase = true)
                if (cmp != 0) cmp else right.updatedAt.compareTo(left.updatedAt)
            }
            KanbanSortField.Status -> {
                if (left.status.sortOrder != right.status.sortOrder) {
                    left.status.sortOrder.compareTo(right.status.sortOrder)
                } else right.updatedAt.compareTo(left.updatedAt)
            }
            KanbanSortField.Agent -> {
                val cmp = left.agentDisplayLabel.compareTo(right.agentDisplayLabel, ignoreCase = true)
                if (cmp != 0) cmp else right.updatedAt.compareTo(left.updatedAt)
            }
        }
    }
}

private fun parseIsoMillis(iso: String): Long? =
    runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

private fun shortRelativeIso(iso: String): String {
    if (iso.isBlank()) return "刚刚"
    val ms = parseIsoMillis(iso)
    if (ms != null) {
        val diffSec = (System.currentTimeMillis() - ms) / 1000L
        return when {
            diffSec < 60L -> "刚刚"
            diffSec < 3600L -> "${diffSec / 60L}分钟前"
            diffSec < 86400L -> "${diffSec / 3600L}小时前"
            diffSec < 86400L * 30L -> "${diffSec / 86400L}天前"
            else -> iso.take(10)
        }
    }
    return iso.substringAfter('T').take(5).ifBlank { iso.take(10) }
}

private fun formatUsageCost(cost: Double?): String {
    if (cost == null) return "--"
    if (cost == 0.0) return "$0.00"
    if (cost < 0.01) return String.format(Locale.US, "$%.4f", cost)
    return String.format(Locale.US, "$%.2f", cost)
}

private fun cleanMarkdownPreview(raw: String): String =
    raw
        .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), "")
        .replace("```", "")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun formatUsageTokens(count: Long): String = when {
    count >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", count / 1_000_000_000.0)
    count >= 1_000_000L -> String.format(Locale.US, "%.2fM", count / 1_000_000.0)
    count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> "$count"
}
