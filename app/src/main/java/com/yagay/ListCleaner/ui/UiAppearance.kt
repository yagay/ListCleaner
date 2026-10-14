package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Appearance only: never changes rules, Root component state or LSPosed revisions. */
internal enum class UiColorMode {
    SYSTEM, LIGHT, DARK;

    fun usesDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): UiColorMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

internal object UiAppearanceStore {
    private const val FILE = "ui_appearance"
    private const val COLOR_MODE_KEY = "color_mode"

    fun readColorMode(context: Context): UiColorMode =
        UiColorMode.fromStored(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(COLOR_MODE_KEY, null)
        )

    fun writeColorMode(context: Context, mode: UiColorMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(COLOR_MODE_KEY, mode.name).apply()
    }
}

@Composable
internal fun ListCleanerUiTheme(
    colorMode: UiColorMode,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = colorMode.usesDarkTheme(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content
    )
}
