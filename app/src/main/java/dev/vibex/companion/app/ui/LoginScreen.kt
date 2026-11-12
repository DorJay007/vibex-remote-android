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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.R
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.data.VibeXAccountRepository
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay

private enum class LoginMode(val label: String) { Password("密码登录"), Code("验证码登录") }

/** Account-first entry: password or code login in-app, Google / GitHub through an in-app browser tab. */
@Composable
fun LoginScreen(state: UiState, model: CompanionModel, onPairWithCode: () -> Unit) {
    val colors = VibexTheme.colors
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var agreed by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(LoginMode.Password) }
    var account by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val busy = state.accountSigningIn
    val filled = when (mode) {
        LoginMode.Password -> account.isNotBlank() && password.isNotEmpty()
        LoginMode.Code -> target.isNotBlank() && code.isNotBlank()
    }
    val submit = {
        if (agreed && filled && !busy) {
            focusManager.clearFocus()
            when (mode) {
                LoginMode.Password -> model.signInWithPassword(account, password)
                LoginMode.Code -> model.signInWithCode(target, code)
            }
        }
    }
    val moveToNextField: () -> Unit = { focusManager.moveFocus(FocusDirection.Down) }
    val startOAuth = { provider: String -> model.startOAuthLogin(provider) { url -> context.openInAppBrowser(url) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.profiles.isNotEmpty()) {
            Text(
                "取消",
                fontSize = 16.sp,
                color = colors.accent,
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable(enabled = !busy) { model.dismissAccountLogin() }
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        BrandMark(64.dp)
        Spacer(Modifier.height(14.dp))
        Text("欢迎回来", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text("登录你的 VibeX 账号", fontSize = 14.sp, color = colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        ModeSwitch(mode) { mode = it }
        Spacer(Modifier.height(16.dp))
        when (mode) {
            LoginMode.Password -> {
                LoginField("邮箱 / 手机号 / 用户名", account, { account = it }, KeyboardType.Email, ImeAction.Next, moveToNextField)
                Spacer(Modifier.height(12.dp))
                LoginField(
                    "密码",
                    password,
                    { password = it },
                    KeyboardType.Password,
                    ImeAction.Done,
                    submit,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    "忘记密码",
                    fontSize = 12.5.sp,
                    color = colors.accent,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { context.openSitePage("/recover") }
                        .padding(top = 8.dp),
                )
            }
            LoginMode.Code -> {
                LoginField("邮箱或手机号", target, { target = it }, KeyboardType.Email, ImeAction.Next, moveToNextField) {
                    SendCodeButton(
                        resendAtMs = state.accountCodeResendAtMs,
                        sending = state.accountSendingCode,
                        enabled = target.isNotBlank(),
                        onClick = { model.sendLoginCode(target) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                LoginField("验证码", code, { code = it }, KeyboardType.Number, ImeAction.Done, submit)
                if (state.accountCodeSent) {
                    Text(
                        "验证码已发送，10 分钟内有效",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.align(Alignment.Start).padding(top = 8.dp),
                    )
                }
            }
        }
        state.accountError?.let { message ->
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Warning, null, tint = colors.danger, modifier = Modifier.size(15.dp))
                Text(message, fontSize = 13.sp, color = colors.danger)
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(label = "登录", onClick = submit, enabled = agreed && filled, loading = busy)
        Spacer(Modifier.height(20.dp))
        OrDivider()
        Spacer(Modifier.height(20.dp))
        OAuthButton("使用 Google 登录", R.drawable.ic_brand_google, Color.Unspecified, enabled = agreed && !busy) {
            startOAuth("google")
        }
        Spacer(Modifier.height(10.dp))
        OAuthButton("使用 GitHub 登录", R.drawable.ic_brand_github, colors.textPrimary, enabled = agreed && !busy) {
            startOAuth("github")
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("还没有账号？", fontSize = 13.sp, color = colors.textSecondary)
            Text(
                "注册",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier.clickable { context.openSitePage("/register") },
            )
        }
        Spacer(Modifier.height(12.dp))
        AgreementRow(agreed = agreed, onAgreedChange = { agreed = it }, onOpen = { context.openSitePage(it) })
        Spacer(Modifier.height(16.dp))
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
private fun ModeSwitch(mode: LoginMode, onSelect: (LoginMode) -> Unit) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.segmentedTrack)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LoginMode.entries.forEach { option ->
            val selected = option == mode
            Text(
                option.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.textPrimary else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.segmentedThumb else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = VibexTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.5.sp, color = colors.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(ShapeControl)
                .background(colors.card)
                .border(0.75.dp, colors.stroke, ShapeControl)
                .padding(start = 14.dp, end = if (trailing != null) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun SendCodeButton(resendAtMs: Long, sending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(resendAtMs) {
        now = System.currentTimeMillis()
        while (now < resendAtMs) {
            delay(250L)
            now = System.currentTimeMillis()
        }
    }
    val remaining = ((resendAtMs - now + 999L) / 1000L).coerceAtLeast(0L)
    val active = enabled && !sending && remaining == 0L
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) colors.accent.copy(0.14f) else colors.segmentedTrack)
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (sending) {
            CircularProgressIndicator(Modifier.size(14.dp), color = colors.accent, strokeWidth = 2.dp)
        } else {
            Text(
                if (remaining > 0L) "${remaining}s 后重发" else "发送验证码",
                style = TextStyle(fontFeatureSettings = "tnum"),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) colors.accent else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun OrDivider() {
    val colors = VibexTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = colors.hairline)
        Text("或", fontSize = 12.sp, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f), color = colors.hairline)
    }
}

@Composable
private fun OAuthButton(label: String, icon: Int, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(ShapeControl)
            .background(colors.card)
            .border(0.75.dp, colors.stroke, ShapeControl)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
    }
}

@Composable
private fun AgreementRow(agreed: Boolean, onAgreedChange: (Boolean) -> Unit, onOpen: (String) -> Unit) {
    val colors = VibexTheme.colors
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accent))
    val text = buildAnnotatedString {
        append("我已阅读并同意")
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpen("/docs/service") }) { append("《用户协议》") }
        append("和")
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpen(VibeXAccountRepository.PRIVACY_PATH) }) {
            append("《隐私政策》")
        }
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
