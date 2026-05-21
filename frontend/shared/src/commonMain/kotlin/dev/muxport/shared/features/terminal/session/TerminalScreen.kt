package dev.muxport.shared.features.terminal.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import dev.muxport.shared.core.domain.model.file.FileBrowseEntry
import dev.muxport.shared.core.domain.model.git.GitDiffFile
import dev.muxport.shared.core.domain.model.git.GitFileChange
import dev.muxport.shared.core.domain.model.git.GitStatusResponse
import dev.muxport.shared.core.domain.repository.FileRepository
import dev.muxport.shared.core.domain.repository.GitRepository
import dev.muxport.shared.core.domain.repository.TerminalRepository
import dev.muxport.shared.core.platform.PlatformPreferences
import dev.muxport.shared.core.platform.PlatformPrefsKeys
import dev.muxport.shared.core.platform.rememberKeyboardState
import dev.muxport.shared.core.settings.AppTheme
import dev.muxport.shared.core.settings.ThemeRepository
import dev.muxport.shared.features.terminal.session.components.DraggableTerminalFloatingControlPill
import dev.muxport.shared.features.terminal.session.components.FileBrowserPopover
import dev.muxport.shared.features.terminal.session.components.GitDiffPopover
import dev.muxport.shared.features.terminal.session.components.SpecialKeysBar
import dev.muxport.shared.features.terminal.session.components.TerminalFloatingControlPosition
import dev.muxport.shared.features.terminal.session.components.TerminalView
import dev.muxport.shared.features.terminal.session.components.rememberTerminalWebView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TERMINAL_KEYBOARD_FOCUS_DELAY_MS = 100L
private val TERMINAL_KEYBOARD_ACCESSORY_HORIZONTAL_PADDING = 4.dp
private val TERMINAL_KEYBOARD_ACCESSORY_VERTICAL_PADDING = 6.dp
private val TERMINAL_KEYBOARD_FOCUS_BUFFER = 16.dp

/**
 * ターミナル画面
 *
 * WebSocket経由でGatewayに接続し、ターミナルエミュレーションを表示する画面。
 *
 * @property agentId エージェントID
 * @property onClose 閉じるボタンが押された時のコールバック
 * @property onNavigateToDocumentViewer ファイルタップ時にドキュメントビューアへ遷移するコールバック
 */
class TerminalScreen(
    private val agentId: String,
    private val onClose: (() -> Unit)? = null,
    private val onNavigateToDocumentViewer: ((fileName: String, contentType: String, content: String, source: String) -> Unit)? = null,
) : Screen {
    override val key: ScreenKey
        get() = "TerminalScreen:$agentId"

    private var pendingRestorePopover: String? = null
    private var restorePopoverRequestId: Int = 0

    fun requestPopoverRestore(source: String) {
        pendingRestorePopover = source
        restorePopoverRequestId++
    }

    @Composable
    override fun Content() {
        TerminalContent(
            agentId = agentId,
            onClose = onClose,
            onNavigateToDocumentViewer = onNavigateToDocumentViewer,
            restorePopover = pendingRestorePopover.also { pendingRestorePopover = null },
            restorePopoverRequestId = restorePopoverRequestId,
        )
    }
}

