package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.PendingItem
import dev.vibex.companion.PendingKind
import dev.vibex.companion.PendingOption
import dev.vibex.companion.SessionConfigOption
import dev.vibex.companion.SessionMode
import dev.vibex.companion.TimelineRow
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.data.CollapseDisplay
import dev.vibex.companion.app.data.MessageStreamDisplay
import dev.vibex.companion.app.data.StreamDisplayPrefs
import dev.vibex.companion.app.data.ThinkingDisplay
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.app.ui.timeline.SessionInfoSheet
import dev.vibex.companion.app.ui.timeline.ToolCallState
import dev.vibex.companion.app.ui.timeline.ToolKindBucket
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import dev.vibex.companion.compactSessionConfigSummary
import dev.vibex.companion.app.ui.PressSurface
import dev.vibex.companion.QueuedInput
import dev.vibex.companion.ComposerToken
import dev.vibex.companion.TokenPrefix
import dev.vibex.companion.TokenDraft
import dev.vibex.companion.slashCatalog
import dev.vibex.companion.ComposerSegment
import dev.vibex.companion.StreamLayoutOptions
import dev.vibex.companion.StreamNode
import dev.vibex.companion.layoutStream
import dev.vibex.companion.parseComposerSegments
import dev.vibex.companion.app.ui.timeline.TokenChip
import dev.vibex.companion.app.ui.timeline.ToolExpandedBody
import dev.vibex.companion.app.ui.timeline.QueueStack
import dev.vibex.companion.app.ui.timeline.SendStopButton
import dev.vibex.companion.app.ui.timeline.SessionDrawerKind
import dev.vibex.companion.app.ui.timeline.TokenComposerField
import dev.vibex.companion.app.ui.timeline.EdgeDock
import dev.vibex.companion.app.ui.timeline.NavMessage
import dev.vibex.companion.app.ui.timeline.StreamingStatus
import dev.vibex.companion.app.ui.timeline.toToolVM
import kotlinx.coroutines.launch

private fun mentionFiles(rows: List<TimelineRow>): List<ComposerToken> {
    val seen = linkedSetOf<String>()
    val tokens = mutableListOf<ComposerToken>()
    val path = Regex("""(?:[\w.-]+/)*[\w.-]+\.[A-Za-z0-9]{1,8}""")
    rows.forEach { row ->
        val blob = listOf(row.title, row.toolInput, row.body).joinToString(" ")
        path.findAll(blob).forEach { match ->
            val value = match.value.trim()
            if (value.length > 2 && seen.add(value)) {
                tokens += ComposerToken(TokenPrefix.File, value.substringAfterLast('/'), value)
            }
        }
    }
    return tokens.take(40)
}


