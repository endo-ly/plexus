package dev.muxport.shared.core.platform.viewer

/**
 * ドキュメント表示用 WebView インターフェース。
 *
 * Markdown / Diff / Code / Plain Text のレンダリングを提供する。
 */
interface DocumentWebView {
    /**
     * viewer.html を読み込む
     */
    fun loadViewer()

    /**
     * コンテンツをレンダリングする
     *
     * @param contentType コンテンツタイプ（"markdown", "diff", "code", "plaintext"）
     * @param content 表示するコンテンツテキスト
     */
    fun render(
        contentType: String,
        content: String,
    )

    /**
     * テーマを適用する
     *
     * @param darkMode true でダークテーマ、false でライトテーマ
     */
    fun setTheme(darkMode: Boolean)

    /**
     * WebView を破棄する
     */
    fun destroy()
}

/**
 * DocumentWebView のファクトリ。
 *
 * プラットフォームごとに適切な実装を返す。
 */
expect fun createDocumentWebView(): DocumentWebView