@Composable
private fun TerminalContent(
    agentId: String,
    onClose: (() -> Unit)? = null,
    onNavigateToDocumentViewer: ((fileName: String, contentType: String, content: String, source: String) -> Unit)? = null,
    restorePopover: String? = null,
    restorePopoverRequestId: Int = 0,
) {
    val navigator = requireNotNull(LocalNavigator.current)
    val webView = rememberTerminalWebView()
    val preferences = koinInject<PlatformPreferences>()
    val themeRepository = koinInject<ThemeRepository>()
    val terminalRepository = koinInject<TerminalRepository>()
    val fileRepository = koinInject<FileRepository>()
    val gitRepository = koinInject<GitRepository>()
    val selectedTheme by themeRepository.theme.collectAsState()
    val connectionState by webView.connectionState.collectAsState(initial = false)
    val keyboardState = rememberKeyboardState()
    val density = LocalDensity.current

    var isConnecting by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var voiceInputError by remember { mutableStateOf<String?>(null) }
    var terminalError by remember { mutableStateOf<String?>(null) }
    var hasConnectedOnce by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableStateOf(0) }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    var isCopyModeOpen by remember { mutableStateOf(false) }
    var keyboardAccessoryHeightPx by remember { mutableIntStateOf(0) }
    var floatingControlPosition by remember(agentId) { mutableStateOf<TerminalFloatingControlPosition?>(null) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showGitDiff by remember { mutableStateOf(false) }
    var gitDiffRefreshRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(restorePopoverRequestId) {
        when (restorePopover) {
            "files" -> {
                showGitDiff = false
                showFileBrowser = true
            }
            "diff" -> {
                showFileBrowser = false
                showGitDiff = true
            }
        }
    }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var currentBrowsePath by remember { mutableStateOf(".") }
    var fileBrowserEntries by remember { mutableStateOf(emptyList<FileBrowseEntry>()) }
    var isGitRepo by remember { mutableStateOf(false) }
    var gitStatus by remember { mutableStateOf<GitStatusResponse?>(null) }
    var gitDiffFiles by remember { mutableStateOf(emptyList<GitDiffFile>()) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var isLoadingGit by remember { mutableStateOf(false) }
    val backoff = remember { createTerminalReconnectBackoff() }
    val coroutineScope = rememberCoroutineScope()

    val voiceInputCoordinator =
        rememberTerminalVoiceInputCoordinator(
            onRecognizedText = { recognizedText -> webView.sendKey(recognizedText) },
            onError = { message -> voiceInputError = message.ifBlank { null } },
        )

    val darkMode = selectedTheme == AppTheme.DARK

    val terminalSettings = rememberTerminalSettings(agentId = agentId, preferences = preferences)

    LaunchedEffect(agentId) {
        preferences.putString(PlatformPrefsKeys.KEY_LAST_TERMINAL_SESSION, agentId)
        val statusResult = gitRepository.getStatus(agentId)
        statusResult
            .onSuccess { status ->
                isGitRepo = true
                gitStatus = status
            }.onFailure {
                isGitRepo = false
            }
    }

    LaunchedEffect(showFileBrowser, currentBrowsePath, showHiddenFiles) {
        if (showFileBrowser) {
            isLoadingFiles = true
            val result =
                fileRepository.browseFiles(
                    sessionId = agentId,
                    path = currentBrowsePath,
                    showHidden = showHiddenFiles,
                )
            result
                .onSuccess { response ->
                    fileBrowserEntries = response.entries
                }
            isLoadingFiles = false
        }
    }

    LaunchedEffect(showGitDiff, gitDiffRefreshRequest) {
        if (showGitDiff) {
            isLoadingGit = true
            gitDiffFiles = emptyList()
            val statusResult = gitRepository.getStatus(agentId)
            statusResult
                .onSuccess { status ->
                    gitStatus = status
                    isGitRepo = true
                }.onFailure {
                    isGitRepo = false
                }
            val unstagedDiffResult = gitRepository.getDiff(agentId, target = "unstaged")
            val stagedDiffResult = gitRepository.getDiff(agentId, target = "staged")
            val unstagedFiles = unstagedDiffResult.getOrNull()?.files.orEmpty()
            val stagedFiles = stagedDiffResult.getOrNull()?.files.orEmpty()
            val statusFiles = gitStatus?.toDiffFallbackFiles().orEmpty()
            gitDiffFiles = mergeGitDiffFiles(unstagedFiles + stagedFiles + statusFiles)
            isLoadingGit = false
        }
    }

    LaunchedEffect(terminalSettings.error) {
        settingsError = terminalSettings.error
    }

    LaunchedEffect(webView, terminalSettings.wsUrl, agentId) {
        webView.loadTerminal()
        webView.setTheme(darkMode)
        if (!terminalSettings.wsUrl.isNullOrBlank()) {
            isConnecting = true
            val result = terminalRepository.issueWsToken(agentId)
            result
                .onSuccess { wsToken ->
                    webView.connect(terminalSettings.wsUrl, wsToken.wsToken)
                }.onFailure { error ->
                    terminalError = "Connection failed"
                    isConnecting = false
                }
        }
    }

    LaunchedEffect(webView, darkMode) {
        webView.setTheme(darkMode)
    }

    val keyboardBottomScrollPaddingPx =
        with(density) {
            if (keyboardState.isVisible) {
                keyboardState.height.toPx() + keyboardAccessoryHeightPx + TERMINAL_KEYBOARD_FOCUS_BUFFER.toPx()
            } else {
                0f
            }
        }
    val floatingControlBottomObstacleHeightPx =
        with(density) {
            if (keyboardState.isVisible) {
                keyboardState.height.toPx() + keyboardAccessoryHeightPx.toFloat()
            } else {
                0f
            }
        }

    LaunchedEffect(webView, keyboardBottomScrollPaddingPx, keyboardState.isVisible) {
        webView.setBottomScrollPadding(keyboardBottomScrollPaddingPx)
        if (keyboardState.isVisible) {
            delay(TERMINAL_KEYBOARD_FOCUS_DELAY_MS)
            webView.focusInputAtBottom()
        }
    }

    LaunchedEffect(connectionState, terminalSettings.wsUrl, agentId) {
        if (connectionState) {
            reconnectJob?.cancel()
            reconnectJob = null
            hasConnectedOnce = true
            isConnecting = false
            terminalError = null
            reconnectAttempts = 0
        } else if (
            hasConnectedOnce &&
            !terminalSettings.wsUrl.isNullOrBlank() &&
            reconnectJob?.isActive != true
        ) {
            reconnectJob =
                coroutineScope.launch {
                    while (isActive && !connectionState) {
                        val delayMs = backoff.calculateDelay(reconnectAttempts)
                        delay(delayMs)

                        if (!isActive || connectionState) {
                            break
                        }

                        reconnectAttempts++
                        isConnecting = true
                        val result = terminalRepository.issueWsToken(agentId)
                        result
                            .onSuccess { wsToken ->
                                webView.connect(terminalSettings.wsUrl, wsToken.wsToken)
                            }.onFailure {
                                isConnecting = false
                            }
                    }
                }
        }
    }

    LaunchedEffect(webView) {
        webView.errors.collect { errorMessage ->
            terminalError = errorMessage
            isConnecting = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            reconnectJob?.cancel()
            webView.disconnect()
        }
    }

    val displayError = settingsError ?: terminalError ?: voiceInputError
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        TerminalView(
            webView = webView,
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        )

        if (isConnecting) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
            )
        }

        displayError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
        }

        if (keyboardState.isVisible) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = TERMINAL_KEYBOARD_ACCESSORY_HORIZONTAL_PADDING,
                                vertical = TERMINAL_KEYBOARD_ACCESSORY_VERTICAL_PADDING,
                            ).onSizeChanged { size ->
                                keyboardAccessoryHeightPx = size.height
                            },
                ) {
                    SpecialKeysBar(
                        onKeyPress = { keySequence -> webView.sendKey(keySequence) },
                        onVoiceInputClick = voiceInputCoordinator.onToggle,
                        isVoiceInputActive = voiceInputCoordinator.isActive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        DraggableTerminalFloatingControlPill(
            sessionId = agentId,
            isConnected = connectionState && displayError == null,
            onBack = { onClose?.invoke() ?: navigator.pop() },
            onPaste = { webView.pasteFromClipboard() },
            onCopy = { isCopyModeOpen = true },
            onFiles = {
                showGitDiff = false
                showFileBrowser = true
            },
            onDiff = {
                showFileBrowser = false
                showGitDiff = true
                gitDiffRefreshRequest++
            },
            isGitRepo = isGitRepo,
            onDragStart = {
                showFileBrowser = false
                showGitDiff = false
            },
            position = floatingControlPosition,
            onPositionChange = { position -> floatingControlPosition = position },
            bottomObstacleHeightPx = floatingControlBottomObstacleHeightPx,
        )

        if (showFileBrowser) {
            FileBrowserPopover(
                entries = fileBrowserEntries,
                currentPath = currentBrowsePath,
                onDirectoryClick = { path -> currentBrowsePath = path },
                onFileClick = { path ->
                    showFileBrowser = false
                    coroutineScope.launch {
                        val result = fileRepository.readFile(agentId, path = path)
                        result
                            .onSuccess { fileResult ->
                                onNavigateToDocumentViewer?.invoke(
                                    path.substringAfterLast("/"),
                                    fileResult.language,
                                    fileResult.content,
                                    "files",
                                )
                            }
                    }
                },
                onBreadcrumbClick = { path -> currentBrowsePath = path },
                onDismiss = { showFileBrowser = false },
                showHidden = showHiddenFiles,
                onShowHiddenChange = { showHiddenFiles = it },
                isLoading = isLoadingFiles,
            )
        }

        if (showGitDiff) {
            GitDiffPopover(
                status = gitStatus,
                diffFiles = gitDiffFiles,
                onFileClick = { path ->
                    showGitDiff = false
                    coroutineScope.launch {
                        val unstagedDiffResult = gitRepository.getDiff(agentId, target = "unstaged", path = path)
                        val stagedDiffResult = gitRepository.getDiff(agentId, target = "staged", path = path)
                        val patch =
                            unstagedDiffResult
                                .getOrNull()
                                ?.files
                                ?.firstOrNull()
                                ?.patch
                                ?: stagedDiffResult
                                    .getOrNull()
                                    ?.files
                                    ?.firstOrNull()
                                    ?.patch
                                ?: "No diff is available for $path."
                        if (unstagedDiffResult.isSuccess || stagedDiffResult.isSuccess) {
                            onNavigateToDocumentViewer?.invoke(
                                path.substringAfterLast("/"),
                                "diff",
                                patch,
                                "diff",
                            )
                        }
                    }
                },
                onDismiss = { showGitDiff = false },
                isLoading = isLoadingGit,
            )
        }
    }

    if (isCopyModeOpen) {
        TerminalCopyModeSheet(
            agentId = agentId,
            onDismiss = { isCopyModeOpen = false },
        )
    }
}

private fun GitStatusResponse.toDiffFallbackFiles(): List<GitDiffFile> =
    (unstaged + staged + untracked).map { change -> change.toDiffFallbackFile() }

private fun GitFileChange.toDiffFallbackFile(): GitDiffFile =
    GitDiffFile(
        path = path,
        additions = if (status == "added" || status == "untracked") 1 else 0,
        deletions = if (status == "deleted") 1 else 0,
        patch = null,
    )

private fun mergeGitDiffFiles(files: List<GitDiffFile>): List<GitDiffFile> =
    files
        .groupBy { it.path }
        .map { (_, pathFiles) ->
            pathFiles.reduce { acc, file ->
                GitDiffFile(
                    path = acc.path,
                    additions = maxOf(acc.additions, file.additions),
                    deletions = maxOf(acc.deletions, file.deletions),
                    patch = acc.patch ?: file.patch,
                )
            }
        }.sortedBy { it.path }
