package dev.muxport.shared.features.terminal.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.muxport.shared.core.platform.PlatformPreferences
import dev.muxport.shared.core.platform.PlatformPrefsDefaults
import dev.muxport.shared.core.platform.PlatformPrefsKeys
import dev.muxport.shared.core.platform.getDefaultGatewayBaseUrl
import dev.muxport.shared.core.platform.isValidUrl
import dev.muxport.shared.core.platform.normalizeBaseUrl
import dev.muxport.shared.core.settings.AppTheme
import dev.muxport.shared.core.settings.ThemeRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Gateway設定画面のScreenModel
 *
 * Gateway接続情報（URL、API Key）の管理・保存・検証を行う。
 * 入力値の検証、正規化、永続化を担当し、UI StateとOne-shotイベントを管理する。
 *
 * @property preferences プラットフォーム設定ストア（URL/Key永続化用）
 */

class GatewaySettingsScreenModel(
    private val preferences: PlatformPreferences,
    private val themeRepository: ThemeRepository,
    private val httpClient: HttpClient,
) : ScreenModel {
    private val saveMutex = Mutex()

    private val _state = MutableStateFlow(GatewaySettingsState())
    val state: StateFlow<GatewaySettingsState> = _state.asStateFlow()

    private val _effect = Channel<GatewaySettingsEffect>(Channel.BUFFERED)
    val effect: Flow<GatewaySettingsEffect> = _effect.receiveAsFlow()

    init {
        _state.update {
            it.copy(
                inputGatewayUrl =
                    preferences
                        .getString(
                            PlatformPrefsKeys.KEY_GATEWAY_API_URL,
                            PlatformPrefsDefaults.DEFAULT_GATEWAY_API_URL,
                        ).ifBlank { getDefaultGatewayBaseUrl() },
                inputApiKey =
                    preferences.getString(
                        PlatformPrefsKeys.KEY_GATEWAY_API_KEY,
                        PlatformPrefsDefaults.DEFAULT_GATEWAY_API_KEY,
                    ),
                inputDefaultWorkingDir =
                    preferences.getString(
                        PlatformPrefsKeys.KEY_DEFAULT_WORKING_DIR,
                        PlatformPrefsDefaults.DEFAULT_DEFAULT_WORKING_DIR,
                    ),
            )
        }

        screenModelScope.launch {
            themeRepository.theme.collect { theme ->
                _state.update { current -> current.copy(selectedTheme = theme) }
            }
        }
    }

    fun onGatewayUrlChange(value: String) {
        _state.update { it.copy(inputGatewayUrl = value, isSaveSuccess = false) }
    }

    fun onApiKeyChange(value: String) {
        _state.update { it.copy(inputApiKey = value, isSaveSuccess = false) }
    }

    fun onThemeSelected(theme: AppTheme) {
        themeRepository.setTheme(theme)
    }

    fun onDefaultWorkingDirChange(value: String) {
        _state.update { it.copy(inputDefaultWorkingDir = value, isSaveSuccess = false) }
    }

    fun saveSettings() {
        val current = _state.value
        if (current.isSaving) {
            return
        }

        // バリデーション: URLとAPI Keyの両方をチェック
        val validationError =
            when {
                !isValidUrl(current.inputGatewayUrl) -> "有効なGateway URLを入力してください"
                current.inputApiKey.isBlank() -> "API Keyを入力してください"
                else -> null
            }

        if (validationError != null) {
            screenModelScope.launch {
                _effect.send(GatewaySettingsEffect.ShowMessage(validationError))
            }
            return
        }

        screenModelScope.launch {
            if (!saveMutex.tryLock()) {
                return@launch
            }
            _state.update { it.copy(isSaving = true) }
            try {
                val normalizedGatewayUrl = normalizeBaseUrl(current.inputGatewayUrl)
                val trimmedApiKey = current.inputApiKey.trim()
                val trimmedWorkingDir = current.inputDefaultWorkingDir.trim()

                if (trimmedWorkingDir.isNotBlank()) {
                    val validationResult = validateWorkingDir(normalizedGatewayUrl, trimmedApiKey, trimmedWorkingDir)
                    if (validationResult != null) {
                        _effect.send(GatewaySettingsEffect.ShowMessage(validationResult))
                        return@launch
                    }
                }

                preferences.putString(PlatformPrefsKeys.KEY_GATEWAY_API_URL, normalizedGatewayUrl)
                preferences.putString(PlatformPrefsKeys.KEY_GATEWAY_API_KEY, trimmedApiKey)
                preferences.putString(PlatformPrefsKeys.KEY_DEFAULT_WORKING_DIR, trimmedWorkingDir)

                _state.update {
                    it.copy(
                        inputGatewayUrl = normalizedGatewayUrl,
                        inputApiKey = trimmedApiKey,
                        inputDefaultWorkingDir = trimmedWorkingDir,
                        isSaveSuccess = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(GatewaySettingsEffect.ShowMessage("Failed to save settings: ${e.message}"))
            } finally {
                _state.update { it.copy(isSaving = false) }
                saveMutex.unlock()
            }
        }
    }

    private suspend fun validateWorkingDir(
        baseUrl: String,
        apiKey: String,
        path: String,
    ): String? =
        try {
            val response =
                httpClient.get("$baseUrl/api/v1/terminal/validate-working-dir?path=${path.encodeURLParameter()}") {
                    header("X-API-Key", apiKey)
                }
            if (response.status == HttpStatusCode.BadRequest) {
                val body = response.bodyAsText()
                "ディレクトリが存在しません: ${body.substringAfter("detail=")}"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
}
