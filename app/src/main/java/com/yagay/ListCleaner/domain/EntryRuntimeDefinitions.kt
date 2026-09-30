package com.yagay.ListCleaner.domain

/** Runtime surfaces that can independently decide which entries are shown to the user. */
enum class EntryRuntimePath {
    RESOLVER_ACTIVITY,
    ROLE_CONTROLLER,
    DIRECT_SHARE_CHOOSER,
    DIRECT_SHARE_EMBEDDED,
    SHORTCUT_SERVICE,
    PACKAGE_MANAGER_ACTIVITY,
    PACKAGE_MANAGER_PROVIDER,
    PACKAGE_MANAGER_SERVICE,
    ACCESSIBILITY_MANAGER,
    INPUT_METHOD_MANAGER,
    PRINT_MANAGER,
    CREDENTIAL_MANAGER,
    COMBINED_PROVIDER_SETTINGS,
    SETTINGS_AUTOFILL_PICKER,
    SETTINGS_VPN,
    /** Historical/raw source retained for diagnostics only; it is not the final Settings VPN list. */
    VPN_APP_OPS,
    NFC_CARD_EMULATION,
}

enum class EmptyResultBehavior {
    ALLOW_EMPTY,
    RESTORE_ORIGINAL,
}

data class EntryRuntimeDefinition(
    val kind: IntentKind,
    val expectedPaths: Set<EntryRuntimePath>,
    val coveredPaths: Set<EntryRuntimePath>,
    val emptyBehavior: Map<EntryRuntimePath, EmptyResultBehavior>,
    val roleName: String? = null,
    val systemCallerBypassPossible: Boolean = false,
) {
    val missingPaths: Set<EntryRuntimePath> get() = expectedPaths - coveredPaths
}

