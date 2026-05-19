package dev.muxport.shared.features.navigation

import androidx.compose.runtime.Composable

/**
 * メインのナビゲーションHost
 *
 * スワイプジェスチャーと画面遷移を管理する。
 *
 * @param activeView 現在のアクティブなView
 * @param onSwipeToTerminal スワイプでターミナル一覧へ遷移するコールバック
 * @param onSwipeToTerminalSession スワイプでターミナルセッションへ遷移するコールバック
 * @param content 表示するコンテンツ
 */
@Composable
fun MainNavigationHost(
    activeView: MainView,
    onSwipeToTerminal: () -> Unit,
    onSwipeToTerminalSession: () -> Unit = {},
    content: @Composable (MainView) -> Unit,
) {
    SwipeNavigationContainer(
        activeView = activeView,
        onSwipeToTerminal = onSwipeToTerminal,
        onSwipeToTerminalSession = onSwipeToTerminalSession,
    ) {
        MainViewTransition(
            activeView = activeView,
            content = content,
        )
    }
}
