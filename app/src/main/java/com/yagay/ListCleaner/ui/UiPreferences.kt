package com.yagay.ListCleaner.ui

import android.content.Context
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yagay.ListCleaner.domain.AppTypeFilter
import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import com.yagay.ListCleaner.data.CleanupKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local display preferences only. Never included in the remote Hook/Root configuration. */
internal enum class UiDensity { COMPACT, STANDARD, COMFORTABLE }
internal enum class UiListStyle { ORIGINAL, GROUPED }
/** Persisted names for compatibility: CATEGORY_CARDS now renders a category LIST.
 * CLASSIC_LIST opens the editor list directly. Neither uses card layouts.
 */
internal enum class UiHomeLayout { CATEGORY_CARDS, CLASSIC_LIST }
internal enum class UiAccent { DYNAMIC, BLUE, GREEN, PURPLE, CUSTOM }
internal enum class UiStartPage { CLEANUP, ORDERING, SURFACES, SETTINGS }
internal enum class UiAppSort { SELECTED_FIRST, NAME, PACKAGE }
internal enum class UiScanMode { SMART, MANUAL, EVERY_ENTRY }
/** Applies to proactive read-only ShortcutService discovery, not passive Hooks. */
internal enum class UiProactiveScanMode { ON_SCAN, MANUAL_ONLY, OFF }
internal enum class UiCandidateSource { ALL, OBSERVED, PROACTIVE, HISTORY }
internal enum class UiComponentStart { CATEGORIES, ALL_COMPONENTS }
internal enum class UiPreviewDefault { CURRENT, CANDIDATES, ENABLED, HISTORY }
internal enum class UiProbeDefaultScenario { SYSTEM, TEXT, IMAGE, PDF, LAST_USED }
enum class UiDiagnosticsLevel { SUMMARY, FULL }
internal enum class UiComponentRefresh { CHANGED_ONLY, FULL }

