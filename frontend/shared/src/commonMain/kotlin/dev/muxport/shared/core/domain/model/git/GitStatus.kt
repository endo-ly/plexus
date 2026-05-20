package dev.muxport.shared.core.domain.model.git

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gitファイル変更情報
 *
 * ステージング・アンステージング・未追跡の各ファイルの変更状態を表現します。
 *
 * @property path ファイルパス
 * @property status 変更状態 (例: "added", "modified", "deleted", "renamed")
 */
@Serializable
data class GitFileChange(
    @SerialName("path") val path: String,
    @SerialName("status") val status: String,
)

/**
 * Gitステータスレスポンス
 *
 * 現在のブランチと各ファイルの変更状態を表現します。
 *
 * @property branch 現在のブランチ名
 * @property staged ステージング済みの変更一覧
 * @property unstaged アンステージングの変更一覧
 * @property untracked 未追跡ファイル一覧
 */
@Serializable
data class GitStatusResponse(
    @SerialName("branch") val branch: String,
    @SerialName("staged") val staged: List<GitFileChange>,
    @SerialName("unstaged") val unstaged: List<GitFileChange>,
    @SerialName("untracked") val untracked: List<GitFileChange>,
)
