package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.CatalogAgent
import dev.vibex.companion.SessionConfigChoice
import dev.vibex.companion.SessionConfigOption
import dev.vibex.companion.SessionMode
import dev.vibex.companion.agentDisplayName
import dev.vibex.companion.bundledAgentAsset
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlin.math.roundToInt

enum class ComposerMenu { Plus, Agent, Model, Effort }

private enum class PlusPanel { Grid, Agent, Model, Mode, Permission }

private val SparkColor = Color(red = 0.62f, green = 0.30f, blue = 0.96f)
private val WarningOrange = Color(0xFFFF9500)

private val SessionConfigOption.currentLabel: String
    get() = choices.firstOrNull { it.value == value }?.label ?: value.ifBlank { label }

private fun findModelOption(options: List<SessionConfigOption>): SessionConfigOption? =
    options.firstOrNull { opt ->
        val k = opt.key.lowercase()
        val c = opt.category.lowercase()
        c == "model" || k == "model" || k.contains("model")
    }

private fun findEffortOption(options: List<SessionConfigOption>): SessionConfigOption? =
    options.firstOrNull { opt ->
        val k = opt.key.lowercase()
        val c = opt.category.lowercase()
        c.contains("thought") || c.contains("effort") || c.contains("reason") ||
            k.contains("thought") || k.contains("effort") || k.contains("thinking") || k.contains("reason")
    }

private fun findPermissionOption(options: List<SessionConfigOption>): SessionConfigOption? =
    options.firstOrNull { opt ->
        val k = opt.key.lowercase()
        val c = opt.category.lowercase()
        c.contains("permission") || c.contains("approval") || c.contains("sandbox") ||
            k.contains("permission") || k.contains("approval") || k.contains("sandbox") || k.contains("ask")
    }

/**
 * 1:1 port of iOS `PromptBar` (`PromptBar.swift`).
 */
