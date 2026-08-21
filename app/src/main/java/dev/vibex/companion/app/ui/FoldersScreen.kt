package dev.vibex.companion.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.CatalogProject
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.app.ui.timeline.DraggableSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    val host = state.profiles.find { it.hostId == state.selectedHostId }
    val query = state.projectQuery
    val projects = state.catalog?.projects.orEmpty().filter {
        query.isBlank() || it.name.contains(query, true) || it.path.contains(query, true)
    }
    var menuProjectId by remember { mutableStateOf<String?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var infoProjectId by remember { mutableStateOf<String?>(null) }
    var createProjectId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("文件夹") { ConnectionChip(state.connection, host?.name) }
            SearchField(query, model::queryProject, "搜索项目或路径", Modifier.padding(horizontal = 16.dp))
            CompanionRefresh(
                refreshing = state.catalogLoading,
                onRefresh = model::refreshCurrent,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.catalogLoading && state.catalog == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent)
                        }
                    }
                    state.catalog == null && state.error != null -> {
                        EmptyState(Icons.Outlined.FolderOpen, "无法读取项目", state.error, "重试", model::refreshCurrent)
                    }
                    projects.isEmpty() -> {
                        EmptyState(
                            Icons.Outlined.FolderOpen,
                            if (query.isBlank()) "没有项目" else "没有匹配的项目",
                            if (query.isBlank()) "连接成功后，这里列出 Host 上的历史项目目录。" else null,
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(projects.size, key = { projects[it].id }) { index ->
                            val project = projects[index]
                            FolderRow(
                                project = project,
                                workspaceCount = state.catalog?.workspaces.orEmpty().count { it.projectId == project.id },
                                selected = project.id == state.selectedProjectId,
                                onClick = { model.selectProject(project.id) },
                                onLongClick = { offset -> menuAnchor = offset; menuProjectId = project.id },
                            )
                        }
                    }
                }
            }
        }
        val menuProject = projects.find { it.id == menuProjectId }
        if (menuProject != null) {
            ActionMenu(
                items = listOf(
                    ActionMenuItem("查看项目信息") { infoProjectId = menuProject.id },
                    ActionMenuItem("创建新会话") { model.loadCatalog(); createProjectId = menuProject.id },
                ),
                anchor = menuAnchor,
                onDismiss = { menuProjectId = null },
            )
        }
        val infoProject = projects.find { it.id == infoProjectId } ?: state.catalog?.projects?.find { it.id == infoProjectId }
        if (infoProject != null) {
            ProjectInfoSheet(state, model, infoProject) { infoProjectId = null }
        }
        if (createProjectId != null) {
            NewConversationSheet(state, model, initialProjectId = createProjectId) { createProjectId = null }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    project: CatalogProject,
    workspaceCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
) {
    val colors = VibexTheme.colors
    val fill = if (selected) lerp(colors.card, colors.accent, 0.16f) else colors.card
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, ShapeRow, ambientColor = colors.textPrimary.copy(0.07f), spotColor = colors.textPrimary.copy(0.10f))
            .background(fill, ShapeRow)
            .clip(ShapeRow)
            .then(longPressModifier(onClick, onLongClick))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FolderBadge(project.id)
        Column(Modifier.weight(1f)) {
            Text(project.name.ifBlank { "项目" }, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                project.path.ifBlank { if (workspaceCount == 0) "无工作区" else "$workspaceCount 个工作区" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textTertiary,
                fontFamily = PlexMono,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
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
        Spacer(Modifier.size(8.dp))
        InfoStat("Git 变更", "没有统计")
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
