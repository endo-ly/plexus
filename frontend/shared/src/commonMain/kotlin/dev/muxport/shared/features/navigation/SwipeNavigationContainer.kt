package dev.muxport.shared.features.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private const val HORIZONTAL_SWIPE_DOMINANCE_RATIO = 1.8f

/**
 * スワイプジェスチャーでViewを切り替えるコンテナ
 *
 * @param activeView 現在のアクティブなView
 * @param onSwipeToTerminal スワイプでターミナル一覧へ遷移するコールバック
 * @param onSwipeToTerminalSession スワイプでターミナルセッションへ遷移するコールバック
 * @param content 表示するコンテンツ
 */
@Composable
fun SwipeNavigationContainer(
    activeView: MainView,
    onSwipeToTerminal: () -> Unit,
    onSwipeToTerminalSession: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(activeView) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var accumulatedDragX = 0f
                        var handled = false
                        var acceptsHorizontalSwipe = false

                        val touchSlopChange =
                            awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                                if (isIntentionalHorizontalSwipe(overSlop)) {
                                    acceptsHorizontalSwipe = true
                                    accumulatedDragX += overSlop.x
                                    change.consume()
                                }
                            }

                        if (touchSlopChange == null || !acceptsHorizontalSwipe) {
                            return@awaitEachGesture
                        }

                        var pointerId = touchSlopChange.id
                        while (!handled) {
                            val event = awaitPointerEvent()
                            val change =
                                event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: break

                            if (!change.pressed) {
                                break
                            }

                            pointerId = change.id
                            accumulatedDragX += change.positionChange().x
                            handled =
                                handleSwipeNavigation(
                                    activeView = activeView,
                                    accumulatedDragX = accumulatedDragX,
                                    swipeThreshold = size.width * 0.2f,
                                    onSwipeToTerminal = onSwipeToTerminal,
                                    onSwipeToTerminalSession = onSwipeToTerminalSession,
                                )
                            change.consume()
                        }
                    }
                },
        content = content,
    )
}

private fun isIntentionalHorizontalSwipe(overSlop: Offset): Boolean {
    val horizontal = abs(overSlop.x)
    val vertical = abs(overSlop.y)
    return horizontal > 0f && horizontal >= vertical * HORIZONTAL_SWIPE_DOMINANCE_RATIO
}

private fun handleSwipeNavigation(
    activeView: MainView,
    accumulatedDragX: Float,
    swipeThreshold: Float,
    onSwipeToTerminal: () -> Unit,
    onSwipeToTerminalSession: () -> Unit,
): Boolean =
    when (activeView) {
        MainView.TerminalSession -> {
            if (accumulatedDragX >= swipeThreshold || accumulatedDragX <= -swipeThreshold) {
                onSwipeToTerminal()
                true
            } else {
                false
            }
        }
        MainView.Terminal -> {
            if (accumulatedDragX <= -swipeThreshold) {
                onSwipeToTerminalSession()
                true
            } else {
                false
            }
        }
        MainView.GatewaySettings -> {
            if (accumulatedDragX <= -swipeThreshold) {
                onSwipeToTerminal()
                true
            } else {
                false
            }
        }
        else -> false
    }
