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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
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
    val dockBackground = if (colors.isDark) {
        Color(0xFF1C1C1E).copy(alpha = 0.92f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.95f)
    }
    val specularBorder = Brush.verticalGradient(
        colors = if (colors.isDark) {
            listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.06f))
        } else {
            listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.05f))
        },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (colors.isDark) 14.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.26f else 0.08f),
                    spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.32f else 0.12f),
                )
                .clip(CircleShape)
                .background(dockBackground)
                .border(0.6.dp, specularBorder, CircleShape)
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab ->
                val tint = if (tab.selected) colors.accent else colors.textSecondary
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.94f else 1f,
                    animationSpec = spring(stiffness = 600f),
                    label = "tabScale",
                )
                val pillBg = if (tab.selected) {
                    if (colors.isDark) Color.White.copy(alpha = 0.10f) else colors.accent.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(pillBg)
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
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        if (tab.selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 10.5.sp,
                        fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
