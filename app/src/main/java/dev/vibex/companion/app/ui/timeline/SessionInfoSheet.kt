package dev.vibex.companion.app.ui.timeline

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.CatalogWorkspace
import dev.vibex.companion.SessionConfigOption
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.effortShort
import dev.vibex.companion.isEffortOption
import dev.vibex.companion.isModelOption
import dev.vibex.companion.optionPriority
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.AgentAvatar
import dev.vibex.companion.app.ui.catalogAgent
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

enum class SessionDrawerKind { Info, AgentOptions }

private val CardShape = RoundedCornerShape(14.dp)
private val ChipShape = RoundedCornerShape(10.dp)

@Composable
fun SessionInfoSheet(
    state: UiState,
    conversationId: String,
    model: CompanionModel,
    kind: SessionDrawerKind = SessionDrawerKind.Info,
    onDismiss: () -> Unit,
) {
    val title = if (kind == SessionDrawerKind.AgentOptions) "智能体选项" else "会话"
    DraggableSheet(title = title, onDismiss = onDismiss) {
        SessionDrawerBody(state, conversationId, model, kind)
    }
}

@Composable
private fun SessionDrawerBody(
    state: UiState,
    conversationId: String,
    model: CompanionModel,
    kind: SessionDrawerKind,
) {
    val colors = VibexTheme.colors
    val conversation = state.conversations.find { it.id == conversationId }
    val session = state.timeline?.takeIf { it.conversationId == conversationId }
    val workspace = state.catalog?.workspaces?.find { it.id == (conversation?.workspaceId ?: session?.workspaceId) }
    val project = state.catalog?.projects?.find { it.id == workspace?.projectId }
        ?: state.catalog?.projects?.find { it.id == state.selectedProjectId }
    val agentId = conversation?.agentId ?: session?.agentId.orEmpty()
    val agent = catalogAgent(state, agentId)
    val options = session?.sessionConfig.orEmpty()
    val siblings = state.catalog?.workspaces.orEmpty().filter { it.projectId == (workspace?.projectId ?: project?.id) }
    var pickingBranch by remember { mutableStateOf(false) }

    if (kind == SessionDrawerKind.Info) {
        var pathOpen by remember { mutableStateOf(false) }
        var statusOpen by remember { mutableStateOf(false) }
        TitleBlock(conversationId, session?.title ?: conversation?.title.orEmpty(), state.connection.allowsWrites, model)
        Spacer(Modifier.height(16.dp))
        StatusBlock(conversationId, conversation?.status.orEmpty(), state.connection.allowsWrites, model, expanded = statusOpen, onToggle = { statusOpen = !statusOpen })
        Spacer(Modifier.height(20.dp))
        SectionLabel("项目")
        ProjectCard(project?.name ?: "—", path = "", locked = true, onClick = { pathOpen = !pathOpen })
        if (pathOpen && !project?.path.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            PathCard(project!!.path)
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("工作区")
        WorkspaceCard(
            directory = project?.path?.trimEnd('/')?.substringAfterLast('/') ?: workspace?.name ?: "—",
            branch = workspace?.branch?.ifBlank { null } ?: "—",
            expandable = true,
            expanded = pickingBranch,
            onClick = { pickingBranch = !pickingBranch },
        )
        if (pickingBranch) {
            Spacer(Modifier.height(8.dp))
            BranchPicker(siblings, workspace?.id.orEmpty()) { chosen ->
                pickingBranch = false
                model.preferWorkspace(conversationId, chosen.id)
            }
        }
        Spacer(Modifier.height(16.dp))
        val plus = session?.additions ?: 0
        val minus = session?.deletions ?: 0
        SectionLabel("工作区更改")
        InfoCard {
            Text(
                if (plus == 0L && minus == 0L) (session?.fileChangeLabel ?: "没有统计") else "+$plus  −$minus",
                fontSize = 15.sp,
                color = colors.textPrimary,
            )
        }
        return
    }

    if (kind == SessionDrawerKind.AgentOptions) {
        SectionLabel("工作区")
        ProjectCard(project?.name ?: "—", project?.path.orEmpty(), locked = true)
        Spacer(Modifier.height(8.dp))
        WorkspaceCard(
            directory = "分支",
            branch = workspace?.branch?.ifBlank { null } ?: "—",
            expandable = siblings.isNotEmpty(),
            expanded = pickingBranch,
            branchLeading = true,
            onClick = { pickingBranch = !pickingBranch },
        )
        if (pickingBranch) {
            Spacer(Modifier.height(8.dp))
            BranchPicker(siblings, workspace?.id.orEmpty()) { chosen ->
                pickingBranch = false
                model.preferWorkspace(conversationId, chosen.id)
            }
        }
    }
    val modelOptions = options.filter { isModelOption(it) }.sortedBy { optionPriority(it) }
    val effortOptions = options.filter { isEffortOption(it) }.sortedBy { optionPriority(it) }
    val other = options.filter { !isModelOption(it) && !isEffortOption(it) }.sortedBy { optionPriority(it) }
    if (modelOptions.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        SectionLabel("MODEL")
        OptionList(modelOptions, state.connection.allowsWrites) { key, value -> model.setSessionConfig(conversationId, key, value) }
    }
    if (effortOptions.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        SectionLabel("REASONING EFFORT")
        OptionList(effortOptions, state.connection.allowsWrites) { key, value -> model.setSessionConfig(conversationId, key, value) }
    }
    other.forEach { option ->
        if (option.choices.isEmpty()) return@forEach
        Spacer(Modifier.height(20.dp))
        SectionLabel(option.label.ifBlank { option.key }.uppercase())
        OptionList(listOf(option), state.connection.allowsWrites) { key, value -> model.setSessionConfig(conversationId, key, value) }
    }
}

@Composable
private fun TitleBlock(conversationId: String, original: String, enabled: Boolean, model: CompanionModel) {
    val colors = VibexTheme.colors
    var title by remember(conversationId, original) { mutableStateOf(original) }
    val dirty = title.trim() != original.trim() && title.trim().isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("标题")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(ChipShape)
                    .background(colors.card)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (enabled && dirty) {
                Text(
                    "保存",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onAccent,
                    modifier = Modifier
                        .clip(ChipShape)
                        .background(colors.accent)
                        .clickable { model.renameConversation(conversationId, title.trim()) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(
    conversationId: String,
    status: String,
    enabled: Boolean,
    model: CompanionModel,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = VibexTheme.colors
    val labels = listOf("todo" to "待开始", "inprogress" to "进行中", "inreview" to "待检查", "done" to "已完成")
    val current = labels.find { it.first.equals(status, true) }?.second ?: status.ifBlank { "—" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("状态", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary, modifier = Modifier.weight(1f))
            Text(
                current,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent,
                modifier = Modifier
                    .clip(ChipShape)
                    .background(colors.accentDim)
                    .clickable(enabled = enabled, onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { (id, label) ->
                    val selected = status.equals(id, true)
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) colors.onAccent else colors.textPrimary,
                        modifier = Modifier
                            .clip(ChipShape)
                            .background(if (selected) colors.accent else colors.card)
                            .clickable(enabled = enabled) { model.setConversationStatus(conversationId, id); onToggle() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = VibexTheme.colors.textTertiary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    val colors = VibexTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun ProjectCard(name: String, path: String, locked: Boolean, onClick: (() -> Unit)? = null) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Folder, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            if (path.isNotBlank()) {
                Text(path, fontSize = 12.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = PlexMono)
            }
        }
        if (locked) Icon(Icons.Outlined.Lock, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PathCard(path: String) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val display = if (path.isBlank()) "—" else path
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (path.isNotBlank()) {
                        clipboard.setText(AnnotatedString(path))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "已复制路径", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            display,
            fontSize = 14.sp,
            color = colors.textPrimary,
            fontFamily = PlexMono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WorkspaceCard(
    directory: String,
    branch: String,
    expandable: Boolean,
    onClick: () -> Unit,
    branchLeading: Boolean = false,
    expanded: Boolean = false,
) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .clickable(enabled = expandable, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.CallSplit, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(if (branchLeading) directory else directory, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(branch, fontSize = 13.sp, color = colors.textTertiary)
        }
        if (expandable) {
            Icon(
                Icons.Rounded.ExpandMore,
                null,
                tint = colors.textTertiary,
                modifier = Modifier.size(22.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun BranchPicker(workspaces: List<CatalogWorkspace>, currentId: String, onPick: (CatalogWorkspace) -> Unit) {
    val colors = VibexTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(colors.card),
    ) {
        workspaces.forEachIndexed { index, item ->
            val selected = item.id == currentId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.branch.ifBlank { item.name }, fontSize = 15.sp, color = colors.textPrimary, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    if (item.name.isNotBlank() && item.name != item.branch) {
                        Text(item.name, fontSize = 12.sp, color = colors.textTertiary)
                    }
                }
                if (selected) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            if (index != workspaces.lastIndex) {
                Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(colors.hairline).padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun OptionList(
    options: List<SessionConfigOption>,
    enabled: Boolean,
    onSelect: (String, String) -> Unit,
) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth().clip(CardShape).background(colors.card)) {
        options.forEach { option ->
            option.choices.forEachIndexed { index, choice ->
                val selected = choice.value == option.value
                val label = if (dev.vibex.companion.isEffortOption(option)) {
                    effortShort(choice.value, choice.label.ifBlank { choice.value })
                } else {
                    choice.label.ifBlank { choice.value }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onSelect(option.key, choice.value) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 16.sp, color = colors.textPrimary, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                }
                if (index != option.choices.lastIndex) {
                    Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(colors.hairline))
                }
            }
        }
    }
}
