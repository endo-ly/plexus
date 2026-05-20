package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.domain.model.file.FileBrowseResponse
import dev.muxport.shared.core.domain.model.file.FileReadResult

/**
 * File Repository
 *
 * ファイルブラウズ・ファイル読み取りを担当します。
 */
interface FileRepository {
    /**
     * ディレクトリ内のエントリ一覧を取得する
     *
     * @param sessionId セッションID
     * @param path ブラウズ対象のパス（デフォルト: "."）
     * @param showHidden 隠しファイルを表示するかどうか
     * @return ファイルブラウズレスポンス
     */
    suspend fun browseFiles(
        sessionId: String,
        path: String = ".",
        showHidden: Boolean = false,
    ): RepositoryResult<FileBrowseResponse>

    /**
     * ファイルの内容を読み取る
     *
     * @param sessionId セッションID
     * @param path ファイルパス
     * @param offset 読み取り開始オフセット（バイト）
     * @param limit 読み取り上限サイズ（バイト）
     * @return ファイル読み取り結果
     */
    suspend fun readFile(
        sessionId: String,
        path: String,
        offset: Int = 0,
        limit: Int = 1_048_576,
    ): RepositoryResult<FileReadResult>
}
