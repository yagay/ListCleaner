package com.yagay.ListCleaner.domain

import kotlinx.serialization.Serializable

/** Presentation-only AOSP surfaces; no component state or task lifecycle is changed. */
@Serializable
enum class SurfaceKind {
    NOTIFICATION_SHADE, LAUNCHER_DRAWER, LAUNCHER_PREDICTIONS, RECENTS,
    QS_EDITOR, DOCUMENT_RECENTS, SETTINGS_APPS, MEDIA_OUTPUT,
}

fun SurfaceKind.supportsSurfaceOrdering(): Boolean = this in setOf(
    SurfaceKind.LAUNCHER_DRAWER, SurfaceKind.LAUNCHER_PREDICTIONS,
    SurfaceKind.QS_EDITOR, SurfaceKind.SETTINGS_APPS,
)

/** User-specific hide, lock and preference-order rules, serialized with existing backups. */
@Serializable
data class SurfaceFilterConfig(
    val packages: Map<SurfaceKind, Set<String>> = emptyMap(),
    val locks: Map<SurfaceKind, Set<String>> = emptyMap(),
    val order: Map<SurfaceKind, List<String>> = emptyMap(),
    /** Specific shade channels, keyed by package and AOSP channel ID. */
    val hiddenNotificationChannels: Set<String> = emptySet(),
) {
    private fun allowed(kind: SurfaceKind, value: String): Boolean =
        if (kind == SurfaceKind.MEDIA_OUTPUT) value.length in 1..256 &&
            value.none { it.isISOControl() || it == '|' }
        else PackageIdentity.valid(value)

    fun validated(): SurfaceFilterConfig {
        require(packages.size <= SurfaceKind.entries.size)
        require(locks.size <= SurfaceKind.entries.size)
        require(order.size <= SurfaceKind.entries.size)
        require(hiddenNotificationChannels.size <= 4000)
        require(hiddenNotificationChannels.all { key ->
            val pkg = key.substringBefore('|', "")
            val channelId = key.substringAfter('|', "")
            PackageIdentity.valid(pkg) && channelId.isNotEmpty() &&
                channelId.length <= 512 && channelId.none(Char::isISOControl)
        })
        require(packages.all { (kind, ids) -> ids.size <= 2000 && ids.all { allowed(kind, it) } })
        require(locks.all { (kind, ids) ->
            ids.size <= 2000 && ids.all { allowed(kind, it) }
        })
        require(order.all { (kind, values) ->
            kind.supportsSurfaceOrdering() && values.size <= 2000 &&
                values.size == values.distinct().size &&
                values.all { allowed(kind, it) }
        })
        val compactPackages = packages.filterValues { it.isNotEmpty() }
            .mapValues { (_, values) -> values.toSet() }
        val compactLocks = locks.filterValues { it.isNotEmpty() }
            .mapValues { (_, values) -> values.toSet() }
        val compactOrder = order.filterValues { it.isNotEmpty() }
            .mapValues { (_, values) -> values.toList() }
        return if (compactPackages == packages && compactLocks == locks &&
            compactOrder == order) this else copy(
            packages = compactPackages, locks = compactLocks, order = compactOrder,
        )
    }

    fun selected(kind: SurfaceKind): Set<String> = packages[kind].orEmpty()

    fun isChannelHidden(pkg: String, id: String): Boolean =
        "$pkg|$id" in hiddenNotificationChannels

    fun withChannelHidden(pkg: String, id: String, hide: Boolean): SurfaceFilterConfig {
        val key = "$pkg|$id"
        return copy(hiddenNotificationChannels = if (hide)
            hiddenNotificationChannels + key else hiddenNotificationChannels - key).validated()
    }
    fun locked(kind: SurfaceKind): Set<String> = locks[kind].orEmpty()
    fun priorities(kind: SurfaceKind): List<String> = order[kind].orEmpty()

    fun withSelected(kind: SurfaceKind, apps: Set<String>): SurfaceFilterConfig {
        val keep = locked(kind)
        val selected = (apps - (keep - selected(kind))) + (keep intersect selected(kind))
        return copy(packages = if (selected.isEmpty()) packages - kind
            else packages + (kind to selected)).validated()
    }

    fun withLocked(kind: SurfaceKind, apps: Set<String>): SurfaceFilterConfig =
        copy(locks = if (apps.isEmpty()) locks - kind else locks + (kind to apps))
            .validated()

    fun withPriorities(kind: SurfaceKind, apps: List<String>): SurfaceFilterConfig {
        require(kind.supportsSurfaceOrdering())
        return copy(order = if (apps.isEmpty()) order - kind
            else order + (kind to apps.distinct())).validated()
    }

    fun movePriority(kind: SurfaceKind, item: String, direction: Int): SurfaceFilterConfig {
        require(kind.supportsSurfaceOrdering())
        val original = priorities(kind).toMutableList()
        if (item !in original) original.add(item)
        val index = original.indexOf(item)
        val destination = (index + direction).coerceIn(0, original.lastIndex)
        if (index != destination) {
            original.removeAt(index)
            original.add(destination, item)
        }
        return withPriorities(kind, original)
    }
}
