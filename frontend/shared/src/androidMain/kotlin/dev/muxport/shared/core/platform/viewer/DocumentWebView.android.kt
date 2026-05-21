package dev.muxport.shared.core.platform.viewer

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

private const val VIEWER_HTML_ASSET_PATH = "viewer/viewer.html"
private const val VIEWER_HTML_ASSET_URL = "file:///android_asset/$VIEWER_HTML_ASSET_PATH"

/**
 * Android 向け DocumentWebView 実装。
 *
 * viewer.html をロードし、JavaScript 経由でコンテンツをレンダリングする。
 */
class AndroidDocumentWebView(
    private val context: Context,
) : DocumentWebView {
    private val internalWebView: WebView by lazy { createWebView() }
    private var isViewerLoaded = false
    private var pendingRender: Pair<String, String>? = null
    private var pendingDarkMode: Boolean? = null

    private fun createWebView(): WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                        if (url.endsWith(VIEWER_HTML_ASSET_PATH)) {
                            val html = loadAssetText(VIEWER_HTML_ASSET_PATH)
                            return createResponse(html, "text/html")
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        if (url?.endsWith(VIEWER_HTML_ASSET_PATH) == true) {
                            isViewerLoaded = true
                            pendingDarkMode?.let { setTheme(it) }
                            pendingRender?.let { (type, content) ->
                                render(type, content)
                            }
                            pendingDarkMode = null
                            pendingRender = null
                        }
                    }
                }
        }

    override fun loadViewer() {
        isViewerLoaded = false
        internalWebView.loadUrl(VIEWER_HTML_ASSET_URL)
    }

    override fun render(
        contentType: String,
        content: String,
    ) {
        if (!isViewerLoaded) {
            pendingRender = contentType to content
            return
        }
        val escapedContent = escapeJsTemplateLiteral(content)
        val escapedType = escapeJsString(contentType)
        internalWebView.evaluateJavascript("render('$escapedType', `$escapedContent`)", null)
    }

    override fun setTheme(darkMode: Boolean) {
        if (!isViewerLoaded) {
            pendingDarkMode = darkMode
            return
        }
        val dark = if (darkMode) "true" else "false"
        internalWebView.evaluateJavascript("setTheme($dark)", null)
    }

    override fun destroy() {
        internalWebView.destroy()
    }

    /**
     * asset テキストを読み込む。失敗時はエラー HTML を返す。
     */
    private fun loadAssetText(path: String): String =
        try {
            context.assets
                .open(path)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            "<html><body><p>Failed to load viewer: ${e.message}</p></body></html>"
        }

    /**
     * 文字列から WebResourceResponse を生成する。
     */
    private fun createResponse(
        data: String,
        mimeType: String,
    ): WebResourceResponse {
        val inputStream = ByteArrayInputStream(data.toByteArray(Charsets.UTF_8))
        return WebResourceResponse(mimeType, "UTF-8", inputStream)
    }

    /**
     * JavaScript テンプレートリテラル用エスケープ。
     */
    private fun escapeJsTemplateLiteral(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")

    /**
     * JavaScript 文字列用エスケープ。
     */
    private fun escapeJsString(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    fun getWebView(): WebView = internalWebView
}

/**
 * Android では Context が必要なため、この経路は利用しない。
 */
actual fun createDocumentWebView(): DocumentWebView = throw NotImplementedError("Use createDocumentWebView(Context) instead")

/**
 * Android 用の DocumentWebView を生成する。
 */
fun createDocumentWebView(context: Context): DocumentWebView = AndroidDocumentWebView(context)
