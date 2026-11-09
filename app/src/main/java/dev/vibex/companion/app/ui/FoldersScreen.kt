package dev.vibex.companion.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.CatalogProject
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.app.ui.timeline.DraggableSheet

/**
 * 1:1 port of iOS `FilesView` (`FilesView.swift`) using `ConnectedDeviceBar`,
 * `SearchListCard`, `ImportedProjectRow`, and `FileBrowserSheet` (`BranchedMenu` + `CodeBlockView`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val host = state.profiles.find { it.hostId == state.selectedHostId }
    val query = state.projectQuery
    val allProjects = state.catalog?.projects.orEmpty()
    val projects = allProjects.filter {
        query.isBlank() || it.name.contains(query, true) || it.path.contains(query, true)
    }

    var menuProjectId by remember { mutableStateOf<String?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var infoProjectId by remember { mutableStateOf<String?>(null) }
    var createProjectId by remember { mutableStateOf<String?>(null) }
    var browseWorkspace by remember { mutableStateOf<Pair<String, String>?>(null) }

    val listState = rememberLazyListState()

    val searchItems = remember(query, projects, state.conversations, state.catalog) {
        if (query.isBlank()) emptyList()
        else buildList {
            projects.forEach { proj ->
                val wsCount = state.catalog?.workspaces.orEmpty().count { it.projectId == proj.id }
                add(
                    SearchResultItem(
                        id = "project:${proj.id}",
                        icon = Icons.Rounded.Folder,
                        title = proj.name.ifBlank { "项目" },
                        subtitle = proj.path.ifBlank { null },
                        badge = if (wsCount > 0) "$wsCount 工作区" else null,
                        action = {
                            val wsId = state.catalog?.workspaces.orEmpty().firstOrNull { it.projectId == proj.id }?.id
                            if (wsId != null) {
                                browseWorkspace = wsId to proj.name
                            } else {
                                model.selectProject(proj.id)
                            }
                        },
                    ),
                )
            }
            state.conversations
                .filter { it.title.contains(query, true) }
                .take(15)
                .forEach { conv ->
                    add(
                        SearchResultItem(
                            id = "conv:${conv.id}",
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            title = conv.title.ifBlank { "未命名对话" },
                            badge = agentDisplayName(conv.agentId, ""),
                            action = { model.openConversation(conv.id) },
                        ),
                    )
                }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("项目文件")
            CompanionRefresh(
                refreshing = state.catalogLoading,
                onRefresh = model::refreshCurrent,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "device_bar") {
                        ConnectedDeviceBar(
                            name = host?.name ?: "VibeX Desktop",
                            detail = "已导入 ${allProjects.size} 个项目",
                            statusColor = if (state.connection == ConnectionState.Online) colors.pass else colors.textTertiary,
                        )
                    }

                    item(key = "search_bar") {
                        if (query.isBlank()) {
                            SearchField(
                                value = query,
                                onValueChange = model::queryProject,
                                placeholder = "搜索已导入项目与对话…",
                            )
                        } else {
                            SearchListCard(
                                query = query,
                                onQueryChange = model::queryProject,
                                placeholder = "搜索已导入项目与对话…",
                                items = searchItems,
                                emptyLabel = "未找到匹配的项目或对话",
                            )
                        }
                    }

                    if (query.isBlank()) {
                        when {
                            state.catalogLoading && state.catalog == null -> {
                                item(key = "loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LoadingState(label = "正在读取项目")
                                    }
                                }
                            }
                            allProjects.isEmpty() -> {
                                item(key = "empty") {
                                    EmptyState(
                                        Icons.Outlined.FolderOpen,
                                        "暂无已导入项目",
                                        "在桌面端导入项目后会自动同步到此处",
                                    )
                                }
                            }
                            else -> {
                                item(key = "projects_card") {
                                    val cardShape = RoundedCornerShape(16.dp)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(cardShape)
                                            .background(colors.card)
                                            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), cardShape)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "已导入项目",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textSecondary,
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = allProjects.size.toString(),
                                                style = TextStyle(
                                                    fontFamily = PlexMono,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colors.textSecondary,
                                                ),
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(colors.textPrimary.copy(alpha = 0.06f))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                            )
                                        }

                                        allProjects.forEach { project ->
                                            val wsList = state.catalog?.workspaces.orEmpty().filter { it.projectId == project.id }
                                            ImportedProjectRow(
                                                project = project,
                                                workspaceCount = wsList.size,
                                                selected = project.id == state.selectedProjectId,
                                                onClick = {
                                                    val firstWs = wsList.firstOrNull()
                                                    if (firstWs != null) {
                                                        browseWorkspace = firstWs.id to project.name
                                                    } else {
                                                        model.selectProject(project.id)
                                                    }
                                                },
                                                onLongClick = { offset ->
                                                    menuAnchor = offset
                                                    menuProjectId = project.id
                                                },
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

        val menuProject = allProjects.find { it.id == menuProjectId }
        if (menuProject != null) {
            val wsId = state.catalog?.workspaces.orEmpty().firstOrNull { it.projectId == menuProject.id }?.id
            ActionMenu(
                items = buildList {
                    if (wsId != null) {
                        add(ActionMenuItem("浏览项目文件") { browseWorkspace = wsId to menuProject.name })
                    }
                    add(ActionMenuItem("切换为当前项目") { model.selectProject(menuProject.id) })
                    add(ActionMenuItem("查看项目信息") { infoProjectId = menuProject.id })
                    add(ActionMenuItem("创建新会话") { model.loadCatalog(); createProjectId = menuProject.id })
                },
                anchor = menuAnchor,
                onDismiss = { menuProjectId = null },
            )
        }

        val activeBrowse = browseWorkspace
        if (activeBrowse != null) {
            FileBrowserSheet(
                workspaceId = activeBrowse.first,
                title = activeBrowse.second,
                model = model,
                onDismiss = { browseWorkspace = null },
            )
        }

        val infoProject = allProjects.find { it.id == infoProjectId }
        if (infoProject != null) {
            ProjectInfoSheet(state, model, infoProject) { infoProjectId = null }
        }
        if (createProjectId != null) {
            NewConversationSheet(state, model, initialProjectId = createProjectId) { createProjectId = null }
        }
    }
}

/**
 * 1:1 port of iOS `ImportedProjectRow` (`FilesView.swift`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedProjectRow(
    project: CatalogProject,
    workspaceCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
) {
    val colors = VibexTheme.colors
    val rowShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else colors.card)
            .then(longPressModifier(onClick, onLongClick))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = project.name.ifBlank { "项目" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.path.isNotBlank()) {
                Text(
                    text = project.path,
                    style = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (workspaceCount > 0) {
            Text(
                text = workspaceCount.toString(),
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                ),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ProjectInfoSheet(state: UiState, model: CompanionModel, project: CatalogProject, onDismiss: () -> Unit) {
    val colors = VibexTheme.colors
    val workspaces = state.catalog?.workspaces.orEmpty().filter { it.projectId == project.id }
    var sessionCount by remember { mutableStateOf<Int?>(null) }
    var sessionCountReady by remember { mutableStateOf(false) }
    LaunchedEffect(project.id) {
        sessionCount = null
        sessionCountReady = false
        model.loadProjectSessionCount(project.id) {
            sessionCount = it
            sessionCountReady = true
        }
    }
    DraggableSheet(title = "项目信息", onDismiss = onDismiss) {
        Text(project.name.ifBlank { "项目" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        if (project.path.isNotBlank()) {
            Text(project.path, fontSize = 13.sp, color = colors.textTertiary, fontFamily = PlexMono, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.size(16.dp))
        InfoStat("会话", if (!sessionCountReady) "…" else sessionCount?.toString() ?: "没有统计")
        Spacer(Modifier.size(18.dp))
        Text("工作区", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary, modifier = Modifier.padding(bottom = 8.dp))
        if (workspaces.isEmpty()) {
            Text("还没有工作区", color = colors.textTertiary, fontSize = 14.sp)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .padding(vertical = 4.dp),
            ) {
                workspaces.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.CallSplit, null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name.ifBlank { item.id }, fontSize = 15.sp, color = colors.textPrimary)
                            if (item.branch.isNotBlank()) {
                                Text(item.branch, fontSize = 12.sp, color = colors.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoStat(label: String, value: String) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.codeSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
    }
}
