package dev.muxport.shared.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppThemeTest {
    @Test
    fun `toAppTheme should parse lowercase theme strings`() {
        // Arrange
        val inputDark = "dark"
        val inputLight = "light"

        // Act
        val resultDark = inputDark.toAppTheme()
        val resultLight = inputLight.toAppTheme()

        // Assert
        assertEquals(AppTheme.DARK, resultDark)
        assertEquals(AppTheme.LIGHT, resultLight)
    }

    @Test
    fun `toAppTheme should parse uppercase theme strings`() {
        // Arrange
        val inputDark = "DARK"
        val inputLight = "LIGHT"

        // Act
        val resultDark = inputDark.toAppTheme()
        val resultLight = inputLight.toAppTheme()

        // Assert
        assertEquals(AppTheme.DARK, resultDark)
        assertEquals(AppTheme.LIGHT, resultLight)
    }

    @Test
    fun `toAppTheme should parse mixed case theme strings`() {
        // Arrange
        val inputDark = "DaRk"
        val inputLight = "LiGhT"

        // Act
        val resultDark = inputDark.toAppTheme()
        val resultLight = inputLight.toAppTheme()

        // Assert
        assertEquals(AppTheme.DARK, resultDark)
        assertEquals(AppTheme.LIGHT, resultLight)
    }

    @Test
    fun `toAppTheme should return DARK as default for invalid inputs`() {
        // Arrange
        val invalidInputs = listOf("invalid_theme", "", "   ", "darkness")

        // Act
        val results = invalidInputs.map { it.toAppTheme() }

        // Assert
        results.forEach { assertEquals(AppTheme.DARK, it) }
    }

    @Test
    fun `toStorageString should return correct lowercase string`() {
        // Arrange
        val lightTheme = AppTheme.LIGHT
        val darkTheme = AppTheme.DARK

        // Act
        val lightString = lightTheme.toStorageString()
        val darkString = darkTheme.toStorageString()

        // Assert
        assertEquals("light", lightString)
        assertEquals("dark", darkString)
    }

    @Test
    fun `displayName should return correct display name`() {
        // Arrange
        val lightTheme = AppTheme.LIGHT
        val darkTheme = AppTheme.DARK

        // Act
        val lightDisplayName = lightTheme.displayName
        val darkDisplayName = darkTheme.displayName

        // Assert
        assertEquals("Light", lightDisplayName)
        assertEquals("Dark", darkDisplayName)
    }

    @Test
    fun `round-trip conversion should preserve theme for all enum values`() {
        // Arrange
        val allThemes = AppTheme.entries

        // Act & Assert
        allThemes.forEach { theme ->
            val storageString = theme.toStorageString()
            val restored = storageString.toAppTheme()
            assertEquals(theme, restored)
        }
    }

    @Test
    fun `round-trip with uppercase storage string normalizes to lowercase`() {
        // Arrange
        val uppercaseInput = "DARK"

        // Act
        val theme = uppercaseInput.toAppTheme()
        val restoredString = theme.toStorageString()

        // Assert
        assertEquals(AppTheme.DARK, theme)
        assertEquals("dark", restoredString)
    }
}
