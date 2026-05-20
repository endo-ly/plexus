package dev.muxport.shared.features.terminal.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.muxport.shared.core.platform.viewer.AndroidDocumentWebView
import dev.muxport.shared.core.platform.viewer.DocumentWebView
import dev.muxport.shared.core.platform.viewer.createDocumentWebView

@Composable
actual fun DocumentViewerView(
    webView: DocumentWebView,
    modifier: Modifier,
) {
    AndroidView(
        factory = {
            val androidWebView = (webView as? AndroidDocumentWebView)?.getWebView()
            requireNotNull(androidWebView) {
                "Expected AndroidDocumentWebView but got ${webView::class.simpleName}."
            }
            androidWebView
        },
        modifier = modifier,
    )
}

@Composable
actual fun rememberDocumentWebView(): DocumentWebView {
    val context = LocalContext.current
    return remember(context) {
        createDocumentWebView(context)
    }
}
