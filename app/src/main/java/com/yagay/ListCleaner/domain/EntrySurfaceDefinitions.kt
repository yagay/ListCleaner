package com.yagay.ListCleaner.domain

/** Stable logical identity used by both manager discovery and runtime filtering. */
enum class EntryIdentityScope {
    COMPONENT,
    PACKAGE,
    RUNTIME_ITEM,
}

/** Authoritative or evidence-producing sources used to populate one management surface. */
enum class EntryDiscoverySource {
    RESOLVER_ACTIVITY,
    APP_LINK_DECLARATION,
    ASSISTANT_ACTIVITY,
    VOICE_INTERACTION_SERVICE,
    HOME_ACTIVITY,
    BROWSER_ACTIVITY,
    SYSTEM_SERVICE,
    ACCESSIBILITY_SHORTCUT_ACTIVITY,
    DOCUMENT_PROVIDER,
    OBSERVED_SHORTCUT,
    OBSERVED_DIRECT_SHARE,
}

data class EntrySurfaceDefinition(
    val kind: IntentKind,
    val identity: EntryIdentityScope,
    val discoverySources: Set<EntryDiscoverySource>,
)

/**
 * Single manager-side contract for what one row means and where its candidates come from.
 * Runtime paths live in [ENTRY_RUNTIME_DEFINITIONS]; tests cross-check both registries.
 */
val ENTRY_SURFACE_DEFINITIONS: Map<IntentKind, EntrySurfaceDefinition> = buildMap {
    val resolverComponents = setOf(
        IntentKind.SHARE,
        IntentKind.SHARE_MULTIPLE,
        IntentKind.SEND_TO,
        IntentKind.OPEN,
        IntentKind.DIAL,
        IntentKind.GET_CONTENT,
        IntentKind.OPEN_DOCUMENT,
        IntentKind.CREATE_DOCUMENT,
        IntentKind.CAPTURE_IMAGE,
        IntentKind.CAPTURE_VIDEO,
        IntentKind.RECORD_AUDIO,
        IntentKind.PROCESS_TEXT,
    )
    resolverComponents.forEach { kind ->
        put(kind, EntrySurfaceDefinition(kind, EntryIdentityScope.COMPONENT, setOf(EntryDiscoverySource.RESOLVER_ACTIVITY)))
    }

    put(
        IntentKind.BROWSER,
        EntrySurfaceDefinition(
            IntentKind.BROWSER,
            EntryIdentityScope.PACKAGE,
            setOf(EntryDiscoverySource.BROWSER_ACTIVITY, EntryDiscoverySource.RESOLVER_ACTIVITY),
        )
    )
    put(
        IntentKind.DEEP_LINK,
        EntrySurfaceDefinition(
            IntentKind.DEEP_LINK,
            EntryIdentityScope.COMPONENT,
            setOf(EntryDiscoverySource.RESOLVER_ACTIVITY, EntryDiscoverySource.APP_LINK_DECLARATION),
        )
    )
    put(
        IntentKind.HOME,
        EntrySurfaceDefinition(IntentKind.HOME, EntryIdentityScope.PACKAGE, setOf(EntryDiscoverySource.HOME_ACTIVITY))
    )
    put(
        IntentKind.ASSISTANT,
        EntrySurfaceDefinition(
            IntentKind.ASSISTANT,
            EntryIdentityScope.PACKAGE,
            setOf(EntryDiscoverySource.ASSISTANT_ACTIVITY, EntryDiscoverySource.VOICE_INTERACTION_SERVICE),
        )
    )
    put(
        IntentKind.DIRECT_SHARE,
        EntrySurfaceDefinition(
            IntentKind.DIRECT_SHARE,
            EntryIdentityScope.RUNTIME_ITEM,
            setOf(EntryDiscoverySource.OBSERVED_DIRECT_SHARE),
        )
    )
    put(
        IntentKind.SHORTCUT_ITEM,
        EntrySurfaceDefinition(
            IntentKind.SHORTCUT_ITEM,
            EntryIdentityScope.RUNTIME_ITEM,
            setOf(EntryDiscoverySource.OBSERVED_SHORTCUT),
        )
    )
    put(
        IntentKind.DOCUMENT_PROVIDER,
        EntrySurfaceDefinition(
            IntentKind.DOCUMENT_PROVIDER,
            EntryIdentityScope.COMPONENT,
            setOf(EntryDiscoverySource.DOCUMENT_PROVIDER),
        )
    )

    SYSTEM_SERVICE_ENTRY_DEFINITIONS.map { it.kind }.distinct().forEach { kind ->
        val sources = if (kind == IntentKind.ACCESSIBILITY) {
            setOf(EntryDiscoverySource.SYSTEM_SERVICE, EntryDiscoverySource.ACCESSIBILITY_SHORTCUT_ACTIVITY)
        } else {
            setOf(EntryDiscoverySource.SYSTEM_SERVICE)
        }
        val identity = if (kind == IntentKind.CALL_SCREENING) EntryIdentityScope.PACKAGE
        else EntryIdentityScope.COMPONENT
        put(kind, EntrySurfaceDefinition(kind, identity, sources))
    }
}

fun IntentKind.surfaceDefinition(): EntrySurfaceDefinition? = ENTRY_SURFACE_DEFINITIONS[this]

fun IntentKind.isPackageScopedEntry(): Boolean =
    surfaceDefinition()?.identity == EntryIdentityScope.PACKAGE

/**
 * Collapse physical components to the logical row identity before they enter UI state.
 * This is especially important for Android Role surfaces where one package can expose multiple
 * qualifying components but the system picker still shows one package.
 */
fun normalizeLogicalCandidates(items: List<ComponentCandidate>): List<ComponentCandidate> {
    if (items.isEmpty()) return items
    return items.map { item ->
        if (!item.rule.kind.isPackageScopedEntry()) {
            item
        } else {
            item.copy(
                rule = SyntheticEntryKeys.packageScopedRule(item.rule.kind, item.rule.packageName),
                activityLabel = item.appLabel,
            )
        }
    }.groupBy { it.rule.id }.values.map { matches ->
        val first = matches.firstOrNull { it.isCatalogCandidate } ?: matches.first()
        first.copy(
            evidence = matches.flatMap { it.evidence }.distinct().take(48),
            restricted = matches.all { it.restricted },
            unavailable = matches.all { it.unavailable },
            broadMatch = matches.all { it.broadMatch },
            browserHosts = matches.flatMap { it.browserHosts }.toSet(),
        )
    }.sortedWith(compareBy({ it.rule.kind.ordinal }, { it.appLabel.lowercase() }, { it.rule.id }))
}