@Composable
fun PromptBar(
    text: String,
    onTextChange: (String) -> Unit,
    menu: ComposerMenu?,
    onMenuChange: (ComposerMenu?) -> Unit,
    agentId: String,
    agent: CatalogAgent? = null,
    agents: List<CatalogAgent> = emptyList(),
    showAgentSelector: Boolean = false,
    onSelectAgent: ((String) -> Unit)? = null,
    onAddCustomModel: ((String) -> Unit)? = null,
    modes: List<SessionMode>,
    currentModeId: String,
    configOptions: List<SessionConfigOption>,
    isStreaming: Boolean,
    enabled: Boolean = true,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSelectMode: (String) -> Unit,
    onSelectOption: (String, String) -> Unit,
    onOpenFileBrowser: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val focusManager = LocalFocusManager.current
    var plusPanel by remember { mutableStateOf(PlusPanel.Grid) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelDraft by remember { mutableStateOf("") }

    val modelOption = remember(configOptions) { findModelOption(configOptions) }
    val effortOption = remember(configOptions) { findEffortOption(configOptions) }
    val permissionOption = remember(configOptions) { findPermissionOption(configOptions) }

    val agentLabel = remember(agent, agentId) {
        agent?.displayName?.takeIf { it.isNotBlank() }
            ?: agentDisplayName(agentId, "")
    }

    val modelName = remember(modelOption, agent, agentId) {
        modelOption?.currentLabel?.takeIf { it.isNotBlank() }
            ?: "默认模型"
    }

    val effortAtMax = remember(effortOption) {
        effortOption != null &&
            effortOption.choices.size > 1 &&
            effortOption.choices.last().value == effortOption.value
    }

    val currentModeName = remember(modes, currentModeId) {
        modes.firstOrNull { it.id == currentModeId }?.name
            ?: modes.firstOrNull()?.name
            ?: "默认"
    }

    val currentPermissionLabel = remember(permissionOption, modes, currentModeId) {
        if (permissionOption != null) return@remember permissionOption.currentLabel
        val mode = modes.firstOrNull { it.id == currentModeId } ?: return@remember "默认"
        val lower = "${mode.id} ${mode.name}".lowercase()
        if (lower.contains("auto") || lower.contains("bypass") || lower.contains("yolo") || lower.contains("full")) {
            mode.name
        } else {
            "默认"
        }
    }

    val isNonDefaultPermission = remember(permissionOption, currentModeId) {
        if (permissionOption != null) {
            val v = permissionOption.value.lowercase()
            return@remember v.contains("auto") || v.contains("bypass") || v.contains("never") || v.contains("full") || v.contains("yolo")
        }
        val m = currentModeId.lowercase()
        m.contains("auto") || m.contains("bypass") || m.contains("yolo") || m.contains("full")
    }

    val canSend = enabled && text.trim().isNotEmpty()
    val dockShape = RoundedCornerShape(22.dp)

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Menus above dock (never overlap the input dock)
            AnimatedVisibility(
                visible = menu == ComposerMenu.Agent && showAgentSelector && agents.isNotEmpty(),
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeOut(),
            ) {
                MenuList(
                    title = "选择 AI Agent",
                    choices = agents.map { a ->
                        SessionConfigChoice(
                            value = a.id,
                            label = agentDisplayName(a.id, a.displayName),
                        )
                    },
                    selected = agentId,
                    leading = { choice ->
                        val rowAgent = agents.find { it.id.equals(choice.value, ignoreCase = true) }
                        AgentAvatar(
                            agentId = choice.value,
                            size = 16.dp,
                            iconSvg = rowAgent?.iconSvg,
                            displayName = rowAgent?.displayName?.ifBlank { choice.label } ?: choice.label,
                        )
                    },
                    onSelect = { value ->
                        onSelectAgent?.invoke(value)
                        onMenuChange(null)
                    },
                )
            }

            AnimatedVisibility(
                visible = menu == ComposerMenu.Model && modelOption != null && (modelOption.choices.isNotEmpty() || onAddCustomModel != null),
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeOut(),
            ) {
                if (modelOption != null) {
                    MenuList(
                        title = modelOption.label,
                        choices = modelOption.choices,
                        selected = modelOption.value,
                        leading = { choice ->
                            val rowAgent = agents.find { it.id.equals(choice.value, ignoreCase = true) }
                            val rowAgentId = rowAgent?.id
                                ?: choice.value.takeIf { bundledAgentAsset(it, false) != null }
                                ?: choice.label.takeIf { bundledAgentAsset(it, false) != null }
                                ?: agentId
                            val rowSvg = when {
                                rowAgent != null -> rowAgent.iconSvg
                                rowAgentId.equals(agentId, ignoreCase = true) -> agent?.iconSvg
                                else -> null
                            }
                            AgentAvatar(
                                agentId = rowAgentId,
                                size = 16.dp,
                                iconSvg = rowSvg,
                                displayName = rowAgent?.displayName?.ifBlank { choice.label } ?: choice.label,
                            )
                        },
                        onSelect = { value ->
                            onSelectOption(modelOption.key, value)
                            onMenuChange(null)
                        },
                        onAddCustomModel = if (onAddCustomModel != null) {
                            {
                                onMenuChange(null)
                                customModelDraft = ""
                                showCustomModelDialog = true
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = menu == ComposerMenu.Effort && effortOption != null && effortOption.choices.size > 1,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeOut(),
            ) {
                if (effortOption != null) {
                    EffortMenu(
                        option = effortOption,
                        onSelect = { value ->
                            onSelectOption(effortOption.key, value)
                            onMenuChange(null)
                        },
                        modifier = Modifier.width(280.dp),
                    )
                }
            }

            // Unified Dock + Expandable Bottom Drawer (1:1 with iOS PromptBar.dockSurface)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (colors.isDark) 10.dp else 6.dp,
                        shape = dockShape,
                        ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.22f else 0.05f),
                        spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.08f),
                    )
                    .clip(dockShape)
                    .background(colors.card)
                    .then(
                        if (effortAtMax) {
                            Modifier.background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SparkColor.copy(alpha = 0.22f),
                                        SparkColor.copy(alpha = 0.06f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(900f, 0f),
                                    radius = 520f,
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .border(
                        width = 0.75.dp,
                        color = if (effortAtMax) SparkColor.copy(alpha = 0.38f) else colors.stroke,
                        shape = dockShape,
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Multiline Input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 6.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "发消息",
                                fontSize = 15.sp,
                                color = colors.textTertiary,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { next ->
                                if (menu != null) onMenuChange(null)
                                onTextChange(next)
                            },
                            enabled = enabled,
                            maxLines = 5,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = colors.textPrimary,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 24.dp)
                                .onFocusChanged { focusState ->
                                    onFocusChange(focusState.isFocused)
                                    if (focusState.isFocused && menu != null) {
                                        onMenuChange(null)
                                    }
                                },
                        )
                    }

                    // Bottom Tool Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolButton(
                            open = menu == ComposerMenu.Plus,
                            hasAlert = isNonDefaultPermission,
                            onClick = {
                                focusManager.clearFocus()
                                plusPanel = PlusPanel.Grid
                                onMenuChange(if (menu == ComposerMenu.Plus) null else ComposerMenu.Plus)
                            },
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 1. Optional AI Agent Pill (shown on New Conversation Landing)
                            if (showAgentSelector && agents.isNotEmpty()) {
                                PickPill(
                                    label = agentLabel,
                                    open = menu == ComposerMenu.Agent,
                                    enabled = agents.size > 1,
                                    leading = {
                                        AgentAvatar(
                                            agentId = agentId,
                                            size = 14.dp,
                                            iconSvg = agent?.iconSvg,
                                            displayName = agentLabel,
                                        )
                                    },
                                    onClick = {
                                        focusManager.clearFocus()
                                        onMenuChange(if (menu == ComposerMenu.Agent) null else ComposerMenu.Agent)
                                    },
                                )
                            }

                            // 2. Model Pill
                            PickPill(
                                label = modelName,
                                open = menu == ComposerMenu.Model,
                                enabled = (modelOption?.choices?.size ?: 0) >= 1 || onAddCustomModel != null,
                                leading = {
                                    if (showAgentSelector) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    } else {
                                        AgentAvatar(
                                            agentId = agentId,
                                            size = 14.dp,
                                            iconSvg = agent?.iconSvg,
                                            displayName = agent?.displayName.orEmpty(),
                                        )
                                    }
                                },
                                onClick = {
                                    focusManager.clearFocus()
                                    onMenuChange(if (menu == ComposerMenu.Model) null else ComposerMenu.Model)
                                },
                            )

                            // 3. Effort Pill
                            if (effortOption != null) {
                                PickPill(
                                    label = effortOption.currentLabel,
                                    open = menu == ComposerMenu.Effort,
                                    enabled = effortOption.choices.size > 1,
                                    tint = if (effortAtMax) SparkColor else null,
                                    leading = {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (effortAtMax) SparkColor else colors.textSecondary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    onClick = {
                                        focusManager.clearFocus()
                                        onMenuChange(if (menu == ComposerMenu.Effort) null else ComposerMenu.Effort)
                                    },
                                )
                            }
                        }

                        SendButton(
                            ready = canSend,
                            busy = isStreaming,
                            onClick = {
                                onMenuChange(null)
                                if (isStreaming) onStop() else if (canSend) onSend()
                            },
                        )
                    }

                    // Integrated Plus Drawer inside the same rounded dock surface
                    AnimatedVisibility(
                        visible = menu == ComposerMenu.Plus,
                        enter = expandVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeOut(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(thickness = 0.6.dp, color = colors.stroke)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                when (plusPanel) {
                                    PlusPanel.Grid -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            PlusGridTile(
                                                title = "文件",
                                                subtitle = "浏览项目",
                                                modifier = Modifier.weight(1f),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CreateNewFolder,
                                                        contentDescription = null,
                                                        tint = colors.textPrimary,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                },
                                                onClick = {
                                                    onMenuChange(null)
                                                    onOpenFileBrowser?.invoke()
                                                },
                                            )
                                            PlusGridTile(
                                                title = "模型",
                                                subtitle = modelName,
                                                modifier = Modifier.weight(1f),
                                                icon = {
                                                    AgentAvatar(
                                                        agentId = agentId,
                                                        size = 22.dp,
                                                        iconSvg = agent?.iconSvg,
                                                        displayName = agent?.displayName.orEmpty(),
                                                    )
                                                },
                                                onClick = {
                                                    if (modelOption != null && modelOption.choices.isNotEmpty()) {
                                                        plusPanel = PlusPanel.Model
                                                    }
                                                },
                                            )
                                            PlusGridTile(
                                                title = "模式",
                                                subtitle = currentModeName,
                                                modifier = Modifier.weight(1f),
                                                icon = { BotModeGlyph(tint = colors.textPrimary) },
                                                onClick = { plusPanel = PlusPanel.Mode },
                                            )
                                            PlusGridTile(
                                                title = "权限",
                                                subtitle = currentPermissionLabel,
                                                badge = isNonDefaultPermission,
                                                modifier = Modifier.weight(1f),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.VerifiedUser,
                                                        contentDescription = null,
                                                        tint = if (isNonDefaultPermission) WarningOrange else colors.textPrimary,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                },
                                                onClick = { plusPanel = PlusPanel.Permission },
                                            )
                                        }
                                    }

                                    PlusPanel.Agent -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PlusSubpanelHeader(title = "选择 AI Agent") { plusPanel = PlusPanel.Grid }
                                            DrawerChoiceList(
                                                choices = agents.map { a ->
                                                    SessionConfigChoice(a.id, agentDisplayName(a.id, a.displayName))
                                                },
                                                selected = agentId,
                                                onSelect = { id ->
                                                    onSelectAgent?.invoke(id)
                                                    onMenuChange(null)
                                                },
                                            )
                                        }
                                    }

                                    PlusPanel.Model -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PlusSubpanelHeader(title = "选择模型") { plusPanel = PlusPanel.Grid }
                                            if (modelOption != null && modelOption.choices.isNotEmpty()) {
                                                DrawerChoiceList(
                                                    choices = modelOption.choices,
                                                    selected = modelOption.value,
                                                    leading = { choice ->
                                                        val rowAgent = agents.find { it.id.equals(choice.value, ignoreCase = true) }
                                                        val rowAgentId = rowAgent?.id
                                                            ?: choice.value.takeIf { bundledAgentAsset(it, false) != null }
                                                            ?: choice.label.takeIf { bundledAgentAsset(it, false) != null }
                                                            ?: agentId
                                                        val rowSvg = when {
                                                            rowAgent != null -> rowAgent.iconSvg
                                                            rowAgentId.equals(agentId, ignoreCase = true) -> agent?.iconSvg
                                                            else -> null
                                                        }
                                                        AgentAvatar(
                                                            agentId = rowAgentId,
                                                            size = 16.dp,
                                                            iconSvg = rowSvg,
                                                            displayName = rowAgent?.displayName?.ifBlank { choice.label } ?: choice.label,
                                                        )
                                                    },
                                                    onSelect = { v ->
                                                        onSelectOption(modelOption.key, v)
                                                        onMenuChange(null)
                                                    },
                                                    onAddCustomModel = if (onAddCustomModel != null) {
                                                        {
                                                            onMenuChange(null)
                                                            customModelDraft = ""
                                                            showCustomModelDialog = true
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                )
                                            } else {
                                                PlusEmptyHint("当前 Agent 不支持切换模型")
                                            }
                                        }
                                    }

                                    PlusPanel.Mode -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PlusSubpanelHeader(title = "模式与推理") { plusPanel = PlusPanel.Grid }
                                            if (modes.isNotEmpty()) {
                                                DrawerChoiceList(
                                                    choices = modes.map { SessionConfigChoice(value = it.id, label = it.name) },
                                                    selected = currentModeId.ifBlank { modes.first().id },
                                                    onSelect = { id ->
                                                        onSelectMode(id)
                                                        onMenuChange(null)
                                                    },
                                                )
                                            }
                                            if (effortOption != null && effortOption.choices.size > 1) {
                                                EffortMenu(
                                                    option = effortOption,
                                                    onSelect = { v -> onSelectOption(effortOption.key, v) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                            if (modes.isEmpty() && effortOption == null) {
                                                PlusEmptyHint("当前会话无可用模式选项")
                                            }
                                        }
                                    }

                                    PlusPanel.Permission -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            PlusSubpanelHeader(title = "权限设置") { plusPanel = PlusPanel.Grid }
                                            when {
                                                permissionOption != null && permissionOption.choices.isNotEmpty() -> {
                                                    DrawerChoiceList(
                                                        choices = permissionOption.choices,
                                                        selected = permissionOption.value,
                                                        onSelect = { v ->
                                                            onSelectOption(permissionOption.key, v)
                                                            onMenuChange(null)
                                                        },
                                                    )
                                                }
                                                modes.isNotEmpty() -> {
                                                    DrawerChoiceList(
                                                        choices = modes.map { SessionConfigChoice(value = it.id, label = it.name) },
                                                        selected = currentModeId.ifBlank { modes.first().id },
                                                        onSelect = { id ->
                                                            onSelectMode(id)
                                                            onMenuChange(null)
                                                        },
                                                    )
                                                }
                                                else -> PlusEmptyHint("当前会话使用默认工具确认权限")
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
        if (showCustomModelDialog) {
            AlertDialog(
                onDismissRequest = { showCustomModelDialog = false },
                title = { Text("自定义 / 中转模型") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "输入桌面端中转服务或网关支持的模型 ID：",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                        OutlinedTextField(
                            value = customModelDraft,
                            onValueChange = { customModelDraft = it },
                            placeholder = { Text("例如 claude-opus-4-8 / deepseek-r1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = customModelDraft.trim()
                            showCustomModelDialog = false
                            if (trimmed.isNotEmpty()) {
                                onAddCustomModel?.invoke(trimmed)
                            }
                        },
                    ) {
                        Text("使用该模型")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomModelDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun PlusGridTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val tileShape = RoundedCornerShape(14.dp)
    val boxShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .clip(tileShape)
            .background(colors.bg.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(boxShape)
                .background(colors.bg),
            contentAlignment = Alignment.Center,
        ) {
            icon()
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WarningOrange),
                )
            }
        }
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 1:1 port of iOS `BotModeGlyph` (`PromptBar.swift`).
 */
@Composable
fun BotModeGlyph(
    modifier: Modifier = Modifier,
    tint: Color = VibexTheme.colors.textPrimary,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.75.dp.toPx()

        // Antenna stem & tip
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.14f),
            end = Offset(w * 0.5f, h * 0.27f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = tint,
            radius = w * 0.055f,
            center = Offset(w * 0.5f, h * 0.11f),
        )

        // Side ears
        drawLine(
            color = tint,
            start = Offset(w * 0.10f, h * 0.48f),
            end = Offset(w * 0.18f, h * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.82f, h * 0.48f),
            end = Offset(w * 0.90f, h * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // Head rounded rect
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.27f),
            size = Size(w * 0.64f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(width = stroke),
        )

        // Eyes
        drawCircle(
            color = tint,
            radius = w * 0.06f,
            center = Offset(w * 0.37f, h * 0.51f),
        )
        drawCircle(
            color = tint,
            radius = w * 0.06f,
            center = Offset(w * 0.63f, h * 0.51f),
        )

        // Smile line
        drawLine(
            color = tint,
            start = Offset(w * 0.39f, h * 0.67f),
            end = Offset(w * 0.61f, h * 0.67f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PlusSubpanelHeader(
    title: String,
    onBack: () -> Unit,
) {
    val colors = VibexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "返回",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun PlusEmptyHint(text: String) {
    val colors = VibexTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ToolButton(
    open: Boolean,
    hasAlert: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "plusRotate",
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(colors.textPrimary.copy(alpha = if (open) 0.14f else 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "更多选项",
            tint = colors.textPrimary,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation),
        )
        if (hasAlert && !open) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(WarningOrange),
            )
        }
    }
}

@Composable
private fun PickPill(
    label: String,
    open: Boolean,
    enabled: Boolean,
    tint: Color? = null,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "pillChevron",
    )

    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(
                when {
                    tint != null -> tint.copy(alpha = if (open) 0.20f else 0.12f)
                    else -> colors.textPrimary.copy(alpha = if (open) 0.14f else 0.07f)
                },
            )
            .then(
                if (tint != null) Modifier.border(0.6.dp, tint.copy(alpha = 0.35f), CircleShape)
                else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint ?: colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
        if (enabled) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = (tint ?: colors.textSecondary).copy(alpha = 0.85f),
                modifier = Modifier
                    .size(13.dp)
                    .rotate(rotation),
            )
        }
    }
}

@Composable
private fun SendButton(
    ready: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val active = ready || busy
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (active) colors.textPrimary else colors.textPrimary.copy(alpha = if (colors.isDark) 0.16f else 0.11f))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SendGlyph(stop = busy, ready = ready)
    }
}

@Composable
private fun SendGlyph(
    stop: Boolean,
    ready: Boolean,
) {
    val colors = VibexTheme.colors
    val morph by animateFloatAsState(
        targetValue = if (stop) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "sendMorph",
    )
    val fg = if (ready || stop) colors.bg else colors.textTertiary

    Canvas(modifier = Modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val m = morph.coerceIn(0f, 1f)

        if (m > 0.02f) {
            val side = minOf(w, h) * (0.45f + 0.37f * m)
            val corner = side * 0.24f
            drawRoundRect(
                color = fg.copy(alpha = m),
                topLeft = Offset(cx - side / 2f, cy - side / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(corner, corner),
            )
        }
        if (m < 0.98f) {
            val k = 1f - m
            val path = Path().apply {
                moveTo(cx, cy + h * 0.40f * k)
                lineTo(cx, cy - h * 0.38f * k)
                moveTo(cx - w * 0.34f * k, cy - h * 0.04f * k)
                lineTo(cx, cy - h * 0.38f * k)
                lineTo(cx + w * 0.34f * k, cy - h * 0.04f * k)
            }
            drawPath(
                path = path,
                color = fg.copy(alpha = k),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun DrawerChoiceList(
    choices: List<SessionConfigChoice>,
    selected: String,
    leading: (@Composable (SessionConfigChoice) -> Unit)? = null,
    onSelect: (String) -> Unit,
    onAddCustomModel: (() -> Unit)? = null,
) {
    val colors = VibexTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 248.dp)
            .clip(shape)
            .background(colors.bg.copy(alpha = 0.65f))
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        choices.forEach { choice ->
            val isSelected = choice.value == selected
            val rowShape = RoundedCornerShape(10.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(choice.value) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke(choice)
                Text(
                    text = choice.label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.accent else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (onAddCustomModel != null) {
            HorizontalDivider(thickness = 0.5.dp, color = colors.stroke, modifier = Modifier.padding(vertical = 2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAddCustomModel() }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "自定义 / 中转模型…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MenuList(
    title: String,
    choices: List<SessionConfigChoice>,
    selected: String,
    leading: (@Composable (SessionConfigChoice) -> Unit)? = null,
    onSelect: (String) -> Unit,
    onAddCustomModel: (() -> Unit)? = null,
) {
    val colors = VibexTheme.colors
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .width(260.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(shape)
            .background(colors.card)
            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), shape),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            choices.forEach { choice ->
                val isSelected = choice.value == selected
                val rowShape = RoundedCornerShape(11.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(if (isSelected) colors.textPrimary.copy(alpha = 0.07f) else Color.Transparent)
                        .clickable { onSelect(choice.value) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading?.invoke(choice)
                    Text(
                        text = choice.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            if (onAddCustomModel != null) {
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke, modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { onAddCustomModel() }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "自定义 / 中转模型…",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EffortMenu(
    option: SessionConfigOption,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val choices = option.choices
    val selectedIndex = remember(choices, option.value) {
        choices.indexOfFirst { it.value == option.value }.coerceAtLeast(0)
    }
    var liveIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    val safeLiveIndex = liveIndex.coerceIn(0, (choices.size - 1).coerceAtLeast(0))
    val current = choices.getOrNull(safeLiveIndex)
    val atMax = choices.size > 1 && safeLiveIndex == choices.lastIndex

    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
            .clip(shape)
            .background(colors.card)
            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (atMax) SparkColor else colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "推理强度",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = current?.label ?: option.currentLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (atMax) SparkColor else colors.textPrimary,
            )
        }

        EffortSlider(
            steps = choices.size,
            index = safeLiveIndex,
            atMax = atMax,
            onIndexChange = { next ->
                liveIndex = next
            },
            onCommit = { next ->
                liveIndex = next
                choices.getOrNull(next)?.let { choice ->
                    onSelect(choice.value)
                }
            },
        )
    }
}

@Composable
private fun EffortSlider(
    steps: Int,
    index: Int,
    atMax: Boolean,
    onIndexChange: (Int) -> Unit,
    onCommit: (Int) -> Unit,
) {
    val colors = VibexTheme.colors
    var widthPx by remember { mutableIntStateOf(1) }
    val trackHeight = 38.dp
    val padPx = 19f
    val count = maxOf(steps, 2)
    val span = (widthPx.toFloat() - padPx * 2f).coerceAtLeast(1f)
    val progress = index.toFloat() / (count - 1).toFloat()
    val fillWidthPx = padPx + span * progress

    fun nearestStep(x: Float): Int {
        val ratio = ((x - padPx) / span).coerceIn(0f, 1f)
        return (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .clip(CircleShape)
            .background(colors.textPrimary.copy(alpha = 0.07f))
            .pointerInput(count) {
                detectTapGestures { offset ->
                    val next = nearestStep(offset.x)
                    onCommit(next)
                }
            }
            .pointerInput(count) {
                var lastIdx = index
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        lastIdx = nearestStep(offset.x)
                        onIndexChange(lastIdx)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        lastIdx = nearestStep(change.position.x)
                        onIndexChange(lastIdx)
                    },
                    onDragEnd = { onCommit(lastIdx) },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val fillColor = if (atMax) SparkColor.copy(alpha = 0.24f) else colors.textPrimary.copy(alpha = 0.14f)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset.Zero,
                size = Size((fillWidthPx + padPx).coerceAtMost(size.width), h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
            )

            for (step in 0 until count) {
                val x = padPx + span * (step.toFloat() / (count - 1).toFloat())
                val dotColor = if (step <= index) {
                    if (atMax) SparkColor else colors.textPrimary.copy(alpha = 0.55f)
                } else {
                    colors.textPrimary.copy(alpha = 0.22f)
                }
                drawCircle(
                    color = dotColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(x, h / 2f),
                )
            }

            // Thumb
            val thumbRadius = 13.dp.toPx()
            drawCircle(
                color = if (atMax) SparkColor else colors.textPrimary,
                radius = thumbRadius,
                center = Offset(fillWidthPx, h / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(fillWidthPx, h / 2f),
            )
        }
    }
}
