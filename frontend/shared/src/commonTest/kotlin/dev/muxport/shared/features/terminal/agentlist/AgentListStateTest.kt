package dev.muxport.shared.features.terminal.agentlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentListStateTest {
    @Test
    fun `AgentListState starts with empty sessions`() {
        val state = AgentListState()

        assertEquals(0, state.sessions.size)
    }

    @Test
    fun `AgentListState keeps loading and error flags`() {
        val state = AgentListState(isLoadingSessions = true, sessionsError = "failed")

        assertEquals(true, state.isLoadingSessions)
        assertEquals("failed", state.sessionsError)
    }

    @Test
    fun `isCreatingSession defaults to false`() {
        val state = AgentListState()

        assertFalse(state.isCreatingSession)
    }

    @Test
    fun `deletingSessionIds defaults to empty set`() {
        val state = AgentListState()

        assertTrue(state.deletingSessionIds.isEmpty())
    }

    @Test
    fun `isCreatingSession can be set to true`() {
        val state = AgentListState(isCreatingSession = true)

        assertTrue(state.isCreatingSession)
    }

    @Test
    fun `sessionId can be added to deletingSessionIds`() {
        val state = AgentListState(deletingSessionIds = setOf("session-01"))

        assertTrue(state.deletingSessionIds.contains("session-01"))
        assertEquals(1, state.deletingSessionIds.size)
    }

    @Test
    fun `sessionId can be removed from deletingSessionIds`() {
        val state = AgentListState(deletingSessionIds = setOf("session-01", "session-02"))
        val updated = state.copy(deletingSessionIds = state.deletingSessionIds - "session-01")

        assertFalse(updated.deletingSessionIds.contains("session-01"))
        assertTrue(updated.deletingSessionIds.contains("session-02"))
    }
}
