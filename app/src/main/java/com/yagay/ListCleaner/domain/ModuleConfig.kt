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
    // Apps in this list are callers from which selected OPEN target packages will be hidden at system_server.
    // They do NOT need to be added to the LSPosed module scope.
    val hiddenFromApps: Set<String> = emptySet(),
    /** Source-compatibility only. Never serialized or consumed by current runtime behavior. */
    @Transient val defaultOpen: DefaultOpenConfig = DefaultOpenConfig(),
    val openTypes: OpenTypeConfig = OpenTypeConfig()
) {
    fun validated(): ModuleConfig {
        require(rules.size <= 20_000 && rules.all(ComponentRule::isValid))
        priorities.validated()
        require(managerAppId == -1 || ManagerIdentity.valid(managerAppId))
        require(hiddenFromApps.size <= 2_000 && hiddenFromApps.all(::validPackageName))
        openTypes.validated()
        return this
    }

    private fun validPackageName(value: String): Boolean =
        value.isNotBlank() && value.length <= 255 && value.none { it.isWhitespace() || it.isISOControl() || it == '|' }
}
