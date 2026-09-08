package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** Categories whose fully-selected app rows may contribute package-level visibility hiding. */
@Serializable
enum class VisibilityScope {
    ALL,
    SHARE,
    SHARE_MULTIPLE,
    OPEN,
    BROWSER,
    PROCESS_TEXT;

    fun matches(kind: IntentKind): Boolean = this == ALL || name == kind.name

    companion object {
        fun forKind(kind: IntentKind): VisibilityScope = valueOf(kind.name)
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
            require(packages.all(::validPackageName))
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

    private fun validPackageName(value: String): Boolean =
        value.isNotBlank() && value.length <= 255 && value.none { it.isWhitespace() || it.isISOControl() || it == '|' }
}

/**
 * An app is eligible for package-level compatibility hiding only when every currently catalogued
 * component in that top-level category is selected. This intentionally rejects tri-state/partial rows.
 */
fun deriveFullySelectedPackages(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>
): Map<VisibilityScope, Set<String>> = IntentKind.entries.associate { kind ->
    val packages = candidates.asSequence()
        .filter { it.isCatalogCandidate && it.rule.kind == kind }
        .groupBy { it.rule.packageName }
        .filterValues { items -> items.isNotEmpty() && items.all { it.rule in selected } }
        .keys
        .toSet()
    VisibilityScope.forKind(kind) to packages
}.filterValues { it.isNotEmpty() }
