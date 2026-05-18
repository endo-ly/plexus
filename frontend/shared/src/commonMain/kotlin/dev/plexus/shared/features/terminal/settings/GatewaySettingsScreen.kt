package dev.plexus.shared.features.terminal.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import dev.plexus.shared.core.platform.isValidUrl
import dev.plexus.shared.core.settings.AppTheme
import dev.plexus.shared.core.ui.components.SecretTextField
import dev.plexus.shared.core.ui.theme.MuxportThemeTokens
import dev.plexus.shared.core.ui.theme.monospaceBody
import dev.plexus.shared.core.ui.theme.monospaceBodyMedium
import dev.plexus.shared.core.ui.theme.monospaceLabelSmall
import kotlinx.coroutines.launch

/**
 * Gateway設定画面
 *
 * Gateway APIのURLとAPIキー、アプリテーマを設定する画面。
 * セッション一覧画面と統一したターミナル風のカードベースUI。
 *
 * @param onBack 戻るボタンコールバック
 */
class GatewaySettingsScreen(
    private val onBack: () -> Unit = {},
) : Screen {
    @Composable
    override fun Content() {
        val screenModel = koinScreenModel<GatewaySettingsScreenModel>()
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            screenModel.effect.collect { effect ->
                when (effect) {
                    is GatewaySettingsEffect.ShowMessage -> launch { snackbarHostState.showSnackbar(effect.message) }
                    GatewaySettingsEffect.NavigateBack -> onBack()
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(paddingValues),
            ) {
                SettingsHeader(onBack = onBack)

                GatewaySettingsContent(
                    selectedTheme = state.selectedTheme,
                    onThemeSelected = screenModel::onThemeSelected,
                    gatewayUrl = state.inputGatewayUrl,
                    onGatewayUrlChange = screenModel::onGatewayUrlChange,
                    apiKey = state.inputApiKey,
                    onApiKeyChange = screenModel::onApiKeyChange,
                    defaultWorkingDir = state.inputDefaultWorkingDir,
                    onDefaultWorkingDirChange = screenModel::onDefaultWorkingDirChange,
                    onSave = screenModel::saveSettings,
                    isSaving = state.isSaving,
                    isSaveSuccess = state.isSaveSuccess,
                )
            }
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val dimens = MuxportThemeTokens.dimens

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space4),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(dimens.space48).align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(dimens.iconSizeMedium),
                )
            }
        }

        Text(
            text = "SETTINGS",
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ─── Content ─────────────────────────────────────────────────────────────

@Composable
private fun GatewaySettingsContent(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    gatewayUrl: String,
    onGatewayUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    defaultWorkingDir: String,
    onDefaultWorkingDirChange: (String) -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    isSaveSuccess: Boolean = false,
) {
    val dimens = MuxportThemeTokens.dimens
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = dimens.space16, vertical = dimens.space8),
    ) {
        // テーマ選択カード
        SettingsCard(title = "APPEARANCE") {
            ThemePillSelector(
                selectedTheme = selectedTheme,
                onThemeSelected = onThemeSelected,
            )
        }

        Spacer(modifier = Modifier.height(dimens.space8))

        // Gateway API 設定カード
        SettingsCard(title = "GATEWAY API") {
            OutlinedTextField(
                value = gatewayUrl,
                onValueChange = onGatewayUrlChange,
                label = {
                    Text(
                        "URL",
                        style = MaterialTheme.typography.monospaceLabelSmall,
                    )
                },
                placeholder = {
                    Text(
                        "http://100.x.x.x:8001",
                        style = MaterialTheme.typography.monospaceLabelSmall,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.monospaceBody,
                shape = MuxportThemeTokens.shapes.radiusMd,
                isError = gatewayUrl.isNotBlank() && !isValidUrl(gatewayUrl),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedBorderColor = MuxportThemeTokens.extendedColors.success.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        errorBorderColor = MaterialTheme.colorScheme.error,
                    ),
            )

            Spacer(modifier = Modifier.height(dimens.space12))

            SecretTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = "Token",
                placeholder = "Required",
                modifier = Modifier.fillMaxWidth(),
                showContentDescription = "Show token",
                hideContentDescription = "Hide token",
            )

            Spacer(modifier = Modifier.height(dimens.space12))

            OutlinedTextField(
                value = defaultWorkingDir,
                onValueChange = onDefaultWorkingDirChange,
                label = {
                    Text(
                        "Default Working Directory",
                        style = MaterialTheme.typography.monospaceLabelSmall,
                    )
                },
                placeholder = {
                    Text(
                        "~/",
                        style = MaterialTheme.typography.monospaceLabelSmall,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.monospaceBody,
                shape = MuxportThemeTokens.shapes.radiusMd,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedBorderColor = MuxportThemeTokens.extendedColors.success.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
            )

            Spacer(modifier = Modifier.height(dimens.space16))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TerminalSaveButton(
                    onClick = onSave,
                    enabled = !isSaving && isValidUrl(gatewayUrl) && apiKey.isNotBlank(),
                    isSaving = isSaving,
                    isSaveSuccess = isSaveSuccess,
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.space16))
    }
}

// ─── Settings Card ───────────────────────────────────────────────────────

/**
 * セッションカードと同一スタイルの設定カードコンテナ。
 * surfaceContainer 背景 + outlineVariant 境界線 + radiusLg 角丸。
 */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val extendedColors = MuxportThemeTokens.extendedColors

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shapes.radiusLg)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = dimens.borderWidthThin,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = shapes.radiusLg,
                ).padding(horizontal = dimens.space16, vertical = dimens.space16),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.monospaceLabelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
            color = extendedColors.success,
        )
        Spacer(modifier = Modifier.height(dimens.space12))
        content()
    }
}

