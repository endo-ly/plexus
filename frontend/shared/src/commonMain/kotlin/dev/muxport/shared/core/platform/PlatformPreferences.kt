package dev.muxport.shared.core.platform

/**
 * Platform preferences (expect class)
 */
expect class PlatformPreferences {
    fun getString(
        key: String,
        default: String,
    ): String

    fun putString(
        key: String,
        value: String,
    )

    fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean

    fun putBoolean(
        key: String,
        value: Boolean,
    )
}

object PlatformPrefsKeys {
    const val KEY_THEME = "theme"
    const val KEY_API_URL = "api_url"
    const val KEY_API_KEY = "api_key"
    const val KEY_GATEWAY_API_URL = "gateway_api_url"
    const val KEY_GATEWAY_API_KEY = "gateway_api_key"
    const val KEY_SELECTED_MODEL = "selected_model"
    const val KEY_LAST_TERMINAL_SESSION = "last_terminal_session"
    const val KEY_DEFAULT_WORKING_DIR = "default_working_dir"
}

object PlatformPrefsValues {
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
}

object PlatformPrefsDefaults {
    const val DEFAULT_THEME = PlatformPrefsValues.THEME_DARK
    const val DEFAULT_API_URL = ""
    const val DEFAULT_API_KEY = ""
    const val DEFAULT_GATEWAY_API_URL = ""
    const val DEFAULT_GATEWAY_API_KEY = ""
    const val DEFAULT_SELECTED_MODEL = ""
    const val DEFAULT_LAST_TERMINAL_SESSION = ""
    const val DEFAULT_DEFAULT_WORKING_DIR = "~/"
}
