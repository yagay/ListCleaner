package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/**
 * Local and system_server configuration for one Android user. A new space starts in HIDE_SELECTED mode; with no selected entries, discovery itself
 * does not hide any resolver candidates.
 */
@Serializable
data class UserSpaceRules(
    val rules: Set<ComponentRule> = emptySet(),
    val mode: DisplayMode = DisplayMode.HIDE_SELECTED,
    val priorities: PriorityConfig = PriorityConfig(),
    val diagnostic: Boolean = false,
    val hiddenFromApps: Set<String> = emptySet(),
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    val directShareTypes: DirectShareTypeConfig = DirectShareTypeConfig(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
    val surfaceFilters: SurfaceFilterConfig = SurfaceFilterConfig(),
    val modeOverrides: Map<IntentKind, DisplayMode> = emptyMap(),
    val disabledEntryGroups: Set<EntryGroup> = emptySet(),
    val disabledEntryKinds: Set<IntentKind> = emptySet()
) {
    fun validated(): UserSpaceRules {
        val validated = toModuleConfig().validated()
        return validated.toSpaceRules()
    }

    fun toModuleConfig(): ModuleConfig = ModuleConfig(
        rules = rules,
        mode = mode,
        priorities = priorities,
        diagnostic = diagnostic,
        hiddenFromApps = hiddenFromApps,
        openTypes = openTypes,
        directShareTypes = directShareTypes,
        browserLinks = browserLinks,
        visibilityCompat = visibilityCompat,
        surfaceFilters = surfaceFilters,
        modeOverrides = modeOverrides,
        disabledEntryGroups = disabledEntryGroups,
        disabledEntryKinds = disabledEntryKinds
    )
}

fun ModuleConfig.toSpaceRules(): UserSpaceRules = UserSpaceRules(
    rules = rules, mode = mode, priorities = priorities, diagnostic = diagnostic,
    hiddenFromApps = hiddenFromApps, openTypes = openTypes, directShareTypes = directShareTypes,
    browserLinks = browserLinks, visibilityCompat = visibilityCompat,
    surfaceFilters = surfaceFilters,
    modeOverrides = modeOverrides,
    disabledEntryGroups = disabledEntryGroups,
        disabledEntryKinds = disabledEntryKinds,
)
