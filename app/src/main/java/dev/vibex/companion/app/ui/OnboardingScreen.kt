package dev.vibex.companion.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.PairingInvitationParser
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun OnboardingScreen(
    state: UiState,
    model: CompanionModel,
    onScan: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current
    val initialOrigin = state.activeOrigin
        ?: state.profiles.firstOrNull { it.hostId == state.selectedHostId }?.lastSuccessOrigin
        ?: state.profiles.firstOrNull()?.lastSuccessOrigin
        ?: ""
    var code by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf(initialOrigin) }
    var deviceName by remember { mutableStateOf(Build.MODEL.ifBlank { "Android" }) }

    val trimmedCode = code.trim()
    val isInvitation = trimmedCode.startsWith("vibex-pairing:") || trimmedCode.startsWith("{")
    val parsedInvitationHost = remember(trimmedCode) {
        if (!isInvitation) null
        else runCatching {
            val inv = PairingInvitationParser.parse(trimmedCode)
            inv.reachability.firstOrNull { !it.origin.value.contains("[fd", true) }?.origin?.value
                ?: inv.reachability.firstOrNull()?.origin?.value
        }.getOrNull()
    }
    val canSubmit = !state.pairing && trimmedCode.isNotEmpty() && (isInvitation || origin.trim().isNotEmpty())
    val recentOrigins = remember(state.profiles) {
        state.profiles.mapNotNull { it.lastSuccessOrigin }.distinct()
    }

    val submit = {
        if (canSubmit) {
            if (isInvitation) {
                model.pairFromRaw(trimmedCode)
            } else {
                model.pairManual(origin.trim(), trimmedCode)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // iOS Inline Navigation Bar: 取消 | 配对电脑 | 配对
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.bgElevated)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "取消",
                fontSize = 16.sp,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(enabled = !state.pairing, onClick = onDismiss),
            )
            Text(
                "配对电脑",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(Modifier.align(Alignment.CenterEnd)) {
                if (state.pairing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = colors.accent, strokeWidth = 2.dp)
                } else {
                    Text(
                        "配对",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSubmit) colors.accent else colors.textTertiary,
                        modifier = Modifier.clickable(enabled = canSubmit, onClick = submit),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.hairline)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Primary QR Scan Action Card
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .clickable(enabled = !state.pairing, onClick = onScan)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("扫码配对", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Text("对准桌面端二维码即可自动识别连接", fontSize = 12.sp, color = colors.textSecondary)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }

            // 2. Connection Code Input Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "连接码",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (code.isEmpty()) {
                                Text(
                                    "输入 8 位连接码或粘贴完整邀请码",
                                    fontSize = 14.sp,
                                    color = colors.textTertiary,
                                    fontFamily = PlexMono,
                                )
                            }
                            BasicTextField(
                                value = code,
                                onValueChange = { code = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontFamily = PlexMono,
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (code.isNotEmpty()) {
                            Icon(
                                Icons.Rounded.Cancel,
                                contentDescription = "清除",
                                tint = colors.textTertiary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { code = "" },
                            )
                        }
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(colors.accent.copy(0.14f))
                                .clickable {
                                    val pasted = clipboard.getText()?.text.orEmpty().trim()
                                    if (pasted.isNotEmpty()) {
                                        code = pasted
                                        if (pasted.startsWith("vibex-pairing:") || pasted.startsWith("{")) {
                                            model.pairFromRaw(pasted)
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null, tint = colors.accent, modifier = Modifier.size(13.dp))
                            Text("粘贴", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
                        }
                    }
                    if (parsedInvitationHost != null) {
                        HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 14.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("识别到的电脑地址", fontSize = 13.sp, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text(parsedInvitationHost, fontSize = 12.sp, fontFamily = PlexMono, color = colors.textSecondary)
                        }
                    }
                }
            }

            // 3. Host Address (Only needed when using a short 8-char code)
            if (!isInvitation) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "电脑地址",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.card)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            if (origin.isEmpty()) {
                                Text(
                                    "例如 192.168.1.2 或 http://192.168.1.2:17891",
                                    fontSize = 14.sp,
                                    color = colors.textTertiary,
                                    fontFamily = PlexMono,
                                )
                            }
                            BasicTextField(
                                value = origin,
                                onValueChange = { origin = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontFamily = PlexMono,
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (recentOrigins.isNotEmpty()) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                recentOrigins.forEach { savedOrigin ->
                                    val selected = origin.trim() == savedOrigin
                                    Row(
                                        Modifier
                                            .clip(CircleShape)
                                            .background(if (selected) colors.accent.copy(0.16f) else colors.bg)
                                            .clickable { origin = savedOrigin }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.History,
                                            null,
                                            tint = if (selected) colors.accent else colors.textSecondary,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            savedOrigin,
                                            fontSize = 11.sp,
                                            fontFamily = PlexMono,
                                            color = if (selected) colors.accent else colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Device Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "本机名称",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.error?.let { err ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = colors.danger, modifier = Modifier.size(16.dp))
                    Text(err, fontSize = 13.sp, color = colors.danger)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
