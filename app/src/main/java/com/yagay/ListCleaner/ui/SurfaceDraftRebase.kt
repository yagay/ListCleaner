package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.SurfaceFilterConfig

/** Three-way staged-draft merge; other Lists editors' concurrent changes survive. */
internal fun rebaseSurfaceDraft(base: SurfaceFilterConfig, local: SurfaceFilterConfig,
    latest: SurfaceFilterConfig): SurfaceFilterConfig {
    fun <T> mergeSet(before: Set<T>, after: Set<T>, newest: Set<T>): Set<T> =
        (newest - (before - after)) + (after - before)
    fun <K, V> mergeMap(before: Map<K, Set<V>>, after: Map<K, Set<V>>,
        newest: Map<K, Set<V>>): Map<K, Set<V>> =
        (before.keys + after.keys + newest.keys).mapNotNull { key ->
            val merged = mergeSet(before[key].orEmpty(), after[key].orEmpty(), newest[key].orEmpty())
            key.takeIf { merged.isNotEmpty() }?.let { it to merged }
        }.toMap()
    val order = latest.order.toMutableMap()
    (base.order.keys + local.order.keys).forEach { kind ->
        if (base.order[kind] != local.order[kind]) {
            val desired = local.order[kind]
            if (desired.isNullOrEmpty()) order.remove(kind) else order[kind] = desired
        }
    }
    // A Root tile name may be saved while a Lists editor still has a draft.
    // Keep external changes, while allowing this draft's own label edits.
    val tileLabels = latest.tileLabels.toMutableMap()
    (base.tileLabels.keys + local.tileLabels.keys).forEach { component ->
        if (base.tileLabels[component] != local.tileLabels[component]) {
            val title = local.tileLabels[component]
            if (title == null) tileLabels.remove(component) else tileLabels[component] = title
        }
    }
    return latest.copy(
        tileLabels = tileLabels,
        tileOrder = if (base.tileOrder != local.tileOrder) local.tileOrder else latest.tileOrder,
        packages = mergeMap(base.packages, local.packages, latest.packages),
        locks = mergeMap(base.locks, local.locks, latest.locks),
        order = order,
        hiddenNotificationChannels = mergeSet(base.hiddenNotificationChannels,
            local.hiddenNotificationChannels, latest.hiddenNotificationChannels),
        lockedNotificationChannels = mergeSet(base.lockedNotificationChannels,
            local.lockedNotificationChannels, latest.lockedNotificationChannels),
        hiddenNotificationCategories = mergeSet(base.hiddenNotificationCategories,
            local.hiddenNotificationCategories, latest.hiddenNotificationCategories),
    ).validated()
}
