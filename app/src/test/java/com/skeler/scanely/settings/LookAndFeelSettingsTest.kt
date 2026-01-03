package com.skeler.scanely.settings

import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.ui.theme.SeedPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Look & Feel settings functionality.
 */
class LookAndFeelSettingsTest {

    // =========================================================================
    // DataStore Key Tests
    // =========================================================================

    @Test
    fun `SEED_COLOR_INDEX default value is 5 (Color06)`() {
        val defaultIndex = SettingsKeys.SEED_COLOR_INDEX.default as Int
        assertEquals(5, defaultIndex)
    }

    @Test
    fun `USE_DYNAMIC_COLORS default value is true`() {
        val defaultValue = SettingsKeys.USE_DYNAMIC_COLORS.default as Boolean
        assertTrue(defaultValue)
    }

    @Test
    fun `HIGH_CONTRAST_DARK_MODE default is false`() {
        val defaultValue = SettingsKeys.HIGH_CONTRAST_DARK_MODE.default as Boolean
        assertEquals(false, defaultValue)
    }

    // =========================================================================
    // SeedPalettes Tests
    // =========================================================================

    @Test
    fun `SeedPalettes ALL contains 6 color palettes`() {
        assertEquals(6, SeedPalettes.ALL.size)
    }

    @Test
    fun `SeedPalettes DEFAULT is Color06`() {
        assertEquals(SeedPalettes.Color06, SeedPalettes.DEFAULT)
    }

    @Test
    fun `SeedPalettes getOrElse returns default for invalid index`() {
        val result = SeedPalettes.ALL.getOrElse(100) { SeedPalettes.DEFAULT }
        assertEquals(SeedPalettes.DEFAULT, result)
    }

    @Test
    fun `SeedPalettes getOrElse returns correct palette for valid index`() {
        val result = SeedPalettes.ALL.getOrElse(0) { SeedPalettes.DEFAULT }
        assertEquals(SeedPalettes.Color01, result)
    }

    @Test
    fun `SeedColor triplet contains distinct primary, secondary, tertiary`() {
        val seedColor = SeedPalettes.Color01
        // Ensure all three are different
        assertTrue(seedColor.primary != seedColor.secondary)
        assertTrue(seedColor.secondary != seedColor.tertiary)
        assertTrue(seedColor.primary != seedColor.tertiary)
    }

    // =========================================================================
    // Theme Mode Tests  
    // =========================================================================

    @Test
    fun `THEME_MODE default follows system`() {
        val defaultMode = SettingsKeys.THEME_MODE.default as Int
        // AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM = -1
        assertEquals(-1, defaultMode)
    }
}
