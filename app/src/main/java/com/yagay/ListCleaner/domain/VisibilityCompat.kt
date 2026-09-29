package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** Categories whose fully-selected app rows may contribute package-level visibility hiding. */
@Serializable
enum class VisibilityScope {
    ALL,
    SHARE,
    SHARE_MULTIPLE,
    SEND_TO,
    OPEN,
    BROWSER,
    DEEP_LINK,
    DIAL,
    GET_CONTENT,
    CAPTURE_IMAGE,
    CAPTURE_VIDEO,
    RECORD_AUDIO,
    PROCESS_TEXT;

    fun matches(kind: IntentKind): Boolean = this == ALL || name == kind.name

    companion object {
        /** Special system-managed surfaces intentionally do not participate in package visibility hiding. */
        fun forKind(kind: IntentKind): VisibilityScope? = entries.firstOrNull { it.name == kind.name }
    }
}

@Serializable
data class VisibilityCompatConfig(
    /** User-selected categories. Empty by default: package-level compatibility hiding is disabled. */
    val scopes: Set<VisibilityScope> = emptySet(),
    /** Derived by the manager from current catalog + rule state. Partial app selections are excluded. */
    val fullPackages: Map<VisibilityScope, Set<String>> = emptyMap()
) {
    fun validated(): VisibilityCompatConfig {
        require(fullPackages.size <= VisibilityScope.entries.size)
        fullPackages.forEach { (scope, packages) ->
            require(scope != VisibilityScope.ALL) { "ALL is derived from concrete categories" }
            require(packages.size <= 20_000)
            require(packages.all(PackageIdentity::valid))
        }
        return this
    }

    fun activePackages(): Set<String> {
        if (scopes.isEmpty()) return emptySet()
        val concrete = if (VisibilityScope.ALL in scopes) {
            VisibilityScope.entries.filter { it != VisibilityScope.ALL }
        } else scopes.filter { it != VisibilityScope.ALL }
        return concrete.asSequence().flatMap { fullPackages[it].orEmpty().asSequence() }.toSet()
    }
}

/**
 * An app is eligible for package-level compatibility hiding only when every currently catalogued
 * logical candidate in that top-level category is selected. System-managed shortcut/provider
 * surfaces are excluded because hiding their owning package would be much broader than hiding them.
 */
fun deriveFullySelectedPackages(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>
): Map<VisibilityScope, Set<String>> {
    val logicalCandidates = normalizeLogicalCandidates(candidates)
    return IntentKind.entries.mapNotNull { kind ->
        val scope = VisibilityScope.forKind(kind) ?: return@mapNotNull null
        val packages = logicalCandidates.asSequence()
            .filter { it.isCatalogCandidate && it.rule.kind == kind }
            .groupBy { it.rule.packageName }
            .filterValues { items -> items.isNotEmpty() && items.all { it.rule in selected } }
            .keys
            .toSet()
        scope to packages
    }.toMap().filterValues { it.isNotEmpty() }
}