internal data class UiPreferences(
    val density: UiDensity = UiDensity.STANDARD,
    val listStyle: UiListStyle = UiListStyle.ORIGINAL,
    val accent: UiAccent = UiAccent.DYNAMIC,
    val customAccentHex: String = "#2859AB",
    val colorOverrides: Map<String, String> = emptyMap(),
    val homeLayout: UiHomeLayout = UiHomeLayout.CLASSIC_LIST,
    val cardRadius: Int = 20,
    val buttonHeight: Int = 40,
    val appIconSize: Int = 36,
    val listRowHeight: Int = 60,
    val startPage: UiStartPage = UiStartPage.CLEANUP,
    val appSort: UiAppSort = UiAppSort.SELECTED_FIRST,
    val defaultFilter: UiFilter = UiFilter.ALL,
    val defaultAppType: AppTypeFilter = AppTypeFilter.USER,
    val pinnedEntryKinds: List<IntentKind> = emptyList(),
    val pinnedRootKinds: List<CleanupKind> = emptyList(),
    val rememberFilters: Boolean = false,
    val openHelpByDefault: Boolean = false,
    val homeColumns: Int = 2,
    val scanMode: UiScanMode = UiScanMode.SMART,
    val scanCacheMinutes: Int = 10,
    val scanRetrySeconds: Int = 30,
    val appLinkEnrichment: Boolean = true,
    val proactiveScanMode: UiProactiveScanMode = UiProactiveScanMode.ON_SCAN,
    val proactiveDirectShare: Boolean = true,
    val proactiveShortcutItems: Boolean = false,
    val proactiveCacheMinutes: Int = 5,
    val candidateSource: UiCandidateSource = UiCandidateSource.ALL,
    val showShortcutHistory: Boolean = true,
    /** Zero keeps history visible indefinitely; selected rules are not deleted. */
    val historyVisibilityDays: Int = 0,
    val componentStart: UiComponentStart = UiComponentStart.ALL_COMPONENTS,
    val previewDefault: UiPreviewDefault = UiPreviewDefault.CURRENT,
    val probeDefaultScenario: UiProbeDefaultScenario = UiProbeDefaultScenario.SYSTEM,
    val diagnosticsLevel: UiDiagnosticsLevel = UiDiagnosticsLevel.SUMMARY,
    val componentRefresh: UiComponentRefresh = UiComponentRefresh.CHANGED_ONLY,
    val fontScalePercent: Int = 100,
    val layoutSpacingPercent: Int = 100,
    val appTitleLines: Int = 1,
    val appSubtitleLines: Int = 1,
    val showAppIcons: Boolean = true,
    val showPackageNames: Boolean = false,
    val rememberPageSearch: Boolean = true,
    val expandFirstApp: Boolean = false,
    val dragHaptics: Boolean = true,
) {
    fun normalized(): UiPreferences = copy(
        // Legacy GROUPED and CATEGORY_CARDS remain readable only for migration.
        // All active screens render one flat list; stored values do not fork UI.
        listStyle = UiListStyle.ORIGINAL,
        pinnedEntryKinds = pinnedEntryKinds.filter { it.isSelectableEntryKind() }.distinct().take(16),
        pinnedRootKinds = pinnedRootKinds.distinct().take(CleanupKind.entries.size),
        homeLayout = UiHomeLayout.CLASSIC_LIST,
        colorOverrides = colorOverrides.filter { (key, hex) -> (key in UiColorRoles.keys || (key.startsWith("dark_") && key.removePrefix("dark_") in UiColorRoles.keys)) && hex.matches(Regex("#[0-9a-fA-F]{6}")) },
        customAccentHex = customAccentHex.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) } ?: "#2859AB",
        cardRadius = cardRadius.coerceIn(0, 32),
        buttonHeight = buttonHeight.coerceIn(36, 64),
        appIconSize = appIconSize.coerceIn(28, 56),
        listRowHeight = listRowHeight.coerceIn(48, 96),
        scanCacheMinutes = scanCacheMinutes.coerceIn(1, 60),
        proactiveCacheMinutes = proactiveCacheMinutes.coerceIn(1, 60),
        historyVisibilityDays = historyVisibilityDays.takeIf { it == 0 || it == 7 || it == 30 || it == 90 } ?: 0,
        homeColumns = homeColumns.coerceIn(1, 2),
        scanRetrySeconds = scanRetrySeconds.coerceIn(10, 180),
        fontScalePercent = fontScalePercent.coerceIn(80, 140),
        layoutSpacingPercent = layoutSpacingPercent.coerceIn(70, 150),
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

    private fun <T : Enum<T>> storedKinds(names: String?, values: Array<T>): List<T> =
        names.orEmpty().split(',').mapNotNull { name -> values.firstOrNull { it.name == name } }.distinct()

    /** Legacy layout names remain readable in backups but resolve to one list. */
    internal fun upgradedListLayout(saved: UiHomeLayout, alreadyUpgraded: Boolean): UiHomeLayout =
        UiHomeLayout.CLASSIC_LIST

    fun read(context: Context): UiPreferences {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val defaults = UiPreferences()
        // Upgrade the previous ALL-apps default once, keeping explicit SYSTEM choices.
        if (!p.getBoolean("default_user_apps_v1", false)) {
            if (p.getString("default_app_type", null) in setOf(null, AppTypeFilter.ALL.name)) {
                p.edit().putString("default_app_type", AppTypeFilter.USER.name)
                    .putBoolean("default_user_apps_v1", true).apply()
            } else {
                p.edit().putBoolean("default_user_apps_v1", true).apply()
            }
        }
        val migrationKey = "direct_list_default_v3"
        val migrated = p.getBoolean(migrationKey, false)
        val savedLayout = stored(p.getString("home_layout", null),
            UiHomeLayout.entries.toTypedArray(), defaults.homeLayout)
        val resolvedLayout = upgradedListLayout(savedLayout, migrated)
        if (!migrated) {
            p.edit().putString("home_layout", resolvedLayout.name)
                .putBoolean(migrationKey, true).apply()
        }
        return UiPreferences(
            density = stored(p.getString("density", null), UiDensity.entries.toTypedArray(), defaults.density),
            listStyle = stored(p.getString("list_style", null), UiListStyle.entries.toTypedArray(), defaults.listStyle),
            accent = stored(p.getString("accent", null), UiAccent.entries.toTypedArray(), defaults.accent),
            customAccentHex = p.getString("custom_accent_hex", defaults.customAccentHex) ?: defaults.customAccentHex,
            colorOverrides = runCatching {
                val saved = JSONObject(p.getString("color_overrides", "{}") ?: "{}")
                saved.keys().asSequence().associateWith { saved.optString(it) }
            }.getOrDefault(emptyMap()),
            homeLayout = resolvedLayout,
            cardRadius = p.getInt("card_radius", defaults.cardRadius),
            buttonHeight = p.getInt("button_height", defaults.buttonHeight),
            appIconSize = p.getInt("app_icon_size", defaults.appIconSize),
            listRowHeight = p.getInt("list_row_height", defaults.listRowHeight),
            startPage = stored(p.getString("start_page", null), UiStartPage.entries.toTypedArray(), defaults.startPage),
            appSort = stored(p.getString("app_sort", null), UiAppSort.entries.toTypedArray(), defaults.appSort),
            defaultFilter = stored(p.getString("default_filter", null), UiFilter.entries.toTypedArray(), defaults.defaultFilter),
            defaultAppType = stored(p.getString("default_app_type", null), AppTypeFilter.entries.toTypedArray(), defaults.defaultAppType),
            pinnedEntryKinds = storedKinds(p.getString("pinned_entry_kinds", null), IntentKind.entries.toTypedArray()),
            pinnedRootKinds = storedKinds(p.getString("pinned_root_kinds", null), CleanupKind.entries.toTypedArray()),
            rememberFilters = p.getBoolean("remember_filters", defaults.rememberFilters),
            openHelpByDefault = p.getBoolean("help_default", defaults.openHelpByDefault),
            homeColumns = p.getInt("home_columns", defaults.homeColumns),
            scanMode = stored(p.getString("scan_mode", null), UiScanMode.entries.toTypedArray(), defaults.scanMode),
            scanCacheMinutes = p.getInt("scan_cache_minutes", defaults.scanCacheMinutes),
            scanRetrySeconds = p.getInt("scan_retry_seconds", defaults.scanRetrySeconds),
            appLinkEnrichment = p.getBoolean("app_link_enrichment", defaults.appLinkEnrichment),
            proactiveScanMode = stored(p.getString("proactive_scan_mode", null),
                UiProactiveScanMode.entries.toTypedArray(), defaults.proactiveScanMode),
            proactiveDirectShare = p.getBoolean("proactive_direct_share", defaults.proactiveDirectShare),
            proactiveShortcutItems = p.getBoolean("proactive_shortcut_items", defaults.proactiveShortcutItems),
            proactiveCacheMinutes = p.getInt("proactive_cache_minutes", defaults.proactiveCacheMinutes),
            candidateSource = stored(p.getString("candidate_source", null), UiCandidateSource.entries.toTypedArray(), defaults.candidateSource),
            showShortcutHistory = p.getBoolean("shortcut_history", defaults.showShortcutHistory),
            historyVisibilityDays = p.getInt("history_visibility_days", defaults.historyVisibilityDays),
            componentStart = stored(p.getString("component_start", null), UiComponentStart.entries.toTypedArray(), defaults.componentStart),
            previewDefault = stored(p.getString("preview_default", null), UiPreviewDefault.entries.toTypedArray(), defaults.previewDefault),
            probeDefaultScenario = stored(p.getString("probe_default_scenario", null), UiProbeDefaultScenario.entries.toTypedArray(), defaults.probeDefaultScenario),
            diagnosticsLevel = stored(p.getString("diagnostics_level", null), UiDiagnosticsLevel.entries.toTypedArray(), defaults.diagnosticsLevel),
            componentRefresh = stored(p.getString("component_refresh", null), UiComponentRefresh.entries.toTypedArray(), defaults.componentRefresh),
            fontScalePercent = p.getInt("font_scale_percent", defaults.fontScalePercent),
            layoutSpacingPercent = p.getInt("layout_spacing_percent", defaults.layoutSpacingPercent),
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
            .putString("list_style", safe.listStyle.name)
            .putString("accent", safe.accent.name)
            .putString("custom_accent_hex", safe.customAccentHex)
            .putString("color_overrides", JSONObject(safe.colorOverrides).toString())
            .putString("home_layout", safe.homeLayout.name)
            .putInt("card_radius", safe.cardRadius)
            .putInt("button_height", safe.buttonHeight)
            .putInt("app_icon_size", safe.appIconSize)
            .putInt("list_row_height", safe.listRowHeight)
            .putString("start_page", safe.startPage.name)
            .putString("app_sort", safe.appSort.name)
            .putString("default_filter", safe.defaultFilter.name)
            .putString("default_app_type", safe.defaultAppType.name)
            .putString("pinned_entry_kinds", safe.pinnedEntryKinds.joinToString(",") { it.name })
            .putString("pinned_root_kinds", safe.pinnedRootKinds.joinToString(",") { it.name })
            .putBoolean("remember_filters", safe.rememberFilters)
            .putBoolean("help_default", safe.openHelpByDefault)
            .putInt("home_columns", safe.homeColumns)
            .putString("scan_mode", safe.scanMode.name)
            .putInt("scan_cache_minutes", safe.scanCacheMinutes)
            .putInt("scan_retry_seconds", safe.scanRetrySeconds)
            .putBoolean("app_link_enrichment", safe.appLinkEnrichment)
            .putString("proactive_scan_mode", safe.proactiveScanMode.name)
            .putBoolean("proactive_direct_share", safe.proactiveDirectShare)
            .putBoolean("proactive_shortcut_items", safe.proactiveShortcutItems)
            .putInt("proactive_cache_minutes", safe.proactiveCacheMinutes)
            .putString("candidate_source", safe.candidateSource.name)
            .putBoolean("shortcut_history", safe.showShortcutHistory)
            .putInt("history_visibility_days", safe.historyVisibilityDays)
            .putString("component_start", safe.componentStart.name)
            .putString("preview_default", safe.previewDefault.name)
            .putString("probe_default_scenario", safe.probeDefaultScenario.name)
            .putString("diagnostics_level", safe.diagnosticsLevel.name)
            .putString("component_refresh", safe.componentRefresh.name)
            .putInt("font_scale_percent", safe.fontScalePercent)
            .putInt("layout_spacing_percent", safe.layoutSpacingPercent)
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

    fun recordProbeScenario(context: Context, scenario: SystemProbeScenario) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("last_probe_scenario", scenario.name).apply()
    }

    fun initialProbeScenario(context: Context, kind: com.yagay.ListCleaner.domain.IntentKind): SystemProbeScenario {
        val options = availableProbeScenarios(kind)
        val requested = when (UiPreferencesRuntime.current.probeDefaultScenario) {
            UiProbeDefaultScenario.SYSTEM -> null
            UiProbeDefaultScenario.TEXT -> SystemProbeScenario.TEXT
            UiProbeDefaultScenario.IMAGE -> SystemProbeScenario.IMAGE
            UiProbeDefaultScenario.PDF -> SystemProbeScenario.PDF
            UiProbeDefaultScenario.LAST_USED -> stored(
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .getString("last_probe_scenario", null),
                SystemProbeScenario.entries.toTypedArray(), SystemProbeScenario.TEXT)
        }
        return requested?.takeIf { it in options } ?: defaultProbeScenario(kind)
    }

    /** Last-used values belong to individual pages and never overwrite configured defaults. */
    private fun pageKey(page: String, field: String): String {
        require(page in setOf("rules", "priority", "components") ||
            (page.startsWith("lists_") && page.all { it.isLowerCase() || it.isDigit() || it == '_' }))
        return "last_${field}_$page"
    }

    fun initialFilter(context: Context, page: String): UiFilter {
        val current = UiPreferencesRuntime.current
        if (!current.rememberFilters) return current.defaultFilter
        return stored(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(pageKey(page, "filter"), null), UiFilter.entries.toTypedArray(), current.defaultFilter)
    }

    /**
     * Entry cleanup and ordering share one persisted app-type filter. Older
     * priority-page values are intentionally not used: they caused separate
     * system/user visibility when navigating between the two Entry screens.
     * The components page keeps its own app-type filter.
     */
    internal fun appTypePreferencePage(page: String): String =
        if (page == "priority") "rules" else page

    fun initialAppType(context: Context, page: String): AppTypeFilter {
        val current = UiPreferencesRuntime.current
        if (!current.rememberFilters) return current.defaultAppType
        return stored(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(pageKey(appTypePreferencePage(page), "app_type"), null),
            AppTypeFilter.entries.toTypedArray(), current.defaultAppType)
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
                .putString(pageKey(appTypePreferencePage(page), "app_type"), appType.name)
                .apply()
        }
    }

    private val backupTextKeys = setOf("density", "accent", "home_layout", "start_page", "app_sort", "default_filter",
        "pinned_entry_kinds", "pinned_root_kinds",
        "default_app_type", "scan_mode", "proactive_scan_mode",
        "candidate_source", "component_start", "preview_default", "probe_default_scenario",
        "diagnostics_level", "component_refresh", "last_probe_scenario", "last_filter_rules", "last_filter_priority", "last_filter_components",
        "last_app_type_rules", "last_app_type_priority", "last_app_type_components")
    private val backupIntKeys = setOf("card_radius", "button_height", "app_icon_size", "list_row_height",
        "home_columns", "scan_cache_minutes", "scan_retry_seconds", "proactive_cache_minutes",
        "history_visibility_days", "font_scale_percent", "layout_spacing_percent",
        "app_title_lines", "app_subtitle_lines")
    private val backupBoolKeys = setOf("remember_filters", "help_default", "app_link_enrichment",
        "proactive_direct_share", "proactive_shortcut_items", "shortcut_history",
        "show_app_icons", "show_package_names", "remember_page_search", "expand_first_app", "drag_haptics")

    fun exportSnapshot(context: Context): JSONObject {
        val v = read(context)
        val snapshot = JSONObject()
        snapshot.put("density", v.density.name)
        snapshot.put("accent", v.accent.name)
        snapshot.put("home_layout", v.homeLayout.name)
        snapshot.put("start_page", v.startPage.name)
        snapshot.put("app_sort", v.appSort.name)
        snapshot.put("default_filter", v.defaultFilter.name)
        snapshot.put("default_app_type", v.defaultAppType.name)
        snapshot.put("pinned_entry_kinds", v.pinnedEntryKinds.joinToString(",") { it.name })
        snapshot.put("pinned_root_kinds", v.pinnedRootKinds.joinToString(",") { it.name })
        snapshot.put("scan_mode", v.scanMode.name)
        snapshot.put("proactive_scan_mode", v.proactiveScanMode.name)
        snapshot.put("proactive_direct_share", v.proactiveDirectShare)
        snapshot.put("proactive_shortcut_items", v.proactiveShortcutItems)
        snapshot.put("proactive_cache_minutes", v.proactiveCacheMinutes)
        snapshot.put("candidate_source", v.candidateSource.name)
        snapshot.put("shortcut_history", v.showShortcutHistory)
        snapshot.put("history_visibility_days", v.historyVisibilityDays)
        snapshot.put("component_start", v.componentStart.name)
        snapshot.put("preview_default", v.previewDefault.name)
        snapshot.put("probe_default_scenario", v.probeDefaultScenario.name)
        snapshot.put("diagnostics_level", v.diagnosticsLevel.name)
        snapshot.put("component_refresh", v.componentRefresh.name)
        snapshot.put("card_radius", v.cardRadius)
        snapshot.put("button_height", v.buttonHeight)
        snapshot.put("app_icon_size", v.appIconSize)
        snapshot.put("list_row_height", v.listRowHeight)
        snapshot.put("home_columns", v.homeColumns)
        snapshot.put("scan_cache_minutes", v.scanCacheMinutes)
        snapshot.put("scan_retry_seconds", v.scanRetrySeconds)
        snapshot.put("font_scale_percent", v.fontScalePercent)
        snapshot.put("layout_spacing_percent", v.layoutSpacingPercent)
        snapshot.put("app_title_lines", v.appTitleLines)
        snapshot.put("app_subtitle_lines", v.appSubtitleLines)
        snapshot.put("remember_filters", v.rememberFilters)
        snapshot.put("help_default", v.openHelpByDefault)
        snapshot.put("app_link_enrichment", v.appLinkEnrichment)
        snapshot.put("show_app_icons", v.showAppIcons)
        snapshot.put("show_package_names", v.showPackageNames)
        snapshot.put("remember_page_search", v.rememberPageSearch)
        snapshot.put("expand_first_app", v.expandFirstApp)
        snapshot.put("drag_haptics", v.dragHaptics)
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        backupTextKeys.filter { it.startsWith("last_") }.forEach { key ->
            stored.getString(key, null)?.let { snapshot.put(key, it) }
        }
        snapshot.put("appearance_color_mode", UiAppearanceStore.readColorMode(context).name)
        return snapshot
    }

    fun validateSnapshot(snapshot: JSONObject) {
        require(snapshot.length() <= backupTextKeys.size + backupIntKeys.size + backupBoolKeys.size + 1)
        for (key in snapshot.keys()) {
            val value = snapshot.get(key)
            require(when (key) {
                "pinned_entry_kinds" -> value is String && value.length <= 1024 &&
                    value.split(',').filter { it.isNotBlank() }.all { name -> IntentKind.entries.any { it.name == name } }
                "pinned_root_kinds" -> value is String && value.length <= 256 &&
                    value.split(',').filter { it.isNotBlank() }.all { name -> CleanupKind.entries.any { it.name == name } }
                in backupTextKeys, "appearance_color_mode" -> value is String && value.length <= 256
                in backupIntKeys -> value is Number && value.toInt() in -1000..10000
                in backupBoolKeys -> value is Boolean
                else -> false
            }) { "Invalid UI settings entry" }
        }
    }

    fun importSnapshot(context: Context, snapshot: JSONObject) {
        validateSnapshot(snapshot)
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        (backupTextKeys + backupIntKeys + backupBoolKeys).forEach { editor.remove(it) }
        for (key in snapshot.keys()) {
            when (key) {
                "appearance_color_mode" ->
                    UiAppearanceStore.writeColorMode(context, UiColorMode.fromStored(snapshot.getString(key)))
                in backupTextKeys -> editor.putString(key, snapshot.getString(key))
                in backupIntKeys -> editor.putInt(key, snapshot.getInt(key))
                in backupBoolKeys -> editor.putBoolean(key, snapshot.getBoolean(key))
            }
        }
        editor.apply()
        UiPreferencesRuntime.apply(read(context))
    }

    fun reset(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
        write(context, UiPreferences())
    }
}

internal fun UiStartPage.destination(): Destination = when (this) {
    UiStartPage.CLEANUP -> Destination.RULES
    UiStartPage.ORDERING -> Destination.PRIORITY
    UiStartPage.SURFACES -> Destination.SURFACES
    UiStartPage.SETTINGS -> Destination.SETTINGS
}