@Composable
fun TimelinePane(state: UiState, model: CompanionModel, conversationId: String) {
    val colors = VibexTheme.colors
    var draft by remember { mutableStateOf(TokenDraft()) }
    var showInfo by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showDiffs by remember { mutableStateOf(false) }
    var dockOpen by remember { mutableStateOf(false) }
    var artifactFiles by remember { mutableStateOf<List<ComposerToken>>(emptyList()) }
    var workspaceFiles by remember { mutableStateOf<List<ComposerToken>>(emptyList()) }
    LaunchedEffect(conversationId) {
        artifactFiles = emptyList()
        model.loadArtifactPaths(conversationId) { paths ->
            artifactFiles = paths.map { path ->
                ComposerToken(TokenPrefix.File, path.substringAfterLast('/'), path, key = path.substringAfterLast('/'))
            }
        }
    }
    val view = state.timeline?.takeIf { it.conversationId == conversationId }
    val conversation = state.conversations.find { it.id == conversationId }
    val workspaceId = view?.workspaceId.orEmpty().ifBlank { conversation?.workspaceId.orEmpty() }
    LaunchedEffect(workspaceId) {
        workspaceFiles = emptyList()
        if (workspaceId.isBlank()) return@LaunchedEffect
        model.loadWorkspaceEntries(workspaceId) { entries ->
            workspaceFiles = entries.map { entry ->
                ComposerToken(
                    prefix = TokenPrefix.File,
                    name = entry.name,
                    value = entry.path,
                    description = if (entry.directory) "目录" else "",
                    key = entry.name,
                )
            }
        }
    }
    val rows = view?.rows.orEmpty()
    val agentId = conversation?.agentId?.takeIf { it.isNotBlank() } ?: view?.agentId.orEmpty()
    LaunchedEffect(conversationId, agentId, workspaceId) {
        if (agentId.isBlank()) return@LaunchedEffect
        model.loadSlashCommands(conversationId, agentId, workspaceId)
    }
    val agent = catalogAgent(state, agentId)
    val inFlight = view?.inFlightTurnId != null
    val pending = rows.filter { it.pendingKind != null }
    val display = state.streamDisplay
    val stream = remember(rows, display, inFlight) {
        layoutStream(
            rows,
            StreamLayoutOptions(
                thinkingHidden = display.thinking == ThinkingDisplay.Hidden,
                showFailedTools = display.showFailedTools,
                foldProcess = display.messages == MessageStreamDisplay.Collapsed,
                inFlight = inFlight,
            ),
        )
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1
        }
    }
    var stick by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) stick = isAtBottom
        }
    }
    LaunchedEffect(stream.size, rows.lastOrNull()?.body?.length, stick) {
        if (stick && stream.isNotEmpty()) listState.scrollToItem(stream.lastIndex)
    }
    val title = view?.title?.ifBlank { null } ?: conversation?.title?.ifBlank { null } ?: "会话"
    Box(Modifier.fillMaxSize().imePadding()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            PressSurface(onClick = { model.go(Route.Conversations) }, radius = 12.dp, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.textPrimary)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).fillMaxWidth(),
            )
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                AgentAvatar(agentId, 24.dp, agent?.iconSvg, agent?.displayName.orEmpty())
                PressSurface(onClick = { showDiffs = true }, radius = 12.dp, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Code, "代码差异", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                PressSurface(onClick = { showInfo = true }, radius = 12.dp, modifier = Modifier.size(44.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Menu, "会话信息", tint = colors.textPrimary)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (stream.isEmpty() && pending.isEmpty()) {
                EmptyState(icon = Icons.Rounded.Forum, title = "发送第一条消息")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(stream, key = { it.id }) { item -> StreamItem(item, inFlight, display) }
                    item(key = "__bottom__") { Spacer(Modifier.height(8.dp)) }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = stream.isNotEmpty() && !isAtBottom,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.bgElevated.copy(0.96f)).border(0.5.dp, colors.stroke, CircleShape).clickable {
                        stick = true
                        scope.launch { listState.animateScrollToItem(stream.size) }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ArrowDownward, "跳到最新", tint = colors.accent, modifier = Modifier.size(20.dp)) }
            }
            val userMessages = stream.mapIndexedNotNull { index, item ->
                (item as? StreamNode.User)?.let { NavMessage(index, it.row.body.ifBlank { it.row.title }) }
            }
            EdgeDock(
                messages = userMessages,
                todos = view?.planLines.orEmpty(),
                open = dockOpen,
                onToggle = { dockOpen = !dockOpen },
                onJump = { index ->
                    dockOpen = false
                    stick = false
                    scope.launch { listState.animateScrollToItem(index) }
                },
                parentWidth = maxWidth,
                parentHeight = maxHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }
        view?.notices.orEmpty().forEach { notice ->
            SessionNoticeCard(notice, onDismiss = { model.dismissSessionNotice(conversationId, notice.id) }, onAction = if (notice.action == "retry") ({ model.retryInterrupted(conversationId) }) else null)
        }
        pending.forEach { row ->
            PendingCard(row, state.connection.allowsWrites) { option ->
                model.approve(
                    PendingItem(conversationId, view?.title.orEmpty(), row.pendingKind ?: PendingKind.Permission, row.pendingId, row.title, row.body, row.options, row.id),
                    option,
                )
            }
        }
        QueueStack(
            items = view?.queued.orEmpty(),
            onCancel = { model.cancelQueuedInput(conversationId, it.id, it.revision) },
        )
        Composer(
            draft = draft,
            onDraft = { draft = it },
            filesLabel = view?.fileChangeLabel,
            usage = view?.usageLabel,
            slash = slashCatalog(agentId, view?.availableCommands.orEmpty(), view?.skillCommands.orEmpty()),
            fileMentions = (workspaceFiles + artifactFiles + mentionFiles(rows)).distinctBy { it.value },
            agentMentions = state.catalog?.agents.orEmpty().map {
                ComposerToken(TokenPrefix.Agent, it.displayName.ifBlank { it.id }, it.id)
            },
            tagMentions = state.catalog?.tags.orEmpty().map {
                ComposerToken(TokenPrefix.Tag, it.name, it.content, description = it.content, key = it.name)
            },
            summary = dev.vibex.companion.compactSessionConfigSummary(view?.sessionModes.orEmpty(), view?.currentModeId.orEmpty(), view?.sessionConfig.orEmpty()),
            enabled = state.connection.allowsWrites,
            inFlight = inFlight,
            canSteer = view?.canSteer == true,
            onSend = { model.send(conversationId, draft.materialize()); draft = TokenDraft() },
            onCancel = { model.cancel(conversationId) },
            onSummary = { showOptions = true },
            onShowDiffs = { showDiffs = true },
        )
    }
    if (showInfo) SessionInfoSheet(state, conversationId, model, SessionDrawerKind.Info) { showInfo = false }
    if (showOptions) SessionInfoSheet(state, conversationId, model, SessionDrawerKind.AgentOptions) { showOptions = false }
    if (showDiffs) CodeDiffSheet(state, model, workspaceId, conversationId) { showDiffs = false }
    }
}

