package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.WorkspaceFileDiff
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.app.ui.timeline.DraggableSheet

@Composable
fun CodeDiffSheet(
    state: UiState,
    model: CompanionModel,
    workspaceId: String,
    conversationId: String,
    onDismiss: () -> Unit,
) {
    val colors = VibexTheme.colors
    var expandedPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workspaceId, conversationId) {
        model.loadWorkspaceDiffs(workspaceId, conversationId)
    }

    DraggableSheet(
        title = "代码差异",
        onDismiss = onDismiss,
    ) {
        when {
            state.diffsLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            state.workspaceDiffs.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Code,
                    title = "暂无未提交的代码差异",
                    message = "当前工作区或关联项目仓库处于干净状态。",
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.workspaceDiffs.forEach { fileDiff ->
                        val isOpen = expandedPath == fileDiff.path
                        FileDiffCard(
                            item = fileDiff,
                            expanded = isOpen,
                            onToggle = {
                                expandedPath = if (isOpen) null else fileDiff.path
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDiffCard(
    item: WorkspaceFileDiff,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = VibexTheme.colors
    val fileName = item.path.substringAfterLast('/')
    val statusLabel = when (item.status.lowercase()) {
        "added", "untracked", "new" -> "新增"
        "deleted", "removed" -> "删除"
        "renamed" -> "重命名"
        else -> "修改"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(0.75.dp, colors.stroke, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        statusLabel,
                        fontSize = 11.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        item.path,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        fontFamily = PlexMono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.addedLines > 0 || item.removedLines > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.addedLines > 0) {
                        Text("+${item.addedLines}", fontSize = 12.sp, color = Color(0xFF2F8A63), fontFamily = PlexMono, fontWeight = FontWeight.SemiBold)
                    }
                    if (item.removedLines > 0) {
                        Text("-${item.removedLines}", fontSize = 12.sp, color = colors.danger, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.codeSurface)
                    .padding(10.dp),
            ) {
                if (item.diff.isBlank()) {
                    Text(
                        "二进制文件、构建产物或未跟踪文件，暂无文本 Patch 预览。",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item.diff.lines().take(240).forEach { line ->
                            val bg = when {
                                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2F8A63).copy(alpha = 0.16f)
                                line.startsWith("-") && !line.startsWith("---") -> colors.danger.copy(alpha = 0.16f)
                                line.startsWith("@@") -> colors.accent.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                            val fg = when {
                                line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF2F8A63)
                                line.startsWith("-") && !line.startsWith("---") -> colors.danger
                                line.startsWith("@@") -> colors.accent
                                else -> colors.textPrimary
                            }
                            Text(
                                line.ifEmpty { " " },
                                fontFamily = PlexMono,
                                fontSize = 11.sp,
                                color = fg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
