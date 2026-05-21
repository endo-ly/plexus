package dev.muxport.shared.features.terminal.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import dev.muxport.shared.core.settings.AppTheme
import dev.muxport.shared.core.settings.ThemeRepository
import org.koin.compose.koinInject

/**
 * ドキュメントビューア画面。
 *
 * Markdown / Diff / Code / Plain Text をフル画面で表示する。
 */
class DocumentViewerScreen(
    private val fileName: String,
    private val contentType: String,
    private val content: String,
    private val onBack: (() -> Unit)? = null,
) : Screen {
    override val key: ScreenKey
        get() = "DocumentViewerScreen:$fileName:$contentType"

    @Composable
    override fun Content() {
        DocumentViewerContent(
            fileName = fileName,
            contentType = contentType,
            content = content,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentViewerContent(
    fileName: String,
    contentType: String,
    content: String,
    onBack: (() -> Unit)? = null,
) {
    val navigator = LocalNavigator.current
    val webView = rememberDocumentWebView()
    val themeRepository = koinInject<ThemeRepository>()
    val selectedTheme by themeRepository.theme.collectAsState()
    val darkMode = selectedTheme == AppTheme.DARK
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(webView) {
        webView.loadViewer()
        webView.setTheme(darkMode)
        webView.render(contentType, content)
    }

    LaunchedEffect(webView, darkMode) {
        webView.setTheme(darkMode)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = { onBack?.invoke() ?: navigator?.pop() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            DocumentViewerView(
                webView = webView,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
