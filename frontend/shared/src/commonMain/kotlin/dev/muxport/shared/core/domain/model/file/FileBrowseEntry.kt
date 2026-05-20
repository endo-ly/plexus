package dev.muxport.shared.core.domain.model.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ファイルブラウズエントリ
 *
 * ディレクトリ内のファイル・ディレクトリ・シンボリックリンクの情報を表現します。
 *
 * @property name エントリ名
 * @property type エントリ種別 ("file", "directory", "symlink")
 * @property size ファイルサイズ（バイト）
 * @property modified 最終更新日時 (ISO 8601形式)
 */
@Serializable
data class FileBrowseEntry(
    @SerialName("name") val name: String,
    @SerialName("type") val type: String,
    @SerialName("size") val size: Long,
    @SerialName("modified") val modified: String,
)
