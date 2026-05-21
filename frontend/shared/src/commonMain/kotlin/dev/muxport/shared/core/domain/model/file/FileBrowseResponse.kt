package dev.muxport.shared.core.domain.model.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ファイルブラウズレスポンス
 *
 * ディレクトリ配下のエントリ一覧を表現します。
 *
 * @property path ブラウズ対象のパス
 * @property entries エントリ一覧
 */
@Serializable
data class FileBrowseResponse(
    @SerialName("path") val path: String,
    @SerialName("entries") val entries: List<FileBrowseEntry>,
)
