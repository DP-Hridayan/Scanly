package com.skeler.scanely.core.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.compositionLocalOf
// OcrHelper import removed
import com.skeler.scanely.settings.domain.model.SettingsState

val LocalSettings = compositionLocalOf {
    SettingsState(
        themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        isOledModeEnabled = false,
        ocrLanguages = emptySet(),
        useDynamicColors = true,
        seedColorIndex = 5 // Default to Color06
    )
}