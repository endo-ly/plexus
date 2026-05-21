package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.domain.model.git.GitCommitDetailResponse
import dev.muxport.shared.core.domain.model.git.GitDiffResponse
import dev.muxport.shared.core.domain.model.git.GitLogResponse
import dev.muxport.shared.core.domain.model.git.GitStatusResponse

/**
 * Gitリポジトリ
 *
 * Gitステータス・差分・ログ・コミット詳細の取得を担当します。
 */
interface GitRepository {
    /**
     * Gitステータスを取得する
     *
     * @param sessionId セッションID
     * @return Gitステータスレスポンス
     */
    suspend fun getStatus(sessionId: String): RepositoryResult<GitStatusResponse>

    /**
     * Git差分を取得する
     *
     * @param sessionId セッションID
     * @param target 差分対象 ("staged", "unstaged", "cached")
     * @param path 特定ファイルパス（nullの場合は全体）
     * @return Git差分レスポンス
     */
    suspend fun getDiff(
        sessionId: String,
        target: String = "unstaged",
        path: String? = null,
    ): RepositoryResult<GitDiffResponse>

    /**
     * Gitログを取得する
     *
     * @param sessionId セッションID
     * @param count 取得件数
     * @return Gitログレスポンス
     */
    suspend fun getLog(
        sessionId: String,
        count: Int = 10,
    ): RepositoryResult<GitLogResponse>

    /**
     * コミット詳細を取得する
     *
     * @param sessionId セッションID
     * @param sha コミットSHA
     * @return コミット詳細レスポンス
     */
    suspend fun getCommitDetail(
        sessionId: String,
        sha: String,
    ): RepositoryResult<GitCommitDetailResponse>
}
