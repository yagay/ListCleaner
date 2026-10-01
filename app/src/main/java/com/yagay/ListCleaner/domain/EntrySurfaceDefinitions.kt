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
    ACCESSIBILITY_MANAGER,
    ACCESSIBILITY_SHORTCUT_ACTIVITY,
    INPUT_METHOD_MANAGER,
    PRINT_MANAGER,
    SETTINGS_VPN,
    CREDENTIAL_MANAGER,
    AUTOFILL_PROVIDER_SETTINGS,
    NFC_CARD_EMULATION,
    DOCUMENT_PROVIDER,
    OBSERVED_SHORTCUT,
    OBSERVED_DIRECT_SHARE,
}

/** Android subsystem whose final list is the authority for one List Cleaner category. */
enum class EntryAuthority {
    PACKAGE_MANAGER,
    ROLE_CONTROLLER,
    ACCESSIBILITY_MANAGER,
    INPUT_METHOD_MANAGER,
    PRINT_MANAGER,
    SETTINGS_VPN,
    CREDENTIAL_MANAGER,
    COMBINED_PROVIDER_SETTINGS,
    NFC_CARD_EMULATION,
    SHORTCUT_SERVICE,
}

/**
 * Defines whether discovery itself is enough to show a candidate.
 *
 * ACTIVE_SCAN: the active discovery source is sufficient for the normal list.
 * AUTHORITY_UPGRADE: active fallback candidates stay visible while a manager/role observation may
 * later confirm the exact Android authority result.
 * OBSERVATION_ONLY: there is no meaningful active fallback; rows appear only after runtime evidence.
 */
enum class EntryAvailabilityMode {
    ACTIVE_SCAN,
    AUTHORITY_UPGRADE,
    OBSERVATION_ONLY,
}

data class EntrySurfaceDefinition(
    val kind: IntentKind,
    val identity: EntryIdentityScope,
    val authority: EntryAuthority,
    val discoverySources: Set<EntryDiscoverySource>,
    val availabilityMode: EntryAvailabilityMode = EntryAvailabilityMode.ACTIVE_SCAN,
)

