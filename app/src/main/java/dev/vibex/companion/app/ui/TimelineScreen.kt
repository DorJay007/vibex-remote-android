package dev.vibex.companion.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.CatalogAgent
import dev.vibex.companion.FileChangeItem
import dev.vibex.companion.QueuedInput
import dev.vibex.companion.SessionConfigOption
import dev.vibex.companion.TimelineItem
import dev.vibex.companion.TimelineSegment
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.app.ui.timeline.SessionInfoSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RunningOrange = Color(0xFFFF9500)

/**
 * 1:1 port of iOS `ConversationDetailView` (`ConversationDetailView.swift`).
 */
@Composable
fun TimelinePane(
    state: UiState,
    model: CompanionModel,
    conversationId: String,
    onBack: (() -> Unit)? = null,
) {
    val colors = VibexTheme.colors
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val view = state.timeline?.takeIf { it.conversationId == conversationId }
    val summary = state.conversations.find { it.id == conversationId }
    val agentId = view?.agentId?.takeIf { it.isNotBlank() } ?: summary?.agentId.orEmpty()
    val agent = catalogAgent(state, agentId)
    val workspaceId = remember(view?.workspaceId, summary?.workspaceId, state.selectedProjectId, state.catalog) {
        view?.workspaceId?.takeIf { it.isNotBlank() && !it.startsWith("/") }
            ?: summary?.workspaceId?.takeIf { it.isNotBlank() && !it.startsWith("/") }
            ?: state.catalog?.workspaces.orEmpty()
                .firstOrNull { state.selectedProjectId == null || it.projectId == state.selectedProjectId }?.id
            ?: state.catalog?.workspaces?.firstOrNull()?.id.orEmpty()
    }
    LaunchedEffect(conversationId, workspaceId) {
        if (workspaceId.isNotBlank() && view?.workspaceId != workspaceId) {
            model.preferWorkspace(conversationId, workspaceId)
        }
    }

    val modes = view?.sessionModes?.takeIf { it.isNotEmpty() } ?: agent?.sessionModes.orEmpty()
    val currentModeId = view?.currentModeId?.takeIf { it.isNotBlank() } ?: agent?.currentModeId.orEmpty()
    val configOptions = view?.sessionConfig?.takeIf { it.isNotEmpty() } ?: agent?.sessionConfig.orEmpty()

    val currentModelLabel = remember(configOptions, agent, agentId) {
        val modelOpt = configOptions.firstOrNull { opt ->
            val k = opt.key.lowercase()
            val c = opt.category.lowercase()
            c == "model" || k == "model" || k.contains("model")
        }
        val selected = modelOpt?.choices?.firstOrNull { it.value == modelOpt.value }?.label ?: modelOpt?.value
        selected?.takeIf { it.isNotBlank() }
            ?: agent?.displayName?.takeIf { it.isNotBlank() }
            ?: agentDisplayName(agentId, "")
    }

    val items = view?.items.orEmpty()
    val isTurnRunning = view?.inFlightTurnId != null

    var workspaceDiffCount by remember(conversationId, workspaceId) { mutableIntStateOf(0) }
    LaunchedEffect(conversationId, workspaceId, isTurnRunning) {
        if (workspaceId.isNotBlank() && !isTurnRunning) {
            model.fetchWorkspaceDiff(workspaceId, conversationId) { result ->
                result.getOrNull()?.let { diffs ->
                    workspaceDiffCount = diffs.size
                }
            }
        }
    }
    val totalChangeCount = remember(workspaceDiffCount, view?.fileChanges, items) {
        val turnChanges = items.flatMap { it.changes }.map { it.path }.toSet().size
        maxOf(workspaceDiffCount, view?.fileChanges?.size ?: 0, turnChanges)
    }

    var input by remember(conversationId) { mutableStateOf("") }
    var menu by remember(conversationId) { mutableStateOf<ComposerMenu?>(null) }
    BackHandler(enabled = menu != null) {
        menu = null
    }
    var respondingPermission by remember(conversationId) { mutableStateOf(false) }
    var respondingQuestion by remember(conversationId) { mutableStateOf(false) }

    var showFiles by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var diffTarget by remember { mutableStateOf<FileChangeItem?>(null) }
    var showSessionSheet by remember { mutableStateOf(false) }
    var showNewConvSheet by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var pinnedToBottom by remember(conversationId) { mutableStateOf(true) }
    var unreadUpdates by remember(conversationId) { mutableIntStateOf(0) }
    var prevCount by remember(conversationId) { mutableIntStateOf(0) }

    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            if (total == 0) true
            else {
                val lastVisible = layout.visibleItemsInfo.lastOrNull()
                lastVisible != null && lastVisible.index >= total - 2
            }
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            pinnedToBottom = true
            unreadUpdates = 0
        } else if (listState.isScrollInProgress) {
            pinnedToBottom = false
        }
    }

    val tailSignature = remember(items, isTurnRunning) {
        val last = items.lastOrNull()
        "${items.size}:${last?.id}:${last?.text?.length}:${last?.thinking?.length}:${last?.tools?.size}:${isTurnRunning}"
    }

    LaunchedEffect(tailSignature) {
        val total = listState.layoutInfo.totalItemsCount
        if (total == 0) return@LaunchedEffect
        if (pinnedToBottom) {
            listState.scrollToItem((total - 1).coerceAtLeast(0))
        } else if (items.size > prevCount) {
            unreadUpdates += (items.size - prevCount).coerceAtLeast(1)
        }
        prevCount = items.size
    }

    val conversationTitle = view?.title?.takeIf { it.isNotBlank() }
        ?: summary?.title?.takeIf { it.isNotBlank() }
        ?: "对话"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Embedded Header Bar (1:1 iOS `embeddedHeaderBar`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), CircleShape)
                    .clickable {
                        if (onBack != null) onBack() else model.openNewChatLanding()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回对话列表",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isTurnRunning) {
                        Box(
                            modifier = Modifier
                                .size(6.5.dp)
                                .clip(CircleShape)
                                .background(RunningOrange),
                        )
                    }
                    Text(
                        text = conversationTitle,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AgentAvatar(
                        agentId = agentId,
                        size = 11.dp,
                        iconSvg = agent?.iconSvg,
                        displayName = agent?.displayName.orEmpty(),
                    )
                    Text(
                        text = currentModelLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), CircleShape)
                        .clickable { model.openNewChatLanding() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = "新建对话",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), CircleShape)
                            .clickable { moreMenuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "更多操作",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("查看代码变更 (Diff)", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                moreMenuExpanded = false
                                showDiff = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("会话信息与操作", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                moreMenuExpanded = false
                                showSessionSheet = true
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)

        // Session Notices
        view?.notices.orEmpty().forEach { item ->
            SessionNoticeCard(
                notice = item,
                onDismiss = { model.dismissSessionNotice(conversationId, item.id) },
                onAction = { if (item.action == "retry") model.retryInterrupted(conversationId) },
            )
        }

        // Main Timeline + Floating Scroll-to-Bottom Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) {
                    if (menu != null) menu = null
                    focusManager.clearFocus()
                },
        ) {
            if (view == null) {
                TimelineSkeletonView()
            } else if (items.isEmpty() && !isTurnRunning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "发送第一条消息开始对话",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        count = items.size,
                        key = { idx -> items[idx].id },
                    ) { idx ->
                        val item = items[idx]
                        MessageRow(
                            item = item,
                            agentId = agentId,
                            agent = agent,
                            openDiff = { change -> diffTarget = change },
                        )
                    }

                    if (isTurnRunning && items.lastOrNull()?.role != TimelineItem.Role.Assistant) {
                        item(key = "loading_indicator") {
                            LoadingState(label = "正在思考")
                        }
                    }

                    item(key = "bottom_anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            if (!pinnedToBottom && items.isNotEmpty()) {
                ScrollToBottomButton(
                    unreadUpdates = unreadUpdates,
                    isTurnRunning = isTurnRunning,
                    onClick = {
                        pinnedToBottom = true
                        unreadUpdates = 0
                        scope.launch {
                            val total = listState.layoutInfo.totalItemsCount
                            if (total > 0) {
                                listState.animateScrollToItem(total - 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                )
            }
        }

        // Queued inputs strip
        if (view != null && view.queued.isNotEmpty()) {
            QueuedStrip(
                items = view.queued,
                enabled = state.connection.allowsWrites,
                onCancel = { model.cancelQueuedInput(conversationId, it.id, it.revision) },
            )
        }

        // TaskDock (`TaskRows.kt`)
        if (view != null && view.planTasks.isNotEmpty()) {
            TaskDock(
                tasks = view.planTasks,
                running = isTurnRunning,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }

        // ApprovalCard (`ApprovalCard.kt`)
        val pendingQuestion = view?.pendingQuestion
        if (pendingQuestion != null) {
            ApprovalCard(
                request = pendingQuestion,
                busy = respondingQuestion,
                respond = { content ->
                    respondingQuestion = true
                    model.respondQuestionContent(
                        conversationId = conversationId,
                        questionId = pendingQuestion.id,
                        content = content,
                        onDone = { respondingQuestion = false },
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }

        // PermissionBanner (`ApprovalCard.kt`)
        val permissionPrompt = view?.permissionPrompt
        if (permissionPrompt != null) {
            PermissionBanner(
                prompt = permissionPrompt,
                busy = respondingPermission,
                respond = { option, allowScope ->
                    respondingPermission = true
                    val persist = allowScope?.persistScope != null
                    model.respondPermission(
                        conversationId = conversationId,
                        permissionId = permissionPrompt.id,
                        optionId = option?.id,
                        persist = persist,
                        onDone = { respondingPermission = false },
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }

        if (totalChangeCount > 0) {
            ChangesSummaryBar(
                count = totalChangeCount,
                onOpenDiff = { showDiff = true },
                onOpenFiles = { showFiles = true },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // PromptBar (`PromptBar.kt`)
        PromptBar(
            text = input,
            onTextChange = { input = it },
            menu = menu,
            onMenuChange = { menu = it },
            agentId = agentId,
            agent = agent,
            agents = state.catalog?.agents.orEmpty(),
            onAddCustomModel = { rawModel ->
                val trimmed = rawModel.trim()
                if (trimmed.isNotEmpty()) {
                    val modelKey = configOptions.firstOrNull {
                        val k = it.key.lowercase()
                        val c = it.category.lowercase()
                        c == "model" || k == "model" || k.contains("model")
                    }?.key ?: "model"
                    model.setSessionConfig(conversationId, modelKey, trimmed)
                }
            },
            modes = modes,
            currentModeId = currentModeId,
            configOptions = configOptions,
            isStreaming = isTurnRunning,
            enabled = state.connection.allowsWrites,
            onSend = {
                val trimmed = input.trim()
                if (trimmed.isNotEmpty()) {
                    input = ""
                    pinnedToBottom = true
                    unreadUpdates = 0
                    model.send(conversationId, trimmed)
                }
            },
            onStop = { model.cancel(conversationId) },
            onSelectMode = { modeId -> model.setSessionMode(conversationId, modeId) },
            onSelectOption = { key, value -> model.setSessionConfig(conversationId, key, value) },
            onOpenFileBrowser = { showFiles = true },
        )
    }

    // Tool & Info Sheets
    if (showFiles && workspaceId.isNotBlank()) {
        FileBrowserSheet(
            workspaceId = workspaceId,
            title = conversationTitle,
            model = model,
            onDismiss = { showFiles = false },
        )
    }

    if (showDiff && workspaceId.isNotBlank()) {
        DiffListSheet(
            workspaceId = workspaceId,
            conversationId = conversationId,
            model = model,
            onDismiss = { showDiff = false },
        )
    }

    val activeDiffTarget = diffTarget
    if (activeDiffTarget != null) {
        FileDiffSheet(
            change = activeDiffTarget,
            workspaceId = workspaceId,
            conversationId = conversationId,
            model = model,
            onDismiss = { diffTarget = null },
        )
    }

    if (showSessionSheet) {
        SessionInfoSheet(
            state = state,
            conversationId = conversationId,
            model = model,
            onDismiss = { showSessionSheet = false },
        )
    }

    if (showNewConvSheet) {
        NewConversationSheet(
            state = state,
            model = model,
            onDismiss = { showNewConvSheet = false },
        )
    }
}

/**
 * 1:1 port of iOS `MessageRow` (`ConversationDetailView.swift`) & Desktop `MessageTurnView.tsx`.
 */
@Composable
fun MessageRow(
    item: TimelineItem,
    agentId: String,
    agent: CatalogAgent? = null,
    openDiff: ((FileChangeItem) -> Unit)? = null,
) {
    val colors = VibexTheme.colors

    when (item.role) {
        TimelineItem.Role.User -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Spacer(modifier = Modifier.width(56.dp))
                val bubbleShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(colors.card)
                        .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), bubbleShape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = item.text,
                            fontSize = 14.5.sp,
                            lineHeight = 20.5.sp,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }

        TimelineItem.Role.Assistant -> {
            val segments = remember(item) { orderedSegments(item) }
            val textSegments = remember(segments) {
                segments.filterIsInstance<TimelineSegment.Text>().filter { it.text.isNotBlank() }
            }
            val processSegments = remember(segments) {
                segments.filter { it !is TimelineSegment.Text }
            }
            val hasProcessSteps = processSegments.isNotEmpty() || item.changes.isNotEmpty()
            var processExpanded by remember(item.id) { mutableStateOf(false) }
            val showExpandedProcess = processExpanded || (item.streaming && textSegments.isEmpty() && item.text.isBlank())

            val workedForLabel = remember(item.streaming, textSegments.isEmpty(), item.thinkingDurationMs) {
                if (item.streaming && textSegments.isEmpty() && item.text.isBlank()) {
                    "Working…"
                } else {
                    val ms = (item.thinkingDurationMs ?: 60_000).toDouble()
                    val totalMinutes = kotlin.math.max(1L, kotlin.math.round(ms / 60_000.0).toLong())
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    "Worked for ${hours}h ${minutes}m"
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Desktop-style `> Worked for Xh Ym` Collapsible Process Group (`MessageTurnView.tsx:537-595`)
                if (hasProcessSteps) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { processExpanded = !processExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(if (showExpandedProcess) 90f else 0f),
                        )
                        Text(
                            text = workedForLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                        )
                        if (item.streaming) {
                            Box(
                                modifier = Modifier
                                    .size(5.5.dp)
                                    .clip(CircleShape)
                                    .background(RunningOrange),
                            )
                        }
                    }

                    if (showExpandedProcess) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            processSegments.forEachIndexed { idx, segment ->
                                val isLastProcess = idx == processSegments.lastIndex && textSegments.isEmpty()
                                when (segment) {
                                    is TimelineSegment.Thinking -> {
                                        ThinkingView(
                                            text = segment.text,
                                            durationMs = segment.durationMs,
                                            working = item.streaming && isLastProcess,
                                        )
                                    }
                                    is TimelineSegment.Tools -> {
                                        ToolChipsView(
                                            tools = segment.steps,
                                            changes = emptyList(),
                                            working = item.streaming && isLastProcess,
                                            openDiff = openDiff,
                                        )
                                    }
                                    is TimelineSegment.Text -> Unit
                                }
                            }

                            if (item.changes.isNotEmpty()) {
                                ToolChipsView(
                                    tools = emptyList(),
                                    changes = item.changes,
                                    working = false,
                                    openDiff = openDiff,
                                )
                            }
                        }
                    }
                }

                // Assistant Markdown Answer
                textSegments.forEachIndexed { idx, segment ->
                    val isLastText = idx == textSegments.lastIndex
                    StreamingMessageText(
                        text = segment.text,
                        isStreaming = item.streaming && isLastText,
                        showCopyButton = !item.streaming && isLastText,
                    )
                }

                if (item.streaming && segments.isEmpty()) {
                    LoadingState(label = "正在思考")
                }
            }
        }
    }
}

@Composable
private fun ChangesSummaryBar(
    count: Int,
    onOpenDiff: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.card)
                .border(0.75.dp, colors.textPrimary.copy(alpha = 0.10f), CircleShape)
                .clickable(onClick = onOpenDiff)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "$count CHANGES",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = colors.textPrimary.copy(alpha = 0.88f),
            )
        }

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.card)
                .clickable(onClick = onOpenFiles),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = "浏览文件",
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun orderedSegments(item: TimelineItem): List<TimelineSegment> {
    if (item.segments.isNotEmpty()) return item.segments
    val fallback = mutableListOf<TimelineSegment>()
    val thinkingText = item.thinking
    if (!thinkingText.isNullOrBlank()) {
        fallback += TimelineSegment.Thinking(
            id = "${item.id}:thinking",
            text = thinkingText,
            durationMs = item.thinkingDurationMs,
        )
    }
    if (item.tools.isNotEmpty()) {
        fallback += TimelineSegment.Tools(
            id = "${item.id}:tools",
            steps = item.tools,
        )
    }
    if (item.text.isNotBlank()) {
        fallback += TimelineSegment.Text(
            id = "${item.id}:text",
            text = item.text,
        )
    }
    return fallback
}

/**
 * 1:1 port of iOS `StreamingMessageText` (`StreamingText.swift`).
 */
@Composable
fun StreamingMessageText(
    text: String,
    isStreaming: Boolean,
    showCopyButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MarkdownContent(raw = text, fillWidth = true)

        if (showCopyButton && !isStreaming && text.isNotBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    }
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "复制",
                    tint = if (copied) colors.pass else colors.textSecondary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = if (copied) "已复制" else "复制",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (copied) colors.pass else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 1:1 port of iOS `scrollToBottomButton` (`ConversationDetailView.swift`).
 */
@Composable
private fun ScrollToBottomButton(
    unreadUpdates: Int,
    isTurnRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val showExtendedLabel = unreadUpdates > 0 || isTurnRunning
    val label = when {
        unreadUpdates > 0 -> "$unreadUpdates 条新动态"
        isTurnRunning -> "跟随最新回答"
        else -> null
    }

    Row(
        modifier = modifier
            .height(34.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(CircleShape)
            .background(colors.card)
            .border(0.8.dp, colors.textPrimary.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (showExtendedLabel) 13.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (unreadUpdates > 0 || isTurnRunning) {
            Box(
                modifier = Modifier
                    .size(6.5.dp)
                    .clip(CircleShape)
                    .background(RunningOrange),
            )
        }
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "回到底部",
            tint = colors.textPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun QueuedStrip(
    items: List<QueuedInput>,
    enabled: Boolean,
    onCancel: (QueuedInput) -> Unit,
) {
    val colors = VibexTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "排队中 (${items.size})",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.text.ifBlank { "排队消息" },
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "取消排队",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(enabled = enabled) { onCancel(item) },
                )
            }
        }
    }
}
