package dev.plexus.shared.features.terminal.agentlist.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.plexus.shared.core.domain.model.terminal.Session
import dev.plexus.shared.core.ui.common.testTagResourceId
import dev.plexus.shared.core.ui.common.toRelativeTimeString
import dev.plexus.shared.core.ui.theme.MuxportThemeTokens
import dev.plexus.shared.core.ui.theme.monospaceBody
import dev.plexus.shared.core.ui.theme.monospaceLabelSmall
import dev.plexus.shared.features.terminal.TerminalTestTags

internal fun previewDisplayLines(session: Session): List<String> =
    if (session.previewLines.isNotEmpty()) {
        session.previewLines
    } else {
        listOf("Preview unavailable")
    }

internal fun sessionSubtitle(session: Session): String? = session.name.takeUnless { it.isBlank() || it == session.sessionId }

internal fun sessionHeaderTitle(session: Session): String? = session.title?.takeUnless { it.isBlank() }

internal fun sessionHeaderPath(session: Session): String? = session.currentPath?.takeUnless { it.isBlank() }

/**
 * セッションリストアイテムコンポーネント
 *
 * @param session セッション情報
 * @param onClick クリックコールバック
 * @param isDeleting 削除処理中かどうか
 * @param onDeleteSession 削除コールバック（null時はコンテキストメニュー非表示）
 * @param modifier Modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionListItem(
    session: Session,
    onClick: () -> Unit,
    isDeleting: Boolean = false,
    onDeleteSession: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val extendedColors = MuxportThemeTokens.extendedColors
    val previewLines = previewDisplayLines(session)
    val subtitle = sessionSubtitle(session)
    val previewScrollState = rememberScrollState()
    val cardBackgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val sessionIdColor = extendedColors.success
    val previewAccentColor = extendedColors.success.copy(alpha = 0.55f)
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val cardModifier =
        modifier
            .testTagResourceId(TerminalTestTags.SESSION_ITEM)
            .fillMaxWidth()
            .clip(shapes.radiusLg)
            .background(cardBackgroundColor)
            .border(
                width = dimens.borderWidthThin,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = shapes.radiusLg,
            ).then(
                if (isDeleting) {
                    Modifier.alpha(0.5f)
                } else {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = { if (onDeleteSession != null) showMenu = true },
                    )
                },
            ).padding(horizontal = dimens.space16, vertical = dimens.space16)
    val previewBoxModifier =
        Modifier
            .testTagResourceId(TerminalTestTags.SESSION_PREVIEW)
            .fillMaxWidth()
            .heightIn(max = dimens.space64 + dimens.space12)
            .clip(shapes.radiusMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = dimens.borderWidthThin,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = shapes.radiusMd,
            ).padding(start = dimens.space12, end = dimens.space12, top = dimens.space12, bottom = dimens.space10)
    val pulse = rememberInfiniteTransition(label = "sessionIndicatorPulse")
    val pulseAlpha =
        pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1300), repeatMode = RepeatMode.Reverse),
            label = "sessionIndicatorAlpha",
        )

    LaunchedEffect(previewLines) {
        // 末尾3行目付近を表示するようスクロール位置を調整
        // 行数-3の位置をビューの下端あたりに表示
        val totalLines = previewLines.size
        if (totalLines > 3) {
            val targetRatio = (totalLines - 3).toFloat() / totalLines.toFloat()
            previewScrollState.scrollTo((previewScrollState.maxValue * targetRatio).toInt())
        }
    }

    Column(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = dimens.space6)
                        .size(dimens.indicatorSizeMedium)
                        .alpha(pulseAlpha.value)
                        .clip(CircleShape)
                        .background(extendedColors.success),
            )
            Spacer(modifier = Modifier.width(dimens.space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionId,
                    style = MaterialTheme.typography.monospaceBody,
                    color = sessionIdColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Spacer(modifier = Modifier.height(dimens.space4))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.monospaceLabelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(dimens.space12))
            Text(
                text = session.lastActivity.toRelativeTimeString(),
                style = MaterialTheme.typography.monospaceLabelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }

        Spacer(modifier = Modifier.height(dimens.space12))

        // タイトルとパスを表示（プレビューの手前）
        val headerTitle = sessionHeaderTitle(session)
        val headerPath = sessionHeaderPath(session)

        Row(modifier = Modifier.fillMaxWidth()) {
            if (headerTitle != null && headerPath != null) {
                FolderBadge(path = headerPath)
                Spacer(modifier = Modifier.width(dimens.space8))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.monospaceLabelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            } else if (headerPath != null) {
                FolderBadge(path = headerPath)
            } else if (headerTitle != null) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.monospaceLabelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (headerTitle != null || headerPath != null) {
            Spacer(modifier = Modifier.height(dimens.space8))
        }

        Box(modifier = previewBoxModifier) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .clip(shapes.radiusSm)
                            .background(previewAccentColor)
                            .width(dimens.space4),
                )
                Column(
                    modifier =
                        Modifier
                            .padding(start = dimens.space8)
                            .fillMaxWidth()
                            .verticalScroll(previewScrollState),
                ) {
                    previewLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.monospaceLabelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (line != previewLines.last()) {
                            Spacer(modifier = Modifier.height(dimens.space4))
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("削除") },
                onClick = {
                    showMenu = false
                    showConfirmDialog = true
                },
            )
        }
    }

    if (showConfirmDialog && onDeleteSession != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("セッション削除") },
            text = { Text("「${session.sessionId}」を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onDeleteSession()
                    },
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

/** パス文字列から一番右側のディレクトリ名を抽出する。 */
private fun String.lastPathSegment(): String = trimEnd('/').substringAfterLast('/', this)

/**
 * フォルダ風のバッジコンポーネント。
 * パスの最後のセグメントだけをピル状のバッジで表示する。
 */
@Composable
private fun FolderBadge(
    path: String,
    modifier: Modifier = Modifier,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val dirName = path.lastPathSegment()

    Box(
        modifier =
            modifier
                .clip(shapes.radiusXs)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = dimens.space6, vertical = dimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dirName,
            style = MaterialTheme.typography.monospaceLabelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
