package dev.muxport.shared.core.settings

/**
 * アプリテーマ
 */
enum class AppTheme(
    val displayName: String,
) {
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * StringをAppThemeに変換する
 */
fun String.toAppTheme(): AppTheme =
    when (this.lowercase()) {
        "dark" -> AppTheme.DARK
        "light" -> AppTheme.LIGHT
        else -> AppTheme.DARK
    }

/**
 * AppThemeをStringに変換する
 */
fun AppTheme.toStorageString(): String = this.name.lowercase()
