package dev.vibex.companion.app.ui.timeline

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.vibex.companion.app.ui.ShapeSheet
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DraggableSheet(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setDimAmount(0f)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                statusBarColor = android.graphics.Color.TRANSPARENT
                navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= 29) {
                    isNavigationBarContrastEnforced = false
                }
                WindowCompat.setDecorFitsSystemWindows(this, false)
            }
            onDispose { }
        }
        val colors = VibexTheme.colors
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val appear = remember { Animatable(1f) }
        val drag = remember { Animatable(0f) }
        val dismissPx = with(density) { 140.dp.toPx() }
        LaunchedEffect(Unit) {
            appear.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f * (1f - appear.value.coerceIn(0f, 1f))))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 64.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .offset {
                        val travel = with(density) { 420.dp.toPx() }
                        IntOffset(0, ((appear.value * travel) + drag.value.coerceAtLeast(0f)).roundToInt())
                    }
                    .shadow(18.dp, ShapeSheet, ambientColor = colors.textPrimary.copy(0.12f), spotColor = colors.textPrimary.copy(0.16f))
                    .clip(ShapeSheet)
                    .background(colors.sheet)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        if (drag.value > dismissPx) {
                                            appear.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                                            onDismiss()
                                        } else {
                                            drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                },
                            )
                        }
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(ShapeSheet)
                            .background(colors.stroke),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.2).sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    if (onConfirm != null) {
                        Text(
                            confirmLabel ?: "完成",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (confirmEnabled) colors.accent else colors.textTertiary,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable(enabled = confirmEnabled, onClick = onConfirm)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 16.dp),
                    content = content,
                )
            }
        }
    }
}
