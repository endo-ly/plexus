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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.muxport.shared.core.domain.model.file.FileBrowseEntry
import dev.muxport.shared.core.ui.theme.MuxportThemeTokens

/** ポップオーバーの最大高さ */
private val POPOVER_MAX_HEIGHT = 400.dp

/** ポップオーバーの幅 */
private val POPOVER_WIDTH = 280.dp

/** エントリ行の高さ */
private val ENTRY_ROW_HEIGHT = 48.dp

/**
 * ファイルブラウザポップオーバー
 *
 * ディレクトリ内のファイル一覧を表示するポップオーバー。
 * パンくずリストによるディレクトリナビゲーションと
 * 隠しファイルの表示切替をサポートする。
 *
 * @param entries ファイル・ディレクトリ一覧
 * @param currentPath 現在のパス
 * @param onDirectoryClick ディレクトリクリック時のコールバック
 * @param onFileClick ファイルクリック時のコールバック
 * @param onBreadcrumbClick パンくずリストのパスクリック時のコールバック
 * @param onDismiss ポップオーバー外タップで閉じるコールバック
 * @param showHidden 隠しファイルの表示状態
 * @param onShowHiddenChange 隠しファイル表示切替コールバック
 * @param isLoading ローディング中かどうか
 * @param modifier Modifier
 */
@Composable
fun FileBrowserPopover(
    entries: List<FileBrowseEntry>,
    currentPath: String,
    onDirectoryClick: (path: String) -> Unit,
    onFileClick: (path: String) -> Unit,
    onBreadcrumbClick: (path: String) -> Unit,
    onDismiss: () -> Unit,
    showHidden: Boolean = false,
    onShowHiddenChange: (Boolean) -> Unit,
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
                // ヘッダー: パンくず + 表示切替
                FileBrowserHeader(
                    currentPath = currentPath,
                    onBreadcrumbClick = onBreadcrumbClick,
                    showHidden = showHidden,
                    onShowHiddenChange = onShowHiddenChange,
                )

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
                } else {
                    val filteredEntries =
                        entries.filter { entry ->
                            (showHidden || !entry.name.startsWith(".")) && entry.name != ".git"
                        }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = dimens.space4),
                    ) {
                        items(
                            items = filteredEntries,
                            key = { it.name },
                        ) { entry ->
                            FileBrowserEntryRow(
                                entry = entry,
                                currentPath = currentPath,
                                onDirectoryClick = onDirectoryClick,
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
 * ファイルブラウザのヘッダー
 *
 * パンくずリストと隠しファイル表示切替ボタンを表示する。
 *
 * @param currentPath 現在のパス
 * @param onBreadcrumbClick パンくずクリック時のコールバック
 * @param showHidden 隠しファイル表示状態
 * @param onShowHiddenChange 隠しファイル表示切替コールバック
 */
@Composable
private fun FileBrowserHeader(
    currentPath: String,
    onBreadcrumbClick: (path: String) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
) {
    val dimens = MuxportThemeTokens.dimens
    val segments = buildBreadcrumbSegments(currentPath)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space8, vertical = dimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // パンくずリスト
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable { onBreadcrumbClick(segment.path) },
                )
            }
        }

        // 隠しファイル表示切替
        IconButton(
            onClick = { onShowHiddenChange(!showHidden) },
            modifier = Modifier.size(dimens.space32),
        ) {
            Icon(
                imageVector = if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (showHidden) "Hide hidden files" else "Show hidden files",
                modifier = Modifier.size(dimens.iconSizeMedium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * ファイルブラウザのエントリ行
 *
 * ファイルまたはディレクトリのアイコン、名前、サイズを表示する。
 *
 * @param entry ファイルエントリ
 * @param currentPath 現在のパス
 * @param onDirectoryClick ディレクトリクリック時のコールバック
 * @param onFileClick ファイルクリック時のコールバック
 */
@Composable
private fun FileBrowserEntryRow(
    entry: FileBrowseEntry,
    currentPath: String,
    onDirectoryClick: (path: String) -> Unit,
    onFileClick: (path: String) -> Unit,
) {
    val dimens = MuxportThemeTokens.dimens
    val isDirectory = entry.type == "directory"
    val fullPath = buildPath(currentPath, entry.name)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ENTRY_ROW_HEIGHT)
                .clickable {
                    if (isDirectory) {
                        onDirectoryClick(fullPath)
                    } else {
                        onFileClick(fullPath)
                    }
                }.padding(horizontal = dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = if (isDirectory) "Directory" else "File",
            tint =
                if (isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(dimens.iconSizeMedium),
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = dimens.space12),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isDirectory) {
                Text(
                    text = formatFileSize(entry.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** パンくずリスト用のセグメントデータ */
private data class BreadcrumbSegment(
    val label: String,
    val path: String,
)

/**
 * パスをパンくずセグメントに分割する
 *
 * @param path 対象パス
 * @return セグメントのリスト
 */
private fun buildBreadcrumbSegments(path: String): List<BreadcrumbSegment> {
    val normalized = path.trimEnd('/')
    if (normalized.isEmpty() || normalized == ".") {
        return listOf(BreadcrumbSegment(".", "."))
    }
    val parts = normalized.split("/").filter { it.isNotEmpty() }
    return parts.foldIndexed(mutableListOf<BreadcrumbSegment>()) { index, acc, part ->
        val segmentPath = parts.subList(0, index + 1).joinToString("/")
        acc.add(BreadcrumbSegment(part, segmentPath))
        acc
    }
}

/**
 * パスを結合する
 *
 * @param basePath ベースパス
 * @param name エントリ名
 * @return 結合後のパス
 */
private fun buildPath(
    basePath: String,
    name: String,
): String {
    if (basePath == ".") return name
    return "${basePath.trimEnd('/')}/$name"
}

/**
 * ファイルサイズを読みやすい形式にフォーマットする
 *
 * @param bytes バイト数
 * @return フォーマット済み文字列
 */
private fun formatFileSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}
