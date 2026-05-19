package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.domain.model.terminal.Session
import dev.muxport.shared.core.domain.model.terminal.SessionStatus
import dev.muxport.shared.core.domain.model.terminal.TerminalSnapshot
import dev.muxport.shared.core.domain.model.terminal.TerminalWsToken
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalRepositoryTest {
    private val testSession =
        Session(
            sessionId = "test-session",
            name = "Test Session",
            status = SessionStatus.CONNECTED,
            lastActivity = "2025-01-01T00:00:00Z",
            createdAt = "2025-01-01T00:00:00Z",
        )

    @Test
    fun `TerminalRepository - all interface methods can be implemented`() =
        runTest {
            val mockRepo =
                object : TerminalRepository {
                    override fun getSessions(forceRefresh: Boolean) =
                        flow<RepositoryResult<List<Session>>> {
                            emit(Result.success(listOf(testSession)))
                        }

                    override fun getSession(
                        sessionId: String,
                        forceRefresh: Boolean,
                    ) = flow<RepositoryResult<Session>> {
                        emit(Result.success(testSession))
                    }

                    override suspend fun issueWsToken(sessionId: String) =
                        Result.success(
                            TerminalWsToken(wsToken = "token", expiresInSeconds = 60),
                        )

                    override suspend fun getSnapshot(sessionId: String) =
                        Result.success(
                            TerminalSnapshot(sessionId = sessionId, content = "output"),
                        )

                    override suspend fun createSession(
                        sessionId: String,
                        workingDir: String,
                    ) = Result.success(testSession)

                    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
                }

            assertNotNull(mockRepo)

            val createResult = mockRepo.createSession("new-session", "~/")
            assertTrue(createResult.isSuccess)
            assertEquals("test-session", createResult.getOrThrow().sessionId)

            val deleteResult = mockRepo.deleteSession("test-session")
            assertTrue(deleteResult.isSuccess)
        }

    @Test
    fun `createSession - returns created session`() =
        runTest {
            val repo =
                object : TerminalRepository {
                    override fun getSessions(forceRefresh: Boolean) =
                        flow<RepositoryResult<List<Session>>> { emit(Result.success(emptyList())) }

                    override fun getSession(
                        sessionId: String,
                        forceRefresh: Boolean,
                    ) = flow<RepositoryResult<Session>> { emit(Result.success(testSession)) }

                    override suspend fun issueWsToken(sessionId: String) = Result.success(TerminalWsToken("t", 60))

                    override suspend fun getSnapshot(sessionId: String) = Result.success(TerminalSnapshot(sessionId, ""))

                    override suspend fun createSession(
                        sessionId: String,
                        workingDir: String,
                    ) = Result.success(
                        testSession.copy(sessionId = sessionId),
                    )

                    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
                }

            val result = repo.createSession("my-session", "/home/user")
            assertTrue(result.isSuccess)
            assertEquals("my-session", result.getOrThrow().sessionId)
        }

    @Test
    fun `createSession - returns failure on error`() =
        runTest {
            val repo =
                object : TerminalRepository {
                    override fun getSessions(forceRefresh: Boolean) =
                        flow<RepositoryResult<List<Session>>> { emit(Result.success(emptyList())) }

                    override fun getSession(
                        sessionId: String,
                        forceRefresh: Boolean,
                    ) = flow<RepositoryResult<Session>> { emit(Result.success(testSession)) }

                    override suspend fun issueWsToken(sessionId: String) = Result.success(TerminalWsToken("t", 60))

                    override suspend fun getSnapshot(sessionId: String) = Result.success(TerminalSnapshot(sessionId, ""))

                    override suspend fun createSession(
                        sessionId: String,
                        workingDir: String,
                    ): RepositoryResult<Session> = Result.failure(ApiError.HttpError(409, "Conflict", "Session already exists"))

                    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
                }

            val result = repo.createSession("duplicate-session", "~/")
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error is ApiError.HttpError)
            assertEquals(409, error.code)
        }

    @Test
    fun `deleteSession - returns success on valid deletion`() =
        runTest {
            val repo =
                object : TerminalRepository {
                    override fun getSessions(forceRefresh: Boolean) =
                        flow<RepositoryResult<List<Session>>> { emit(Result.success(emptyList())) }

                    override fun getSession(
                        sessionId: String,
                        forceRefresh: Boolean,
                    ) = flow<RepositoryResult<Session>> { emit(Result.success(testSession)) }

                    override suspend fun issueWsToken(sessionId: String) = Result.success(TerminalWsToken("t", 60))

                    override suspend fun getSnapshot(sessionId: String) = Result.success(TerminalSnapshot(sessionId, ""))

                    override suspend fun createSession(
                        sessionId: String,
                        workingDir: String,
                    ) = Result.success(testSession)

                    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
                }

            val result = repo.deleteSession("test-session")
            assertTrue(result.isSuccess)
        }

    @Test
    fun `deleteSession - returns failure on not found`() =
        runTest {
            val repo =
                object : TerminalRepository {
                    override fun getSessions(forceRefresh: Boolean) =
                        flow<RepositoryResult<List<Session>>> { emit(Result.success(emptyList())) }

                    override fun getSession(
                        sessionId: String,
                        forceRefresh: Boolean,
                    ) = flow<RepositoryResult<Session>> { emit(Result.success(testSession)) }

                    override suspend fun issueWsToken(sessionId: String) = Result.success(TerminalWsToken("t", 60))

                    override suspend fun getSnapshot(sessionId: String) = Result.success(TerminalSnapshot(sessionId, ""))

                    override suspend fun createSession(
                        sessionId: String,
                        workingDir: String,
                    ) = Result.success(testSession)

                    override suspend fun deleteSession(sessionId: String): RepositoryResult<Unit> =
                        Result.failure(ApiError.HttpError(404, "Not Found", "Session not found"))
                }

            val result = repo.deleteSession("nonexistent")
            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertNotNull(error)
            assertTrue(error is ApiError.HttpError)
            assertEquals(404, error.code)
        }

    @Test
    fun `createSession - receives workingDir`() =
        runTest {
            var capturedWorkingDir = ""

            val repo =
                object : TerminalRepository {
                    override fun getSessions(forceRefresh: Boolean) =
                        flow<RepositoryResult<List<Session>>> { emit(Result.success(emptyList())) }

                    override fun getSession(
                        sessionId: String,
                        forceRefresh: Boolean,
                    ) = flow<RepositoryResult<Session>> { emit(Result.success(testSession)) }

                    override suspend fun issueWsToken(sessionId: String) = Result.success(TerminalWsToken("t", 60))

                    override suspend fun getSnapshot(sessionId: String) = Result.success(TerminalSnapshot(sessionId, ""))

                    override suspend fun createSession(
                        sessionId: String,
                        workingDir: String,
                    ): RepositoryResult<Session> {
                        capturedWorkingDir = workingDir
                        return Result.success(testSession)
                    }

                    override suspend fun deleteSession(sessionId: String) = Result.success(Unit)
                }

            repo.createSession("test", "~/projects")
            assertEquals("~/projects", capturedWorkingDir)
        }
}
