package dev.muxport.shared.features.terminal.agentlist

import dev.muxport.shared.core.domain.model.terminal.Session
import dev.muxport.shared.core.domain.model.terminal.SessionStatus
import dev.muxport.shared.core.domain.repository.TerminalRepository
import dev.muxport.shared.core.platform.PlatformPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentListScreenModelTest {
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var terminalRepository: TerminalRepository
    private lateinit var preferences: PlatformPreferences
    private lateinit var model: AgentListScreenModel

    private fun testSession(
        sessionId: String,
        name: String = sessionId,
    ) = Session(
        sessionId = sessionId,
        name = name,
        status = SessionStatus.CONNECTED,
        lastActivity = "2025-01-01T00:00:00Z",
        createdAt = "2025-01-01T00:00:00Z",
    )

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        terminalRepository = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        model = AgentListScreenModel(terminalRepository, preferences)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `suggestSessionName returns session-01 when no sessions`() =
        runTest {
            coEvery { terminalRepository.getSessions(any()) } returns
                flowOf(
                    Result.success(emptyList<Session>()),
                )
            model.loadSessions()
            advanceUntilIdle()

            val result = model.suggestSessionName()

            assertEquals("session-01", result)
        }

    @Test
    fun `suggestSessionName increments from max`() =
        runTest {
            coEvery { terminalRepository.getSessions(any()) } returns
                flowOf(
                    Result.success(listOf(testSession("session-01"))),
                )
            model.loadSessions()
            advanceUntilIdle()

            val result = model.suggestSessionName()

            assertEquals("session-02", result)
        }

    @Test
    fun `suggestSessionName ignores non-matching sessions`() =
        runTest {
            coEvery { terminalRepository.getSessions(any()) } returns
                flowOf(
                    Result.success(
                        listOf(
                            testSession("my-project"),
                            testSession("debug-session"),
                        ),
                    ),
                )
            model.loadSessions()
            advanceUntilIdle()

            val result = model.suggestSessionName()

            assertEquals("session-01", result)
        }

    @Test
    fun `suggestSessionName handles gaps`() =
        runTest {
            coEvery { terminalRepository.getSessions(any()) } returns
                flowOf(
                    Result.success(
                        listOf(
                            testSession("session-01"),
                            testSession("session-03"),
                        ),
                    ),
                )
            model.loadSessions()
            advanceUntilIdle()

            val result = model.suggestSessionName()

            assertEquals("session-02", result)
        }
}
