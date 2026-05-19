package dev.muxport.shared.features.terminal.agentlist

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.muxport.shared.core.domain.model.terminal.Session
import dev.muxport.shared.core.domain.repository.TerminalRepository
import dev.muxport.shared.core.platform.PlatformPreferences
import dev.muxport.shared.core.platform.PlatformPrefsDefaults
import dev.muxport.shared.core.platform.PlatformPrefsKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ターミナル画面のViewModel
 *
 * セッション一覧管理、画面遷移などのビジネスロジックを担当する。
 */
class AgentListScreenModel(
    private val terminalRepository: TerminalRepository,
    private val preferences: PlatformPreferences,
) : ScreenModel {
    private val _state = MutableStateFlow(AgentListState())
    val state: StateFlow<AgentListState> = _state.asStateFlow()

    private val _effect = Channel<AgentListEffect>(capacity = 1)
    val effect: Flow<AgentListEffect> = _effect.receiveAsFlow()

    private var sessionsJob: Job? = null

    fun loadSessions() {
        sessionsJob?.cancel()
        sessionsJob =
            screenModelScope.launch {
                _state.update { it.copy(isLoadingSessions = true, sessionsError = null) }

                terminalRepository
                    .getSessions(forceRefresh = true)
                    .collect { result ->
                        result
                            .onSuccess { sessions ->
                                _state.update {
                                    it.copy(
                                        sessions = sessions,
                                        isLoadingSessions = false,
                                        sessionsError = null,
                                    )
                                }
                            }.onFailure { error ->
                                val message = "セッション一覧の読み込みに失敗: ${error.message}"
                                _state.update { it.copy(sessionsError = message, isLoadingSessions = false) }
                                _effect.send(AgentListEffect.ShowError(message))
                            }
                    }
            }
    }

    fun selectSession(sessionId: String) {
        saveLastSession(sessionId)
        screenModelScope.launch {
            _effect.send(AgentListEffect.NavigateToSession(sessionId))
        }
    }

    fun saveLastSession(sessionId: String) {
        preferences.putString(PlatformPrefsKeys.KEY_LAST_TERMINAL_SESSION, sessionId)
    }

    fun createSession(sessionId: String) {
        val workingDir =
            preferences.getString(
                PlatformPrefsKeys.KEY_DEFAULT_WORKING_DIR,
                PlatformPrefsDefaults.DEFAULT_DEFAULT_WORKING_DIR,
            )
        screenModelScope.launch {
            _state.update { it.copy(isCreatingSession = true) }
            terminalRepository
                .createSession(sessionId, workingDir)
                .onSuccess { session ->
                    _state.update { it.copy(isCreatingSession = false) }
                    _effect.send(AgentListEffect.SessionCreated(session))
                    loadSessions()
                }.onFailure { error ->
                    _state.update { it.copy(isCreatingSession = false) }
                    val message = "セッション作成に失敗: ${error.message}"
                    _effect.send(AgentListEffect.ShowError(message))
                }
        }
    }

    fun deleteSession(sessionId: String) {
        screenModelScope.launch {
            _state.update { it.copy(deletingSessionIds = it.deletingSessionIds + sessionId) }
            terminalRepository
                .deleteSession(sessionId)
                .onSuccess {
                    _state.update { it.copy(deletingSessionIds = it.deletingSessionIds - sessionId) }
                    _effect.send(AgentListEffect.SessionDeleted(sessionId))
                    loadSessions()
                }.onFailure { error ->
                    _state.update { it.copy(deletingSessionIds = it.deletingSessionIds - sessionId) }
                    val message = "セッション削除に失敗: ${error.message}"
                    _effect.send(AgentListEffect.ShowError(message))
                }
        }
    }

    fun suggestSessionName(): String {
        val regex = Regex("^session-(\\d+)$")
        val usedNumbers =
            sessionsInState()
                .mapNotNull {
                    regex
                        .matchEntire(it.sessionId)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.toSet()
        val nextNum = generateSequence(1) { it + 1 }.first { it !in usedNumbers }
        return "session-${nextNum.toString().padStart(2, '0')}"
    }

    private fun sessionsInState(): List<Session> = _state.value.sessions
}
