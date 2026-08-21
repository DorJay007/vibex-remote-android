package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.ConversationSummary
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme

private data class KanbanColumn(val id: String, val label: String)

private val columns = listOf(
    KanbanColumn("todo", "待开始"),
    KanbanColumn("inprogress", "进行中"),
    KanbanColumn("inreview", "待检查"),
    KanbanColumn("done", "已完成"),
)

@Composable
fun StatusScreen(state: UiState, model: CompanionModel) {
    val colors = VibexTheme.colors
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    val host = state.profiles.find { it.hostId == state.selectedHostId }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("状态") { ConnectionChip(state.connection, host?.name) }
        when {
            state.selectedProjectId == null -> EmptyState(
                Icons.Outlined.SpaceDashboard,
                "先选择文件夹",
                "状态按当前项目近几天的会话分组。",
                "去选择",
                { model.go(Route.Folders) },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
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
                                ) { model.openConversation(item.id) }
                            }
                        }
                    }
                }
            }
        }
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
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun KanbanSessionCard(item: ConversationSummary, branch: String?, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    val live = normalizeStatus(item.status) == "inprogress"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bg.copy(if (colors.isDark) 0.35f else 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                item.title.ifBlank { "会话" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (live) {
                Icon(Icons.Rounded.Bolt, null, tint = colors.pass, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(relativeTime(item.updatedAt).ifBlank { "刚刚" }, style = MaterialTheme.typography.bodyMedium, color = colors.accent, fontSize = 11.sp)
            if (!branch.isNullOrBlank()) {
                Text("·", color = colors.textTertiary)
                Icon(Icons.Rounded.Folder, null, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
                Text(branch, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.agentId.isNotBlank()) {
            Text(item.agentId, style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun kanbanColor(id: String, colors: dev.vibex.companion.app.ui.theme.CompanionColors): Color = when (id) {
    "todo" -> colors.danger
    "inprogress" -> colors.pass
    "inreview" -> colors.hold
    else -> colors.textTertiary
}
