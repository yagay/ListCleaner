package com.yagay.ListCleaner.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yagay.ListCleaner.domain.AppTypeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local display preferences only. Never included in the remote Hook/Root configuration. */
internal enum class UiDensity { COMPACT, STANDARD, COMFORTABLE }
internal enum class UiAccent { DYNAMIC, BLUE, GREEN, PURPLE }
internal enum class UiStartPage { CLEANUP, ORDERING, SETTINGS }
internal enum class UiAppSort { SELECTED_FIRST, NAME, PACKAGE }
internal enum class UiScanMode { SMART, MANUAL, EVERY_ENTRY }

internal data class UiPreferences(
    val density: UiDensity = UiDensity.STANDARD,
    val accent: UiAccent = UiAccent.DYNAMIC,
    val cardRadius: Int = 20,
    val buttonHeight: Int = 40,
    val appIconSize: Int = 36,
    val listRowHeight: Int = 60,
    val startPage: UiStartPage = UiStartPage.CLEANUP,
    val appSort: UiAppSort = UiAppSort.SELECTED_FIRST,
    val defaultFilter: UiFilter = UiFilter.ALL,
    val defaultAppType: AppTypeFilter = AppTypeFilter.ALL,
    val rememberFilters: Boolean = false,
    val openHelpByDefault: Boolean = false,
    val homeColumns: Int = 2,
    val scanMode: UiScanMode = UiScanMode.SMART,
    val scanCacheMinutes: Int = 10,
    val scanRetrySeconds: Int = 30,
    val appLinkEnrichment: Boolean = true,
    val fontScalePercent: Int = 100,
    val appTitleLines: Int = 1,
    val appSubtitleLines: Int = 1,
    val showAppIcons: Boolean = true,
    val showPackageNames: Boolean = false,
    val rememberPageSearch: Boolean = true,
    val expandFirstApp: Boolean = false,
    val dragHaptics: Boolean = true,
) {
    fun normalized(): UiPreferences = copy(
        cardRadius = cardRadius.coerceIn(0, 32),
        buttonHeight = buttonHeight.coerceIn(36, 64),
        appIconSize = appIconSize.coerceIn(28, 56),
        listRowHeight = listRowHeight.coerceIn(48, 96),
        scanCacheMinutes = scanCacheMinutes.coerceIn(1, 60),
        homeColumns = homeColumns.coerceIn(1, 2),
        scanRetrySeconds = scanRetrySeconds.coerceIn(10, 180),
        fontScalePercent = fontScalePercent.coerceIn(80, 140),
        appTitleLines = appTitleLines.coerceIn(1, 3),
        appSubtitleLines = appSubtitleLines.coerceIn(1, 3)
    )
}

internal object UiPreferencesRuntime {
    var current by mutableStateOf(UiPreferences())
        private set
    private val mutableFlow = MutableStateFlow(current)
    val flow = mutableFlow.asStateFlow()

    fun apply(value: UiPreferences) {
        val safe = value.normalized()
        current = safe
        mutableFlow.value = safe
    }
}

/** Persist user-facing UI defaults independently from data rules and module config. */
internal object UiPreferencesStore {
    private const val FILE = "ui_preferences_v1"
    private fun <T : Enum<T>> stored(name: String?, values: Array<T>, fallback: T): T =
        values.firstOrNull { it.name == name } ?: fallback

    fun read(context: Context): UiPreferences {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = UiPreferences()
        return UiPreferences(
            density = stored(p.getString("density", null), UiDensity.entries.toTypedArray(), defaults.density),
            accent = stored(p.getString("accent", null), UiAccent.entries.toTypedArray(), defaults.accent),
            cardRadius = p.getInt("card_radius", defaults.cardRadius),
            buttonHeight = p.getInt("button_height", defaults.buttonHeight),
            appIconSize = p.getInt("app_icon_size", defaults.appIconSize),
            listRowHeight = p.getInt("list_row_height", defaults.listRowHeight),
            startPage = stored(p.getString("start_page", null), UiStartPage.entries.toTypedArray(), defaults.startPage),
            appSort = stored(p.getString("app_sort", null), UiAppSort.entries.toTypedArray(), defaults.appSort),
            defaultFilter = stored(p.getString("default_filter", null), UiFilter.entries.toTypedArray(), defaults.defaultFilter),
            defaultAppType = stored(p.getString("default_app_type", null), AppTypeFilter.entries.toTypedArray(), defaults.defaultAppType),
            rememberFilters = p.getBoolean("remember_filters", defaults.rememberFilters),
            openHelpByDefault = p.getBoolean("help_default", defaults.openHelpByDefault),
            homeColumns = p.getInt("home_columns", defaults.homeColumns),
            scanMode = stored(p.getString("scan_mode", null), UiScanMode.entries.toTypedArray(), defaults.scanMode),
            scanCacheMinutes = p.getInt("scan_cache_minutes", defaults.scanCacheMinutes),
            scanRetrySeconds = p.getInt("scan_retry_seconds", defaults.scanRetrySeconds),
            appLinkEnrichment = p.getBoolean("app_link_enrichment", defaults.appLinkEnrichment),
            fontScalePercent = p.getInt("font_scale_percent", defaults.fontScalePercent),
            appTitleLines = p.getInt("app_title_lines", defaults.appTitleLines),
            appSubtitleLines = p.getInt("app_subtitle_lines", defaults.appSubtitleLines),
            showAppIcons = p.getBoolean("show_app_icons", defaults.showAppIcons),
            showPackageNames = p.getBoolean("show_package_names", defaults.showPackageNames),
            rememberPageSearch = p.getBoolean("remember_page_search", defaults.rememberPageSearch),
            expandFirstApp = p.getBoolean("expand_first_app", defaults.expandFirstApp),
            dragHaptics = p.getBoolean("drag_haptics", defaults.dragHaptics),
        ).normalized()
    }

