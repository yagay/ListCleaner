package com.yagay.ListCleaner.domain

/** Runtime surfaces that can independently decide which entries are shown to the user. */
enum class EntryRuntimePath {
    RESOLVER_ACTIVITY,
    ROLE_CONTROLLER,
    DIRECT_SHARE_CHOOSER,
    DIRECT_SHARE_EMBEDDED,
    SHORTCUT_SERVICE,
    PACKAGE_MANAGER_PROVIDER,
    PACKAGE_MANAGER_SERVICE,
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

/**
 * Central runtime coverage registry.
 *
 * Discovery, UI and hooks historically grew independently. This table documents the actual final
 * Android surfaces for every selectable entry kind so diagnostics and future hook work cannot assume
 * that a PackageManager probe is also the final system UI source.
 */
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
                kind = kind,
                expectedPaths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY),
                coveredPaths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY),
                emptyBehavior = mapOf(
                    EntryRuntimePath.RESOLVER_ACTIVITY to if (kind == IntentKind.PROCESS_TEXT) {
                        EmptyResultBehavior.ALLOW_EMPTY
                    } else {
                        EmptyResultBehavior.RESTORE_ORIGINAL
                    }
                ),
            )
        )
    }

    put(
        IntentKind.ASSISTANT,
        EntryRuntimeDefinition(
            kind = IntentKind.ASSISTANT,
            expectedPaths = setOf(
                EntryRuntimePath.RESOLVER_ACTIVITY,
                EntryRuntimePath.PACKAGE_MANAGER_SERVICE,
                EntryRuntimePath.ROLE_CONTROLLER,
            ),
            coveredPaths = setOf(
                EntryRuntimePath.RESOLVER_ACTIVITY,
                EntryRuntimePath.PACKAGE_MANAGER_SERVICE,
                EntryRuntimePath.ROLE_CONTROLLER,
            ),
            emptyBehavior = mapOf(
                EntryRuntimePath.RESOLVER_ACTIVITY to EmptyResultBehavior.RESTORE_ORIGINAL,
                EntryRuntimePath.PACKAGE_MANAGER_SERVICE to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.ROLE_CONTROLLER to EmptyResultBehavior.ALLOW_EMPTY,
            ),
            roleName = "android.app.role.ASSISTANT",
        )
    )
    put(
        IntentKind.HOME,
        EntryRuntimeDefinition(
            kind = IntentKind.HOME,
            expectedPaths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY, EntryRuntimePath.ROLE_CONTROLLER),
            coveredPaths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY, EntryRuntimePath.ROLE_CONTROLLER),
            emptyBehavior = mapOf(
                EntryRuntimePath.RESOLVER_ACTIVITY to EmptyResultBehavior.RESTORE_ORIGINAL,
                EntryRuntimePath.ROLE_CONTROLLER to EmptyResultBehavior.ALLOW_EMPTY,
            ),
            roleName = "android.app.role.HOME",
        )
    )
    put(
        IntentKind.BROWSER,
        EntryRuntimeDefinition(
            kind = IntentKind.BROWSER,
            expectedPaths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY, EntryRuntimePath.ROLE_CONTROLLER),
            coveredPaths = setOf(EntryRuntimePath.RESOLVER_ACTIVITY, EntryRuntimePath.ROLE_CONTROLLER),
            emptyBehavior = mapOf(
                EntryRuntimePath.RESOLVER_ACTIVITY to EmptyResultBehavior.RESTORE_ORIGINAL,
                EntryRuntimePath.ROLE_CONTROLLER to EmptyResultBehavior.ALLOW_EMPTY,
            ),
            roleName = "android.app.role.BROWSER",
        )
    )
    put(
        IntentKind.DIRECT_SHARE,
        EntryRuntimeDefinition(
            kind = IntentKind.DIRECT_SHARE,
            expectedPaths = setOf(
                EntryRuntimePath.DIRECT_SHARE_CHOOSER,
                EntryRuntimePath.DIRECT_SHARE_EMBEDDED,
            ),
            coveredPaths = setOf(
                EntryRuntimePath.DIRECT_SHARE_CHOOSER,
                EntryRuntimePath.DIRECT_SHARE_EMBEDDED,
            ),
            emptyBehavior = mapOf(
                EntryRuntimePath.DIRECT_SHARE_CHOOSER to EmptyResultBehavior.ALLOW_EMPTY,
                EntryRuntimePath.DIRECT_SHARE_EMBEDDED to EmptyResultBehavior.ALLOW_EMPTY,
            ),
        )
    )
    put(
        IntentKind.SHORTCUT_ITEM,
        EntryRuntimeDefinition(
            kind = IntentKind.SHORTCUT_ITEM,
            expectedPaths = setOf(EntryRuntimePath.SHORTCUT_SERVICE),
            coveredPaths = setOf(EntryRuntimePath.SHORTCUT_SERVICE),
            emptyBehavior = mapOf(EntryRuntimePath.SHORTCUT_SERVICE to EmptyResultBehavior.ALLOW_EMPTY),
        )
    )
    put(
        IntentKind.DOCUMENT_PROVIDER,
        EntryRuntimeDefinition(
            kind = IntentKind.DOCUMENT_PROVIDER,
            expectedPaths = setOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER),
            coveredPaths = setOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER),
            emptyBehavior = mapOf(EntryRuntimePath.PACKAGE_MANAGER_PROVIDER to EmptyResultBehavior.ALLOW_EMPTY),
            systemCallerBypassPossible = true,
        )
    )

    SYSTEM_SERVICE_ENTRY_DEFINITIONS.map { it.kind }.distinct().forEach { kind ->
        val roleName = when (kind) {
            IntentKind.CALL_SCREENING -> "android.app.role.CALL_SCREENING"
            else -> null
        }
        val expected = buildSet {
            add(EntryRuntimePath.PACKAGE_MANAGER_SERVICE)
            if (roleName != null) add(EntryRuntimePath.ROLE_CONTROLLER)
        }
        put(
            kind,
            EntryRuntimeDefinition(
                kind = kind,
                expectedPaths = expected,
                coveredPaths = expected,
                emptyBehavior = buildMap {
                    put(EntryRuntimePath.PACKAGE_MANAGER_SERVICE, EmptyResultBehavior.ALLOW_EMPTY)
                    if (roleName != null) put(EntryRuntimePath.ROLE_CONTROLLER, EmptyResultBehavior.ALLOW_EMPTY)
                },
                roleName = roleName,
                systemCallerBypassPossible = true,
            )
        )
    }
}

fun IntentKind.runtimeDefinition(): EntryRuntimeDefinition? = ENTRY_RUNTIME_DEFINITIONS[this]
