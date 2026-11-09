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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.FormQuestion
import dev.vibex.companion.PendingQuestion
import dev.vibex.companion.PermissionAllowScope
import dev.vibex.companion.PermissionCategory
import dev.vibex.companion.PermissionDiffPreview
import dev.vibex.companion.PermissionOption
import dev.vibex.companion.PermissionPrompt
import dev.vibex.companion.QuestionAnswer
import dev.vibex.companion.QuestionChoice
import dev.vibex.companion.QuestionForm
import dev.vibex.companion.ToolStep
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.remote.v1.JsonValue

/**
 * 1:1 port of iOS `ApprovalCard` (`ApprovalCard.swift`).
 * Interactive multi-step question card anchored above the prompt bar.
 */
@Composable
fun ApprovalCard(
    request: PendingQuestion,
    busy: Boolean,
    respond: (JsonValue?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val questions = remember(request) { QuestionForm.questions(request) }
    var index by remember(request.id) { mutableIntStateOf(0) }
    val answers = remember(request.id) { mutableStateMapOf<String, QuestionAnswer>() }
    var collapsed by remember(request.id) { mutableStateOf(false) }

    val safeIndex = index.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
    val current = questions.getOrNull(safeIndex) ?: return
    val currentAnswer = answers[current.id] ?: QuestionAnswer()
    val isUsingCustom = currentAnswer.useCustom || current.options.isEmpty()

    val canContinue = remember(current, currentAnswer, busy) {
        if (busy) false
        else if (!current.required) true
        else if ((currentAnswer.useCustom || current.options.isEmpty()) && currentAnswer.customText.trim().isNotEmpty()) true
        else !currentAnswer.useCustom && currentAnswer.selected.isNotEmpty()
    }

    val isLast = safeIndex >= questions.size - 1
    val cardShape = RoundedCornerShape(16.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "approvalCollapse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, cardShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(cardShape)
            .background(colors.card)
            .border(0.7.dp, colors.textPrimary.copy(alpha = 0.08f), cardShape),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${safeIndex + 1} / ${questions.size}",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                ),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Text(
                text = current.header?.takeIf { it.isNotBlank() } ?: current.title.ifBlank { "问题确认" },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "刚刚",
                fontSize = 11.5.sp,
                color = colors.textSecondary,
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(15.dp)
                        .rotate(chevronRotation),
                )
            }
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Question prompt + description
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = current.title.ifBlank { request.prompt.ifBlank { "请确认以下问题" } },
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp,
                            color = colors.textPrimary,
                        )
                        val desc = current.description?.takeIf { it.isNotBlank() }
                        if (desc != null) {
                            Text(
                                text = desc,
                                fontSize = 12.5.sp,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    // Choices + custom answer
                    if (current.options.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            current.options.forEach { choice ->
                                val selected = !currentAnswer.useCustom && currentAnswer.selected.contains(choice.value)
                                ApprovalChoiceRow(
                                    question = current,
                                    choice = choice,
                                    selected = selected,
                                    enabled = !busy,
                                    onClick = {
                                        val next = if (current.multiSelect) {
                                            val list = currentAnswer.selected.toMutableList()
                                            if (list.contains(choice.value)) list.remove(choice.value) else list.add(choice.value)
                                            currentAnswer.copy(selected = list, useCustom = false)
                                        } else {
                                            currentAnswer.copy(selected = listOf(choice.value), useCustom = false)
                                        }
                                        answers[current.id] = next
                                    },
                                )
                            }

                            if (current.allowCustom) {
                                val customRowShape = RoundedCornerShape(11.dp)
                                val customChevron by animateFloatAsState(
                                    targetValue = if (isUsingCustom) 90f else 0f,
                                    label = "customChevron",
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(customRowShape)
                                        .background(if (isUsingCustom) colors.accent.copy(alpha = 0.12f) else colors.bg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isUsingCustom) colors.accent.copy(alpha = 0.55f) else colors.textPrimary.copy(alpha = 0.06f),
                                            shape = customRowShape,
                                        )
                                        .clickable(enabled = !busy) {
                                            val nextUse = !currentAnswer.useCustom
                                            answers[current.id] = currentAnswer.copy(
                                                useCustom = nextUse,
                                                selected = if (nextUse && !current.multiSelect) emptyList() else currentAnswer.selected,
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EditNote,
                                        contentDescription = null,
                                        tint = if (isUsingCustom) colors.accent else colors.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "自定义回答",
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isUsingCustom) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isUsingCustom) colors.textPrimary else colors.textSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .rotate(customChevron),
                                    )
                                }

                                if (isUsingCustom) {
                                    ApprovalCustomField(
                                        value = currentAnswer.customText,
                                        placeholder = current.customPlaceholder ?: "请直接输入你的回答…",
                                        onValueChange = { text ->
                                            answers[current.id] = currentAnswer.copy(customText = text, useCustom = true)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        ApprovalCustomField(
                            value = currentAnswer.customText,
                            placeholder = current.customPlaceholder ?: "请直接输入你的回答…",
                            onValueChange = { text ->
                                answers[current.id] = currentAnswer.copy(customText = text, useCustom = true)
                            },
                        )
                    }

                    // Footer buttons
                    val btnShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (questions.size > 1) {
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(btnShape)
                                    .background(colors.bg)
                                    .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), btnShape)
                                    .clickable(enabled = safeIndex > 0 && !busy) {
                                        if (safeIndex > 0) index = safeIndex - 1
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "上一题",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (safeIndex > 0) colors.textPrimary else colors.textTertiary,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(btnShape)
                                .background(colors.bg)
                                .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), btnShape)
                                .clickable(enabled = !busy) { respond(null) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "拒绝",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(btnShape)
                                .background(if (canContinue) colors.accent else colors.textPrimary.copy(alpha = 0.12f))
                                .clickable(enabled = canContinue) {
                                    if (isLast) {
                                        val payload = QuestionForm.content(request, questions, answers)
                                        respond(payload)
                                    } else {
                                        index = safeIndex + 1
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (isLast) "提交" else "下一题",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canContinue) Color.White else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalChoiceRow(
    question: FormQuestion,
    choice: QuestionChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val rowShape = RoundedCornerShape(11.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.bg)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.55f) else colors.textPrimary.copy(alpha = 0.06f),
                shape = rowShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (question.multiSelect) {
                val checkShape = RoundedCornerShape(5.dp)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(checkShape)
                        .background(if (selected) colors.accent else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.35f),
                            shape = checkShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            width = 1.5.dp,
                            color = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.35f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = choice.label,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.textPrimary,
                )
                if (choice.recommended) {
                    Text(
                        text = "推荐",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    )
                }
            }
            val desc = choice.description?.takeIf { it.isNotBlank() }
            if (desc != null) {
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ApprovalCustomField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val colors = VibexTheme.colors
    val fieldShape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(fieldShape)
            .background(colors.bg)
            .border(0.7.dp, colors.accent.copy(alpha = 0.35f), fieldShape)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 13.5.sp,
                color = colors.textTertiary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 13.5.sp,
                color = colors.textPrimary,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp),
        )
    }
}

