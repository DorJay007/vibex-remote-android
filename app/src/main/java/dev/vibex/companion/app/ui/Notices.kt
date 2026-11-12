package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.NoticeSeverity
import dev.vibex.companion.PendingQuestion
import dev.vibex.companion.PermissionOption
import dev.vibex.companion.PermissionPrompt
import dev.vibex.companion.QuestionChoice
import dev.vibex.companion.QuestionForm
import dev.vibex.companion.SessionNotice
import dev.vibex.companion.app.R
import dev.vibex.companion.app.monitor.AttentionToastKind
import dev.vibex.companion.app.monitor.CompanionAttentionToast
import dev.vibex.companion.app.monitor.TaskLiveActivityState
import dev.vibex.companion.app.monitor.VibeXActivityPhase
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun phaseTint(phase: VibeXActivityPhase): Color = when (phase) {
    VibeXActivityPhase.Thinking -> Color(0xFFA385FF)
    VibeXActivityPhase.Exploring -> Color(0xFF59B8FA)
    VibeXActivityPhase.Developing -> Color(0xFF479EFF)
    VibeXActivityPhase.Verifying -> Color(0xFF38D1B8)
    VibeXActivityPhase.Summarizing -> Color(0xFF73C7FA)
    VibeXActivityPhase.WaitingInput -> Color(0xFFFA9E29)
    VibeXActivityPhase.Completed -> Color(0xFF4CD170)
    VibeXActivityPhase.Failed -> Color(0xFFF54747)
}

@Composable
private fun PhaseIcon(phase: VibeXActivityPhase, tint: Color, size: Dp = 13.dp) {
    val icon = when (phase) {
        VibeXActivityPhase.Thinking -> Icons.Rounded.AutoAwesome
        VibeXActivityPhase.Exploring -> Icons.Rounded.Search
        VibeXActivityPhase.Developing -> Icons.Rounded.Build
        VibeXActivityPhase.Verifying -> Icons.Rounded.VerifiedUser
        VibeXActivityPhase.Summarizing -> Icons.Rounded.ChatBubbleOutline
        VibeXActivityPhase.WaitingInput -> Icons.Rounded.NotificationsActive
        VibeXActivityPhase.Completed -> Icons.Rounded.CheckCircle
        VibeXActivityPhase.Failed -> Icons.Rounded.WarningAmber
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

/**
 * 1:1 port of iOS `TaskActivityWidget` Dynamic Island (compact + expandable capsule).
 */
@Composable
fun DynamicIslandOverlay(
    activity: TaskLiveActivityState?,
    onOpenConversation: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(activity?.conversationId) {
        mutableStateOf(
            activity?.phase == VibeXActivityPhase.WaitingInput ||
                activity?.phase == VibeXActivityPhase.Failed,
        )
    }
    LaunchedEffect(activity?.phase) {
        if (activity?.phase == VibeXActivityPhase.WaitingInput || activity?.phase == VibeXActivityPhase.Failed) {
            expanded = true
        }
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activity?.isRunning) {
        while (activity?.isRunning == true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    AnimatedVisibility(
        visible = activity != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val state = activity ?: return@AnimatedVisibility
        val tint = phaseTint(state.phase)
        val elapsedSec = ((nowMs - state.startedAtMs).coerceAtLeast(0L) / 1000L)
        val timerText = "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                targetState = expanded,
                label = "dynamicIslandExpand",
            ) { isExpanded ->
                if (!isExpanded) {
                    // Compact Dynamic Island Pill
                    Row(
                        modifier = Modifier
                            .shadow(
                                elevation = 14.dp,
                                shape = CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.32f),
                                spotColor = Color.Black.copy(alpha = 0.42f),
                            )
                            .clip(CircleShape)
                            .background(Color(0xF2101115))
                            .border(0.75.dp, tint.copy(alpha = 0.35f), CircleShape)
                            .pointerInput(state.conversationId) {
                                var dy = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, drag ->
                                        dy += drag
                                        if (dy < -18f) onDismiss()
                                        else if (dy > 18f) expanded = true
                                    },
                                )
                            }
                            .clickable { expanded = true }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VibeXIslandLogo(phase = state.phase, size = 22.dp)
                        Text(
                            text = state.phase.shortLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = state.conversationTitle,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 128.dp),
                        )
                        VibeXCircularProgressRing(
                            progress = state.progress,
                            phase = state.phase,
                            size = 22.dp,
                            strokeWidth = 2.4.dp,
                            showLabel = true,
                        )
                    }
                } else {
                    // Expanded Dynamic Island Card
                    val cardShape = RoundedCornerShape(26.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 20.dp,
                                shape = cardShape,
                                ambientColor = Color.Black.copy(alpha = 0.38f),
                                spotColor = Color.Black.copy(alpha = 0.48f),
                            )
                            .clip(cardShape)
                            .background(Color(0xF5101115))
                            .border(0.8.dp, tint.copy(alpha = 0.36f), cardShape)
                            .pointerInput(state.conversationId) {
                                var dy = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, drag ->
                                        dy += drag
                                        if (dy < -20f) expanded = false
                                    },
                                )
                            }
                            .clickable { onOpenConversation(state.conversationId) }
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            VibeXIslandLogo(phase = state.phase, size = 34.dp)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = state.projectName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    PhaseBadgePill(phase = state.phase, title = state.stageTitle)
                                }
                                Text(
                                    text = state.conversationTitle,
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            VibeXCircularProgressRing(
                                progress = state.progress,
                                phase = state.phase,
                                size = 36.dp,
                                strokeWidth = 3.2.dp,
                                showLabel = true,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PhaseIcon(phase = state.phase, tint = tint, size = 13.dp)
                            Text(
                                text = state.stageSubtitle,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (state.isRunning) timerText else if (state.phase == VibeXActivityPhase.Failed) "需处理" else "100%",
                                color = if (state.isRunning) Color.White.copy(alpha = 0.68f) else tint,
                                fontFamily = PlexMono,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        TaskSmoothProgressBar(progress = state.progress, tint = tint)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val stepHint = when {
                                state.total > 0 -> "计划 ${state.completed}/${state.total}"
                                state.stepCount > 0 -> "已推进 ${state.stepCount} 步"
                                else -> "实时同步中"
                            }
                            Text(
                                text = stepHint,
                                color = Color.White.copy(alpha = 0.56f),
                                fontSize = 11.sp,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "收起",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { expanded = false }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.22f))
                                    .border(0.6.dp, tint.copy(alpha = 0.45f), CircleShape)
                                    .clickable {
                                        expanded = false
                                        onOpenConversation(state.conversationId)
                                    }
                                    .padding(horizontal = 11.dp, vertical = 4.5.dp),
                            ) {
                                Text(
                                    text = "进入会话",
                                    color = tint,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeXIslandLogo(phase: VibeXActivityPhase, size: Dp) {
    val tint = phaseTint(phase)
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF24262E), Color(0xFF14151A)),
                    ),
                )
                .border(0.75.dp, tint.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_dark),
                contentDescription = null,
                modifier = Modifier.size(size * 0.60f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.28f)
                .clip(CircleShape)
                .background(tint)
                .border(1.2.dp, Color(0xFF101115), CircleShape),
        )
    }
}

