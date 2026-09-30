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
    val emptyBehavior: Map<EntryRuntimePath, EmptyResultBehavior>,
    val roleName: String? = null,
    val systemCallerBypassPossible: Boolean = false,
) {
    init {
        require(expectedPaths.isNotEmpty()) { "expectedPaths must not be empty for $kind" }
        require(emptyBehavior.keys.all { it in expectedPaths }) {
            "emptyBehavior contains undeclared runtime path for $kind"
        }
    }

    /** Source compatibility only. Runtime audit must use device evidence, not this alias. */
    @Deprecated("Use expectedPaths plus runtime installation evidence")
    val coveredPaths: Set<EntryRuntimePath> get() = expectedPaths
}

/** Final Android runtime paths expected for every selectable entry kind. */
val ENTRY_RUNTIME_DEFINITIONS: Map<IntentKind, EntryRuntimeDefinition> = buildMap {
    fun add(
        kind: IntentKind,
        paths: Set<EntryRuntimePath>,
        restoreOriginal: Set<EntryRuntimePath> = emptySet(),
        roleName: String? = null,
        systemCallerBypassPossible: Boolean = false,
    ) {
        put(
            kind,
            EntryRuntimeDefinition(
                kind = kind,
                expectedPaths = paths,
                emptyBehavior = paths.associateWith { path ->
                    if (path in restoreOriginal) EmptyResultBehavior.RESTORE_ORIGINAL
                    else EmptyResultBehavior.ALLOW_EMPTY
                },
                roleName = roleName,
                systemCallerBypassPossible = systemCallerBypassPossible,
            )
        )
    }

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
        val path = EntryRuntimePath.RESOLVER_ACTIVITY
        add(
            kind = kind,
            paths = setOf(path),
            restoreOriginal = if (kind == IntentKind.PROCESS_TEXT) emptySet() else setOf(path),
        )
    }

    fun role(kind: IntentKind, name: String, extra: Set<EntryRuntimePath> = emptySet()) {
        val paths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY, EntryRuntimePath.ROLE_CONTROLLER) + extra
        add(
            kind = kind,
            paths = paths,
            restoreOriginal = setOf(EntryRuntimePath.RESOLVER_ACTIVITY),
            roleName = name,
        )
    }
    role(
        IntentKind.ASSISTANT,
        "android.app.role.ASSISTANT",
        setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE),
    )
    role(IntentKind.HOME, "android.app.role.HOME")
    role(IntentKind.BROWSER, "android.app.role.BROWSER")

    add(
        kind = IntentKind.CALL_SCREENING,
        paths = setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE, EntryRuntimePath.ROLE_CONTROLLER),
        roleName = "android.app.role.CALL_SCREENING",
    )

    add(
        kind = IntentKind.DIRECT_SHARE,
        paths = setOf(
            EntryRuntimePath.DIRECT_SHARE_CHOOSER,
            EntryRuntimePath.DIRECT_SHARE_EMBEDDED,
            EntryRuntimePath.SHORTCUT_SERVICE,
        ),
    )
    add(IntentKind.SHORTCUT_ITEM, setOf(EntryRuntimePath.SHORTCUT_SERVICE))
    add(
        kind = IntentKind.DOCUMENT_PROVIDER,
        paths = setOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER),
        systemCallerBypassPossible = true,
    )

    add(IntentKind.INPUT_METHOD, setOf(EntryRuntimePath.INPUT_METHOD_MANAGER))
    add(
        IntentKind.ACCESSIBILITY,
        setOf(EntryRuntimePath.ACCESSIBILITY_MANAGER, EntryRuntimePath.PACKAGE_MANAGER_ACTIVITY),
    )
    add(IntentKind.PRINT, setOf(EntryRuntimePath.PRINT_MANAGER))
    add(IntentKind.VPN, setOf(EntryRuntimePath.SETTINGS_VPN))
    add(
        IntentKind.AUTOFILL,
        setOf(EntryRuntimePath.COMBINED_PROVIDER_SETTINGS, EntryRuntimePath.SETTINGS_AUTOFILL_PICKER),
    )
    add(
        IntentKind.CREDENTIAL_PROVIDER,
        setOf(EntryRuntimePath.CREDENTIAL_MANAGER, EntryRuntimePath.COMBINED_PROVIDER_SETTINGS),
    )
    add(IntentKind.NFC_HCE, setOf(EntryRuntimePath.NFC_CARD_EMULATION))

    setOf(IntentKind.NOTIFICATION_LISTENER, IntentKind.WALLPAPER, IntentKind.DREAM).forEach { kind ->
        add(
            kind = kind,
            paths = setOf(EntryRuntimePath.PACKAGE_MANAGER_SERVICE),
            systemCallerBypassPossible = true,
        )
    }
}

fun IntentKind.runtimeDefinition(): EntryRuntimeDefinition? = ENTRY_RUNTIME_DEFINITIONS[this]
