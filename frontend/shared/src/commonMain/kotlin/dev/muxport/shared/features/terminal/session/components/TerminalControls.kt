package dev.muxport.shared.features.terminal.session.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.muxport.shared.core.ui.common.testTagResourceId
import dev.muxport.shared.core.ui.theme.MuxportThemeTokens
import dev.muxport.shared.features.terminal.TerminalTestTags

@Composable
fun TerminalBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTagResourceId(TerminalTestTags.TERMINAL_BACK_BUTTON),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
fun TerminalCopyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTagResourceId(TerminalTestTags.TERMINAL_COPY_BUTTON),
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Open copy mode")
    }
}

@Composable
fun TerminalPasteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTagResourceId(TerminalTestTags.TERMINAL_PASTE_BUTTON),
    ) {
        Icon(Icons.Default.ContentPaste, contentDescription = "Paste clipboard")
    }
}

@Composable
fun TerminalFloatingControlPill(
    sessionId: String,
    isConnected: Boolean,
    onBack: () -> Unit,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onFiles: () -> Unit = {},
    onDiff: () -> Unit = {},
    isGitRepo: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val indicatorColor = if (isConnected) MuxportThemeTokens.extendedColors.success else MaterialTheme.colorScheme.error

    Surface(
        modifier = modifier.testTagResourceId(TerminalTestTags.TERMINAL_STATUS_PILL),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shapes.radiusXl,
        tonalElevation = dimens.space8,
        shadowElevation = dimens.space8,
    ) {
        Row(
            modifier =
                Modifier.padding(horizontal = dimens.space8, vertical = dimens.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalBackButton(onClick = onBack)
            Spacer(modifier = Modifier.width(dimens.space8))
            Row(modifier = Modifier.widthIn(max = 180.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(dimens.indicatorSizeSmall)
                            .clip(CircleShape)
                            .background(indicatorColor),
                )
                Spacer(modifier = Modifier.width(dimens.space8))
                Text(
                    text = sessionId,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(dimens.space8))
            TerminalFilesButton(onClick = onFiles)
            Spacer(modifier = Modifier.width(dimens.space4))
            TerminalDiffButton(onClick = onDiff, enabled = isGitRepo)
            Spacer(modifier = Modifier.width(dimens.space4))
            TerminalPasteButton(onClick = onPaste)
            Spacer(modifier = Modifier.width(dimens.space4))
            TerminalCopyButton(onClick = onCopy)
        }
    }
}

@Composable
fun TerminalFilesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTagResourceId(TerminalTestTags.TERMINAL_FILES_BUTTON),
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = "Files")
    }
}

@Composable
fun TerminalDiffButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTagResourceId(TerminalTestTags.TERMINAL_DIFF_BUTTON),
    ) {
        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Diff")
    }
}
