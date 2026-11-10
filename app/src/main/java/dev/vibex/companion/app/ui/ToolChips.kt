package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.FileChangeItem
import dev.vibex.companion.ToolStep
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

/**
 * 1:1 port of iOS `ToolChipsView` (`ToolChips.swift`).
 * Collapsible tool-run group + inline `AskQuestionToolCardView` + file-diff chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolChipsView(
    tools: List<ToolStep>,
    changes: List<FileChangeItem>,
    working: Boolean,
    openDiff: ((FileChangeItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var openId by remember { mutableStateOf<String?>(null) }
    var runOpen by remember { mutableStateOf(false) }

    val regularTools = remember(tools) {
        tools.filter { !it.isAskQuestion || it.questions.isEmpty() }
    }
    val questionTools = remember(tools) {
        tools.filter { it.isAskQuestion && it.questions.isNotEmpty() }
    }
    val anyToolRunning = remember(regularTools, working) {
        working && regularTools.any { !it.isFinished }
    }
    val latestToolSummary = remember(regularTools) {
        val last = regularTools.lastOrNull() ?: return@remember ""
        val preview = rowPreview(last)
        if (preview.isEmpty()) rowLabel(last) else "${rowLabel(last)} $preview"
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (runOpen) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "toolRunChevron",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (regularTools.isNotEmpty()) {
            val headerShape = RoundedCornerShape(9.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(colors.card)
                    .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), headerShape)
                    .clickable {
                        runOpen = !runOpen
                        if (!runOpen) openId = null
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (anyToolRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            color = colors.textSecondary,
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Text(
                    text = if (anyToolRunning) "正在执行工具 (${regularTools.size})" else "${regularTools.size} 次工具调用",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )

                if (!runOpen && latestToolSummary.isNotEmpty()) {
                    Text(
                        text = "·",
                        fontSize = 12.sp,
                        color = colors.textSecondary.copy(alpha = 0.5f),
                    )
                    Text(
                        text = latestToolSummary,
                        style = TextStyle(
                            fontFamily = PlexMono,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary.copy(alpha = 0.85f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(chevronRotation),
                )
            }

            AnimatedVisibility(
                visible = runOpen,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    regularTools.forEach { tool ->
                        ToolStepRow(
                            tool = tool,
                            working = working,
                            isOpen = openId == tool.id && tool.detail.isNotEmpty(),
                            onToggle = {
                                if (tool.detail.isNotEmpty()) {
                                    openId = if (openId == tool.id) null else tool.id
                                }
                            },
                        )
                    }
                }
            }
        }

        questionTools.forEach { tool ->
            AskQuestionToolCardView(tool = tool)
        }

        if (changes.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                changes.take(6).forEach { change ->
                    FileChangeChip(change = change, openDiff = openDiff)
                }
                if (changes.size > 6) {
                    Text(
                        text = "+${changes.size - 6}",
                        style = TextStyle(
                            fontFamily = PlexMono,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary,
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.card)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolStepRow(
    tool: ToolStep,
    working: Boolean,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    val colors = VibexTheme.colors
    val isRunning = working && !tool.isFinished
    val label = remember(tool) { rowLabel(tool) }
    val preview = remember(tool) { rowPreview(tool) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isOpen) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "toolStepChevron",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = tool.detail.isNotEmpty(), onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        color = colors.textSecondary,
                        strokeWidth = 1.5.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isInlineCommand(tool)) Icons.Rounded.Terminal else toolIcon(tool.kind),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                maxLines = 1,
            )

            if (preview.isNotEmpty()) {
                val chipShape = RoundedCornerShape(7.dp)
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(chipShape)
                        .background(colors.card)
                        .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), chipShape)
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = preview,
                        style = TextStyle(
                            fontFamily = PlexMono,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (tool.detail.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(13.dp)
                        .rotate(chevronRotation),
                )
            }
        }

        if (isOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 22.dp, top = 2.dp, bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(colors.textPrimary.copy(alpha = 0.14f)),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    tool.detail.forEach { line ->
                        Text(
                            text = line,
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileChangeChip(
    change: FileChangeItem,
    openDiff: ((FileChangeItem) -> Unit)?,
) {
    val colors = VibexTheme.colors
    val chipShape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(chipShape)
            .background(colors.card)
            .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), chipShape)
            .then(
                if (openDiff != null) Modifier.clickable { openDiff(change) }
                else Modifier,
            )
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = change.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
        )
        if (change.additions > 0) {
            Text(
                text = "+${change.additions}",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.pass,
                ),
            )
        }
        if (change.deletions > 0) {
            Text(
                text = "−${change.deletions}",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.danger,
                ),
            )
        }
    }
}

private fun isInlineCommand(tool: ToolStep): Boolean {
    return tool.preview.isEmpty() && (
        tool.label.contains(" ") ||
            tool.label.startsWith("/") ||
            tool.label.startsWith("./") ||
            tool.label.contains("|") ||
            tool.label.contains("&&")
        )
}

private fun rowLabel(tool: ToolStep): String =
    if (isInlineCommand(tool)) "终端" else tool.label

private fun rowPreview(tool: ToolStep): String =
    if (isInlineCommand(tool)) tool.label else tool.preview

private fun toolIcon(kind: String): ImageVector {
    val k = kind.lowercase()
    return when {
        k.contains("read") -> Icons.Rounded.Description
        k.contains("edit") || k.contains("write") || k.contains("patch") -> Icons.Rounded.Edit
        k.contains("bash") || k.contains("exec") || k.contains("shell") || k.contains("cmd") || k.contains("command") -> Icons.Rounded.Terminal
        k.contains("search") || k.contains("grep") || k.contains("glob") || k.contains("find") -> Icons.Rounded.Search
        else -> Icons.Rounded.Build
    }
}
