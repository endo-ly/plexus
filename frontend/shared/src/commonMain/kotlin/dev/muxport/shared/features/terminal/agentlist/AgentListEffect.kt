package dev.muxport.shared.features.terminal.agentlist

import dev.muxport.shared.core.domain.model.terminal.Session

/**
 * ターミナル画面のOne-shotイベント (Effect)
 *
 * 画面遷移やSnackbar表示など、状態として保持しない単発のイベントを定義します。
 */
sealed class AgentListEffect {
    /**
     * エラーメッセージを表示する
     *
     * @property message エラーメッセージ
     */
    data class ShowError(
        val message: String,
    ) : AgentListEffect()

    /**
     * 特定のセッションに遷移する
     *
     * @property sessionId 遷移先のセッションID
     */
    data class NavigateToSession(
        val sessionId: String,
    ) : AgentListEffect()

    /**
     * セッションが作成された
     *
     * @property session 作成されたセッション
     */
    data class SessionCreated(
        val session: Session,
    ) : AgentListEffect()

    /**
     * セッションが削除された
     *
     * @property sessionId 削除されたセッションID
     */
    data class SessionDeleted(
        val sessionId: String,
    ) : AgentListEffect()
}
