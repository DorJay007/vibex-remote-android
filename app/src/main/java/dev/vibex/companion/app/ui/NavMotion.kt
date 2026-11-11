package dev.vibex.companion.app.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment

/**
 * Page transitions shared by every navigation level, so a push, a pop and a tab switch
 * feel the same wherever they happen. Timings follow the iOS companion: a 160ms tab
 * crossfade and a decelerating slide for pushes.
 *
 * `zIndex` is the elevation of the page being entered; the page on top always covers
 * the one below it, whichever direction the transition runs.
 */
internal object NavMotion {
    private const val SLIDE_MILLIS = 340
    private const val FADE_MILLIS = 160
    private const val CHROME_MILLIS = 220
    private const val UNDERLAY_ALPHA = 0.6f

    private val Decelerate = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    private fun <T> slide(): FiniteAnimationSpec<T> = tween(SLIDE_MILLIS, easing = Decelerate)

    val coverEnter: EnterTransition = slideInHorizontally(slide()) { it }
    val coverExit: ExitTransition = slideOutHorizontally(slide()) { it }

    fun push(zIndex: Float): ContentTransform = ContentTransform(
        targetContentEnter = coverEnter,
        initialContentExit = slideOutHorizontally(slide()) { -it / 4 } +
            fadeOut(slide(), targetAlpha = UNDERLAY_ALPHA),
        targetContentZIndex = zIndex,
        sizeTransform = null,
    )

    fun pop(zIndex: Float): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(slide()) { -it / 4 } +
            fadeIn(slide(), initialAlpha = UNDERLAY_ALPHA),
        initialContentExit = coverExit,
        targetContentZIndex = zIndex,
        sizeTransform = null,
    )

    fun present(zIndex: Float): ContentTransform = ContentTransform(
        targetContentEnter = slideInVertically(slide()) { it },
        // The page underneath stays put until the sheet has fully covered it.
        initialContentExit = fadeOut(snap(delayMillis = SLIDE_MILLIS)),
        targetContentZIndex = zIndex,
        sizeTransform = null,
    )

    fun dismiss(zIndex: Float): ContentTransform = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = slideOutVertically(slide()) { it },
        targetContentZIndex = zIndex,
        sizeTransform = null,
    )

    fun crossfade(zIndex: Float): ContentTransform = ContentTransform(
        targetContentEnter = fadeIn(tween(FADE_MILLIS)),
        initialContentExit = fadeOut(tween(FADE_MILLIS)),
        targetContentZIndex = zIndex,
        sizeTransform = null,
    )

    val bottomBarEnter: EnterTransition =
        expandVertically(tween(CHROME_MILLIS, easing = Decelerate), expandFrom = Alignment.Top)
    val bottomBarExit: ExitTransition =
        shrinkVertically(tween(CHROME_MILLIS, easing = Decelerate), shrinkTowards = Alignment.Top)
}
