package dev.muxport.shared.core.settings

import dev.muxport.shared.core.platform.PlatformPrefsDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeRepositoryTest {
    @Test
    fun `default theme value should be DARK`() {
        // Assert
        assertEquals(PlatformPrefsDefaults.DEFAULT_THEME, "dark")
    }

    @Test
    fun `theme repository should have two variants`() {
        // Assert
        assertEquals(2, AppTheme.entries.size)
        assertEquals(setOf(AppTheme.LIGHT, AppTheme.DARK), AppTheme.entries.toSet())
    }

    @Test
    fun `theme values should be ordered consistently`() {
        // Act
        val themes = AppTheme.entries

        // Assert
        assertEquals(2, themes.size)
        assertTrue(themes.contains(AppTheme.LIGHT))
        assertTrue(themes.contains(AppTheme.DARK))
    }
}
