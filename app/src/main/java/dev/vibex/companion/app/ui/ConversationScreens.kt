package dev.vibex.companion.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.ConversationSummary
import dev.vibex.companion.SessionConfigChoice
import dev.vibex.companion.SessionConfigOption
import dev.vibex.companion.SessionMode
import dev.vibex.companion.WorkspaceEntry
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

private data class StarterChip(val id: String, val label: String, val prompt: String)

private val starterSuggestions = listOf(
    StarterChip("arch", "梳理项目架构", "梳理当前项目的整体架构、目录结构与核心模块职责"),
    StarterChip("bug", "排查近期改动", "检查当前工作区最近的代码改动，排查潜在问题并给出修复方案"),
    StarterChip("test", "补充单元测试", "为当前工作区最近修改的核心逻辑补充单元测试并运行验证"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationList(
    state: UiState,
    model: CompanionModel,
    onChromeHiddenChange: (Boolean) -> Unit = {},
) {
    val colors = VibexTheme.colors
    val focusManager = LocalFocusManager.current
    var isSidebarOpen by remember { mutableStateOf(false) }
    var sidebarTab by remember { mutableStateOf("conversations") }
    var sidebarSearch by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var composerMenu by remember { mutableStateOf<ComposerMenu?>(null) }
    var isComposerFocused by remember { mutableStateOf(false) }
    val shouldHideBottomChrome = isSidebarOpen || composerMenu != null || isComposerFocused
    LaunchedEffect(shouldHideBottomChrome) {
        onChromeHiddenChange(shouldHideBottomChrome)
        if (isSidebarOpen) {
            composerMenu = null
            focusManager.clearFocus()
        }
    }
    BackHandler(enabled = isSidebarOpen || composerMenu != null) {
        if (isSidebarOpen) {
            isSidebarOpen = false
        } else {
            composerMenu = null
        }
    }
    var landingModeId by remember { mutableStateOf("auto") }
    var landingEffortValue by remember { mutableStateOf("medium") }
    var selectedModelByAgent by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var customRelayModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showProjectSheet by remember { mutableStateOf(false) }
    var showDiffSheet by remember { mutableStateOf(false) }
    var showFileBrowserSheet by remember { mutableStateOf(false) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var expandedProjects by remember { mutableStateOf(setOf<String>()) }
    var workspaceEntries by remember { mutableStateOf<Map<String, List<WorkspaceEntry>>>(emptyMap()) }

    val projects = state.catalog?.projects.orEmpty()
    val workspaces = state.catalog?.workspaces.orEmpty()
    val agents = state.catalog?.agents.orEmpty().let { list ->
        list.filter { it.usable }.ifEmpty { list }
    }
    val currentProject = projects.find { it.id == state.selectedProjectId } ?: projects.firstOrNull()
    val currentWorkspace = workspaces.firstOrNull { it.projectId == currentProject?.id } ?: workspaces.firstOrNull()
    val currentAgent = agents.find { it.id == selectedAgentId }
        ?: agents.find { it.id == state.conversations.firstOrNull()?.agentId }
        ?: agents.firstOrNull()

    LaunchedEffect(projects.size, state.selectedProjectId) {
        if (state.selectedProjectId == null && projects.isNotEmpty()) {
            model.selectProject(projects.first().id)
        }
    }

    val isOnline = state.connection == ConnectionState.Online

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .pointerInput(isSidebarOpen) {
                var totalDx = 0f
                var startX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        totalDx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDx += dragAmount
                    },
                    onDragEnd = {
                        if (!isSidebarOpen && startX < 96f && totalDx > 100f) {
                            isSidebarOpen = true
                        } else if (isSidebarOpen && totalDx < -100f) {
                            isSidebarOpen = false
                        }
                    },
                )
            },
    ) {
        // Main Calm AI Chat Landing View (1:1 with iOS ConversationsView.landingWorkspaceView)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // Top Bar: Sidebar Button | Connection Status | Diff/New Button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.bgElevated)
                        .clickable { isSidebarOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Menu, contentDescription = "打开侧边栏", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) colors.pass else colors.hold),
                    )
                    Text(
                        if (isOnline) "已连接" else connectionLabel(state.connection),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.bgElevated)
                        .clickable {
                            if (currentWorkspace != null) {
                                showDiffSheet = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = "代码差异", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                }
            }

            if (state.inbox.isNotEmpty()) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.hold.copy(0.14f))
                        .clickable { model.openConversation(state.inbox.first().conversationId) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${state.inbox.size} 条待你审批或回复",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.hold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.hold, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // Center Focal Area: VibeX Logo + "有什么可以帮你？" + Project Context Pill
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (composerMenu != null) composerMenu = null
                        focusManager.clearFocus()
                    }
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(0.10f), spotColor = Color.Black.copy(0.14f))
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.bgElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandMark(40.dp)
                }

                Text(
                    "有什么可以帮你？",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )

                // Project Context Pill (1:1 with iOS projectContextPill)
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(colors.bgElevated)
                        .border(0.6.dp, colors.stroke, CircleShape)
                        .clickable { showProjectSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                    Text(
                        currentProject?.name ?: "选择项目",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val branch = currentWorkspace?.branch?.ifBlank { "main" } ?: "main"
                    Text("·", color = colors.textTertiary, fontSize = 13.sp)
                    Icon(Icons.Rounded.AccountTree, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                    Text(branch, fontSize = 12.5.sp, color = colors.textSecondary)
                    Icon(Icons.Rounded.UnfoldMore, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // Quiet Starter Suggestion Chips (shown when draft is empty and composer menu is closed)
            if (draft.isBlank() && composerMenu == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    starterSuggestions.forEach { chip ->
                        Text(
                            chip.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.bgElevated)
                                .border(0.6.dp, colors.stroke, CircleShape)
                                .clickable { draft = chip.prompt }
                                .padding(horizontal = 13.dp, vertical = 7.5.dp),
                        )
                    }
                }
            }

            // 1:1 reuse of iOS PromptBar (`ConversationsView.swift` landingPromptBar)
            val resolvedWsId = currentWorkspace?.id?.ifBlank { null }
                ?: state.conversations.firstOrNull { it.workspaceId.isNotBlank() }?.workspaceId
            val landingModes = remember(currentAgent) {
                currentAgent?.sessionModes?.takeIf { it.isNotEmpty() } ?: listOf(
                    SessionMode("manual", "手动审批"),
                    SessionMode("accept_edits", "自动接受编辑"),
                    SessionMode("plan", "仅规划"),
                    SessionMode("auto", "自动"),
                    SessionMode("bypass", "跳过权限"),
                )
            }
            val currentAgentId = currentAgent?.id ?: "claude_code"
            val baseModelOption = remember(currentAgent, currentAgentId) {
                currentAgent?.sessionConfig?.firstOrNull {
                    val k = it.key.lowercase()
                    val c = it.category.lowercase()
                    c == "model" || k == "model" || k.contains("model")
                } ?: dev.vibex.companion.fallbackModelOptionForAgent(currentAgentId)
            }
            val baseEffortOption = remember(currentAgent) {
                currentAgent?.sessionConfig?.firstOrNull {
                    val k = it.key.lowercase()
                    val c = it.category.lowercase()
                    c.contains("thought") || c.contains("effort") || c.contains("reason") ||
                        k.contains("thought") || k.contains("effort") || k.contains("thinking") || k.contains("reason")
                } ?: SessionConfigOption(
                    key = "reasoning_effort",
                    label = "推理强度",
                    category = "thought_level",
                    value = "medium",
                    choices = listOf(
                        SessionConfigChoice("low", "低"),
                        SessionConfigChoice("medium", "中"),
                        SessionConfigChoice("high", "高"),
                    ),
                )
            }
            val activeModelValue = selectedModelByAgent[currentAgentId]
                ?: baseModelOption.value.ifBlank { baseModelOption.choices.firstOrNull()?.value ?: "default" }
            val landingConfigOptions = remember(
                currentAgentId,
                baseModelOption,
                baseEffortOption,
                activeModelValue,
                landingEffortValue,
                customRelayModels,
            ) {
                val mergedChoices = buildList {
                    customRelayModels.forEach { custom ->
                        if (baseModelOption.choices.none { it.value == custom }) {
                            add(SessionConfigChoice(custom, "$custom (自定义/中转)"))
                        }
                    }
                    addAll(baseModelOption.choices)
                    if (none { it.value == activeModelValue }) {
                        add(0, SessionConfigChoice(activeModelValue, activeModelValue))
                    }
                }
                listOf(
                    baseModelOption.copy(
                        value = activeModelValue,
                        choices = mergedChoices,
                    ),
                    baseEffortOption.copy(
                        value = landingEffortValue,
                    ),
                )
            }

            PromptBar(
                text = draft,
                onTextChange = { draft = it },
                menu = composerMenu,
                onMenuChange = { composerMenu = it },
                agentId = currentAgentId,
                agent = currentAgent,
                agents = agents,
                showAgentSelector = true,
                onSelectAgent = { nextAgentId ->
                    selectedAgentId = nextAgentId
                },
                onAddCustomModel = { rawModel ->
                    val trimmed = rawModel.trim()
                    if (trimmed.isNotEmpty()) {
                        if (trimmed !in customRelayModels) {
                            customRelayModels = listOf(trimmed) + customRelayModels
                        }
                        selectedModelByAgent = selectedModelByAgent + (currentAgentId to trimmed)
                    }
                },
                modes = landingModes,
                currentModeId = landingModeId,
                configOptions = landingConfigOptions,
                isStreaming = false,
                enabled = state.connection.allowsWrites && !resolvedWsId.isNullOrBlank(),
                onSend = {
                    val text = draft.trim()
                    val wsId = resolvedWsId ?: return@PromptBar
                    if (text.isEmpty()) return@PromptBar
                    composerMenu = null
                    draft = ""
                    model.createConversation(
                        workspaceId = wsId,
                        agentId = currentAgentId,
                        title = text.take(36),
                        prompt = text,
                        modeId = landingModeId,
                        config = mapOf(
                            baseModelOption.key to activeModelValue,
                            baseEffortOption.key to landingEffortValue,
                        ),
                    )
                },
                onStop = {},
                onSelectMode = { modeId ->
                    landingModeId = modeId
                },
                onSelectOption = { key, value ->
                    if (key == baseModelOption.key || key.lowercase().contains("model")) {
                        selectedModelByAgent = selectedModelByAgent + (currentAgentId to value)
                    } else {
                        landingEffortValue = value
                    }
                },
                onOpenFileBrowser = if (currentWorkspace != null) {
                    { showFileBrowserSheet = true }
                } else {
                    null
                },
                onFocusChange = { isComposerFocused = it },
            )
        }

        // Slide-Out Sidebar Scrim
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(1f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.36f))
                    .clickable { isSidebarOpen = false },
            )
        }

        // Slide-Out Sidebar Drawer (1:1 with iOS ConversationsView.sidebarDrawer)
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.zIndex(2f),
        ) {
            Column(
                Modifier
                    .width(304.dp)
                    .fillMaxHeight()
                    .background(colors.bgElevated)
                    .clickable(enabled = false) {},
            ) {
                // Sidebar Top Bar: Segmented Switcher (最近对话 | 项目文件) + New Chat Button
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.segmentedTrack)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        listOf("conversations" to "最近对话", "files" to "项目文件").forEach { (id, label) ->
                            val selected = sidebarTab == id
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) colors.textPrimary else colors.textSecondary,
                                modifier = Modifier
                                    .then(
                                        if (selected) Modifier.shadow(1.5.dp, CircleShape, ambientColor = Color.Black.copy(0.08f))
                                        else Modifier,
                                    )
                                    .clip(CircleShape)
                                    .background(if (selected) colors.segmentedThumb else Color.Transparent)
                                    .clickable { sidebarTab = id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.bg)
                            .clickable { isSidebarOpen = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.EditNote, contentDescription = "新对话", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                // Sidebar Search Box
                Row(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Search, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                    Box(Modifier.weight(1f)) {
                        if (sidebarSearch.isEmpty()) {
                            Text(
                                if (sidebarTab == "conversations") "搜索对话…" else "搜索项目…",
                                fontSize = 13.5.sp,
                                color = colors.textTertiary,
                            )
                        }
                        BasicTextField(
                            value = sidebarSearch,
                            onValueChange = { sidebarSearch = it },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (sidebarSearch.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Cancel,
                            null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(15.dp).clickable { sidebarSearch = "" },
                        )
                    }
                }

                HorizontalDivider(color = colors.hairline)

                // Sidebar Body
                if (sidebarTab == "conversations") {
                    val q = sidebarSearch.trim().lowercase()
                    val filtered = remember(state.conversations, q) {
                        state.conversations.filter {
                            q.isEmpty() || it.title.lowercase().contains(q) || it.agentId.lowercase().contains(q)
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        if (currentProject != null) {
                            item {
                                Text(
                                    currentProject.name,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    "暂无对话记录",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { item ->
                                val agent = catalogAgent(state, item.agentId)
                                val relTime = remember(item.updatedAt) { relativeTime(item.updatedAt) }
                                Row(
                                    Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .then(
                                            longPressModifier(
                                                onClick = {
                                                    isSidebarOpen = false
                                                    model.openConversation(item.id)
                                                },
                                                onLongClick = { offset ->
                                                    menuAnchor = offset
                                                    menuId = item.id
                                                },
                                            ),
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    AgentAvatar(item.agentId, size = 20.dp, iconSvg = agent?.iconSvg, displayName = agent?.displayName.orEmpty())
                                    Text(
                                        item.title.ifBlank { "未命名对话" },
                                        fontSize = 13.5.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        relTime,
                                        fontSize = 10.5.sp,
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val q = sidebarSearch.trim().lowercase()
                    val filteredProjects = remember(projects, q) {
                        projects.filter {
                            q.isEmpty() || it.name.lowercase().contains(q) || it.path.lowercase().contains(q)
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredProjects, key = { it.id }) { proj ->
                            val ws = workspaces.firstOrNull { it.projectId == proj.id }
                            val expanded = proj.id in expandedProjects
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (expanded) {
                                                expandedProjects = expandedProjects - proj.id
                                            } else {
                                                expandedProjects = expandedProjects + proj.id
                                                if (ws != null && !workspaceEntries.containsKey(ws.id)) {
                                                    model.loadWorkspaceEntries(ws.id) { entries ->
                                                        workspaceEntries = workspaceEntries + (ws.id to entries)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Icon(Icons.Rounded.Folder, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                    Text(
                                        proj.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (ws != null) {
                                        Text(
                                            "差异",
                                            fontSize = 11.sp,
                                            color = colors.accent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(colors.accent.copy(0.12f))
                                                .clickable {
                                                    model.selectProject(proj.id)
                                                    isSidebarOpen = false
                                                    showDiffSheet = true
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                if (expanded && ws != null) {
                                    val entries = workspaceEntries[ws.id].orEmpty()
                                    Column(Modifier.padding(start = 28.dp)) {
                                        entries.take(30).forEach { entry ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    if (entry.directory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                                    null,
                                                    tint = colors.textSecondary,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Text(
                                                    entry.name,
                                                    fontSize = 12.5.sp,
                                                    color = colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Project Picker Bottom Sheet
        if (showProjectSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProjectSheet = false },
                containerColor = colors.bgElevated,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "切换项目",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    projects.forEach { proj ->
                        val selected = proj.id == currentProject?.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.accent.copy(0.12f) else colors.bg)
                                .clickable {
                                    showProjectSheet = false
                                    model.selectProject(proj.id)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(proj.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                                if (proj.path.isNotBlank()) {
                                    Text(proj.path, fontSize = 11.sp, fontFamily = PlexMono, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (selected) {
                                Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        if (showDiffSheet && currentWorkspace != null) {
            DiffListSheet(
                workspaceId = currentWorkspace.id,
                conversationId = state.conversations.firstOrNull()?.id.orEmpty(),
                model = model,
                onDismiss = { showDiffSheet = false },
            )
        }

        if (showFileBrowserSheet && currentWorkspace != null) {
            FileBrowserSheet(
                workspaceId = currentWorkspace.id,
                title = currentProject?.name ?: currentWorkspace.name.ifBlank { "文件" },
                model = model,
                onDismiss = { showFileBrowserSheet = false },
            )
        }

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
private fun SidebarQuickNav(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary, maxLines = 1)
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
