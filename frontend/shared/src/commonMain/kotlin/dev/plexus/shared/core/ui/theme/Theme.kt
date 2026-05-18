package dev.plexus.shared.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Zinc Palette（モノクロ）
val Zinc50 = Color(0xFFFAFAFA)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc300 = Color(0xFFD4D4D8)
val Zinc400 = Color(0xFFA1A1AA)
val Zinc500 = Color(0xFF71717A)
val Zinc600 = Color(0xFF52525B)
val Zinc700 = Color(0xFF3F3F46)
val Zinc800 = Color(0xFF27272A)
val Zinc900 = Color(0xFF18181B)
val Zinc950 = Color(0xFF09090B)

// Teal Palette（ブランドカラー）
val Teal50 = Color(0xFFF0FDFA)
val Teal100 = Color(0xFFCCFBF1)
val Teal200 = Color(0xFF99F6E4)
val Teal300 = Color(0xFF5EEAD4)
val Teal400 = Color(0xFF2DD4BF)
val Teal500 = Color(0xFF14B8A6)
val Teal600 = Color(0xFF0D9488)
val Teal700 = Color(0xFF0F766E)
val Teal800 = Color(0xFF115E59)
val Teal900 = Color(0xFF134E4A)
val Teal950 = Color(0xFF042F2E)

// === Error Colors ===
// Light Theme
private val ErrorRed = Color(0xFFBA1A1A)
private val OnErrorRed = Color(0xFFFFFFFF)
private val ErrorContainerRed = Color(0xFFFFDAD6)
private val OnErrorContainerRed = Color(0xFF410002)

// Dark Theme
private val ErrorRedDark = Color(0xFFFFB4AB)
private val OnErrorRedDark = Color(0xFF690005)
private val ErrorContainerRedDark = Color(0xFF93000A)
private val OnErrorContainerRedDark = ErrorContainerRed

private val LightColorScheme =
    lightColorScheme(
        // === Primary: Teal ===
        primary = Teal600,
        onPrimary = Color.White,
        primaryContainer = Teal100,
        onPrimaryContainer = Teal950,
        // === Secondary: Teal ===
        secondary = Teal500,
        onSecondary = Color.White,
        secondaryContainer = Teal300,
        onSecondaryContainer = Teal950,
        // === Tertiary: Zinc ===
        tertiary = Zinc700,
        onTertiary = Zinc50,
        tertiaryContainer = Zinc200,
        onTertiaryContainer = Zinc900,
        // === Error ===
        error = ErrorRed,
        onError = OnErrorRed,
        errorContainer = ErrorContainerRed,
        onErrorContainer = OnErrorContainerRed,
        // === Background / Surface ===
        background = Color.White,
        onBackground = Zinc900,
        surface = Zinc50,
        onSurface = Zinc900,
        surfaceVariant = Zinc100,
        onSurfaceVariant = Zinc700,
        // === Surface Container ===
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Zinc50,
        surfaceContainer = Zinc100,
        surfaceContainerHigh = Zinc200,
        surfaceContainerHighest = Zinc300,
        // === Outline ===
        outline = Zinc300,
        outlineVariant = Zinc200,
    )

// Pattern A: primary = Teal300 (#5EEAD4)
private val DarkColorScheme =
    darkColorScheme(
        // === Primary: Teal ===
        primary = Teal300,
        onPrimary = Teal950,
        primaryContainer = Teal700,
        onPrimaryContainer = Teal50,
        // === Secondary: Teal ===
        secondary = Teal400,
        onSecondary = Teal950,
        secondaryContainer = Teal600,
        onSecondaryContainer = Teal100,
        // === Tertiary: Zinc ===
        tertiary = Zinc300,
        onTertiary = Zinc900,
        tertiaryContainer = Zinc700,
        onTertiaryContainer = Zinc50,
        // === Error ===
        error = ErrorRedDark,
        onError = OnErrorRedDark,
        errorContainer = ErrorContainerRedDark,
        onErrorContainer = OnErrorContainerRedDark,
        // === Background / Surface ===
        background = Zinc950,
        onBackground = Zinc50,
        surface = Zinc900,
        onSurface = Zinc50,
        surfaceVariant = Zinc800,
        onSurfaceVariant = Zinc300,
        // === Surface Container ===
        surfaceContainerLowest = Zinc800,
        surfaceContainerLow = Zinc900,
        surfaceContainer = Zinc900,
        surfaceContainerHigh = Zinc800,
        surfaceContainerHighest = Zinc700,
        // === Outline ===
        outline = Zinc700,
        outlineVariant = Zinc700,
    )

@Composable
fun MuxportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalPlexusDimens provides PlexusDimens(),
        LocalPlexusShapes provides PlexusShapes(),
        LocalMuxportExtendedColors provides MuxportExtendedColors(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
