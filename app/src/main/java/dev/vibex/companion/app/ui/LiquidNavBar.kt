package dev.vibex.companion.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.VibexTheme

data class LiquidTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun LiquidNavBar(tabs: List<LiquidTab>, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bgElevated)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.hairline),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val tint = if (tab.selected) colors.accent else colors.textSecondary
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.92f else 1f,
                    animationSpec = spring(stiffness = 600f),
                    label = "tabScale",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .semantics {
                            contentDescription = tab.label
                            role = Role.Tab
                            selected = tab.selected
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (!tab.selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                focusManager.clearFocus()
                                tab.onClick()
                            },
                        )
                        .padding(top = 6.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        if (tab.selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 10.5.sp,
                        fontWeight = if (tab.selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
            }
        }
    }
}