// ─── Theme Pill Selector ─────────────────────────────────────────────────

/**
 * Pill badge 形式のテーマセレクタ。
 * RadioButton を置き換えるターミナル風の選択UI。
 */
@Composable
private fun ThemePillSelector(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val dimens = MuxportThemeTokens.dimens

    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space8)) {
        AppTheme.entries.forEach { theme ->
            ThemePill(
                label = theme.displayName,
                selected = selectedTheme == theme,
                onClick = { onThemeSelected(theme) },
            )
        }
    }
}

@Composable
private fun ThemePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val extendedColors = MuxportThemeTokens.extendedColors

    val backgroundColor =
        if (selected) {
            extendedColors.success.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    val borderColor =
        if (selected) {
            extendedColors.success
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        }
    val textColor =
        if (selected) {
            extendedColors.success
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            Modifier
                .clip(shapes.radiusMd)
                .background(backgroundColor)
                .border(
                    width = dimens.borderWidthThin,
                    color = borderColor,
                    shape = shapes.radiusMd,
                ).clickable(onClick = onClick)
                .padding(horizontal = dimens.space20, vertical = dimens.space10),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.monospaceBody.copy(
                    fontWeight = FontWeight.Medium,
                ),
            color = textColor,
        )
    }
}

// ─── Save Button ─────────────────────────────────────────────────────────

/**
 * ターミナル風のghost style保存ボタン。
 * OutlinedButton + Tealアクセント + モノスペース。
 */
@Composable
private fun TerminalSaveButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isSaving: Boolean,
    isSaveSuccess: Boolean = false,
) {
    val dimens = MuxportThemeTokens.dimens
    val shapes = MuxportThemeTokens.shapes
    val extendedColors = MuxportThemeTokens.extendedColors

    val isButtonEnabled = enabled && !isSaving && !isSaveSuccess

    OutlinedButton(
        onClick = onClick,
        enabled = isButtonEnabled,
        shape = shapes.radiusMd,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = if (isButtonEnabled) extendedColors.success.copy(alpha = 0.1f) else Color.Transparent,
                contentColor = if (isButtonEnabled || isSaveSuccess) extendedColors.success else MaterialTheme.colorScheme.outline,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.outline,
            ),
        border =
            androidx.compose.foundation.BorderStroke(
                width = dimens.borderWidthThin,
                color =
                    if (isButtonEnabled ||
                        isSaveSuccess
                    ) {
                        extendedColors.success
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    },
            ),
    ) {
        Text(
            text =
                when {
                    isSaveSuccess -> "SAVED"
                    isSaving -> "SAVING..."
                    else -> "SAVE"
                },
            style = MaterialTheme.typography.monospaceBodyMedium,
        )
    }
}