@Composable
private fun StreamItem(item: StreamNode, inFlight: Boolean, display: StreamDisplayPrefs) {
    val colors = VibexTheme.colors
    when (item) {
        is StreamNode.Fold -> ProcessFold(item.rows, display)
        is StreamNode.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TokenizedUserMessage(item.row.body.ifBlank { item.row.title })
        }
        is StreamNode.Assistant -> MarkdownContent(item.row.body, Modifier.fillMaxWidth())
        is StreamNode.Waiting -> StreamingStatus(compact = item.compact)
        is StreamNode.Reasoning -> ReasoningBlock(item.row.body.ifBlank { item.row.thinking }, initiallyExpanded = display.thinking == ThinkingDisplay.All)
        is StreamNode.Tools -> DesktopToolGroup(item.rows, display)
        is StreamNode.Error -> SessionNoticeCard(
            dev.vibex.companion.SessionNotice(item.row.id, dev.vibex.companion.NoticeSeverity.Error, item.row.title.ifBlank { "失败" }, item.row.body),
            onDismiss = {},
        )
        is StreamNode.System -> if (item.row.body.isNotBlank() || item.row.title.isNotBlank()) {
            Text(listOf(item.row.title, item.row.body).filter { it.isNotBlank() }.joinToString("\n"), fontSize = 12.sp, color = colors.textTertiary)
        }
    }
}

@Composable
private fun DesktopToolGroup(rows: List<TimelineRow>, display: StreamDisplayPrefs) {
    val colors = VibexTheme.colors
    val vms = rows.map { it.toToolVM() }
    var expanded by remember(rows.first().id, display.tools) {
        mutableStateOf(display.tools == CollapseDisplay.Expanded || vms.any { it.state == ToolCallState.RUNNING })
    }
    Column(Modifier.fillMaxWidth()) {
        PressSurface(onClick = { expanded = !expanded }, radius = 10.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Build, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                Text(desktopSummary(vms), fontSize = 13.sp, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 2)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(tween(180)) + fadeIn(tween(180)), exit = shrinkVertically(tween(140)) + fadeOut(tween(120))) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                vms.forEach { vm -> DesktopToolRow(vm, display) }
            }
        }
    }
}

@Composable
private fun DesktopToolRow(vm: dev.vibex.companion.app.ui.timeline.ToolCallVM, display: StreamDisplayPrefs) {
    val colors = VibexTheme.colors
    var open by remember(vm.id, display.failedTools, display.tools) {
        mutableStateOf(
            if (vm.state == ToolCallState.ERROR) display.failedTools == CollapseDisplay.Expanded
            else display.tools == CollapseDisplay.Expanded,
        )
    }
    Column {
        PressSurface(onClick = { open = !open }, radius = 10.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (vm.state) {
                ToolCallState.RUNNING -> CircularProgressIndicator(Modifier.size(14.dp), colors.accent, 2.dp)
                ToolCallState.ERROR -> Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(15.dp))
                ToolCallState.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = ColorPass, modifier = Modifier.size(15.dp))
            }
            Text(actionLabel(vm.bucket), fontSize = 13.sp, color = colors.textSecondary)
            Icon(fileBadge(vm.bucket), null, tint = colors.accent, modifier = Modifier.size(14.dp))
            Text(vm.displayTitle, fontSize = 13.sp, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        }
        AnimatedVisibility(visible = open && (vm.output.isNotBlank() || vm.input.isNotBlank()), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            ToolExpandedBody(vm, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
        }
    }
}

private val ColorPass = androidx.compose.ui.graphics.Color(0xFF2F8A63)

