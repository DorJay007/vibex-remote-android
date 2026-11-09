package dev.vibex.companion.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

data class BranchedItem<ID>(
    val id: ID,
    val label: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val tint: Color? = null,
    val action: () -> Unit,
)

data class BranchedSection<ID>(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val count: Int? = null,
    val items: List<BranchedItem<ID>>,
)

/**
 * 1:1 port of iOS `BranchedMenu` (`BranchedMenu.swift`).
 * Vertical tree menu with collapsible sections and left vertical trunk line.
 */
@Composable
fun <ID> BranchedMenu(
    sections: List<BranchedSection<ID>>,
    selected: ID? = null,
    initiallyExpanded: Set<String>? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    var expanded by remember(sections.map { it.id }) {
        mutableStateOf(initiallyExpanded ?: sections.map { it.id }.toSet())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            val isOpen = expanded.contains(section.id)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            expanded = if (isOpen) expanded - section.id else expanded + section.id
                        }
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = if (isOpen) colors.textPrimary else colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = section.title,
                        fontSize = 14.5.sp,
                        fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isOpen) colors.textPrimary else colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    val count = section.count ?: section.items.size
                    if (count > 0) {
                        Text(
                            text = count.toString(),
                            style = TextStyle(
                                fontFamily = PlexMono,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.textPrimary.copy(alpha = 0.06f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isOpen && section.items.isNotEmpty(),
                    enter = expandVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.86f)) + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(start = 13.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .width(1.5.dp)
                                .fillMaxHeight()
                                .background(colors.textPrimary.copy(alpha = 0.14f)),
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            section.items.forEach { item ->
                                val isSelected = selected != null && selected == item.id
                                val itemShape = RoundedCornerShape(10.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(itemShape)
                                        .background(
                                            if (isSelected) colors.textPrimary.copy(alpha = 0.08f)
                                            else Color.Transparent,
                                        )
                                        .clickable(onClick = item.action)
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colors.textPrimary),
                                        )
                                    }
                                    Text(
                                        text = item.label,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    val sub = item.subtitle?.takeIf { it.isNotBlank() }
                                    if (sub != null) {
                                        Text(
                                            text = sub,
                                            style = TextStyle(
                                                fontFamily = PlexMono,
                                                fontSize = 12.sp,
                                                color = colors.textTertiary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    val badge = item.badge?.takeIf { it.isNotBlank() }
                                    if (badge != null) {
                                        Text(
                                            text = badge,
                                            style = TextStyle(
                                                fontFamily = PlexMono,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = item.tint ?: colors.textSecondary,
                                            ),
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
