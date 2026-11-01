package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** One remote preference value prevents mixed old/new fields during backup restore. */
@Serializable
data class ModuleConfig(
    val rules: Set<ComponentRule>,
    val mode: DisplayMode,
    val priorities: PriorityConfig,
    val diagnostic: Boolean,
    // Supplied only by our manager app. Runtime Probe v2 carries the serialized config after
    // system_server verifies the Binder caller UID belongs to com.yagay.ListCleaner.
    val managerAppId: Int = -1,
    // Apps in this list are callers from which selected target packages may be hidden at system_server.
    // They do NOT need to be added to the LSPosed module scope.
    val hiddenFromApps: Set<String> = emptySet(),
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    val directShareTypes: DirectShareTypeConfig = DirectShareTypeConfig(),
    /** Empty by default. Only explicitly selected categories contribute fully-selected package targets. */
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
    /** Per-profile presentation-only AOSP surface filtering; never disables packages. */
    val surfaceFilters: SurfaceFilterConfig = SurfaceFilterConfig(),
    /**
     * Runtime-only component protection snapshot. It is carried in the atomic config so the
     * system_server guard/discovery hooks do not depend on RemotePreferences cache freshness.
     * RuleRepository deliberately does not restore this field into rule backups.
     */
    val rootDisabledComponents: Set<String>? = null,
    /** Additional Android user profiles. User 0 remains the legacy top-level configuration. */
    val userSpaces: Map<Int, UserSpaceRules> = emptyMap(),
    val modeOverrides: Map<IntentKind, DisplayMode> = emptyMap(),
    val disabledEntryGroups: Set<EntryGroup> = emptySet(),
    val disabledEntryKinds: Set<IntentKind> = emptySet()
) {
    /**
     * Pure Modes transition shared by repository updates and tests.
     * Null leaves the corresponding family unchanged; an empty set enables all.
     * Other cleanup rules, priorities and user profiles must remain intact.
     */
    fun withModeDisables(
        entryKinds: Set<IntentKind>?,
        surfaces: Set<SurfaceKind>?,
    ): ModuleConfig {
        if (entryKinds != null) require(entryKinds.all { it.isSelectableEntryKind() })
        val result = copy(
            disabledEntryKinds = entryKinds?.toSet() ?: disabledEntryKinds,
            disabledEntryGroups = if (entryKinds != null) emptySet() else disabledEntryGroups,
            surfaceFilters = if (surfaces == null) surfaceFilters
                else surfaceFilters.copy(disabledCleanupSurfaces = surfaces.toSet()),
        )
        return result.validated()
    }

    fun modeFor(kind: IntentKind): DisplayMode = modeOverrides[kind] ?: mode

    /**
     * Upgrade legacy MIME-specific entries to one decision per component and
     * entry action. OPEN, SEND and SEND_MULTIPLE remain distinct, but a selected
     * receiver is hidden for every MIME it supports in that action.
     *
     * Keep custom MIME definitions for discovery. Old typed priorities are
     * merged deterministically into the generic order only when no generic
     * ordering exists. Repeated validation must be a no-op.
     */
    fun unifiedMimeManagement(): ModuleConfig {
        val typedOpen = openTypes
        if (typedOpen.rules.isEmpty() && typedOpen.priorities.isEmpty() &&
            typedOpen.shareRules.isEmpty() && typedOpen.sharePriorities.isEmpty()) return this

        val migratedRules = linkedSetOf<ComponentRule>().apply {
            addAll(rules)
            typedOpen.rules.values.forEach { ids ->
                ids.mapNotNullTo(this, ComponentRule::fromId)
            }
            typedOpen.shareRules.values.forEach { groups ->
                groups.values.forEach { ids -> ids.mapNotNullTo(this, ComponentRule::fromId) }
            }
        }
        val mergedOrder = priorities.apps.toMutableMap()
        val oldOpenOrder = typedOpen.priorities.entries
            .sortedBy { it.key.ordinal }.flatMap { it.value }.distinct()
        val mergedOpen = (mergedOrder[IntentKind.OPEN].orEmpty() + oldOpenOrder)
            .distinct().take(200)
        if (mergedOpen.isNotEmpty()) mergedOrder[IntentKind.OPEN] = mergedOpen
        for (kind in listOf(IntentKind.SHARE, IntentKind.SHARE_MULTIPLE)) {
            val oldOrder = typedOpen.sharePriorities[kind].orEmpty().toSortedMap()
                .values.flatMap { it }.distinct()
            // Do not discard older per-MIME priorities when a generic order
            // already exists. Append only the missing package identities.
            val merged = (mergedOrder[kind].orEmpty() + oldOrder).distinct().take(200)
            if (merged.isNotEmpty()) mergedOrder[kind] = merged
        }
        return copy(
            rules = migratedRules,
            priorities = priorities.copy(apps = mergedOrder),
            openTypes = typedOpen.copy(
                rules = emptyMap(),
                priorities = emptyMap(),
                shareRules = emptyMap(),
                sharePriorities = emptyMap(),
            )
        )
    }

    fun validated(): ModuleConfig {
        val unified = unifiedMimeManagement()
        if (unified != this) return unified.validated()
        require(rules.size <= 20_000 && rules.all(ComponentRule::isValid))
        require(userSpaces.size <= 16 && userSpaces.keys.all { it in 1..99999 })
        require(disabledEntryGroups.size <= EntryGroup.entries.size)
        require(disabledEntryKinds.size <= IntentKind.entries.size && disabledEntryKinds.all { it.isSelectableEntryKind() })
        require(modeOverrides.size <= IntentKind.entries.size && modeOverrides.keys.all { it.isSelectableEntryKind() })
        val validatedSpaces = userSpaces.mapValues { (_, space) -> space.validated() }
        val cleanRules = rules.asSequence()
            .filter { it.kind.isSelectableEntryKind() || it.kind == IntentKind.SHORTCUT_ITEM }
            .map { rule ->
                val normalized = SyntheticEntryKeys.normalizePackageScopedRule(rule)
                ComponentRule.fromId(normalized.id) ?: normalized
            }
            .toCollection(linkedSetOf())
        val cleanPriorities = priorities.validated()
        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))
        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(PackageIdentity::valid))
        require(rootDisabledComponents == null ||
            (rootDisabledComponents.size <= 20_000 && rootDisabledComponents.all(::validRootComponentKey)))

        // Android role pickers expose package identities. Collapse historical component rules for
        // every package-scoped surface to one stable synthetic key so manager discovery, resolver
        // filtering and RoleController cannot disagree about what one checkbox means.
        val packageScopedIds = rules.asSequence()
            .filter { it.kind.isPackageScopedEntry() }
            .map { rule -> rule.id to SyntheticEntryKeys.packageScopedRule(rule.kind, rule.packageName).id }
            .filter { (oldId, newId) -> oldId != newId }
            .toMap()

        // Old per-host Browser rules represented Deep Links before DEEP_LINK became its own kind.
        // Copy their titles to the migrated Deep Link component before the generic Browser role
        // migration removes the obsolete component-scoped Browser title.
        val legacyDomainIds = browserLinks.rules.values.flatten().mapNotNull { id ->
            val parsed = ComponentRule.fromId(id) ?: return@mapNotNull null
            if (parsed.kind == IntentKind.BROWSER) parsed.id to parsed.copy(kind = IntentKind.DEEP_LINK).id
            else null
        }.toMap()

        val migratedTitles = cleanPriorities.titles.toMutableMap()
        legacyDomainIds.forEach { (oldId, newId) ->
            migratedTitles[oldId]?.let { title ->
                if (newId !in migratedTitles) migratedTitles[newId] = title
            }
        }
        packageScopedIds.forEach { (oldId, newId) ->
            migratedTitles[oldId]?.let { title ->
                if (newId !in migratedTitles) migratedTitles[newId] = title
                migratedTitles.remove(oldId)
            }
        }
        val migratedPriorities = if (migratedTitles == cleanPriorities.titles) cleanPriorities
        else cleanPriorities.copy(titles = migratedTitles).validated()

        val cleanDirectShareTypes = directShareTypes.validated()
        val cleanOpenTypes = openTypes.validated()
        val cleanBrowserLinks = browserLinks.validated()
        val cleanSurfaceFilters = surfaceFilters.validated()
        val cleanVisibilityCompat = visibilityCompat.validated()
        return if (
            cleanRules == rules &&
            migratedPriorities == priorities &&
            cleanOpenTypes == openTypes &&
            cleanDirectShareTypes == directShareTypes &&
            cleanBrowserLinks == browserLinks &&
            cleanSurfaceFilters == surfaceFilters &&
            cleanVisibilityCompat == visibilityCompat &&
            validatedSpaces == userSpaces
        ) this else copy(
            rules = cleanRules,
            priorities = migratedPriorities,
            openTypes = cleanOpenTypes,
            directShareTypes = cleanDirectShareTypes,
            browserLinks = cleanBrowserLinks,
            surfaceFilters = cleanSurfaceFilters,
            visibilityCompat = cleanVisibilityCompat,
            userSpaces = validatedSpaces
        )
    }
}

private fun validRootComponentKey(value: String): Boolean {
    val parts = value.split('|', limit = 3)
    if (parts.size != 3) return false
    val user = parts[0].toIntOrNull() ?: return false
    return ComponentStatePolicy.valid(parts[1], parts[2], user)
}
