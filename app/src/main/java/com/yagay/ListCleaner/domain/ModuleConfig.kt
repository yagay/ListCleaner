package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One remote preference value prevents mixed old/new fields during backup restore. */
@Serializable
data class ModuleConfig(
    val rules: Set<ComponentRule>,
    val mode: DisplayMode,
    val priorities: PriorityConfig,
    val diagnostic: Boolean,
    // Supplied by our own app via framework-owned remote preferences, never by an Intent extra.
    val managerAppId: Int = -1,
    /** Source-compatibility only. Never serialized or consumed by current runtime behavior. */
    @Transient val tiles: TileConfig = TileConfig(),
    // Apps in this list are callers from which selected target packages may be hidden at system_server.
    // They do NOT need to be added to the LSPosed module scope.
    val hiddenFromApps: Set<String> = emptySet(),
    /** Source-compatibility only. Never serialized or consumed by current runtime behavior. */
    @Transient val defaultOpen: DefaultOpenConfig = DefaultOpenConfig(),
    val openTypes: OpenTypeConfig = OpenTypeConfig(),
    /** Empty by default. Only explicitly selected categories contribute fully-selected package targets. */
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
    val browserLinks: BrowserLinkConfig = BrowserLinkConfig()
) {
    fun validated(): ModuleConfig {
        require(rules.size <= 20_000 && rules.all(ComponentRule::isValid))
        val cleanPriorities = priorities.validated()
        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))
        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(PackageIdentity::valid))

        val legacyDomainIds = browserLinks.rules.values.flatten().mapNotNull { id ->
            val parsed = ComponentRule.fromId(id) ?: return@mapNotNull null
            if (parsed.kind == IntentKind.BROWSER) parsed.id to parsed.copy(kind = IntentKind.DEEP_LINK).id
            else null
        }.toMap()
        val migratedTitles = cleanPriorities.titles.toMutableMap()
        legacyDomainIds.forEach { (oldId, newId) ->
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
            migratedPriorities == priorities &&
            cleanOpenTypes == openTypes &&
            cleanBrowserLinks == browserLinks &&
            cleanVisibilityCompat == visibilityCompat
        ) this else copy(
            priorities = migratedPriorities,
            openTypes = cleanOpenTypes,
            browserLinks = cleanBrowserLinks,
            visibilityCompat = cleanVisibilityCompat
        )
    }
}
