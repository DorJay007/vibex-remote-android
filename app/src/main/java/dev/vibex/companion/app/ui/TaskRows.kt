package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.PlanTask
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

/**
 * 1:1 port of iOS `SpinnerRing` (`TaskRows.swift`).
 */
@Composable
fun SpinnerRing(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val transition = rememberInfiniteTransition(label = "spinnerRing")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinnerRingAngle",
    )

    val ringTrack = colors.textPrimary.copy(alpha = 0.10f)
    val ringActive = colors.textPrimary.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(colors.textPrimary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(1.dp)) {
            val strokePx = 1.8.dp.toPx()
            drawCircle(
                color = ringTrack,
                style = Stroke(width = strokePx),
            )
            if (active) {
                drawArc(
                    color = ringActive,
                    startAngle = angle - 90f,
                    sweepAngle = 360f * 0.62f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
            ),
        )
    }
}

/**
 * 1:1 port of iOS `DoneBadge` (`TaskRows.swift`).
 */
@Composable
fun DoneBadge(
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.pass),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * 1:1 port of iOS `TaskRowsView` (`TaskRows.swift`).
 */
@Composable
fun TaskRowsView(
    tasks: List<PlanTask>,
    modifier: Modifier = Modifier,
) {
    var openId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tasks.forEach { task ->
            val isOpen = openId == task.id
            TaskItemRow(
                task = task,
                totalCount = tasks.size,
                isOpen = isOpen,
                onToggle = { openId = if (isOpen) null else task.id },
            )
        }
    }
}

@Composable
private fun TaskItemRow(
    task: PlanTask,
    totalCount: Int,
    isOpen: Boolean,
    onToggle: () -> Unit,
) {
    val colors = VibexTheme.colors
    val rowShape = RoundedCornerShape(if (isOpen) 14.dp else 22.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "taskRowChevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(colors.card)
            .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), rowShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (task.status) {
                PlanTask.Status.Completed -> DoneBadge(size = 24.dp)
                PlanTask.Status.Running -> SpinnerRing(label = "${task.id + 1}", active = true)
                PlanTask.Status.Pending -> SpinnerRing(label = "${task.id + 1}", active = false)
            }

            Text(
                text = task.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = "${task.id + 1}/$totalCount",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 12.5.sp,
                    color = colors.textSecondary,
                ),
            )

            if (task.status == PlanTask.Status.Completed) {
                Text(
                    text = "已完成",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.pass,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.pass.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
            )
        }

        if (isOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 24.dp, end = 14.dp, bottom = 10.dp, top = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.textPrimary.copy(alpha = 0.14f)),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TaskDetailLine(key = "状态", value = statusName(task.status))
                    task.priority?.takeIf { it.isNotBlank() }?.let { priority ->
                        TaskDetailLine(key = "优先级", value = priorityName(priority))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskDetailLine(key: String, value: String) {
    val colors = VibexTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            fontSize = 12.sp,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 12.sp,
                color = colors.textPrimary,
            ),
        )
    }
}

private fun statusName(status: PlanTask.Status): String = when (status) {
    PlanTask.Status.Completed -> "已完成"
    PlanTask.Status.Running -> "进行中"
    PlanTask.Status.Pending -> "待开始"
}

private fun priorityName(raw: String): String = when (raw.lowercase()) {
    "high" -> "高"
    "medium" -> "中"
    "low" -> "低"
    else -> raw
}

/**
 * 1:1 port of iOS `TaskDock` (`TaskRows.swift`).
 * Capsule bar above the prompt bar that expands into the task rows list.
 */
@Composable
fun TaskDock(
    tasks: List<PlanTask>,
    running: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    val colors = VibexTheme.colors
    var expanded by remember { mutableStateOf(false) }

    val completed = remember(tasks) { tasks.count { it.status == PlanTask.Status.Completed } }
    val anyTaskRunning = remember(tasks) { tasks.any { it.status == PlanTask.Status.Running } }
    val isRunning = running || anyTaskRunning
    val finished = tasks.isNotEmpty() && completed == tasks.size
    val current = remember(tasks) {
        tasks.firstOrNull { it.status == PlanTask.Status.Running }
            ?: tasks.firstOrNull { it.status == PlanTask.Status.Pending }
            ?: tasks.lastOrNull()
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "taskDockChevron",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                TaskRowsView(tasks = tasks)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(CircleShape)
                .background(colors.card)
                .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), CircleShape)
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                finished -> DoneBadge(size = 22.dp)
                isRunning -> PixelLoader()
                else -> Icon(
                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }

            Text(
                text = "任务 $completed/${tasks.size}",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                ),
            )

            Text(
                text = if (finished) "已全部完成" else (current?.label.orEmpty()),
                fontSize = 12.5.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
            )
        }
    }
}
