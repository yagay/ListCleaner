package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI-only presentation preference. Kept separate from LSPosed/Root configuration:
 * switching a style must not cause new rules, Hook revisions or a system restart.
 *
 * MIUIX_STYLE uses the genuine upstream Miuix Compose library for the native
 * application chrome and adaptive controls. Complex menus can be migrated
 * independently without creating a second copy of application business logic.
 */
internal enum class UiThemeStyle {
    MATERIAL, MIUIX_STYLE;

    companion object {
        fun fromStored(value: String?): UiThemeStyle =
            entries.firstOrNull { it.name == value } ?: MATERIAL
    }
}

/** Colour mode is independent of the Material/MIUIX component style. */
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
    private const val STYLE_KEY = "theme_style"
    private const val COLOR_MODE_KEY = "color_mode"

    fun read(context: Context): UiThemeStyle =
        UiThemeStyle.fromStored(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(STYLE_KEY, null)
        )

    fun write(context: Context, style: UiThemeStyle) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(STYLE_KEY, style.name).apply()
    }

    fun readColorMode(context: Context): UiColorMode =
        UiColorMode.fromStored(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(COLOR_MODE_KEY, null)
        )

    fun writeColorMode(context: Context, mode: UiColorMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(COLOR_MODE_KEY, mode.name).apply()
    }
}

internal val LocalUiThemeStyle = staticCompositionLocalOf { UiThemeStyle.MATERIAL }

private val MiuixLightColors = lightColorScheme(
    primary = Color(0xFF347DF4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EEFF),
    onPrimaryContainer = Color(0xFF173E7D),
    secondary = Color(0xFF477AB9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9F0FA),
    onSecondaryContainer = Color(0xFF233F63),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF191B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF5A606B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF6F6FA),
    surfaceContainerHigh = Color(0xFFEFEFF5),
    surfaceContainerHighest = Color(0xFFE7E8F0),
    outline = Color(0xFFB9BECA),
    outlineVariant = Color(0xFFE0E3EB),
)

private val MiuixDarkColors = darkColorScheme(
    primary = Color(0xFF85B3FF),
    onPrimary = Color(0xFF103C7B),
    primaryContainer = Color(0xFF23446E),
    onPrimaryContainer = Color(0xFFDCE9FF),
    secondary = Color(0xFF9FC3F2),
    onSecondary = Color(0xFF1B4268),
    secondaryContainer = Color(0xFF31455F),
    onSecondaryContainer = Color(0xFFDBE9FF),
    background = Color(0xFF101115),
    onBackground = Color(0xFFF0F0F5),
    surface = Color(0xFF1C1D23),
    onSurface = Color(0xFFF1F1F7),
    surfaceVariant = Color(0xFF262831),
    onSurfaceVariant = Color(0xFFB6B9C4),
    surfaceContainerLowest = Color(0xFF111216),
    surfaceContainerLow = Color(0xFF18191E),
    surfaceContainer = Color(0xFF202127),
    surfaceContainerHigh = Color(0xFF292B32),
    surfaceContainerHighest = Color(0xFF33353C),
    outline = Color(0xFF777D8B),
    outlineVariant = Color(0xFF3D414B),
)

private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val MiuixTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
internal fun ListCleanerUiTheme(
    style: UiThemeStyle,
    colorMode: UiColorMode,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = colorMode.usesDarkTheme(isSystemInDarkTheme())
    CompositionLocalProvider(LocalUiThemeStyle provides style) {
        when (style) {
            UiThemeStyle.MATERIAL -> MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                content = content
            )
            UiThemeStyle.MIUIX_STYLE -> {
                val controller = remember(colorMode) {
                    ThemeController(
                        when (colorMode) {
                            UiColorMode.SYSTEM -> ColorSchemeMode.System
                            UiColorMode.LIGHT -> ColorSchemeMode.Light
                            UiColorMode.DARK -> ColorSchemeMode.Dark
                        }
                    )
                }
                MiuixTheme(controller = controller) {
                    // M3-only widgets remain legible while the native Miuix migration proceeds.
                    MaterialTheme(
                        colorScheme = if (dark) MiuixDarkColors else MiuixLightColors,
                        shapes = MiuixShapes,
                        typography = MiuixTypography,
                        content = content
                    )
                }
            }
        }
    }
}
