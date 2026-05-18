package dev.plexus.shared.features.terminal.agentlist.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.plexus.shared.core.domain.model.terminal.Session
import dev.plexus.shared.core.ui.common.ListStateContent
import dev.plexus.shared.core.ui.theme.MuxportThemeTokens
import dev.plexus.shared.core.ui.theme.monospaceLabelSmall

/**
 * ターミナルセッション一覧コンポーネント
 *
 * @param sessions セッション一覧
 * @param isLoading 読み込み中フラグ
 * @param error エラーメッセージ
 * @param onSessionClick セッション選択コールバック
 * @param onRefresh 更新コールバック
 * @param onOpenGatewaySettings Gateway設定を開くコールバック
 * @param isCreatingSession セッション作成中フラグ
 * @param suggestedSessionName 提案セッション名
 * @param onCreateSession セッション作成コールバック（null時は作成ボタン非表示）
 * @param deletingSessionIds 削除処理中のセッションID一覧
 * @param onDeleteSession セッション削除コールバック（null時は削除ボタン非表示）
 * @param modifier Modifier
 */
@Composable
fun SessionList(
    sessions: List<Session>,
    isLoading: Boolean,
    error: String?,
    onSessionClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenGatewaySettings: () -> Unit,
    isCreatingSession: Boolean = false,
    suggestedSessionName: String = "session-01",
    onCreateSession: ((String) -> Unit)? = null,
    deletingSessionIds: Set<String> = emptySet(),
    onDeleteSession: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val extendedColors = MuxportThemeTokens.extendedColors
    val sessionCount = sessions.size

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var sessionNameInput by rememberSaveable { mutableStateOf("") }

    if (showCreateDialog && onCreateSession != null) {
        CreateSessionDialog(
            sessionNameInput = sessionNameInput,
            onSessionNameChange = { sessionNameInput = it },
            suggestedName = suggestedSessionName,
            isCreating = isCreatingSession,
            onCreate = {
                val name = sessionNameInput.ifBlank { suggestedSessionName }
                onCreateSession(name)
                showCreateDialog = false
                sessionNameInput = ""
            },
            onDismiss = {
                showCreateDialog = false
                sessionNameInput = ""
            },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.space16, vertical = dimens.space12),
        ) {
            // ヘッダー行: タイトル + Active バッジ | アイコンボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TerminalIndicatorIcon(
                    tint = if (sessionCount > 0) extendedColors.success else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp),
                )

                Spacer(modifier = Modifier.width(dimens.space10))

                Text(
                    text = "SESSIONS",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(dimens.space12))

                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onCreateSession != null) {
                            IconButton(
                                onClick = { showCreateDialog = true },
                                enabled = !isLoading && !isCreatingSession,
                                modifier = Modifier.size(dimens.space48),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Session",
                                    modifier = Modifier.size(dimens.iconSizeMedium),
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading,
                            modifier = Modifier.size(dimens.space48),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                modifier = Modifier.size(dimens.iconSizeMedium),
                            )
                        }

                        IconButton(
                            onClick = onOpenGatewaySettings,
                            modifier = Modifier.size(dimens.space48),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(dimens.iconSizeMedium),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.space4))

            Text(
                text = "$sessionCount Active",
                style =
                    MaterialTheme.typography.monospaceLabelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (sessionCount > 0) extendedColors.success else MaterialTheme.colorScheme.outline,
            )
        }

        ListStateContent(
            items = sessions,
            isLoading = isLoading,
            errorMessage = error,
            modifier = Modifier.fillMaxSize(),
            loading = { containerModifier ->
                SessionListLoading(modifier = containerModifier)
            },
            empty = { containerModifier ->
                SessionListEmpty(modifier = containerModifier)
            },
            error = { message, containerModifier ->
                SessionListError(
                    error = message,
                    onRefresh = onRefresh,
                    modifier = containerModifier,
                )
            },
            content = { items, containerModifier ->
                SessionListContent(
                    sessions = items,
                    onSessionClick = onSessionClick,
                    deletingSessionIds = deletingSessionIds,
                    onDeleteSession = onDeleteSession,
                    modifier = containerModifier,
                )
            },
        )
    }
}

@Composable
private fun CreateSessionDialog(
    sessionNameInput: String,
    onSessionNameChange: (String) -> Unit,
    suggestedName: String,
    isCreating: Boolean,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(text = "New Session") },
        text = {
            OutlinedTextField(
                value = sessionNameInput,
                onValueChange = onSessionNameChange,
                placeholder = { Text(text = suggestedName) },
                singleLine = true,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onCreate,
                enabled = !isCreating,
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconSize18),
                        strokeWidth = dimens.borderWidthThin,
                    )
                } else {
                    Text(text = "Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating,
            ) {
                Text(text = "Cancel")
            }
        },
    )
}

private val dimens
    @Composable
    get() = MuxportThemeTokens.dimens

@Composable
private fun SessionListContent(
    sessions: List<Session>,
    onSessionClick: (String) -> Unit,
    deletingSessionIds: Set<String> = emptySet(),
    onDeleteSession: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dimens = MuxportThemeTokens.dimens
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.padding(vertical = dimens.space8),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(dimens.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(
            items = sessions,
            key = { it.sessionId },
        ) { session ->
            SessionListItem(
                session = session,
                onClick = { onSessionClick(session.sessionId) },
                isDeleting = deletingSessionIds.contains(session.sessionId),
                onDeleteSession = onDeleteSession?.let { { it(session.sessionId) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.space16),
            )
        }
        item {
            Spacer(modifier = Modifier.height(dimens.space16))
        }
    }
}

/**
 * Lucide Terminal 風のアイコン。
 * 角丸矩形の中に `>_` プロンプトを描画する。
 */
@Composable
private fun TerminalIndicatorIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.4.dp.toPx()
        val pad = size.width * 0.06f
        val w = size.width - pad * 2
        val h = size.height * 0.72f
        val tl = Offset(pad, (size.height - h) / 2)

        drawRoundRect(
            color = tint,
            topLeft = tl,
            size = Size(w, h),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )

        // > chevron
        val cx = tl.x + w * 0.2f
        val mx = tl.x + w * 0.4f
        val ty = tl.y + h * 0.28f
        val my = tl.y + h * 0.50f
        val by = tl.y + h * 0.72f
        drawLine(tint, Offset(cx, ty), Offset(mx, my), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(tint, Offset(mx, my), Offset(cx, by), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        // _ cursor
        drawLine(
            tint,
            Offset(tl.x + w * 0.52f, tl.y + h * 0.72f),
            Offset(tl.x + w * 0.78f, tl.y + h * 0.72f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
