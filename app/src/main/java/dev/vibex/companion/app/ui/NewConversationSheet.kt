package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.vibex.companion.CatalogAgent
import dev.vibex.companion.agentAvailabilityLabel
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.configOptionCurrentLabel
import dev.vibex.companion.isModeOption
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.app.ui.timeline.DraggableSheet

@Composable
fun NewConversationSheet(
    state: UiState,
    model: CompanionModel,
    initialProjectId: String? = null,
    onDismiss: () -> Unit,
) {
    val colors = VibexTheme.colors
    val catalog = state.catalog
    val projects = catalog?.projects.orEmpty()
    val agents = catalog?.agents.orEmpty().filter { it.ready }
    var projectId by remember { mutableStateOf(initialProjectId ?: state.selectedProjectId.orEmpty()) }
    var modeNew by remember { mutableStateOf(false) }
    var workspaceId by remember {
        mutableStateOf(
            catalog?.workspaces.orEmpty().firstOrNull { it.projectId == (initialProjectId ?: state.selectedProjectId.orEmpty()) }?.id.orEmpty(),
        )
    }
    var agentId by remember {
        mutableStateOf(agents.firstOrNull { it.usable }?.id ?: agents.firstOrNull()?.id.orEmpty())
    }
    var title by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newBranch by remember {
        mutableStateOf(
            catalog?.workspaces.orEmpty().firstOrNull { it.projectId == (initialProjectId ?: state.selectedProjectId.orEmpty()) }?.branch.orEmpty(),
        )
    }
    var projectOpen by remember { mutableStateOf(false) }
    val projectWorkspaces = catalog?.workspaces.orEmpty().filter { it.projectId == projectId }
    val project = projects.find { it.id == projectId }
    val agent = agents.find { it.id == agentId }
    val workspace = projectWorkspaces.find { it.id == workspaceId }
    var modeId by remember(agentId) { mutableStateOf(agent?.currentModeId.orEmpty()) }
    var configValues by remember(agentId) {
        mutableStateOf(agent?.sessionConfig.orEmpty().associate { it.key to it.value })
    }
    var openPicker by remember(agentId) { mutableStateOf<String?>(null) }
    val canSubmit = canCreate(state, modeNew, workspaceId, agentId, projectId, agent?.usable == true)
    DraggableSheet(
        title = "新建会话",
        onDismiss = onDismiss,
        confirmLabel = "完成",
        confirmEnabled = canSubmit,
        onConfirm = {
            val selectedMode = modeId.takeIf { it.isNotBlank() }
            if (modeNew) {
                if (projectId.isBlank()) return@DraggableSheet
                model.createWorkspace(projectId, newName.ifBlank { "workspace" }, newBranch.ifBlank { null }) { id ->
                    model.createConversation(id, agentId, title, modeId = selectedMode, config = configValues)
                }
            } else {
                model.createConversation(workspaceId, agentId, title, modeId = selectedMode, config = configValues)
            }
            onDismiss()
        },
    ) {
        FieldLabel("会话名称（可选）")
        SurfaceCard {
            TextField(title, { title = it }, "不填则使用首条消息自动命名")
        }
        Spacer(Modifier.height(18.dp))
        FieldLabel("Agent")
        SurfaceCard {
            if (agents.isEmpty()) {
                Text("Host 上还没有已启用的 Agent", color = colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
            } else {
                LazyRow(
                    Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(agents, key = { it.id }) { item ->
                        AgentRailItem(item, selected = item.id == agentId) { agentId = item.id }
                    }
                }
            }
        }
        if (agent != null && (agent.sessionModes.isNotEmpty() || agent.sessionConfig.isNotEmpty())) {
            Spacer(Modifier.height(18.dp))
            FieldLabel("会话配置")
            SurfaceCard {
                val hideModeOption = agent.sessionModes.isNotEmpty()
                if (agent.sessionModes.isNotEmpty()) {
                    val current = agent.sessionModes.find { it.id == modeId } ?: agent.sessionModes.find { it.id == agent.currentModeId }
                    ConfigPickerRow(
                        label = "Mode",
                        value = current?.name ?: modeId.ifBlank { "—" },
                        open = openPicker == "mode",
                        choices = agent.sessionModes.map { it.name to it.id },
                        selectedId = modeId.ifBlank { agent.currentModeId },
                        onToggle = { openPicker = if (openPicker == "mode") null else "mode" },
                        onDismiss = { openPicker = null },
                        onSelect = { id ->
                            modeId = id
                            openPicker = null
                        },
                    )
                }
                agent.sessionConfig.filter { option ->
                    if (hideModeOption && isModeOption(option)) return@filter false
                    option.choices.isNotEmpty() || option.value.isNotBlank()
                }.forEach { option ->
                    val selected = configValues[option.key] ?: option.value
                    val current = configOptionCurrentLabel(option, selected)
                    if (current.isBlank()) return@forEach
                    ConfigPickerRow(
                        label = option.label.ifBlank { option.key },
                        value = current,
                        open = openPicker == option.key,
                        choices = option.choices.map { it.label.ifBlank { it.value } to it.value },
                        selectedId = selected,
                        enabled = option.choices.isNotEmpty(),
                        onToggle = { openPicker = if (openPicker == option.key) null else option.key },
                        onDismiss = { openPicker = null },
                        onSelect = { id ->
                            configValues = configValues + (option.key to id)
                            openPicker = null
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        FieldLabel("工作区")
        SurfaceCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { projectOpen = !projectOpen }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Folder, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                Text(project?.name?.ifBlank { project.path } ?: "选择项目", fontSize = 15.sp, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
            if (projectOpen) {
                projects.forEach { item ->
                    SelectItem(item.name.ifBlank { item.path }, item.id == projectId, item.path.takeIf { it.isNotBlank() && it != item.name }) {
                        projectId = item.id
                        workspaceId = catalog?.workspaces.orEmpty().firstOrNull { it.projectId == item.id }?.id.orEmpty()
                        newBranch = catalog?.workspaces.orEmpty().firstOrNull { it.projectId == item.id }?.branch.orEmpty()
                        projectOpen = false
                    }
                }
                if (projects.isEmpty()) {
                    Text("没有项目", color = colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
            if (projectId.isNotBlank()) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.hairline))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModePill("现有工作区", selected = !modeNew, modifier = Modifier.weight(1f)) { modeNew = false }
                    ModePill("新工作区", selected = modeNew, modifier = Modifier.weight(1f)) { modeNew = true }
                }
                if (modeNew) {
                    TextField(newName, { newName = it }, "工作区名称")
                    TextField(newBranch, { newBranch = it }, "分支，例如 main")
                } else if (projectWorkspaces.isEmpty()) {
                    Text("这个项目还没有工作区", color = colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                } else {
                    projectWorkspaces.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { workspaceId = item.id }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.CallSplit, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name.ifBlank { item.id }, fontSize = 15.sp, color = colors.textPrimary)
                                if (item.branch.isNotBlank()) Text(item.branch, fontSize = 12.sp, color = colors.textTertiary)
                            }
                            if (item.id == workspaceId) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRailItem(item: CatalogAgent, selected: Boolean, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    val status = agentAvailabilityLabel(item.lifecycle, item.authentication)
    val statusColor = when (status) {
        "可用" -> colors.pass
        "未登录", "需配置", "处理中", "状态未知" -> colors.hold
        null -> null
        else -> colors.danger
    }
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(indication = null, interactionSource = interaction, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.accent.copy(0.16f) else colors.sheet)
                    .border(if (selected) 2.dp else 0.dp, if (selected) colors.accent else colors.stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AgentAvatar(item.id, 26.dp, item.iconSvg, item.displayName)
            }
            if (statusColor != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .border(2.dp, colors.card, CircleShape)
                        .clip(CircleShape)
                        .background(statusColor),
                )
            }
        }
        Text(
            agentDisplayName(item.id, item.displayName),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConfigPickerRow(
    label: String,
    value: String,
    open: Boolean,
    choices: List<Pair<String, String>>,
    selectedId: String,
    enabled: Boolean = true,
    onToggle: () -> Unit,
    onDismiss: () -> Unit = onToggle,
    onSelect: (String) -> Unit,
) {
    val colors = VibexTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = colors.textTertiary, modifier = Modifier.weight(1f))
        Box {
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (open) colors.accent else colors.textPrimary,
                modifier = Modifier
                    .clip(ShapeRow)
                    .clickable(
                        enabled = enabled && choices.isNotEmpty(),
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggle,
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (open && choices.isNotEmpty()) {
                Popup(
                    popupPositionProvider = LeftOfAnchorProvider(),
                    onDismissRequest = onDismiss,
                    properties = PopupProperties(focusable = true, clippingEnabled = false),
                ) {
                    Column(
                        Modifier
                            .widthIn(min = 128.dp, max = 220.dp)
                            .heightIn(max = 280.dp)
                            .shadow(16.dp, ShapeRow, ambientColor = colors.textPrimary.copy(0.14f), spotColor = colors.textPrimary.copy(0.18f))
                            .clip(ShapeRow)
                            .background(colors.card)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        choices.forEach { (choiceLabel, choiceId) ->
                            val selected = choiceId == selectedId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(choiceId) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    choiceLabel,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private class LeftOfAnchorProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = 8
        val pad = 12
        var x = anchorBounds.left - popupContentSize.width - gap
        var y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
        if (x < pad) x = pad
        val maxX = (windowSize.width - popupContentSize.width - pad).coerceAtLeast(pad)
        if (x > maxX) x = maxX
        val maxY = (windowSize.height - popupContentSize.height - pad).coerceAtLeast(pad)
        if (y < pad) y = pad
        if (y > maxY) y = maxY
        return IntOffset(x, y)
    }
}

private fun canCreate(
    state: UiState,
    modeNew: Boolean,
    workspaceId: String,
    agentId: String,
    projectId: String,
    usable: Boolean,
): Boolean {
    if (!state.connection.allowsWrites || agentId.isBlank() || projectId.isBlank() || !usable) return false
    return if (modeNew) true else workspaceId.isNotBlank()
}

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = VibexTheme.colors.textTertiary, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 8.dp, start = 2.dp))
}

@Composable
private fun ModePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) colors.onAccent else colors.textPrimary,
        modifier = modifier
            .clip(ShapeRow)
            .background(if (selected) colors.accent else colors.sheet)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = VibexTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(placeholder, color = colors.textTertiary, fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SelectItem(label: String, selected: Boolean, hint: String?, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, fontSize = 15.sp, color = if (selected) colors.accent else colors.textPrimary, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        if (!hint.isNullOrBlank()) Text(hint, fontSize = 12.sp, color = colors.textTertiary)
    }
}
