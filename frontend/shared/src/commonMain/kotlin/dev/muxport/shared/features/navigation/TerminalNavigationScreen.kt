package dev.muxport.shared.features.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.screen.Screen
import dev.muxport.shared.core.platform.PlatformPreferences
import dev.muxport.shared.core.platform.PlatformPrefsDefaults
import dev.muxport.shared.core.platform.PlatformPrefsKeys
import dev.muxport.shared.features.terminal.agentlist.AgentListScreen
import dev.muxport.shared.features.terminal.session.TerminalScreen
import dev.muxport.shared.features.terminal.settings.GatewaySettingsScreen
import dev.muxport.shared.features.terminal.viewer.DocumentViewerScreen
import org.koin.compose.koinInject

/**
 * ターミナルナビゲーション画面
 *
 * セッション一覧、ターミナルセッション、Gateway設定、ドキュメントビューアの4画面を切り替えるルート画面。
 */
class TerminalNavigationScreen : Screen {
    @Composable
    override fun Content() {
        var activeView by rememberSaveable { mutableStateOf(MainView.Terminal) }
        var documentFileName by rememberSaveable { mutableStateOf("") }
        var documentContentType by rememberSaveable { mutableStateOf("") }
        var documentContent by rememberSaveable { mutableStateOf("") }
        var documentReturnSource by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingRestoreSource by rememberSaveable { mutableStateOf<String?>(null) }

        BackHandler(enabled = activeView != MainView.Terminal) {
            when (activeView) {
                MainView.DocumentViewer -> {
                    pendingRestoreSource = documentReturnSource
                    activeView = MainView.TerminalSession
                }
                else -> activeView = MainView.Terminal
            }
        }

        val preferences = koinInject<PlatformPreferences>()

        val agentListScreen =
            remember {
                AgentListScreen(
                    onSessionSelected = {
                        activeView = MainView.TerminalSession
                    },
                    onOpenGatewaySettings = {
                        activeView = MainView.GatewaySettings
                    },
                )
            }

        MainNavigationHost(
            activeView = activeView,
            onSwipeToTerminal = {
                activeView = MainView.Terminal
            },
            onSwipeToTerminalSession = {
                val lastSessionId =
                    preferences.getString(
                        PlatformPrefsKeys.KEY_LAST_TERMINAL_SESSION,
                        PlatformPrefsDefaults.DEFAULT_LAST_TERMINAL_SESSION,
                    )
                if (lastSessionId.isNotBlank()) {
                    activeView = MainView.TerminalSession
                }
            },
        ) { targetView ->
            when (targetView) {
                MainView.Terminal -> agentListScreen.Content()
                MainView.GatewaySettings -> {
                    val gatewaySettingsScreen =
                        remember {
                            GatewaySettingsScreen(
                                onBack = { activeView = MainView.Terminal },
                            )
                        }
                    gatewaySettingsScreen.Content()
                }
                MainView.TerminalSession -> {
                    val lastSessionId =
                        preferences.getString(
                            PlatformPrefsKeys.KEY_LAST_TERMINAL_SESSION,
                            PlatformPrefsDefaults.DEFAULT_LAST_TERMINAL_SESSION,
                        )
                    if (lastSessionId.isNotBlank()) {
                        val terminalScreen =
                            remember(lastSessionId) {
                                TerminalScreen(
                                    agentId = lastSessionId,
                                    onClose = { activeView = MainView.Terminal },
                                    onNavigateToDocumentViewer = { fileName, contentType, content, source ->
                                        documentFileName = fileName
                                        documentContentType = contentType
                                        documentContent = content
                                        documentReturnSource = source
                                        activeView = MainView.DocumentViewer
                                    },
                                )
                            }
                        if (pendingRestoreSource != null) {
                            val source = pendingRestoreSource
                            LaunchedEffect(source) {
                                terminalScreen.requestPopoverRestore(requireNotNull(source))
                                pendingRestoreSource = null
                            }
                        }
                        terminalScreen.Content()
                    } else {
                        activeView = MainView.Terminal
                    }
                }
                MainView.DocumentViewer -> {
                    val documentViewerScreen =
                        remember(documentFileName, documentContentType, documentContent) {
                            DocumentViewerScreen(
                                fileName = documentFileName,
                                contentType = documentContentType,
                                content = documentContent,
                                onBack = {
                                    pendingRestoreSource = documentReturnSource
                                    activeView = MainView.TerminalSession
                                },
                            )
                        }
                    documentViewerScreen.Content()
                }
            }
        }
    }
}
