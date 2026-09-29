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
    /** Empty by default. Only explicitly selected categories contribute fully-selected package targets. */
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig(),
    /**
     * Runtime-only component protection snapshot. It is carried in the atomic config so the
     * system_server guard/discovery hooks do not depend on RemotePreferences cache freshness.
     * RuleRepository deliberately does not restore this field into rule backups.
     */
    val rootDisabledComponents: Set<String>? = null
) {
    fun validated(): ModuleConfig {
        require(rules.size <= 20_000 && rules.all(ComponentRule::isValid))
        val cleanRules = rules.asSequence()
            .filter { it.kind.isSelectableEntryKind() }
            .map(SyntheticEntryKeys::normalizePackageScopedRule)
            .toCollection(linkedSetOf())
        val cleanPriorities = priorities.validated()
        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))
        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(PackageIdentity::valid))
        require(rootDisabledComponents == null ||
            (rootDisabledComponents.size <= 20_000 && rootDisabledComponents.all(::validRootComponentKey)))

        // Assistant is selected at package level by Android RoleController. Migrate historical
        // component-level Assistant rules/titles to the single stable package-level identity.
        val assistantIds = rules.asSequence()
            .filter { it.kind == IntentKind.ASSISTANT }
            .map { it.id to SyntheticEntryKeys.assistantPackageRule(it.packageName).id }
            .filter { (oldId, newId) -> oldId != newId }
            .toMap()

        val legacyDomainIds = browserLinks.rules.values.flatten().mapNotNull { id ->
            val parsed = ComponentRule.fromId(id) ?: return@mapNotNull null
            if (parsed.kind == IntentKind.BROWSER) parsed.id to parsed.copy(kind = IntentKind.DEEP_LINK).id
            else null
        }.toMap()
        val migratedTitles = cleanPriorities.titles.toMutableMap()
        // Browser/Deep Link compatibility historically copies the title while retaining the old
        // Browser key; do not change that established backup/UI behavior.
        legacyDomainIds.forEach { (oldId, newId) ->
            migratedTitles[oldId]?.let { title ->
                if (newId !in migratedTitles) migratedTitles[newId] = title
            }
        }
        // Assistant component identities are retired, so move the title to the package key and
        // remove the obsolete component key to avoid duplicate unavailable entries after upgrade.
        assistantIds.forEach { (oldId, newId) ->
            migratedTitles[oldId]?.let { title ->
                if (newId !in migratedTitles) migratedTitles[newId] = title
                migratedTitles.remove(oldId)
            }
        }
        val migratedPriorities = if (migratedTitles == cleanPriorities.titles) cleanPriorities
        else cleanPriorities.copy(titles = migratedTitles).validated()

        val cleanOpenTypes = openTypes.validated()
        val cleanBrowserLinks = browserLinks.validated()
        val cleanVisibilityCompat = visibilityCompat.validated()
        return if (
            cleanRules == rules &&
            migratedPriorities == priorities &&
            cleanOpenTypes == openTypes &&
            cleanBrowserLinks == browserLinks &&
            cleanVisibilityCompat == visibilityCompat
        ) this else copy(
            rules = cleanRules,
            priorities = migratedPriorities,
            openTypes = cleanOpenTypes,
            browserLinks = cleanBrowserLinks,
            visibilityCompat = cleanVisibilityCompat
        )
    }
}

private fun validRootComponentKey(value: String): Boolean {
    val parts = value.split('|', limit = 3)
    if (parts.size != 3) return false
    val user = parts[0].toIntOrNull() ?: return false
    return ComponentStatePolicy.valid(parts[1], parts[2], user)
}
