package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val shape = RoundedCornerShape(34.dp)
    val fill = if (colors.isDark) Color(0xCC1A2029) else Color(0xD6FFFFFF)
    val highlight = if (colors.isDark) Color.White.copy(0.20f) else Color.White.copy(0.86f)
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(0.16f), spotColor = Color.Black.copy(0.10f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (colors.isDark) Color.White.copy(0.14f) else Color.White.copy(0.92f),
                        fill,
                    ),
                ),
            )
            .border(0.75.dp, highlight, shape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (tab.selected) colors.accent.copy(if (colors.isDark) 0.22f else 0.14f) else Color.Transparent)
                    .semantics {
                        contentDescription = tab.label
                        role = Role.Tab
                        selected = tab.selected
                    }
                    .clickable(onClick = tab.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (tab.selected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = if (tab.selected) colors.accent else colors.textTertiary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    tab.label,
                    color = if (tab.selected) colors.accent else colors.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
