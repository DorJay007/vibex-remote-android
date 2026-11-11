package dev.vibex.companion.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.ConnectionState
import dev.vibex.companion.app.R
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.Appearance
import dev.vibex.companion.app.ui.theme.VibexTheme
import dev.vibex.companion.bundledAgentAsset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource

val ShapeControl = RoundedCornerShape(14.dp)
val ShapeRow = RoundedCornerShape(14.dp)
val ShapeCard = RoundedCornerShape(14.dp)
val ShapeSheet = RoundedCornerShape(36.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = VibexTheme.colors
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = colors.card,
                color = colors.accent,
            )
        },
        content = content,
    )
}

@Composable
fun BrandMark(size: Dp = 72.dp) {
    val res = if (VibexTheme.colors.isDark) R.drawable.logo_dark else R.drawable.logo_light
    Image(painterResource(res), "VibeX", Modifier.size(size))
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val colors = VibexTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = ShapeControl,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), colors.onAccent, 2.dp)
        else Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val colors = VibexTheme.colors
    Column(
        modifier
            .clip(ShapeCard)
            .background(colors.bgElevated.copy(if (colors.isDark) 0.60f else 0.88f))
            .border(0.75.dp, colors.stroke, ShapeCard)
            .padding(padding),
        content = content,
    )
}

@Composable
fun GlassRow(modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = VibexTheme.colors
    val fill = if (selected) colors.accent.copy(0.16f) else colors.bgElevated.copy(if (colors.isDark) 0.55f else 0.88f)
    val stroke = if (selected) colors.accent.copy(0.40f) else colors.stroke
    Column(
        modifier
            .clip(ShapeRow)
            .background(fill)
            .border(0.75.dp, stroke, ShapeRow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String? = null, action: String? = null, onAction: (() -> Unit)? = null, enabled: Boolean = true) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(36.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        if (!message.isNullOrBlank()) Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) PrimaryButton(action, onAction, enabled = enabled)
    }
}

@Composable
fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    radius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = VibexTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer {
                val s = if (pressed) 0.98f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(radius))
            .background(if (pressed) colors.textPrimary.copy(if (colors.isDark) 0.10f else 0.06f) else Color.Transparent)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        content = content,
    )
}

private val agentAssetCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
@Volatile
private var sharedSvgImageLoader: coil.ImageLoader? = null

private fun getSvgImageLoader(context: android.content.Context): coil.ImageLoader {
    sharedSvgImageLoader?.let { return it }
    synchronized(agentAssetCache) {
        sharedSvgImageLoader?.let { return it }
        val loader = coil.ImageLoader.Builder(context.applicationContext)
            .components { add(coil.decode.SvgDecoder.Factory()) }
            .crossfade(false)
            .build()
        sharedSvgImageLoader = loader
        return loader
    }
}

@Composable
fun AgentAvatar(agentId: String, size: Dp = 28.dp, iconSvg: String? = null, displayName: String = "") {
    val colors = VibexTheme.colors
    val context = LocalContext.current
    val asset = remember(agentId, colors.isDark) { bundledAgentAsset(agentId, colors.isDark) }
    val bytes = remember(asset, iconSvg, colors.isDark) {
        if (asset != null) {
            agentAssetCache[asset] ?: runCatching {
                context.assets.open("agents/$asset").use { it.readBytes() }.also {
                    agentAssetCache[asset] = it
                }
            }.getOrNull()
        } else {
            val fill = if (colors.isDark) "#F5F5F5" else "#111111"
            iconSvg
                ?.takeIf { it.contains("<svg", true) }
                ?.replace("currentColor", fill)
                ?.let { raw ->
                    if (!raw.contains("width=", ignoreCase = true)) {
                        raw.replaceFirst("<svg", "<svg width=\"24\" height=\"24\"", ignoreCase = true)
                    } else {
                        raw
                    }
                }
                ?.toByteArray()
        }
    }
    if (bytes != null) {
        val loader = remember(context) { getSvgImageLoader(context) }
        val request = remember(asset ?: iconSvg, colors.isDark, size) {
            coil.request.ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey("agent_svg:${asset ?: iconSvg.hashCode()}:${colors.isDark}:${size.value}")
                .crossfade(false)
                .build()
        }
        coil.compose.AsyncImage(
            model = request,
            imageLoader = loader,
            contentDescription = displayName.ifBlank { agentId },
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
        return
    }
    val label = (displayName.ifBlank { agentId }).trim().take(1).ifBlank { "A" }.uppercase()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(colors.accentDim),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
    }
}

fun catalogAgent(state: dev.vibex.companion.app.UiState, agentId: String): dev.vibex.companion.CatalogAgent? =
    state.catalog?.agents?.find { it.id.equals(agentId, true) }

@Composable
fun FolderBadge(id: String, size: Dp = 40.dp) {
    val tint = dev.vibex.companion.app.ui.theme.folderTint(id)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tint.copy(0.18f))
            .border(1.dp, tint.copy(0.40f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Folder, null, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).clip(CircleShape).background(color))
}


