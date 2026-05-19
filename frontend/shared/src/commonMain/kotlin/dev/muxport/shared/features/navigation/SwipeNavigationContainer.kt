package dev.muxport.shared.features.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

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
                    var accumulatedDragX = 0f
                    var handled = false

                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDragX = 0f
                            handled = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (handled) {
                                return@detectHorizontalDragGestures
                            }

                            accumulatedDragX += dragAmount
                            val swipeThreshold = size.width * 0.2f

                            when (activeView) {
                                MainView.TerminalSession -> {
                                    if (accumulatedDragX >= swipeThreshold || accumulatedDragX <= -swipeThreshold) {
                                        handled = true
                                        onSwipeToTerminal()
                                        change.consume()
                                    }
                                }
                                MainView.Terminal -> {
                                    if (accumulatedDragX <= -swipeThreshold) {
                                        handled = true
                                        onSwipeToTerminalSession()
                                        change.consume()
                                    }
                                }
                                MainView.GatewaySettings -> {
                                    if (accumulatedDragX <= -swipeThreshold) {
                                        handled = true
                                        onSwipeToTerminal()
                                        change.consume()
                                    }
                                }
                                else -> Unit
                            }
                        },
                    )
                },
        content = content,
    )
}