/** Final Android authority coverage for every selectable entry kind. */
val ENTRY_RUNTIME_DEFINITIONS: Map<IntentKind, EntryRuntimeDefinition> = buildMap {
    val resolverOnly = setOf(
        IntentKind.SHARE,
        IntentKind.SHARE_MULTIPLE,
        IntentKind.SEND_TO,
        IntentKind.OPEN,
        IntentKind.DEEP_LINK,
        IntentKind.DIAL,
        IntentKind.GET_CONTENT,
        IntentKind.OPEN_DOCUMENT,
        IntentKind.CREATE_DOCUMENT,
        IntentKind.CAPTURE_IMAGE,
        IntentKind.CAPTURE_VIDEO,
        IntentKind.RECORD_AUDIO,
        IntentKind.PROCESS_TEXT,
    )
    resolverOnly.forEach { kind ->
        put(
            kind,
            EntryRuntimeDefinition(
                kind,
                setOf(EntryRuntimePath.RESOLVER_ACTIVITY),
                setOf(EntryRuntimePath.RESOLVER_ACTIVITY),
                mapOf(
                    EntryRuntimePath.RESOLVER_ACTIVITY to if (kind == IntentKind.PROCESS_TEXT) {
                        EmptyResultBehavior.ALLOW_EMPTY
                    } else EmptyResultBehavior.RESTORE_ORIGINAL
                ),
            )
        )
    }

    fun role(kind: IntentKind, name: String, extra: Set<EntryRuntimePath> = emptySet()) {
        val paths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY, EntryRuntimePath.ROLE_CONTROLLER) + extra
        put(
            kind,
            EntryRuntimeDefinition(
                kind,
                paths,
                paths,
                paths.associateWith { path ->
                    if (path == EntryRuntimePath.RESOLVER_ACTIVITY) EmptyResultBehavior.RESTORE_ORIGINAL
                    else EmptyResultBehavior.ALLOW_EMPTY
                },
                roleName = name,
            )
        )
    }
    role(
        IntentKind.ASSISTANT,
        "android.app.role.ASSISTANT",
        setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE),
    )
    role(IntentKind.HOME, "android.app.role.HOME")
    role(IntentKind.BROWSER, "android.app.role.BROWSER")

    put(
        IntentKind.CALL_SCREENING,
        EntryRuntimeDefinition(
            IntentKind.CALL_SCREENING,
            setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE, EntryRuntimePath.ROLE_CONTROLLER),
            setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE, EntryRuntimePath.ROLE_CONTROLLER),
            mapOf(
                EntryRuntimePath.PACKAGE_MANAGER_SERVICE to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.ROLE_CONTROLLER to EmptyResultBehavior.ALLOW_EMPTY,
            ),
            roleName = "android.app.role.CALL_SCREENING",
        )
    )

    put(
        IntentKind.DIRECT_SHARE,
        EntryRuntimeDefinition(
            IntentKind.DIRECT_SHARE,
            setOf(EntryRuntimePath.DIRECT_SHARE_CHOOSER, EntryRuntimePath.DIRECT_SHARE_EMBEDDED),
            setOf(EntryRuntimePath.DIRECT_SHARE_CHOOSER, EntryRuntimePath.DIRECT_SHARE_EMBEDDED),
            mapOf(
                EntryRuntimePath.DIRECT_SHARE_CHOOSER to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.DIRECT_SHARE_EMBEDDED to EmptyResultBehavior.ALLOW_EMPTY,
            ),
        )
    )
    put(
        IntentKind.SHORTCUT_ITEM,
        EntryRuntimeDefinition(
            IntentKind.SHORTCUT_ITEM,
            setOf(EntryRuntimePath.SHORTCUT_SERVICE),
            setOf(EntryRuntimePath.SHORTCUT_SERVICE),
            mapOf(EntryRuntimePath.SHORTCUT_SERVICE to EmptyResultBehavior.ALLOW_EMPTY),
        )
    )
    put(
        IntentKind.DOCUMENT_PROVIDER,
        EntryRuntimeDefinition(
            IntentKind.DOCUMENT_PROVIDER,
            setOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER),
            setOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER),
            mapOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER to EmptyResultBehavior.ALLOW_EMPTY),
            systemCallerBypassPossible = true,
        )
    )

    put(
        IntentKind.INPUT_METHOD,
        EntryRuntimeDefinition(
            IntentKind.INPUT_METHOD,
            setOf(EntryRuntimePath.INPUT_METHOD_MANAGER),
            setOf(EntryRuntimePath.INPUT_METHOD_MANAGER),
            mapOf(EntryRuntimePath.INPUT_METHOD_MANAGER to EmptyResultBehavior.ALLOW_EMPTY),
        )
    )
    put(
        IntentKind.ACCESSIBILITY,
        EntryRuntimeDefinition(
            IntentKind.ACCESSIBILITY,
            setOf(EntryRuntimePath.ACCESSIBILITY_MANAGER, EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY),
            setOf(EntryRuntimePath.ACCESSIBILITY_MANAGER, EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY),
            mapOf(
                EntryRuntimePath.ACCESSIBILITY_MANAGER to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY to EmptyResultBehavior.ALLOW_EMPTY,
            ),
        )
    )
    put(
        IntentKind.PRINT,
        EntryRuntimeDefinition(
            IntentKind.PRINT,
            setOf(EntryRuntimePath.PRINT_MANAGER),
            setOf(EntryRuntimePath.PRINT_MANAGER),
            mapOf(EntryRuntimePath.PRINT_MANAGER to EmptyResultBehavior.ALLOW_EMPTY),
        )
    )
    put(
        IntentKind.VPN,
        EntryRuntimeDefinition(
            IntentKind.VPN,
            setOf(EntryRuntimePath.SETTINGS_VPN),
            setOf(EntryRuntimePath.SETTINGS_VPN),
            mapOf(EntryRuntimePath.SETTINGS_VPN to EmptyResultBehavior.ALLOW_EMPTY),
        )
    )
    put(
        IntentKind.AUTOFILL,
        EntryRuntimeDefinition(
            IntentKind.AUTOFILL,
            setOf(
                EntryRuntimePath.COMBINED_PROVIDER_SETTINGS,
                EntryRuntimePath.SETTINGS_AUTOFILL_PICKER,
            ),
            setOf(
                EntryRuntimePath.COMBINED_PROVIDER_SETTINGS,
                EntryRuntimePath.SETTINGS_AUTOFILL_PICKER,
            ),
            mapOf(
                EntryRuntimePath.COMBINED_PROVIDER_SETTINGS to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.SETTINGS_AUTOFILL_PICKER to EmptyResultBehavior.ALLOW_EMPTY,
            ),
        )
    )
    put(
        IntentKind.CREDENTIAL_PROVIDER,
        EntryRuntimeDefinition(
            IntentKind.CREDENTIAL_PROVIDER,
            setOf(EntryRuntimePath.CREDENTIAL_MANAGER, EntryRuntimePath.COMBINED_PROVIDER_SETTINGS),
            setOf(EntryRuntimePath.CREDENTIAL_MANAGER, EntryRuntimePath.COMBINED_PROVIDER_SETTINGS),
            mapOf(
                EntryRuntimePath.CREDENTIAL_MANAGER to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.COMBINED_PROVIDER_SETTINGS to EmptyResultBehavior.ALLOW_EMPTY,
            ),
        )
    )
    put(
        IntentKind.NFC_HCE,
        EntryRuntimeDefinition(
            IntentKind.NFC_HCE,
            setOf(EntryRuntimePath.NFC_CARD_EMULATION),
            setOf(EntryRuntimePath.NFC_CARD_EMULATION),
            mapOf(EntryRuntimePath.NFC_CARD_EMULATION to EmptyResultBehavior.ALLOW_EMPTY),
        )
    )

    setOf(IntentKind.NOTIFICATION_LISTENER, IntentKind.WALLPAPER, IntentKind.DREAM).forEach { kind ->
        put(
            kind,
            EntryRuntimeDefinition(
                kind,
                setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE),
                setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE),
                mapOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE to EmptyResultBehavior.ALLOW_EMPTY),
                systemCallerBypassPossible = true,
            )
        )
    }
}

fun IntentKind.runtimeDefinition(): EntryRuntimeDefinition? = ENTRY_RUNTIME_DEFINITIONS[this]