private fun actionLabel(bucket: ToolKindBucket) = when (bucket) {
    ToolKindBucket.READ -> "查看文件"
    ToolKindBucket.EDIT -> "编辑"
    ToolKindBucket.SEARCH -> "搜索"
    ToolKindBucket.LIST -> "查看目录"
    ToolKindBucket.EXECUTE -> "终端"
    ToolKindBucket.WEB -> "网页"
    ToolKindBucket.TODO -> "任务"
    ToolKindBucket.TASK -> "委派"
    ToolKindBucket.OTHER -> "工具"
}

private fun fileBadge(bucket: ToolKindBucket): ImageVector = when (bucket) {
    ToolKindBucket.READ, ToolKindBucket.EDIT -> Icons.Rounded.Description
    ToolKindBucket.SEARCH -> Icons.Rounded.Search
    ToolKindBucket.LIST -> Icons.Rounded.Folder
    ToolKindBucket.EXECUTE -> Icons.Rounded.Terminal
    ToolKindBucket.TODO, ToolKindBucket.TASK -> Icons.Rounded.Folder
    else -> Icons.Rounded.Build
}

private fun desktopSummary(vms: List<dev.vibex.companion.app.ui.timeline.ToolCallVM>): String {
    val counts = vms.groupingBy { it.bucket }.eachCount()
    val parts = listOf(
        ToolKindBucket.EXECUTE to counts[ToolKindBucket.EXECUTE],
        ToolKindBucket.READ to counts[ToolKindBucket.READ],
        ToolKindBucket.EDIT to counts[ToolKindBucket.EDIT],
        ToolKindBucket.SEARCH to counts[ToolKindBucket.SEARCH],
        ToolKindBucket.LIST to counts[ToolKindBucket.LIST],
        ToolKindBucket.WEB to counts[ToolKindBucket.WEB],
        ToolKindBucket.OTHER to listOf(ToolKindBucket.OTHER, ToolKindBucket.TODO, ToolKindBucket.TASK).sumOf { counts[it] ?: 0 },
    ).mapNotNull { (b, c) ->
        val n = c ?: return@mapNotNull null
        if (n == 0) return@mapNotNull null
        when (b) {
            ToolKindBucket.EXECUTE -> "运行 $n 个命令"
            ToolKindBucket.READ -> "已读 $n 个文件"
            ToolKindBucket.EDIT -> "已改 $n 个文件"
            ToolKindBucket.SEARCH -> "完成 $n 次搜索"
            ToolKindBucket.LIST -> "查看 $n 个目录"
            ToolKindBucket.WEB -> "获取 $n 个网页"
            else -> "$n 次其他工具调用"
        }
    }
    return parts.joinToString("、").ifBlank { "${vms.size} 次工具调用" }
}

@Composable
private fun ReasoningBlock(text: String, initiallyExpanded: Boolean) {
    val colors = VibexTheme.colors
    var expanded by remember(text.take(24)) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
        PressSurface(onClick = { expanded = !expanded }, radius = 10.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Psychology, null, tint = colors.textTertiary, modifier = Modifier.size(15.dp))
                Text("思考", fontSize = 12.sp, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(visible = expanded) { MarkdownContent(text) }
    }
}

