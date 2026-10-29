package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** AOSP system UI surfaces; never confuse presentation filtering with disabling components. */
@Serializable
enum class SurfaceKind {
    NOTIFICATION_SHADE,
    LAUNCHER_DRAWER,
    LAUNCHER_PREDICTIONS,
    RECENTS,
    QS_EDITOR,
    DOCUMENT_RECENTS,
    SETTINGS_APPS,
    MEDIA_OUTPUT,
}

/** Explicit package selections per surface, independent for every Android user profile. */
@Serializable
data class SurfaceFilterConfig(val packages: Map<SurfaceKind, Set<String>> = emptyMap()) {
    fun validated(): SurfaceFilterConfig {
        require(packages.size <= SurfaceKind.entries.size)
        require(packages.all { (kind, ids) ->
            ids.size <= 2000 && ids.all { id ->
                if (kind == SurfaceKind.MEDIA_OUTPUT) {
                    id.length in 1..256 &&
                        id.none { it.isISOControl() || it == '|' }
                } else PackageIdentity.valid(id)
            }
        })
        val compact = packages.filterValues { it.isNotEmpty() }
            .mapValues { (_, apps) -> apps.toSet() }
        return if (compact == packages) this else copy(packages = compact)
    }
    fun selected(kind: SurfaceKind): Set<String> = packages[kind].orEmpty()
    fun withSelected(kind: SurfaceKind, apps: Set<String>): SurfaceFilterConfig =
        copy(packages = if (apps.isEmpty()) packages - kind else packages + (kind to apps))
            .validated()
}
