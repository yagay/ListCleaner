package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

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
    val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val accent = UiPreferencesRuntime.current.accent
    val scheme = when (accent) {
        UiAccent.DYNAMIC -> base
        else -> {
            val primary = when (accent) {
                UiAccent.BLUE -> if (dark) Color(0xFFB0C6FF) else Color(0xFF2859AB)
                UiAccent.GREEN -> if (dark) Color(0xFFA3DAB8) else Color(0xFF256845)
                UiAccent.PURPLE -> if (dark) Color(0xFFD4BBFF) else Color(0xFF7144AA)
                UiAccent.DYNAMIC -> base.primary
            }
            base.copy(
                primary = primary,
                onPrimary = if (dark) Color(0xFF172331) else Color.White,
                primaryContainer = primary.copy(alpha = if (dark) 0.35f else 0.16f),
                onPrimaryContainer = primary
            )
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