@Composable
private fun ProcessFold(rows: List<TimelineRow>, display: StreamDisplayPrefs) {
    val colors = VibexTheme.colors
    var open by remember(rows.first().id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        PressSurface(onClick = { open = !open }, radius = 10.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (open) 0f else -90f },
                )
                Text(
                    "已折叠 ${rows.size} 条过程消息",
                    color = colors.textTertiary,
                    fontSize = 13.sp,
                )
            }
        }
        AnimatedVisibility(visible = open, enter = expandVertically(tween(180)) + fadeIn(tween(180)), exit = shrinkVertically(tween(140)) + fadeOut(tween(120))) {
            val inner = remember(rows, display) {
                layoutStream(
                    rows,
                    StreamLayoutOptions(
                        thinkingHidden = display.thinking == ThinkingDisplay.Hidden,
                        showFailedTools = display.showFailedTools,
                        foldProcess = false,
                    ),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                inner.forEach { StreamItem(it, inFlight = false, display = display) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenizedUserMessage(text: String) {
    val colors = VibexTheme.colors
    val segments = remember(text) { parseComposerSegments(text) }
    val hasToken = segments.any { it is ComposerSegment.Token }
    val bubble = Modifier
        .widthIn(max = 320.dp)
        .wrapContentWidth(Alignment.End)
        .clip(RoundedCornerShape(14.dp))
        .background(colors.textPrimary.copy(if (colors.isDark) 0.10f else 0.06f))
        .padding(horizontal = 14.dp, vertical = 10.dp)
    if (!hasToken) {
        MarkdownContent(text, bubble, fillWidth = false)
        return
    }
    val lines = remember(segments) {
        val out = mutableListOf<MutableList<ComposerSegment>>()
        var current = mutableListOf<ComposerSegment>()
        fun flush() {
            if (current.isNotEmpty()) {
                out += current
                current = mutableListOf()
            }
        }
        for (seg in segments) {
            when (seg) {
                is ComposerSegment.Token -> current += seg
                is ComposerSegment.Text -> {
                    val parts = seg.value.split('\n')
                    parts.forEachIndexed { i, part ->
                        if (part.isNotEmpty()) current += ComposerSegment.Text(part)
                        if (i < parts.lastIndex) flush()
                    }
                }
            }
        }
        flush()
        out
    }
    Column(bubble, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                line.forEach { part ->
                    when (part) {
                        is ComposerSegment.Token -> TokenChip(part.token, fontSize = 14.sp, lineHeight = 20.sp)
                        is ComposerSegment.Text -> Text(
                            part.value,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: TokenDraft, onDraft: (TokenDraft) -> Unit,
    filesLabel: String?, usage: String?,
    slash: List<ComposerToken>, fileMentions: List<ComposerToken>, agentMentions: List<ComposerToken>,
    tagMentions: List<ComposerToken> = emptyList(),
    summary: String, enabled: Boolean, inFlight: Boolean, canSteer: Boolean,
    onSend: () -> Unit, onCancel: () -> Unit, onSummary: () -> Unit,
    onShowDiffs: () -> Unit = {},
) {
    val colors = VibexTheme.colors
    val canSend = !draft.isBlank && enabled
    val field = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, field, ambientColor = colors.textPrimary.copy(0.12f), spotColor = colors.textPrimary.copy(0.16f))
                .clip(field)
                .background(colors.bgElevated)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TokenComposerField(
                draft = draft,
                onDraft = onDraft,
                slash = slash,
                files = fileMentions,
                agents = agentMentions,
                tags = tagMentions,
                enabled = enabled,
                placeholder = if (inFlight && canSteer) "纠偏或排队下一条" else "发送消息，输入 / @ # &",
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (summary.isNotBlank()) {
                    Row(
                        Modifier
                            .shadow(2.dp, RoundedCornerShape(14.dp), ambientColor = colors.textPrimary.copy(0.08f), spotColor = colors.textPrimary.copy(0.10f))
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.card)
                            .clickable(onClick = onSummary)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(summary, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                filesLabel?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShowDiffs)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                ComposerIcon(Icons.Rounded.Lightbulb, "提示词优化", enabled = false, onClick = {})
                UsageRing(usage)
                SendStopButton(inFlight = inFlight, canSend = canSend, onSend = onSend, onStop = onCancel)
            }
        }
    }
}

@Composable
private fun ComposerIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color? = null) {
    val colors = VibexTheme.colors
    PressSurface(onClick = onClick, enabled = enabled, radius = 10.dp, modifier = Modifier.size(36.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint ?: if (enabled) colors.textSecondary else colors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun UsageRing(usage: String?) {
    if (usage.isNullOrBlank()) return
    val colors = VibexTheme.colors
    val parts = usage.split("/").mapNotNull { it.trim().toFloatOrNull() }
    val fraction = if (parts.size == 2 && parts[1] > 0f) (parts[0] / parts[1]).coerceIn(0f, 1f) else 0f
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        drawArc(colors.stroke, -90f, 360f, false, style = stroke)
        if (fraction > 0f) drawArc(colors.accent, -90f, 360f * fraction, false, style = stroke)
    }
}

@Composable
private fun PendingCard(row: TimelineRow, enabled: Boolean, onOption: (String) -> Unit) {
    val colors = VibexTheme.colors
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.hold.copy(0.12f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.title.ifBlank { "需要你处理" }, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        if (row.body.isNotBlank()) Text(row.body, color = colors.textSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (row.options.ifEmpty { listOf(PendingOption("allow-once", "允许"), PendingOption("deny", "拒绝")) }).forEach { option ->
                Text(
                    option.label.ifBlank { option.id },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (option.id.contains("deny", true) || option.id.contains("reject", true)) colors.danger else colors.accent)
                        .clickable(enabled = enabled) { onOption(option.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun ConnectionSheet(state: UiState, model: CompanionModel, close: () -> Unit) {
    val colors = VibexTheme.colors
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("连接", style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
        Text(state.activeOrigin ?: "没有当前地址", fontFamily = PlexMono, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        PrimaryButton("重新连接", { model.connectSelected(); close() })
        SecondaryButton("关闭", close)
    }
}
