package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.NoticeSeverity
import dev.vibex.companion.SessionNotice
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun HostBanner(text: String, severity: NoticeSeverity, onDismiss: () -> Unit) {
    val colors = VibexTheme.colors
    val tint = when (severity) {
        NoticeSeverity.Error -> colors.danger
        NoticeSeverity.Warning -> colors.hold
        NoticeSeverity.Info -> colors.accent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint.copy(0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            modifier = Modifier.size(18.dp),
        )
        Text(text, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, "关闭", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        }
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
