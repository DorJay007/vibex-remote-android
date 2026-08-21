package dev.vibex.companion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vibex.companion.app.CompanionModel
import dev.vibex.companion.app.Route
import dev.vibex.companion.app.UiState
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun OnboardingScreen(state: UiState, model: CompanionModel, onScan: () -> Unit) {
    val colors = VibexTheme.colors
    val manual = state.route is Route.ManualPair
    var origin by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMark(72.dp)
            Text(
                if (manual) "手动输入连接码" else "连接到你的 VibeX",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                if (manual) {
                    "填写电脑地址和 8 位连接码。"
                } else {
                    "打开电脑桌面端 → 设置 → 远程连接，点击「生成连接码」。"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (manual) {
            Field(origin, { origin = it }, "地址")
            Field(
                token,
                { token = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                "连接码",
                capitalize = true,
            )
            PrimaryButton("配对", { model.pairManual(origin, token) }, loading = state.pairing)
            TextButton(
                onClick = { model.go(Route.Onboarding) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("扫描连接二维码", color = colors.accent) }
        } else {
            PrimaryButton("扫描连接二维码", onScan, loading = state.pairing)
            SecondaryButton("手动输入连接码", { model.go(Route.ManualPair) }, enabled = !state.pairing)
        }
        state.error?.let {
            Text(it, color = colors.danger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    capitalize: Boolean = false,
) {
    val colors = VibexTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = ShapeControl,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.stroke,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.accent,
        ),
    )
}
