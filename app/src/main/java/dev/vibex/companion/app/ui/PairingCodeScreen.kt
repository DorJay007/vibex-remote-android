package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ContentPaste
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
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun PairingCodeScreen(
    state: UiState,
    model: CompanionModel,
    onScan: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    val colors = VibexTheme.colors
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }
    val trimmedCode = code.trim()
    val isInvitation = trimmedCode.startsWith("vibex-pairing:") || trimmedCode.startsWith("{")
    val canSubmit = !state.pairing && trimmedCode.isNotEmpty()

    val submit = {
        if (canSubmit) {
            if (isInvitation) {
                model.pairFromRaw(trimmedCode)
            } else {
                model.pairManual("", trimmedCode)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(0.75.dp, colors.stroke, RoundedCornerShape(12.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.pairing, onClick = onScan)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("扫码配对", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("扫描桌面端二维码自动配对", fontSize = 12.sp, color = colors.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(start = 14.dp))
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
                                "输入 8 位连接码或粘贴邀请码",
                                fontSize = 13.5.sp,
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
