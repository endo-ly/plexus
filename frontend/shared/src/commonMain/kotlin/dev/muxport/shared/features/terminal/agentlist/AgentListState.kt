package dev.muxport.shared.features.terminal.agentlist

import dev.muxport.shared.core.domain.model.terminal.Session

/**
 * ターミナル画面の状態
 *
 * @property sessions セッション一覧
 * @property isLoadingSessions セッション一覧読み込み中
 * @property sessionsError セッション関連のエラーメッセージ
 * @property isCreatingSession セッション作成中
 * @property deletingSessionIds 削除処理中のセッションID一覧
 */
data class AgentListState(
    val sessions: List<Session> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val sessionsError: String? = null,
    val isCreatingSession: Boolean = false,
    val deletingSessionIds: Set<String> = emptySet(),
)
