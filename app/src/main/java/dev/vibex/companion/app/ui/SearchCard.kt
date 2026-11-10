package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

/**
 * 1:1 port of iOS `ConnectedDeviceBar` (`SearchCard.swift`).
 */
@Composable
fun ConnectedDeviceBar(
    name: String,
    detail: String,
    statusColor: Color = VibexTheme.colors.pass,
    action: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(0.6.dp, colors.textPrimary.copy(alpha = 0.08f), shape)
            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.textPrimary.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(15.dp),
            )
        }

        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Text(
                text = detail,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }

        if (action != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

data class SearchResultItem<ID>(
    val id: ID,
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val badgeColor: Color? = null,
    val action: () -> Unit,
)

/**
 * 1:1 port of iOS `SearchListCard` (`SearchCard.swift`).
 */
@Composable
fun <ID> SearchListCard(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    items: List<SearchResultItem<ID>>,
    emptyLabel: String = "无匹配结果",
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val cardShape = RoundedCornerShape(16.dp)

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
                .height(42.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Cancel,
                    contentDescription = "清除",
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.stroke)

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyLabel,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEach { item ->
                    val rowShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(rowShape)
                            .clickable(onClick = item.action)
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = item.title,
                            fontSize = 14.sp,
                            color = colors.textPrimary,
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
                                    color = item.badgeColor ?: colors.textSecondary,
                                ),
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