    fun write(context: Context, value: UiPreferences) {
        val safe = value.normalized()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("density", safe.density.name)
            .putString("accent", safe.accent.name)
            .putInt("card_radius", safe.cardRadius)
            .putInt("button_height", safe.buttonHeight)
            .putInt("app_icon_size", safe.appIconSize)
            .putInt("list_row_height", safe.listRowHeight)
            .putString("start_page", safe.startPage.name)
            .putString("app_sort", safe.appSort.name)
            .putString("default_filter", safe.defaultFilter.name)
            .putString("default_app_type", safe.defaultAppType.name)
            .putBoolean("remember_filters", safe.rememberFilters)
            .putBoolean("help_default", safe.openHelpByDefault)
            .putInt("home_columns", safe.homeColumns)
            .putString("scan_mode", safe.scanMode.name)
            .putInt("scan_cache_minutes", safe.scanCacheMinutes)
            .putInt("scan_retry_seconds", safe.scanRetrySeconds)
            .putBoolean("app_link_enrichment", safe.appLinkEnrichment)
            .putInt("font_scale_percent", safe.fontScalePercent)
            .putInt("app_title_lines", safe.appTitleLines)
            .putInt("app_subtitle_lines", safe.appSubtitleLines)
            .putBoolean("show_app_icons", safe.showAppIcons)
            .putBoolean("show_package_names", safe.showPackageNames)
            .putBoolean("remember_page_search", safe.rememberPageSearch)
            .putBoolean("expand_first_app", safe.expandFirstApp)
            .putBoolean("drag_haptics", safe.dragHaptics)
            .apply()
        UiPreferencesRuntime.apply(safe)
    }

    /** Last-used values belong to individual pages and never overwrite configured defaults. */
    private fun pageKey(page: String, field: String): String {
        require(page in setOf("rules", "priority", "components"))
        return "last_${field}_$page"
    }

    fun initialFilter(context: Context, page: String): UiFilter {
        val current = UiPreferencesRuntime.current
        if (!current.rememberFilters) return current.defaultFilter
        return stored(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(pageKey(page, "filter"), null), UiFilter.entries.toTypedArray(), current.defaultFilter)
    }

    fun initialAppType(context: Context, page: String): AppTypeFilter {
        val current = UiPreferencesRuntime.current
        if (!current.rememberFilters) return current.defaultAppType
        return stored(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(pageKey(page, "app_type"), null), AppTypeFilter.entries.toTypedArray(), current.defaultAppType)
    }

    fun rememberFilter(context: Context, filter: UiFilter, page: String = "rules") {
        if (UiPreferencesRuntime.current.rememberFilters) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(pageKey(page, "filter"), filter.name).apply()
        }
    }

    fun rememberAppType(context: Context, appType: AppTypeFilter, page: String = "rules") {
        if (UiPreferencesRuntime.current.rememberFilters) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(pageKey(page, "app_type"), appType.name).apply()
        }
    }

    /** Export only known manager preferences; v1–v11 backups leave them unchanged. */
    fun backupValues(context: Context): Map<String, String> {
        val values = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).all
        return backupKeys.mapNotNull { key ->
            val value = values[key]
            if (value is String || value is Int || value is Boolean) key to value.toString()
            else null
        }.toMap()
    }

    fun restoreBackupValues(context: Context, values: Map<String, String>) {
        require(values.size <= backupKeys.size && values.all { (key, value) ->
            key in backupKeys && value.length <= 120
        }) { "Invalid UI settings backup" }
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        backupKeys.forEach { editor.remove(it) }
        values.forEach { (key, value) ->
            when (key) {
                in textKeys -> editor.putString(key, value)
                in numberKeys -> value.toIntOrNull()?.let { editor.putInt(key, it) }
                in toggleKeys -> when (value) {
                    "true" -> editor.putBoolean(key, true)
                    "false" -> editor.putBoolean(key, false)
                    else -> Unit
                }
            }
        }
        editor.apply()
        UiPreferencesRuntime.apply(read(context))
    }

    private val textKeys = setOf(
        "density", "accent", "start_page", "app_sort", "default_filter",
        "default_app_type", "scan_mode",
        "last_filter_rules", "last_filter_priority", "last_filter_components",
        "last_app_type_rules", "last_app_type_priority", "last_app_type_components"
    )
    private val numberKeys = setOf(
        "card_radius", "button_height", "app_icon_size", "list_row_height",
        "home_columns", "scan_cache_minutes", "scan_retry_seconds",
        "font_scale_percent", "app_title_lines", "app_subtitle_lines"
    )
    private val toggleKeys = setOf(
        "remember_filters", "help_default", "app_link_enrichment",
        "show_app_icons", "show_package_names", "remember_page_search",
        "expand_first_app", "drag_haptics"
    )
    private val backupKeys get() = textKeys + numberKeys + toggleKeys

    fun reset(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        write(context, UiPreferences())
    }
}

internal fun UiStartPage.destination(): Destination = when (this) {
    UiStartPage.CLEANUP -> Destination.RULES
    UiStartPage.ORDERING -> Destination.PRIORITY
    UiStartPage.SETTINGS -> Destination.SETTINGS
}
