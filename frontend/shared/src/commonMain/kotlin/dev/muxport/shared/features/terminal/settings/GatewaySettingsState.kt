package dev.muxport.shared.features.terminal.settings

import dev.muxport.shared.core.platform.PlatformPrefsDefaults
import dev.muxport.shared.core.settings.AppTheme

/**
 * Gateway設定画面のUI状態
 *
 * @property inputGatewayUrl 入力されたGateway URL
 * @property inputApiKey 入力されたAPI Key
 * @property inputDefaultWorkingDir 入力されたデフォルト作業ディレクトリ
 * @property selectedTheme 選択中のテーマ
 * @property isSaving 保存処理中かどうか
 */

data class GatewaySettingsState(
    val inputGatewayUrl: String = "",
    val inputApiKey: String = "",
    val inputDefaultWorkingDir: String = PlatformPrefsDefaults.DEFAULT_DEFAULT_WORKING_DIR,
    val selectedTheme: AppTheme = AppTheme.DARK,
    val isSaving: Boolean = false,
    val isSaveSuccess: Boolean = false,
) {
    val canSave: Boolean
        get() = inputGatewayUrl.isNotBlank() && inputApiKey.isNotBlank()
}
