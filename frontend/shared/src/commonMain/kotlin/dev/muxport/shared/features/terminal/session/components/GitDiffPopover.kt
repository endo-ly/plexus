package dev.muxport.shared.features.terminal.session.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.muxport.shared.core.domain.model.git.GitDiffFile
import dev.muxport.shared.core.domain.model.git.GitStatusResponse
import dev.muxport.shared.core.ui.theme.MuxportThemeTokens

/** ポップオーバーの最大高さ */
private val POPOVER_MAX_HEIGHT = 400.dp

/** エントリ行の高さ */
private val ENTRY_ROW_HEIGHT = 48.dp

/**
 * Git差分ポップオーバー
 *
 * 現在のブランチと変更ファイル一覧を表示するポップオーバー。
 * 各ファイルの追加・削除行数を色分けして表示する。
 *
 * @param status Gitステータス（ブランチ名取得用）
 * @param diffFiles 差分ファイル一覧
 * @param onFileClick ファイルクリック時のコールバック
 * @param onDismiss ポップオーバー外タップで閉じるコールバック
 * @param isLoading ローディング中かどうか
 * @param modifier Modifier
 */
@Composable
fun GitDiffPopover(
    status: GitStatusResponse?,
    diffFiles: List<GitDiffFile>,
    onFileClick: (path: String) -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dimens = MuxportThemeTokens.dimens

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 0),
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = dimens.space8,
            tonalElevation = dimens.space8,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(POPOVER_MAX_HEIGHT),
            ) {
                // ヘッダー: ブランチ名
                GitDiffHeader(branch = status?.branch)

                // ファイル一覧
                if (isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimens.space32),
                        )
                    }
                } else if (diffFiles.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No changes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = dimens.space4),
                    ) {
                        items(
                            items = diffFiles,
                            key = { it.path },
                        ) { diffFile ->
                            GitDiffFileRow(
                                diffFile = diffFile,
                                onFileClick = onFileClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Git差分ポップオーバーのヘッダー
 *
 * 現在のブランチ名を表示する。
 *
 * @param branch ブランチ名
 */
@Composable
private fun GitDiffHeader(branch: String?) {
    val dimens = MuxportThemeTokens.dimens

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space12, vertical = dimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = branch ?: "unknown",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Git差分ファイル行
 *
 * 変更種別アイコン、ファイルパス、追加・削除行数を表示する。
 *
 * @param diffFile 差分ファイル情報
 * @param onFileClick ファイルクリック時のコールバック
 */
@Composable
private fun GitDiffFileRow(
    diffFile: GitDiffFile,
    onFileClick: (path: String) -> Unit,
) {
    val dimens = MuxportThemeTokens.dimens
    val changeType = resolveChangeType(diffFile)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ENTRY_ROW_HEIGHT)
                .clickable { onFileClick(diffFile.path) }
                .padding(horizontal = dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = changeType.icon,
            contentDescription = changeType.label,
            tint = changeType.color,
            modifier = Modifier.size(dimens.iconSizeMedium),
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = dimens.space12),
        ) {
            Text(
                text = diffFile.path,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
                if (diffFile.additions > 0) {
                    Text(
                        text = "+${diffFile.additions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (diffFile.deletions > 0) {
                    Text(
                        text = "-${diffFile.deletions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 変更種別 */
private enum class ChangeType(
    val icon: ImageVector,
    val label: String,
    val color: Color,
) {
    MODIFIED(Icons.Filled.Edit, "Modified", Color(0xFFE8A317)),
    ADDED(Icons.Filled.Add, "Added", Color(0xFF4CAF50)),
    DELETED(Icons.Filled.Delete, "Deleted", Color(0xFFF44336)),
    UNTRACKED(Icons.Filled.Help, "Untracked", Color.Gray),
}

/**
 * 差分ファイルから変更種別を推定する
 *
 * 追加行数のみの場合は追加、削除行数のみの場合は削除、
 * 両方ある場合は変更、どちらもない場合は未追跡と判定する。
 *
 * @param diffFile 差分ファイル
 * @return 変更種別
 */
private fun resolveChangeType(diffFile: GitDiffFile): ChangeType {
    val hasAdditions = diffFile.additions > 0
    val hasDeletions = diffFile.deletions > 0
    return when {
        hasAdditions && hasDeletions -> ChangeType.MODIFIED
        hasAdditions -> ChangeType.ADDED
        hasDeletions -> ChangeType.DELETED
        else -> ChangeType.UNTRACKED
    }
}
