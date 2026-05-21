package dev.muxport.shared.features.terminal.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.muxport.shared.core.platform.viewer.DocumentWebView

/**
 * ドキュメント WebView を表示する Composable。
 *
 * @param webView ドキュメント WebView
 * @param modifier Modifier
 */
@Composable
expect fun DocumentViewerView(
    webView: DocumentWebView,
    modifier: Modifier = Modifier,
)

/**
 * DocumentWebView を生成して remember で保持する。
 *
 * @return DocumentWebView
 */
@Composable
expect fun rememberDocumentWebView(): DocumentWebView
