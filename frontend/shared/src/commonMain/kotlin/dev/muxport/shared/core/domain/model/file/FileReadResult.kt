package dev.muxport.shared.core.domain.model.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ファイル読み取り結果
 *
 * ファイルの内容と言語情報を表現します。
 *
 * @property content ファイル内容
 * @property language 言語識別子 (例: "python", "kotlin", "markdown")
 * @property size ファイルサイズ（バイト）
 * @property truncated 内容が切り詰められているかどうか
 */
@Serializable
data class FileReadResult(
    @SerialName("content") val content: String,
    @SerialName("language") val language: String,
    @SerialName("size") val size: Long,
    @SerialName("truncated") val truncated: Boolean,
)
