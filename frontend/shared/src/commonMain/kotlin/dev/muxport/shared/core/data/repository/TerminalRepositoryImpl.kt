package dev.muxport.shared.core.data.repository

import dev.muxport.shared.core.data.repository.internal.InMemoryCache
import dev.muxport.shared.core.data.repository.internal.RepositoryClient
import dev.muxport.shared.core.domain.model.terminal.Session
import dev.muxport.shared.core.domain.model.terminal.TerminalSnapshot
import dev.muxport.shared.core.domain.model.terminal.TerminalWsToken
import dev.muxport.shared.core.domain.repository.RepositoryResult
import dev.muxport.shared.core.domain.repository.TerminalRepository
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TerminalRepositoryの実装
 *
 * RepositoryClientを使用してGateway APIと通信します。
 */
class TerminalRepositoryImpl(
    private val repositoryClient: RepositoryClient,
) : TerminalRepository,
    BaseRepository {
    private val sessionsCache = InMemoryCache<String, List<Session>>()
    private val sessionCache = InMemoryCache<String, Session>()

    override fun getSessions(forceRefresh: Boolean): Flow<RepositoryResult<List<Session>>> =
        flow {
            val cacheKey = "terminal:sessions:list"

            if (!forceRefresh) {
                val cached = sessionsCache.get(cacheKey)
                if (cached != null) {
                    emit(Result.success(cached))
                    return@flow
                }
            }

            val result =
                wrapRepositoryOperation {
                    repositoryClient.get<SessionListResponse>("/api/v1/terminal/sessions").sessions
                }

            result.onSuccess { sessionsCache.put(cacheKey, it) }
            emit(result)
        }.flowOn(Dispatchers.IO)

    override fun getSession(
        sessionId: String,
        forceRefresh: Boolean,
    ): Flow<RepositoryResult<Session>> =
        flow {
            val cacheKey = "terminal:session:$sessionId"

            if (!forceRefresh) {
                val cached = sessionCache.get(cacheKey)
                if (cached != null) {
                    emit(Result.success(cached))
                    return@flow
                }
            }

            val encodedSessionId = sessionId.encodeURLPathPart()
            val result =
                wrapRepositoryOperation {
                    repositoryClient.get<Session>("/api/v1/terminal/sessions/$encodedSessionId")
                }

            result.onSuccess { sessionCache.put(cacheKey, it) }
            emit(result)
        }.flowOn(Dispatchers.IO)

    override suspend fun issueWsToken(sessionId: String): RepositoryResult<TerminalWsToken> =
        wrapRepositoryOperation {
            val encodedSessionId = sessionId.encodeURLPathPart()
            repositoryClient.post<TerminalWsToken>("/api/v1/terminal/sessions/$encodedSessionId/ws-token")
        }

    override suspend fun getSnapshot(sessionId: String): RepositoryResult<TerminalSnapshot> =
        wrapRepositoryOperation {
            val encodedSessionId = sessionId.encodeURLPathPart()
            repositoryClient.get<TerminalSnapshot>("/api/v1/terminal/sessions/$encodedSessionId/snapshot")
        }

    override suspend fun createSession(
        sessionId: String,
        workingDir: String,
    ): RepositoryResult<Session> =
        wrapRepositoryOperation {
            val session =
                repositoryClient.post<Session>(
                    "/api/v1/terminal/sessions",
                    body = CreateSessionRequest(sessionId, workingDir),
                )
            sessionsCache.clear()
            sessionCache.clear()
            session
        }

    override suspend fun deleteSession(sessionId: String): RepositoryResult<Unit> =
        wrapRepositoryOperation {
            val encodedSessionId = sessionId.encodeURLPathPart()
            repositoryClient.deleteAndValidate("/api/v1/terminal/sessions/$encodedSessionId")
            sessionsCache.clear()
            sessionCache.clear()
        }

    @Serializable
    private data class SessionListResponse(
        val sessions: List<Session>,
        val count: Int,
    )

    @Serializable
    private data class CreateSessionRequest(
        @SerialName("session_id") val sessionId: String,
        @SerialName("working_dir") val workingDir: String,
    )
}
