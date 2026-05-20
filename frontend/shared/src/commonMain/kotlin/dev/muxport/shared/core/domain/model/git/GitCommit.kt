package dev.muxport.shared.core.domain.model.git

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gitログエントリ
 *
 * コミットログの1件分の情報を表現します。
 *
 * @property sha コミットSHA（フルハッシュ）
 * @property shortSha コミットSHA（短縮形式）
 * @property message コミットメッセージ
 * @property author 作成者
 * @property date コミット日時 (ISO 8601形式)
 */
@Serializable
data class GitLogEntry(
    @SerialName("sha") val sha: String,
    @SerialName("short_sha") val shortSha: String,
    @SerialName("message") val message: String,
    @SerialName("author") val author: String,
    @SerialName("date") val date: String,
)

/**
 * Gitログレスポンス
 *
 * コミットログ一覧を表現します。
 *
 * @property commits コミット一覧
 */
@Serializable
data class GitLogResponse(
    @SerialName("commits") val commits: List<GitLogEntry>,
)

/**
 * Gitコミット詳細レスポンス
 *
 * コミット情報と差分を表現します。
 *
 * @property commit コミット情報
 * @property diff 差分
 */
@Serializable
data class GitCommitDetailResponse(
    @SerialName("commit") val commit: GitCommitInfo,
    @SerialName("diff") val diff: GitDiffResponse,
)

/**
 * Gitコミット情報
 *
 * 個別コミットのメタ情報を表現します。
 *
 * @property sha コミットSHA
 * @property message コミットメッセージ
 * @property author 作成者
 * @property date コミット日時 (ISO 8601形式)
 */
@Serializable
data class GitCommitInfo(
    @SerialName("sha") val sha: String,
    @SerialName("message") val message: String,
    @SerialName("author") val author: String,
    @SerialName("date") val date: String,
)
