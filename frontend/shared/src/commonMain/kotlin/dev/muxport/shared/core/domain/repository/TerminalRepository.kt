package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.domain.model.terminal.Session
import dev.muxport.shared.core.domain.model.terminal.TerminalSnapshot
import dev.muxport.shared.core.domain.model.terminal.TerminalWsToken
import kotlinx.coroutines.flow.Flow

/**
 * Terminal Repository
 *
 * ターミナルセッションの一覧取得、詳細取得を担当します。
 */
interface TerminalRepository {
    /**
     * セッション一覧を取得する（Flowベース）
     *
     * @param forceRefresh trueの場合、キャッシュを無視してAPIから取得
     * @return セッション一覧のFlow
     */
    fun getSessions(forceRefresh: Boolean = false): Flow<RepositoryResult<List<Session>>>

    /**
     * 特定のセッションを取得する（Flowベース）
     *
     * @param sessionId セッションID
     * @param forceRefresh trueの場合、キャッシュを無視してAPIから取得
     * @return セッションのFlow
     */
    fun getSession(
        sessionId: String,
        forceRefresh: Boolean = false,
    ): Flow<RepositoryResult<Session>>

    /**
     * WebSocket トークンを発行する
     *
     * @param sessionId セッションID
     * @return WebSocket トークン
     */
    suspend fun issueWsToken(sessionId: String): RepositoryResult<TerminalWsToken>

    /**
     * セッションのプレーンテキスト snapshot を取得する
     *
     * @param sessionId セッションID
     * @return 端末 snapshot
     */
    suspend fun getSnapshot(sessionId: String): RepositoryResult<TerminalSnapshot>

    /**
     * セッションを作成する
     *
     * @param sessionId セッション名
     * @param workingDir 初期作業ディレクトリ
     * @return 作成されたセッション
     */
    suspend fun createSession(
        sessionId: String,
        workingDir: String,
    ): RepositoryResult<Session>

    /**
     * セッションを削除する
     *
     * @param sessionId セッションID
     * @return 削除結果
     */
    suspend fun deleteSession(sessionId: String): RepositoryResult<Unit>
}