@Composable
private fun PhaseBadgePill(phase: VibeXActivityPhase, title: String) {
    val tint = phaseTint(phase)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(0.6.dp, tint.copy(alpha = 0.34f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(
            text = title,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun VibeXCircularProgressRing(
    progress: Float,
    phase: VibeXActivityPhase,
    size: Dp,
    strokeWidth: Dp,
    showLabel: Boolean,
) {
    val clamped = if (phase == VibeXActivityPhase.Completed) 1f else progress.coerceIn(0.08f, 0.99f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "ringProgress",
    )
    val tint = phaseTint(phase)
    val percent = if (phase == VibeXActivityPhase.Completed) 100 else (animatedProgress * 100f).roundToInt()

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        if (showLabel) {
            Text(
                text = if (size >= 32.dp) "$percent%" else "$percent",
                color = Color.White,
                fontFamily = PlexMono,
                fontSize = if (size >= 32.dp) 9.5.sp else 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TaskSmoothProgressBar(progress: Float, tint: Color) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0.08f, 1f),
        animationSpec = spring(dampingRatio = 0.86f),
        label = "barProgress",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape),
    ) {
        val h = size.height
        drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            size = size,
            cornerRadius = CornerRadius(h / 2f, h / 2f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(tint.copy(alpha = 0.72f), tint),
            ),
            size = Size(size.width * animated, h),
            cornerRadius = CornerRadius(h / 2f, h / 2f),
        )
    }
}

/**
 * 1:1 port of iOS `CompanionToastOverlay` (`CompanionAttentionCenter.swift`).
 * Interactive top attention banner with countdown ring, one-tap permission/question approvals,
 * inline quick-reply composer, and session jump.
 */
@Composable
fun CompanionToastOverlay(
    toast: CompanionAttentionToast?,
    actionBusy: Boolean,
    actionError: String?,
    onDismiss: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRespondPermission: (conversationId: String, permissionId: String, optionId: String) -> Unit,
    onRespondQuestion: (conversationId: String, question: PendingQuestion, choice: QuestionChoice?, fallbackText: String?) -> Unit,
    onSendQuickReply: (toast: CompanionAttentionToast, text: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isReplying by remember(toast?.id) { mutableStateOf(false) }
    var replyText by remember(toast?.id) { mutableStateOf("") }
    var remainingFraction by remember(toast?.id) { mutableFloatStateOf(1f) }

    LaunchedEffect(toast?.id, isReplying) {
        val current = toast ?: return@LaunchedEffect
        if (isReplying) return@LaunchedEffect
        val totalMs = (current.kind.durationSeconds * 1000f).toLong().coerceAtLeast(1000L)
        val stepMs = 80L
        val steps = (totalMs / stepMs).coerceAtLeast(1L)
        for (i in 0..steps) {
            if (isReplying) return@LaunchedEffect
            remainingFraction = (1f - i.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
            delay(stepMs)
        }
        if (!isReplying) {
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val item = toast ?: return@AnimatedVisibility
        val colors = VibexTheme.colors
        val accentColor = when (item.kind) {
            is AttentionToastKind.Permission,
            is AttentionToastKind.Question,
            is AttentionToastKind.Failed -> Color(0xFFF53333)
            is AttentionToastKind.Created -> Color(0xFF2680F5)
            is AttentionToastKind.Completed -> Color(0xFF59B80F)
        }
        val cardShape = RoundedCornerShape(20.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.24f),
                )
                .clip(cardShape)
                .background(colors.card.copy(alpha = 0.97f))
                .border(1.dp, accentColor.copy(alpha = 0.30f), cardShape)
                .pointerInput(item.id) {
                    var totalDy = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            totalDy += dragAmount
                            if (totalDy < -20f) onDismiss()
                        },
                    )
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(modifier = Modifier.size(40.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.textPrimary.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (colors.isDark) R.drawable.logo_dark else R.drawable.logo_light,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .border(2.dp, colors.card, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val badgeIcon = when (item.kind) {
                            is AttentionToastKind.Permission,
                            is AttentionToastKind.Question,
                            is AttentionToastKind.Failed -> Icons.Rounded.WarningAmber
                            is AttentionToastKind.Created -> Icons.Rounded.Add
                            is AttentionToastKind.Completed -> Icons.Rounded.Check
                        }
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.title,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Countdown Ring + Close Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(26.dp)) {
                        val stroke = 2.dp.toPx()
                        drawArc(
                            color = accentColor.copy(alpha = 0.18f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * remainingFraction,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭提醒",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }

            // Direct one-tap approval buttons for Permission or Question
            when (val kind = item.kind) {
                is AttentionToastKind.Permission -> {
                    PermissionToastActionsRow(
                        prompt = kind.prompt,
                        busy = actionBusy,
                        onSelect = { opt ->
                            onRespondPermission(item.conversationId, kind.prompt.id, opt.id)
                        },
                    )
                }
                is AttentionToastKind.Question -> {
                    QuestionToastActionsRow(
                        question = kind.question,
                        busy = actionBusy,
                        onSelectChoice = { choice ->
                            onRespondQuestion(item.conversationId, kind.question, choice, null)
                        },
                        onApproveDefault = {
                            onRespondQuestion(item.conversationId, kind.question, null, "Yes")
                        },
                        onReject = {
                            onRespondQuestion(item.conversationId, kind.question, null, null)
                        },
                    )
                }
                else -> {}
            }

            if (!actionError.isNullOrBlank()) {
                Text(
                    text = actionError,
                    color = colors.danger,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                )
            }

            // Expandable inline quick reply or bottom action bar
            if (isReplying) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(colors.textPrimary.copy(alpha = 0.06f))
                            .border(0.6.dp, colors.stroke, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        if (replyText.isEmpty()) {
                            Text(
                                text = "输入回复内容并发送…",
                                color = colors.textTertiary,
                                fontSize = 13.sp,
                            )
                        }
                        BasicTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(colors.accent),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = "取消",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { isReplying = false }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    val canSubmit = replyText.trim().isNotEmpty() && !actionBusy
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (canSubmit) colors.textPrimary else colors.textPrimary.copy(alpha = 0.25f))
                            .clickable(enabled = canSubmit) {
                                val trimmed = replyText.trim()
                                replyText = ""
                                isReplying = false
                                onSendQuickReply(item, trimmed)
                            }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "发送",
                            color = colors.bg,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    if (item.kind is AttentionToastKind.Completed) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.14f))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "确认完成",
                                color = colors.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.textPrimary.copy(alpha = 0.07f))
                            .border(0.5.dp, colors.stroke, CircleShape)
                            .clickable { isReplying = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "快捷回复",
                            color = colors.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.textPrimary)
                            .clickable {
                                onDismiss()
                                onOpenConversation(item.conversationId)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "打开会话",
                            color = colors.bg,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionToastActionsRow(
    prompt: PermissionPrompt,
    busy: Boolean,
    onSelect: (PermissionOption) -> Unit,
) {
    val colors = VibexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        prompt.options.forEach { option ->
            val allow = option.kind.lowercase().startsWith("allow")
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (allow) colors.accent else colors.textPrimary.copy(alpha = 0.08f))
                    .clickable(enabled = !busy) { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (allow) Icons.Rounded.VerifiedUser else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (allow) Color.White else colors.textPrimary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = option.label,
                    color = if (allow) Color.White else colors.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun QuestionToastActionsRow(
    question: PendingQuestion,
    busy: Boolean,
    onSelectChoice: (QuestionChoice) -> Unit,
    onApproveDefault: () -> Unit,
    onReject: () -> Unit,
) {
    val colors = VibexTheme.colors
    val choices = remember(question) {
        QuestionForm.questions(question).firstOrNull()?.options?.take(3).orEmpty()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (choices.isEmpty()) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(enabled = !busy, onClick = onApproveDefault)
                    .padding(horizontal = 12.dp, vertical = 6.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "批准执行",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            choices.forEachIndexed { idx, choice ->
                val primary = idx == 0
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (primary) colors.accent else colors.textPrimary.copy(alpha = 0.08f))
                        .clickable(enabled = !busy) { onSelectChoice(choice) }
                        .padding(horizontal = 12.dp, vertical = 6.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = choice.label,
                        color = if (primary) Color.White else colors.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.danger.copy(alpha = 0.12f))
                .clickable(enabled = !busy, onClick = onReject)
                .padding(horizontal = 11.dp, vertical = 6.5.dp),
        ) {
            Text(
                text = "拒绝",
                color = colors.danger,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun HostBanner(text: String, severity: NoticeSeverity, onDismiss: () -> Unit) {
    val colors = VibexTheme.colors
    val tint = when (severity) {
        NoticeSeverity.Error -> colors.danger
        NoticeSeverity.Warning -> colors.hold
        NoticeSeverity.Info -> colors.accent
    }
    LaunchedEffect(text, severity) {
        delay(if (severity == NoticeSeverity.Info) 2_600L else 3_500L)
        onDismiss()
    }
    val shape = CircleShape
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(colors.card)
            .border(0.75.dp, tint.copy(alpha = 0.28f), shape)
            .pointerInput(text) {
                var totalDy = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        totalDy += dragAmount
                        if (totalDy < -24f) {
                            onDismiss()
                        }
                    },
                )
            }
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            when (severity) {
                NoticeSeverity.Error -> Icons.Rounded.ErrorOutline
                NoticeSeverity.Warning -> Icons.Rounded.WarningAmber
                NoticeSeverity.Info -> Icons.Rounded.Info
            },
            null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SessionNoticeCard(
    notice: SessionNotice,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null,
) {
    val colors = VibexTheme.colors
    val tint = when (notice.severity) {
        NoticeSeverity.Error -> colors.danger
        NoticeSeverity.Warning -> colors.hold
        NoticeSeverity.Info -> colors.accent
    }
    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(0.12f))
            .border(0.5.dp, tint.copy(0.32f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                when (notice.severity) {
                    NoticeSeverity.Error -> Icons.Rounded.ErrorOutline
                    NoticeSeverity.Warning -> Icons.Rounded.WarningAmber
                    NoticeSeverity.Info -> Icons.Rounded.Info
                },
                null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Text(notice.title, color = colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Close, "关闭", tint = colors.textTertiary, modifier = Modifier.size(16.dp).clickable(onClick = onDismiss))
        }
        if (notice.body.isNotBlank()) Text(notice.body, color = colors.textSecondary, fontSize = 13.sp)
        if (notice.action == "retry" && onAction != null) {
            TextButton(onClick = onAction) { Text("重试", color = colors.accent) }
        }
    }
}

fun hostBannerSeverity(text: String): NoticeSeverity = when {
    text.contains("timeout", true) ||
        text.contains("超时") ||
        text.contains("失败") ||
        text.contains("需要重新配对") -> NoticeSeverity.Error
    text.contains("恢复") || text.contains("不兼容") -> NoticeSeverity.Warning
    else -> NoticeSeverity.Info
}
