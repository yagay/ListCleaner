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
    val lockedNotificationChannels: Set<String> = emptySet(),
    /** Persistent AOSP category rules; (other) matches missing/unknown categories. */
    val hiddenNotificationCategories: Set<String> = emptySet(),
    /** Actual SystemUI CustomTile labels, scoped to this Android user. */
    val tileLabels: Map<String, String> = emptyMap(),
    /** Preferred order in the add-tile chooser; never changes already-added QS tiles. */
    val tileOrder: List<String> = emptyList(),
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
        require(lockedNotificationChannels.size <= 4000)
        require(lockedNotificationChannels.all { key ->
            val pkg = key.substringBefore('|', "")
            val id = key.substringAfter('|', "")
            PackageIdentity.valid(pkg) && id.isNotEmpty() && id.length <= 512 && id.none(Char::isISOControl)
        })
        require(tileOrder.size <= 2000)
        require(tileOrder.size == tileOrder.distinct().size)
        require(tileOrder.all(::validTileComponent))
        require(tileLabels.size <= 2000)
        require(tileLabels.all { (component, label) ->
            validTileComponent(component) && label.length in 1..64 &&
                label == label.trim() && label.none(Char::isISOControl)
        })
        require(hiddenNotificationCategories.size <= 32)
        require(hiddenNotificationCategories.all { it.length in 1..64 && it.none(Char::isISOControl) })
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

    private fun validTileComponent(component: String): Boolean {
        val pkg = component.substringBefore('/', "")
        val clazz = component.substringAfter('/', "")
        return PackageIdentity.valid(pkg) && component.count { it == '/' } == 1 &&
            clazz.length in 3..240 && clazz.contains('.') &&
            clazz.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '
    fun withTileLabel(component: String, label: String?): SurfaceFilterConfig {
        val next = label?.trim().orEmpty()
        val updated = if (next.isEmpty()) tileLabels - component
            else tileLabels + (component to next)
        return copy(tileLabels = updated).validated()
    }

    fun selected(kind: SurfaceKind): Set<String> = packages[kind].orEmpty()

    fun withNotificationCategoryHidden(category: String, hidden: Boolean): SurfaceFilterConfig =
        copy(hiddenNotificationCategories = if (hidden) hiddenNotificationCategories + category
            else hiddenNotificationCategories - category).validated()

    fun isChannelLocked(pkg: String, id: String): Boolean = "$pkg|$id" in lockedNotificationChannels

    fun withChannelLocked(pkg: String, id: String, locked: Boolean): SurfaceFilterConfig {
        val key = "$pkg|$id"
        return copy(lockedNotificationChannels = if (locked)
            lockedNotificationChannels + key else lockedNotificationChannels - key).validated()
    }

    fun isChannelHidden(pkg: String, id: String): Boolean =
        "$pkg|$id" in hiddenNotificationChannels

    fun withChannelHidden(pkg: String, id: String, hide: Boolean): SurfaceFilterConfig {
        val key = "$pkg|$id"
        if (key in lockedNotificationChannels) return this
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
 }
    }

    /** Saves an explicit component-level order; empty means AOSP's original order. */
    fun withTileOrder(components: List<String>): SurfaceFilterConfig =
        copy(tileOrder = components.distinct()).validated()

    /** Null or blank restores the service's original SystemUI name. */
    fun withTileLabel(component: String, label: String?): SurfaceFilterConfig {
        val next = label?.trim().orEmpty()
        val updated = if (next.isEmpty()) tileLabels - component
            else tileLabels + (component to next)
        return copy(tileLabels = updated).validated()
    }

    fun selected(kind: SurfaceKind): Set<String> = packages[kind].orEmpty()

    fun withNotificationCategoryHidden(category: String, hidden: Boolean): SurfaceFilterConfig =
        copy(hiddenNotificationCategories = if (hidden) hiddenNotificationCategories + category
            else hiddenNotificationCategories - category).validated()

    fun isChannelLocked(pkg: String, id: String): Boolean = "$pkg|$id" in lockedNotificationChannels

    fun withChannelLocked(pkg: String, id: String, locked: Boolean): SurfaceFilterConfig {
        val key = "$pkg|$id"
        return copy(lockedNotificationChannels = if (locked)
            lockedNotificationChannels + key else lockedNotificationChannels - key).validated()
    }

    fun isChannelHidden(pkg: String, id: String): Boolean =
        "$pkg|$id" in hiddenNotificationChannels

    fun withChannelHidden(pkg: String, id: String, hide: Boolean): SurfaceFilterConfig {
        val key = "$pkg|$id"
        if (key in lockedNotificationChannels) return this
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