@Composable
fun longPressModifier(onClick: () -> Unit, onLongClick: (Offset) -> Unit): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    return Modifier
        .onGloballyPositioned { origin = it.localToWindow(Offset.Zero) }
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { local -> onLongClick(origin + local) },
            )
        }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .shadow(3.dp, ShapeRow, ambientColor = colors.textPrimary.copy(0.06f), spotColor = colors.textPrimary.copy(0.08f))
            .clip(ShapeRow)
            .background(colors.card)
            .height(36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = colors.textTertiary, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = VibexTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .shadow(4.dp, ShapeRow, ambientColor = colors.textPrimary.copy(0.07f), spotColor = colors.textPrimary.copy(0.10f))
            .clip(ShapeRow)
            .background(colors.card),
        content = content,
    )
}

data class ActionMenuItem(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

@Composable
fun ActionMenu(
    items: List<ActionMenuItem>,
    onDismiss: () -> Unit,
    anchor: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
) {
    val colors = VibexTheme.colors
    Popup(
        popupPositionProvider = PressAnchorPosition(anchor),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = true),
    ) {
        Column(
            Modifier
                .widthIn(min = 168.dp, max = 228.dp)
                .shadow(14.dp, ShapeRow, ambientColor = colors.textPrimary.copy(0.14f), spotColor = colors.textPrimary.copy(0.18f))
                .clip(ShapeRow)
                .background(colors.card)
                .padding(vertical = 6.dp),
        ) {
            items.forEach { item ->
                Text(
                    item.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (item.danger) colors.danger else colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeRow)
                        .clickable {
                            item.onClick()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private class PressAnchorPosition(
    private val anchor: Offset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val pad = 16
        var x = anchor.x.toInt() - 8
        var y = anchor.y.toInt()
        val maxX = (windowSize.width - popupContentSize.width - pad).coerceAtLeast(pad)
        val maxY = (windowSize.height - popupContentSize.height - pad).coerceAtLeast(pad)
        x = x.coerceIn(pad, maxX)
        y = y.coerceIn(pad, maxY)
        return IntOffset(x, y)
    }
}

@Composable
fun ConnectionChip(connection: ConnectionState, hostName: String?) {
    val colors = VibexTheme.colors
    val (label, tint) = when (connection) {
        ConnectionState.Online -> "在线" to colors.pass
        ConnectionState.Connecting -> "正在连接" to colors.accent
        ConnectionState.Recovering -> "正在恢复" to colors.hold
        ConnectionState.HostSleeping -> "电脑已休眠" to colors.hold
        ConnectionState.AuthRequired -> "授权已撤销" to colors.danger
        ConnectionState.Incompatible -> "版本不兼容" to colors.danger
        ConnectionState.Offline -> "电脑离线" to colors.textTertiary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(tint)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 88.dp).fillMaxWidth(),
            )
            if (trailing != null) {
                Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, fontFamily = PlexMono, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

fun statusColor(status: String, colors: dev.vibex.companion.app.ui.theme.CompanionColors): Color = when (normalizeStatus(status)) {
    "inprogress" -> colors.accent
    "inreview" -> colors.hold
    "done" -> colors.pass
    "todo" -> colors.textTertiary
    else -> colors.textTertiary
}

fun statusLabel(status: String): String = when (normalizeStatus(status)) {
    "inprogress" -> "进行中"
    "inreview" -> "审核中"
    "done" -> "完成"
    "todo" -> "待办"
    else -> status.ifBlank { "待办" }
}

fun normalizeStatus(status: String): String = when (status.lowercase()) {
    "inprogress", "in_progress", "running" -> "inprogress"
    "inreview", "in_review" -> "inreview"
    "done" -> "done"
    "todo", "" -> "todo"
    else -> status.lowercase()
}

fun connectionLabel(connection: ConnectionState): String = when (connection) {
    ConnectionState.Online -> "在线"
    ConnectionState.Connecting -> "正在连接"
    ConnectionState.Recovering -> "正在恢复"
    ConnectionState.HostSleeping -> "电脑已休眠"
    ConnectionState.AuthRequired -> "授权已撤销"
    ConnectionState.Incompatible -> "版本不兼容"
    ConnectionState.Offline -> "电脑离线"
}


@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        GlassCard(modifier.widthIn(min = 188.dp).padding(top = 6.dp), padding = 6.dp, content = content)
    }
}

@Composable
fun GlassMenuItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) colors.accent else colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeRow)
            .background(if (selected) colors.accentDim else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}


@Composable
fun AppearanceStrip(current: Appearance, onSelect: (Appearance) -> Unit) {
    val colors = VibexTheme.colors
    val options = listOf(Appearance.Light to "浅色", Appearance.Dark to "深色", Appearance.System to "系统")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgElevated.copy(if (colors.isDark) 0.55f else 0.88f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = current == value
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onAccent else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.accent else colors.bgElevated.copy(0f))
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
