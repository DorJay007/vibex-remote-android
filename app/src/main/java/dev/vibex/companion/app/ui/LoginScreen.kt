package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme

/** Account-first entry shown while signed out with no paired computer. */
@Composable
fun LoginScreen(state: UiState, model: CompanionModel, onPairWithCode: () -> Unit) {
    val colors = VibexTheme.colors
    var agreed by rememberSaveable { mutableStateOf(false) }
    val login = state.accountLoginState

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(76.dp)
        Spacer(Modifier.height(14.dp))
        Text("VibeX", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.weight(1f))

        if (login != null) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LoadingState("请在浏览器中完成登录")
                if (login.userCode.isNotBlank()) {
                    Text(
                        login.userCode,
                        fontSize = 15.sp,
                        fontFamily = PlexMono,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { model.cancelAccountLogin() }) {
                        Text("取消", color = colors.textSecondary)
                    }
                    TextButton(onClick = { model.reopenAccountVerifyUrl() }) {
                        Text("重新打开授权页", fontWeight = FontWeight.SemiBold, color = colors.accent)
                    }
                }
            }
        } else {
            PrimaryButton(
                label = "登录 VibeX 账号",
                onClick = { model.startAccountLogin() },
                enabled = agreed,
                loading = state.accountStartingLogin,
            )
        }
        Spacer(Modifier.height(12.dp))
        AgreementRow(agreed = agreed, onAgreedChange = { agreed = it }, onOpen = model::openAccountSitePath)
        state.accountError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.5.sp, color = colors.danger)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "使用连接码配对",
            fontSize = 13.sp,
            color = colors.textTertiary,
            modifier = Modifier
                .clickable(onClick = onPairWithCode)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AgreementRow(agreed: Boolean, onAgreedChange: (Boolean) -> Unit, onOpen: (String) -> Unit) {
    val colors = VibexTheme.colors
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accent))
    val text = buildAnnotatedString {
        append("我已阅读并同意")
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpen("/terms") }) { append("《用户协议》") }
        append("和")
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpen("/privacy") }) { append("《隐私政策》") }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = agreed,
            onCheckedChange = onAgreedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.textTertiary,
                checkmarkColor = colors.onAccent,
            ),
        )
        Text(text, fontSize = 12.5.sp, color = colors.textSecondary)
    }
}
