package dev.muxport.shared.core.domain.model.git

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Git差分ファイル情報
 *
 * ファイル単位の追加・削除行数とパッチを表現します。
 *
 * @property path ファイルパス
 * @property additions 追加行数
 * @property deletions 削除行数
 * @property patch 差分パッチ（大きい場合はnull）
 */
@Serializable
data class GitDiffFile(
    @SerialName("path") val path: String,
    @SerialName("additions") val additions: Int,
    @SerialName("deletions") val deletions: Int,
    @SerialName("patch") val patch: String? = null,
)

/**
 * Git差分レスポンス
 *
 * ファイル単位の差分一覧を表現します。
 *
 * @property files 差分ファイル一覧
 */
@Serializable
data class GitDiffResponse(
    @SerialName("files") val files: List<GitDiffFile>,
)
