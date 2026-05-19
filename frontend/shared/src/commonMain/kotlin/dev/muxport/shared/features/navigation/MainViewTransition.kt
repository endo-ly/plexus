package dev.muxport.shared.features.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable

/**
 * メインView間の遷移アニメーション
 *
 * @param activeView 現在のアクティブなView
 * @param content 表示するコンテンツ
 */
@Composable
fun MainViewTransition(
    activeView: MainView,
    content: @Composable (MainView) -> Unit,
) {
    AnimatedContent(
        targetState = activeView,
        transitionSpec = {
            when {
                initialState == MainView.Terminal && targetState == MainView.TerminalSession -> {
                    slideInHorizontally { fullWidth -> fullWidth } togetherWith slideOutHorizontally { fullWidth -> -fullWidth }
                }
                initialState == MainView.TerminalSession && targetState == MainView.Terminal -> {
                    slideInHorizontally { fullWidth -> -fullWidth } togetherWith slideOutHorizontally { fullWidth -> fullWidth }
                }
                initialState == MainView.Terminal && targetState == MainView.GatewaySettings -> {
                    slideInHorizontally { fullWidth -> fullWidth } togetherWith slideOutHorizontally { fullWidth -> -fullWidth }
                }
                initialState == MainView.GatewaySettings && targetState == MainView.Terminal -> {
                    slideInHorizontally { fullWidth -> -fullWidth } togetherWith slideOutHorizontally { fullWidth -> fullWidth }
                }
                else -> EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "main-view-transition",
    ) { targetView ->
        content(targetView)
    }
}
