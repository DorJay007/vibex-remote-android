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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.VibexTheme

/**
 * 1:1 port of iOS `ThinkingView` (`ThinkingView.swift`).
 * Collapsed by default; header previews the latest reasoning line while working,
 * or shows total thinking duration when finished.
 */
@Composable
fun ThinkingView(
    text: String,
    durationMs: Int?,
    working: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "thinkingChevron",
    )

    val completedLabel = remember(durationMs) {
        if (durationMs != null && durationMs >= 1000) {
            "已思考 ${maxOf(1, durationMs / 1000)} 秒"
        } else {
            "已思考"
        }
    }

    val previewLine = remember(text) {
        text.split('\n')
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    val pillShape = RoundedCornerShape(9.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(pillShape)
                .background(colors.card)
                .border(0.5.dp, colors.textPrimary.copy(alpha = 0.06f), pillShape)
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        color = colors.textSecondary,
                        strokeWidth = 1.5.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            if (working) {
                ShimmerText(text = "思考中", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(
                    text = completedLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )
            }

            if (!expanded && previewLine.isNotEmpty()) {
                Text(
                    text = "·",
                    fontSize = 12.sp,
                    color = colors.textSecondary.copy(alpha = 0.5f),
                )
                Text(
                    text = previewLine,
                    fontSize = 12.sp,
                    color = colors.textSecondary.copy(alpha = 0.85f),
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
            visible = expanded,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 6.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(colors.textPrimary.copy(alpha = 0.16f)),
                )
                SelectionContainer {
                    Text(
                        text = text,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