/**
 * 1:1 port of iOS `AskQuestionToolCardView` (`ApprovalCard.swift`).
 * Inline timeline card for `ask_question` / `AskUserQuestion` tool calls.
 */
@Composable
fun AskQuestionToolCardView(
    tool: ToolStep,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val questions = tool.questions
    if (questions.isEmpty()) return

    var expanded by remember(tool.id) { mutableStateOf(false) }
    var index by remember(tool.id) { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, questions.lastIndex)
    val current = questions[safeIndex]

    val cardShape = RoundedCornerShape(14.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "askToolChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.card)
            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), cardShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.HelpOutline,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = current.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded) {
                    Text(
                        text = if (questions.size > 1) "共 ${questions.size} 个问题 · 点击查看详情" else "点击查看详情",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
            }

            Text(
                text = "问答确认",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.textPrimary.copy(alpha = 0.06f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(15.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (questions.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${safeIndex + 1} / ${questions.size}",
                                style = TextStyle(
                                    fontFamily = PlexMono,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary,
                                ),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.textPrimary.copy(alpha = 0.06f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                            val headerText = current.header?.takeIf { it.isNotBlank() }
                            if (headerText != null) {
                                Text(
                                    text = headerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = "上一题",
                                tint = if (safeIndex > 0) colors.textPrimary else colors.textTertiary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = safeIndex > 0) { if (safeIndex > 0) index = safeIndex - 1 },
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "下一题",
                                tint = if (safeIndex < questions.lastIndex) colors.textPrimary else colors.textTertiary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(enabled = safeIndex < questions.lastIndex) {
                                        if (safeIndex < questions.lastIndex) index = safeIndex + 1
                                    },
                            )
                        }
                    }

                    val desc = current.description?.takeIf { it.isNotBlank() }
                    if (desc != null) {
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.options.forEach { choice ->
                            val rowShape = RoundedCornerShape(9.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .background(colors.bg.copy(alpha = 0.7f))
                                    .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), rowShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (choice.recommended) colors.accent else colors.textSecondary.copy(alpha = 0.4f)),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = choice.label,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (choice.recommended) FontWeight.SemiBold else FontWeight.Normal,
                                            color = colors.textPrimary,
                                        )
                                        if (choice.recommended) {
                                            Text(
                                                text = "推荐",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.accent,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(colors.accent.copy(alpha = 0.14f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    val choiceDesc = choice.description?.takeIf { it.isNotBlank() }
                                    if (choiceDesc != null) {
                                        Text(
                                            text = choiceDesc,
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary,
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

/**
 * 1:1 port of iOS `PermissionBanner` (`ConversationDetailView.swift`).
 * Displays category badge, heading, expandable file diffs / command / locations preview,
 * and progressive `[ 拒绝 ]` + `[ 允许 | ▾ ]` (允许一次 / 本会话允许 / 始终允许) footer.
 */
@Composable
fun PermissionBanner(
    prompt: PermissionPrompt,
    busy: Boolean,
    respond: (PermissionOption?, PermissionAllowScope?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var expandedDiffPaths by remember(prompt.id) { mutableStateOf(setOf<String>()) }
    var scopeMenuExpanded by remember(prompt.id) { mutableStateOf(false) }

    val hasPreview = prompt.displaySubtitle != null ||
        prompt.command != null ||
        prompt.diffs.isNotEmpty() ||
        prompt.textBlocks.isNotEmpty() ||
        prompt.locations.isNotEmpty()

    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.card)
            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), cardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header: category icon + label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val categoryIcon = when (prompt.category) {
                PermissionCategory.Terminal -> Icons.Rounded.Terminal
                PermissionCategory.File -> Icons.Rounded.Description
                PermissionCategory.Web -> Icons.Rounded.Language
                PermissionCategory.Tool -> Icons.Rounded.Build
            }
            Icon(
                imageVector = categoryIcon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = prompt.category.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = colors.textSecondary,
                    strokeWidth = 1.5.dp,
                )
            }
        }

        // Heading
        Text(
            text = prompt.displayHeading,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Preview block
        if (hasPreview) {
            val previewShape = RoundedCornerShape(9.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(previewShape)
                    .background(colors.bg.copy(alpha = 0.7f))
                    .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), previewShape)
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                prompt.displaySubtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                prompt.diffs.forEach { diff ->
                    PermissionDiffPreviewRow(
                        diff = diff,
                        isExpanded = expandedDiffPaths.contains(diff.path),
                        onToggle = {
                            expandedDiffPaths = if (expandedDiffPaths.contains(diff.path)) {
                                expandedDiffPaths - diff.path
                            } else {
                                expandedDiffPaths + diff.path
                            }
                        },
                    )
                }

                prompt.textBlocks.forEach { block ->
                    Text(
                        text = block,
                        style = TextStyle(
                            fontFamily = PlexMono,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary,
                        ),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                prompt.command?.let { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = cmd,
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                            ),
                        )
                    }
                }

                if (prompt.diffs.isEmpty() && prompt.command == null && prompt.locations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        prompt.locations.take(3).forEach { loc ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = loc,
                                    style = TextStyle(
                                        fontFamily = PlexMono,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Progressive Action Footer: [ 拒绝 ] + [ 允许 | ▾ ]
        val btnShape = RoundedCornerShape(9.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(btnShape)
                    .background(colors.bg)
                    .border(0.5.dp, colors.textPrimary.copy(alpha = 0.1f), btnShape)
                    .clickable(enabled = !busy) { respond(prompt.rejectOption, null) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "拒绝",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(btnShape)
                        .background(colors.textPrimary),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = !busy) {
                                val option = prompt.resolveAllowOption(PermissionAllowScope.Once)
                                respond(option, PermissionAllowScope.Once)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "允许",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.bg,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(18.dp)
                            .background(colors.bg.copy(alpha = 0.22f)),
                    )

                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .fillMaxHeight()
                            .clickable(enabled = !busy) { scopeMenuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "允许范围",
                            tint = colors.bg,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                DropdownMenu(
                    expanded = scopeMenuExpanded,
                    onDismissRequest = { scopeMenuExpanded = false },
                ) {
                    PermissionAllowScope.entries.forEach { scope ->
                        DropdownMenuItem(
                            text = { Text(scope.label, fontSize = 14.sp) },
                            onClick = {
                                scopeMenuExpanded = false
                                val option = prompt.resolveAllowOption(scope)
                                respond(option, scope)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDiffPreviewRow(
    diff: PermissionDiffPreview,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = VibexTheme.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "diffPreviewChevron",
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(13.dp)
                    .rotate(chevronRotation),
            )
            Text(
                text = diff.path,
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (diff.oldText.isNullOrEmpty()) "新文件" else "已修改",
                fontSize = 10.5.sp,
                color = colors.textSecondary,
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val oldText = diff.oldText?.takeIf { it.isNotEmpty() }
                if (oldText != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.danger.copy(alpha = 0.07f))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "修改前",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.danger.copy(alpha = 0.85f),
                        )
                        Text(
                            text = oldText.take(600),
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                            ),
                        )
                    }
                }
                if (diff.newText.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.pass.copy(alpha = 0.07f))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (oldText == null) "文件内容" else "修改后",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.pass.copy(alpha = 0.85f),
                        )
                        Text(
                            text = diff.newText.take(600),
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 11.sp,
                                color = colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