/**
 * Single contract for what one row means, where Android gets it from and where candidates come from.
 * PackageManager/AppOps fallbacks remain visible for AUTHORITY_UPGRADE surfaces; they are marked as
 * provisional until the owning Android subsystem produces a final observation.
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
        put(
            kind,
            EntrySurfaceDefinition(
                kind,
                EntryIdentityScope.COMPONENT,
                EntryAuthority.PACKAGE_MANAGER,
                setOf(EntryDiscoverySource.RESOLVER_ACTIVITY),
            )
        )
    }

    put(
        IntentKind.BROWSER,
        EntrySurfaceDefinition(
            IntentKind.BROWSER,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.ROLE_CONTROLLER,
            setOf(EntryDiscoverySource.BROWSER_ACTIVITY, EntryDiscoverySource.RESOLVER_ACTIVITY),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.DEEP_LINK,
        EntrySurfaceDefinition(
            IntentKind.DEEP_LINK,
            EntryIdentityScope.COMPONENT,
            EntryAuthority.PACKAGE_MANAGER,
            setOf(EntryDiscoverySource.RESOLVER_ACTIVITY, EntryDiscoverySource.APP_LINK_DECLARATION),
        )
    )
    put(
        IntentKind.HOME,
        EntrySurfaceDefinition(
            IntentKind.HOME,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.ROLE_CONTROLLER,
            setOf(EntryDiscoverySource.HOME_ACTIVITY),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.ASSISTANT,
        EntrySurfaceDefinition(
            IntentKind.ASSISTANT,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.ROLE_CONTROLLER,
            setOf(EntryDiscoverySource.ASSISTANT_ACTIVITY, EntryDiscoverySource.VOICE_INTERACTION_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.DIRECT_SHARE,
        EntrySurfaceDefinition(
            IntentKind.DIRECT_SHARE,
            EntryIdentityScope.RUNTIME_ITEM,
            EntryAuthority.SHORTCUT_SERVICE,
            setOf(EntryDiscoverySource.OBSERVED_DIRECT_SHARE),
            EntryAvailabilityMode.OBSERVATION_ONLY,
        )
    )
    put(
        IntentKind.SHORTCUT_ITEM,
        EntrySurfaceDefinition(
            IntentKind.SHORTCUT_ITEM,
            EntryIdentityScope.RUNTIME_ITEM,
            EntryAuthority.SHORTCUT_SERVICE,
            setOf(EntryDiscoverySource.OBSERVED_SHORTCUT),
            EntryAvailabilityMode.OBSERVATION_ONLY,
        )
    )
    put(
        IntentKind.DOCUMENT_PROVIDER,
        EntrySurfaceDefinition(
            IntentKind.DOCUMENT_PROVIDER,
            EntryIdentityScope.COMPONENT,
            EntryAuthority.PACKAGE_MANAGER,
            setOf(EntryDiscoverySource.DOCUMENT_PROVIDER),
        )
    )

    put(
        IntentKind.INPUT_METHOD,
        EntrySurfaceDefinition(
            IntentKind.INPUT_METHOD,
            EntryIdentityScope.COMPONENT,
            EntryAuthority.INPUT_METHOD_MANAGER,
            setOf(EntryDiscoverySource.INPUT_METHOD_MANAGER, EntryDiscoverySource.SYSTEM_SERVICE),
        )
    )
    put(
        IntentKind.ACCESSIBILITY,
        EntrySurfaceDefinition(
            IntentKind.ACCESSIBILITY,
            EntryIdentityScope.COMPONENT,
            EntryAuthority.ACCESSIBILITY_MANAGER,
            setOf(
                EntryDiscoverySource.ACCESSIBILITY_MANAGER,
                EntryDiscoverySource.ACCESSIBILITY_SHORTCUT_ACTIVITY,
                EntryDiscoverySource.SYSTEM_SERVICE,
            ),
        )
    )
    put(
        IntentKind.PRINT,
        EntrySurfaceDefinition(
            IntentKind.PRINT,
            EntryIdentityScope.COMPONENT,
            EntryAuthority.PRINT_MANAGER,
            setOf(EntryDiscoverySource.PRINT_MANAGER, EntryDiscoverySource.SYSTEM_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.VPN,
        EntrySurfaceDefinition(
            IntentKind.VPN,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.SETTINGS_VPN,
            setOf(EntryDiscoverySource.SETTINGS_VPN, EntryDiscoverySource.SYSTEM_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.CREDENTIAL_PROVIDER,
        EntrySurfaceDefinition(
            IntentKind.CREDENTIAL_PROVIDER,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.CREDENTIAL_MANAGER,
            setOf(EntryDiscoverySource.CREDENTIAL_MANAGER, EntryDiscoverySource.SYSTEM_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.AUTOFILL,
        EntrySurfaceDefinition(
            IntentKind.AUTOFILL,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.COMBINED_PROVIDER_SETTINGS,
            setOf(EntryDiscoverySource.AUTOFILL_PROVIDER_SETTINGS, EntryDiscoverySource.SYSTEM_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.NFC_HCE,
        EntrySurfaceDefinition(
            IntentKind.NFC_HCE,
            EntryIdentityScope.COMPONENT,
            EntryAuthority.NFC_CARD_EMULATION,
            setOf(EntryDiscoverySource.NFC_CARD_EMULATION, EntryDiscoverySource.SYSTEM_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )
    put(
        IntentKind.CALL_SCREENING,
        EntrySurfaceDefinition(
            IntentKind.CALL_SCREENING,
            EntryIdentityScope.PACKAGE,
            EntryAuthority.ROLE_CONTROLLER,
            setOf(EntryDiscoverySource.SYSTEM_SERVICE),
            EntryAvailabilityMode.AUTHORITY_UPGRADE,
        )
    )

    setOf(
        IntentKind.NOTIFICATION_LISTENER,
        IntentKind.WALLPAPER,
        IntentKind.DREAM,
    ).forEach { kind ->
        put(
            kind,
            EntrySurfaceDefinition(
                kind,
                EntryIdentityScope.COMPONENT,
                EntryAuthority.PACKAGE_MANAGER,
                setOf(EntryDiscoverySource.SYSTEM_SERVICE),
            )
        )
    }
}

fun IntentKind.surfaceDefinition(): EntrySurfaceDefinition? = ENTRY_SURFACE_DEFINITIONS[this]

fun IntentKind.isPackageScopedEntry(): Boolean =
    surfaceDefinition()?.identity == EntryIdentityScope.PACKAGE

fun IntentKind.entryAuthority(): EntryAuthority? = surfaceDefinition()?.authority

fun IntentKind.entryAvailabilityMode(): EntryAvailabilityMode? = surfaceDefinition()?.availabilityMode

/**
 * Collapse physical components to the logical row identity before they enter UI state.
 * Android Role, final Settings VPN and Android 16's combined autofill/credential picker are package rows.
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
