package dev.muxport.shared.core.data.repository

import dev.muxport.shared.core.data.repository.internal.RepositoryClient
import dev.muxport.shared.core.domain.model.git.GitCommitDetailResponse
import dev.muxport.shared.core.domain.model.git.GitDiffResponse
import dev.muxport.shared.core.domain.model.git.GitLogResponse
import dev.muxport.shared.core.domain.model.git.GitStatusResponse
import dev.muxport.shared.core.domain.repository.GitRepository
import dev.muxport.shared.core.domain.repository.RepositoryResult
import io.ktor.http.encodeURLParameter

/**
 * GitRepositoryの実装
 *
 * RepositoryClientを使用してGateway APIと通信します。
 */
class GitRepositoryImpl(
    private val repositoryClient: RepositoryClient,
) : GitRepository,
    BaseRepository {
    override suspend fun getStatus(sessionId: String): RepositoryResult<GitStatusResponse> =
        wrapRepositoryOperation {
            repositoryClient.get<GitStatusResponse>(
                "/api/v1/git/sessions/$sessionId/status",
            )
        }

    override suspend fun getDiff(
        sessionId: String,
        target: String,
        path: String?,
    ): RepositoryResult<GitDiffResponse> =
        wrapRepositoryOperation {
            val pathParam = path?.let { "&path=${it.encodeURLParameter()}" } ?: ""
            repositoryClient.get<GitDiffResponse>(
                "/api/v1/git/sessions/$sessionId/diff?target=${target.encodeURLParameter()}$pathParam",
            )
        }

    override suspend fun getLog(
        sessionId: String,
        count: Int,
    ): RepositoryResult<GitLogResponse> =
        wrapRepositoryOperation {
            repositoryClient.get<GitLogResponse>(
                "/api/v1/git/sessions/$sessionId/log?count=$count",
            )
        }

    override suspend fun getCommitDetail(
        sessionId: String,
        sha: String,
    ): RepositoryResult<GitCommitDetailResponse> =
        wrapRepositoryOperation {
            repositoryClient.get<GitCommitDetailResponse>(
                "/api/v1/git/sessions/$sessionId/commits/$sha",
            )
        }
}
